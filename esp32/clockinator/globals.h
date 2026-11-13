#pragma once
#include "Version.h"

/*
 * globals.h
 * Shared declarations for every .ino in this sketch.
 *
 * Arduino IDE inserts auto-generated function prototypes BEFORE the first
 * #include in the merged translation unit. Placing all required headers and
 * type definitions here, and including this file first in each .ino, ensures
 * that every type used in a function signature is already known when the IDE
 * generates those prototypes.
 */

#include <time.h>
#include <string>
#include <set>
#include <map>
#include <vector>
#include <ArduinoJson.h>
#include "driver/i2s.h"

/* -------------------------------------------------------------------------
 * I2S port shared between sound.ino and clockinator.ino
 * -------------------------------------------------------------------------*/
#define I2S_PORT I2S_NUM_0

/* -------------------------------------------------------------------------
 * RingtoneType
 * Mirrors the Android RingtoneType enum. Wire strings must match exactly.
 * -------------------------------------------------------------------------*/
enum RingtoneType {
    RT_LOCK_PHONE = 0,  // "lock_phone"
    RT_BEDTIME    = 1,  // "bedtime"
    RT_WAKEUP     = 2,  // "wakeup"
    RT_UNKNOWN    = 3
};

static inline RingtoneType ringtoneFromWire(const char* s) {
    if (!s) return RT_UNKNOWN;
    if (strcmp(s, "lock") == 0 || strcmp(s, "lock_phone") == 0) return RT_LOCK_PHONE;
    if (strcmp(s, "bed")  == 0 || strcmp(s, "bedtime") == 0) return RT_BEDTIME;
    if (strcmp(s, "wake") == 0 || strcmp(s, "wakeup") == 0) return RT_WAKEUP;
    return RT_UNKNOWN;
}

static inline const char* ringtoneToWire(RingtoneType t) {
    switch (t) {
        case RT_LOCK_PHONE: return "lock_phone";
        case RT_BEDTIME:    return "bedtime";
        case RT_WAKEUP:     return "wakeup";
        default:            return "unknown";
    }
}

/* -------------------------------------------------------------------------
 * RingtoneSlot
 * Configuration for one of the three trigger times in an alarm.
 * -------------------------------------------------------------------------*/
struct RingtoneSlot {
    int  hour         = 0;
    int  minute       = 0;
    bool ringOnDevice = true;
    bool ringOnPhone  = true;   // informational only on ESP side
    bool enabled      = true;
};

/* -------------------------------------------------------------------------
 * Recurrence
 * -------------------------------------------------------------------------*/
enum RecurrenceType { REC_DAILY, REC_WEEKLY, REC_INTERVAL };

struct Recurrence {
    RecurrenceType type     = REC_DAILY;
    std::set<int>  days;        // weekdays 1=Mon..7=Sun, for REC_WEEKLY
    int            interval = 1; // days between firings, for REC_INTERVAL
    long           anchorEpochDay = 0; // local calendar date, shared with Android

    time_t nextOccurrence(time_t from_time, int hour, int minute) const;
};

/* -------------------------------------------------------------------------
 * Alarm
 * -------------------------------------------------------------------------*/
struct Alarm {
    std::vector<int> snoozeDurations = {5, 10, 30, 60};
    std::string  id;
    bool         isEnabled    = true;
    std::string  ringtoneFile;  // LittleFS path e.g. "/alarm1.wav"; empty = melody

    RingtoneSlot lockPhone;
    RingtoneSlot bedtime;
    RingtoneSlot wakeup;

    Recurrence   recurrence;

    /* Bitmask of dismissed ringtone slots (bit 0 = lock_phone, 1 = bedtime, 2 = wakeup). */
    uint8_t dismissedMask = 0;
    time_t dismissedUntil[3] = {0, 0, 0};

    bool isDismissed(RingtoneType t) const {
        return (t < 3) && (dismissedMask & (1u << t));
    }
    void setDismissed(RingtoneType t, bool v);

    const RingtoneSlot& slot(RingtoneType t) const {
        switch (t) {
            case RT_LOCK_PHONE: return lockPhone;
            case RT_BEDTIME:    return bedtime;
            case RT_WAKEUP:     return wakeup;
            default:            return wakeup;
        }
    }

    /* Returns the next trigger time for slot [t] after [now], or 0 if disabled. */
    time_t nextTrigger(time_t now, RingtoneType t) const;

    /* Returns the earliest trigger time across all active slots, and sets [outType]. */
    time_t nextAnyTrigger(time_t now, RingtoneType& outType) const;
};

/* -------------------------------------------------------------------------
 * Global state  (defined in clockinator.ino)
 * -------------------------------------------------------------------------*/
extern time_t                           start_time;
extern time_t                           next_alarm;
extern RingtoneType                     next_alarm_type;
extern std::string                      next_alarm_id;
extern std::map<std::string, Alarm>     alarms;
extern volatile bool                    isRinging;
extern volatile RingtoneType            firingRingtone;
extern std::string                      firingAlarmId;
extern const char*                      activeRingtoneFile;
extern bool                             is_bluetooth_connected;
extern bool                             is_wifi_connected;

/* -------------------------------------------------------------------------
 * Cross-core command queue
 *
 * The BLE callback runs on core 0. The main loop (display, sound) runs on
 * core 1. Calling stopAlarm() / snoozeAlarm() directly from the BLE task
 * would race with the main loop over shared state AND call drawClock() /
 * i2s_zero_dma_buffer() from the wrong core (SPI and I2S drivers are not
 * thread-safe on ESP32).
 *
 * Solution: the BLE task posts a lightweight PendingCommand into a FreeRTOS
 * queue. The main loop drains it at the top of every iteration before doing
 * anything else. All SPI / I2S calls therefore stay on core 1.
 * -------------------------------------------------------------------------*/
enum PendingCommandType {
    CMD_NONE,
    CMD_STOP,
    CMD_SNOOZE,
    CMD_SYNC_PUSH,  /* alarm list was updated; recalculate next trigger */
    CMD_BLE_CONNECTED,
    CMD_BLE_DISCONNECTED,
    CMD_DISPLAY_REFRESH
};

struct PendingCommand {
    PendingCommandType type    = CMD_NONE;
    int                minutes = 0;   /* used by CMD_SNOOZE */
};

/* -------------------------------------------------------------------------
 * Cross-file function declarations
 * -------------------------------------------------------------------------*/

/* clockinator.ino */
void setNextAlarm();
void startRinging();
void setRingTimeoutMinutes(int minutes);
void serviceRingTimeout();
void stopAlarm();
void snoozeAlarm(int minutes);
void initPhysicalControls();
void setClockLight(bool enabled);
void setClockLightColor(int red, int green, int blue);
void notifyLightStatus();
void servicePhysicalControls();
void applyPhysicalControls();
void cyclePhysicalSnooze();
void stopFromPhysicalButton();
bool hasPendingSnooze();
bool selectAlarmControlTarget(const char* id, RingtoneType type);
void postCommand(PendingCommandType type, int minutes = 0);
extern QueueHandle_t cmdQueue;

/* display.ino */
void initDisplay();
void show(const char* line1);
void show(const char* line1, const char* line2);
void drawClock();
extern time_t lastClockMinute;
void requestFullClockRefresh();
void queueInitialClockRefresh();
void noteClockLinkChange();
void serviceClockRefresh();
void setDisplayRefreshMinutes(unsigned minutes);
bool clockConnectionsReadyForRefresh();
void serviceIncomingBluetooth();

/* sound.ino */
void initAlarmVolume();
int getAlarmVolume();
bool requestAlarmVolume(int level);
void serviceAlarmVolume();
void notifyVolumeStatus();
void refreshVolumeDisplay();
void initAudioTest();
void requestAudioTest(const char* filename, bool stop);
void serviceAudioTest();
void cancelAudioTest();
void setupI2S();
void playAlarm();
bool playWavFile(const char* path);

/* bluetooth.ino */
void initBluetoothServer();
void notifyPhone(const char* json);
void processIncomingCommand(String raw);

/* clockinator.ino */
void initWifi();
void serviceWifi();
void queueWifiCredentials(const char* ssid, const char* password);
void notifyWifiClient(const char* message);
void notifyWifiStatus();
