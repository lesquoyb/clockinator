#include "globals.h"
#include <LittleFS.h>
#include <freertos/FreeRTOS.h>
#include <freertos/queue.h>

/*
 * clockinator.ino
 * Global state definitions, alarm scheduling, and main loop.
 *
 * Thread-safety model
 * -------------------
 * Core 0: BLE RTOS task (bluetooth.ino callbacks). Must NOT touch SPI
 *         (e-paper) or I2S (speaker) directly. Posts PendingCommand items
 *         into cmdQueue instead.
 * Core 1: Arduino loop(). Owns the display and the I2S driver. Drains
 *         cmdQueue at the top of every iteration and executes the commands.
 *
 * Shared state accessed from both cores uses 'volatile' where a single
 * word-sized read/write is sufficient. Multi-field updates are protected
 * by only writing from the loop core (commands are queued, not applied
 * directly by the BLE task).
 */

/* -------------------------------------------------------------------------
 * Cross-core command queue (depth = 8, each item is a PendingCommand)
 * -------------------------------------------------------------------------*/
QueueHandle_t cmdQueue;

/* Post a command from any task/core without blocking. */
void postCommand(PendingCommandType type, int minutes) {
    PendingCommand cmd;
    cmd.type    = type;
    cmd.minutes = minutes;
    /* xQueueSendFromISR would be needed inside an ISR; xQueueSend is fine
       from a regular FreeRTOS task (BLE callback). */
    xQueueSend(cmdQueue, &cmd, 0);
}

/* -------------------------------------------------------------------------
 * Global state definitions  (extern declarations live in globals.h)
 * -------------------------------------------------------------------------*/
time_t          start_time          = 0;
time_t          next_alarm          = 0;
RingtoneType    next_alarm_type     = RT_UNKNOWN;
std::string     next_alarm_id;
bool            is_client_connected = false;

std::map<std::string, Alarm> alarms;

volatile bool         isRinging      = false;
volatile RingtoneType firingRingtone = RT_UNKNOWN;
std::string           firingAlarmId;
const char*           activeRingtoneFile = "";

/* -------------------------------------------------------------------------
 * setNextAlarm
 * Recalculates next_alarm / next_alarm_type / next_alarm_id.
 * Must only be called from core 1 (main loop).
 * Does NOT call drawClock(): the caller is responsible for refreshing the
 * display at the right time to avoid cross-core SPI access.
 * -------------------------------------------------------------------------*/
void setNextAlarm() {
    time_t now      = start_time + (time_t)(millis() / 1000);
    time_t earliest = 0;
    next_alarm_type = RT_UNKNOWN;
    next_alarm_id.clear();

    for (auto& kv : alarms) {
        const Alarm& a = kv.second;
        if (!a.isEnabled) continue;

        RingtoneType t;
        time_t ts = a.nextAnyTrigger(now, t);
        if (ts > 0 && (earliest == 0 || ts < earliest)) {
            earliest        = ts;
            next_alarm_type = t;
            next_alarm_id   = a.id;
        }
    }

    next_alarm = earliest;
}

/* -------------------------------------------------------------------------
 * startRinging
 * Must only be called from core 1.
 * -------------------------------------------------------------------------*/
void startRinging() {
    auto it = alarms.find(firingAlarmId);
    if (it != alarms.end() && !it->second.ringtoneFile.empty()) {
        activeRingtoneFile = it->second.ringtoneFile.c_str();
    } else {
        activeRingtoneFile = "";
    }
    isRinging = true;
}

/* -------------------------------------------------------------------------
 * stopAlarm
 * Must only be called from core 1.
 * Silences the current alarm, notifies the phone, then reschedules.
 * -------------------------------------------------------------------------*/
void stopAlarm() {
    isRinging          = false;
    activeRingtoneFile = "";
    i2s_zero_dma_buffer(I2S_PORT);

    /* Notify the phone so it mirrors the stop. */
    if (!firingAlarmId.empty() && firingRingtone != RT_UNKNOWN) {
        char buf[256];
        snprintf(buf, sizeof(buf),
            "{\"action\":\"STOP_ALARM\",\"alarmId\":\"%s\",\"ringtone\":\"%s\"}",
            firingAlarmId.c_str(), ringtoneToWire(firingRingtone));
        notifyPhone(buf);
    }

    /* Clear the dismiss flag so the alarm fires again next cycle. */
    auto it = alarms.find(firingAlarmId);
    if (it != alarms.end())
        it->second.setDismissed(firingRingtone, false);

    next_alarm = 0;
    setNextAlarm();
    firingAlarmId.clear();
    firingRingtone = RT_UNKNOWN;
    drawClock();   /* safe: we are on core 1 */
}

/* -------------------------------------------------------------------------
 * snoozeAlarm
 * Must only be called from core 1.
 * -------------------------------------------------------------------------*/
void snoozeAlarm(int minutes) {
    isRinging          = false;
    activeRingtoneFile = "";
    i2s_zero_dma_buffer(I2S_PORT);

    /* Notify the phone. */
    if (!firingAlarmId.empty() && firingRingtone != RT_UNKNOWN) {
        char buf[256];
        snprintf(buf, sizeof(buf),
            "{\"action\":\"SNOOZE_ALARM\",\"alarmId\":\"%s\",\"ringtone\":\"%s\",\"minutes\":%d}",
            firingAlarmId.c_str(), ringtoneToWire(firingRingtone), minutes);
        notifyPhone(buf);
    }

    time_t now      = start_time + (time_t)(millis() / 1000);
    next_alarm      = now + (time_t)(minutes * 60);
    next_alarm_type = firingRingtone;
    next_alarm_id   = firingAlarmId;
    firingAlarmId.clear();
    firingRingtone = RT_UNKNOWN;

    struct tm ti;
    localtime_r(&next_alarm, &ti);
    char buf[32];
    strftime(buf, sizeof(buf), "Snooze: %H:%M", &ti);
    show("Snoozed", buf);
}

/* -------------------------------------------------------------------------
 * drainCommandQueue
 * Called at the top of loop(): applies all pending commands posted by the
 * BLE task. Everything here runs on core 1, so SPI/I2S accesses are safe.
 * Returns true if any command was processed (caller may want to redraw).
 * -------------------------------------------------------------------------*/
static bool drainCommandQueue() {
    PendingCommand cmd;
    bool anyProcessed = false;

    while (xQueueReceive(cmdQueue, &cmd, 0) == pdTRUE) {
        anyProcessed = true;
        switch (cmd.type) {
            case CMD_STOP:
                stopAlarm();
                break;
            case CMD_SNOOZE:
                snoozeAlarm(cmd.minutes);
                break;
            case CMD_SYNC_PUSH:
                /* Alarm list was updated by the BLE task; recalculate. */
                setNextAlarm();
                drawClock();
                break;
            case CMD_BLE_CONNECTED:
                is_client_connected = true;
                drawClock();
                break;
            case CMD_BLE_DISCONNECTED:
                is_client_connected = false;
                drawClock();
                break;
            default:
                break;
        }
    }
    return anyProcessed;
}

/* -------------------------------------------------------------------------
 * setup / loop
 * -------------------------------------------------------------------------*/
void setup() {
    /* Create the command queue before starting anything that might use it. */
    cmdQueue = xQueueCreate(8, sizeof(PendingCommand));

    setupI2S();

    if (!LittleFS.begin(true))
        show("FS Error", "LittleFS failed");

    initDisplay();
    initBluetoothServer();
}

static time_t last_update = 0;

void loop() {
    /* Drain any commands posted by the BLE task first. */
    drainCommandQueue();

    time_t now = start_time + (time_t)(millis() / 1000);

    /* Refresh the clock independently of alarm playback. */
    if (now / 60 != last_update / 60) {
        drawClock();
        last_update = now;
    }

    if (!isRinging && next_alarm > 0 && now >= next_alarm) {
        firingRingtone = next_alarm_type;
        firingAlarmId  = next_alarm_id;
        next_alarm     = 0;
        startRinging();
    }

    if (isRinging) playAlarm();

    delay(100);
}
