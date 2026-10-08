#include "globals.h"
#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <LittleFS.h>
#include "ClockTime.h"

/*
 * bluetooth.ino
 * BLE server and shared command parser for BLE and local TCP messages.
 *
 * Phone -> ESP  (BLE write or TCP line):
 *   SYNC, STOP_ALARM, SNOOZE_ALARM, DISMISS_NEXT, PUSH_RINGTONE,
 *   UPLOAD_BEGIN, UPLOAD_CHUNK, UPLOAD_END, DELETE_RINGTONE
 *
 * ESP -> Phone  (Notify characteristic):
 *   STOP_ALARM, SNOOZE_ALARM, DISMISS_NEXT,
 *   CHUNK_ACK:<n>, UPLOAD_OK, UPLOAD_ERR
 */

#define SERVICE_UUID "12345678-1234-1234-1234-123456789abc"
#define RX_UUID      "12345678-1234-1234-1234-123456789abd"
#define TX_UUID      "12345678-1234-1234-1234-123456789abe"

static const char* DEVICE_NAME = "ESP32-Bapresearch";

BLECharacteristic* txCharacteristic;

/* JSON action constants (must match SyncPayload.kt) */
static const char* A_SYNC             = "SYNC";
static const char* A_STOP_ALARM       = "STOP_ALARM";
static const char* A_SNOOZE_ALARM     = "SNOOZE_ALARM";
static const char* A_DISMISS_NEXT     = "DISMISS_NEXT";
static const char* A_PUSH_RINGTONE    = "PUSH_RINGTONE";
static const char* A_UPLOAD_BEGIN     = "UPLOAD_BEGIN";
static const char* A_UPLOAD_CHUNK     = "UPLOAD_CHUNK";
static const char* A_UPLOAD_END       = "UPLOAD_END";
static const char* A_DELETE_RINGTONE  = "DELETE_RINGTONE";
static const char* A_WIFI_CONFIG      = "WIFI_CONFIG";

/* -------------------------------------------------------------------------
 * notifyPhone  - send a message to the phone over BLE and TCP
 * BLE invokes this on its task; TCP invokes it from the main loop. 
 * -------------------------------------------------------------------------*/
void notifyPhone(const char* json) {
    if (txCharacteristic) {
        txCharacteristic->setValue(json);
        txCharacteristic->notify();
    }
    notifyWifiClient(json);
}

/* -------------------------------------------------------------------------
 * Upload session state
 * -------------------------------------------------------------------------*/
static struct UploadSession {
    bool     active        = false;
    String   filename;
    File     file;
    int      expectedIndex = 0;
    uint32_t totalSize     = 0;
    uint32_t receivedSize  = 0;
} uploadSession;

static uint32_t uploadCrc = 0xFFFFFFFFu;

static void crcUpdate(const uint8_t* data, size_t len) {
    for (size_t i = 0; i < len; i++) {
        uploadCrc ^= data[i];
        for (int b = 0; b < 8; b++)
            uploadCrc = (uploadCrc >> 1) ^ (0xEDB88320u & (uint32_t)(-(int32_t)(uploadCrc & 1)));
    }
}
static uint32_t crcFinalise() { return uploadCrc ^ 0xFFFFFFFFu; }

/* -------------------------------------------------------------------------
 * Base64 decoder
 * -------------------------------------------------------------------------*/
static const int8_t B64[256] = {
    -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
    -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
    -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,62,-1,-1,-1,63,
    52,53,54,55,56,57,58,59,60,61,-1,-1,-1,-1,-1,-1,
    -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9,10,11,12,13,14,
    15,16,17,18,19,20,21,22,23,24,25,-1,-1,-1,-1,-1,
    -1,26,27,28,29,30,31,32,33,34,35,36,37,38,39,40,
    41,42,43,44,45,46,47,48,49,50,51,-1,-1,-1,-1,-1,
    -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
    -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
    -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
    -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
    -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
    -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
    -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,
    -1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1,-1
};

static int b64Decode(const char* src, size_t srcLen, uint8_t* out, size_t outMax) {
    size_t   n    = 0;
    uint32_t buf  = 0;
    int      bits = 0;
    for (size_t i = 0; i < srcLen; i++) {
        int8_t v = B64[(uint8_t)src[i]];
        if (v < 0) continue;
        buf = (buf << 6) | (uint32_t)v;
        bits += 6;
        if (bits >= 8) {
            bits -= 8;
            if (n >= outMax) return -1;
            out[n++] = (uint8_t)(buf >> bits);
        }
    }
    return (int)n;
}

/* -------------------------------------------------------------------------
 * Recurrence JSON parser
 * -------------------------------------------------------------------------*/
static Recurrence parseRecurrence(JsonObject rec) {
    Recurrence r;
    if (rec.isNull()) { r.type = REC_DAILY; return r; }

    const char* t = rec["type"] | "daily";
    if (strcmp(t, "weekly") == 0) {
        r.type = REC_WEEKLY;
        std::string ds(rec["days"] | "");
        std::string tok;
        for (size_t i = 0; i <= ds.size(); i++) {
            char c = (i < ds.size()) ? ds[i] : ',';
            if (c == ',') {
                if (!tok.empty()) {
                    int d = atoi(tok.c_str());
                    if (d >= 1 && d <= 7) r.days.insert(d);
                    tok.clear();
                }
            } else { tok += c; }
        }
        if (r.days.empty()) r.type = REC_DAILY;
    } else if (strcmp(t, "interval") == 0) {
        r.type     = REC_INTERVAL;
        r.interval = max(1, (int)(rec["intervalDays"] | 1));
        r.interval = min(365, r.interval);
        r.anchorEpochDay = rec["anchorEpochDay"] | (long)((start_time + millis() / 1000) / 86400);
    } else {
        r.type = REC_DAILY;
    }
    return r;
}

/* -------------------------------------------------------------------------
 * RingtoneSlot JSON parser
 * -------------------------------------------------------------------------*/
static RingtoneSlot parseSlot(JsonObject obj, RingtoneType type) {
    RingtoneSlot s;
    s.hour         = obj["h"]           | 0;
    s.minute       = obj["m"]           | 0;
    s.ringOnDevice = obj["ringDevice"]  | false;
    s.ringOnPhone  = obj["ringPhone"]   | false;

    // Those are not transmitted as they are mandatory by design
    switch (type){
        case RT_LOCK_PHONE:
            s.ringOnPhone = true;
            break;
        case RT_WAKEUP:
            s.ringOnDevice = true;
            break;
    }
    s.enabled      = true; // disabled slots are not transmitted
    return s;
}

/* -------------------------------------------------------------------------
 * Alarm JSON parser
 * -------------------------------------------------------------------------*/
static void parseAndStoreAlarm(JsonObject obj) {
    const char* idStr = obj["id"] | "";
    if (strlen(idStr) == 0) return;

    Alarm a;
    a.id        = std::string(idStr);
    a.isEnabled = true;
    if (obj["snoozeDurations"].is<JsonArray>()) {
        std::vector<int> durations;
        for (JsonVariant value : obj["snoozeDurations"].as<JsonArray>()) {
            if (value.is<int>() && value.as<int>() >= 1 && value.as<int>() <= 1440 && durations.size() < 16)
                durations.push_back(value.as<int>());
        }
        if (!durations.empty()) a.snoozeDurations = durations;
    }
    a.lockPhone.enabled = false;
    a.bedtime.enabled = false;
    a.wakeup.enabled = false;

    const char* rf = obj["file"] | "";
    a.ringtoneFile = (strlen(rf) > 0) ? (std::string("/") + rf) : std::string("");

    /* Three-slot model */
    if (obj.containsKey("lock")) {
        a.lockPhone = parseSlot(obj["lock"].as<JsonObject>(), RT_LOCK_PHONE);
    }
    if (obj.containsKey("bed")){
        a.bedtime   = parseSlot(obj["bed"].as<JsonObject>(), RT_BEDTIME);
    }
    if (obj.containsKey("wake")){
        a.wakeup    = parseSlot(obj["wake"].as<JsonObject>(), RT_WAKEUP);
    } 

    // Parse recurrence before calculating the skipped occurrence's date.
    a.recurrence = parseRecurrence(obj["recurrence"].as<JsonObject>());

    /* Dismissed set */
    a.dismissedMask = 0;
    if (obj.containsKey("dismissed")) {
        for (JsonVariant v : obj["dismissed"].as<JsonArray>()) {
            RingtoneType rt = ringtoneFromWire(v.as<const char*>());
            if (rt != RT_UNKNOWN) a.setDismissed(rt, true);
        }
    }

    alarms[a.id] = a;
}

/* -------------------------------------------------------------------------
 * Shared command parser. Only the main loop / audio task calls it.
 * -------------------------------------------------------------------------*/
void processIncomingCommand(String raw) {
        if (raw.length() == 0) return;

        /* Legacy plain-text snooze from very old firmware */
        if (raw.startsWith("SNOOZE:")) {
            int min = raw.substring(7).toInt();
            isRinging = false;
            postCommand(CMD_SNOOZE, (min > 0) ? min : 10);
            return;
        }

        JsonDocument doc;
        if (deserializeJson(doc, raw) != DeserializationError::Ok) {
            return;
        }

        /* Update local clock */
        if (doc["timestamp"].is<long long>()) {
            long long ts      = doc["timestamp"].as<long long>();
            long      utcOff  = doc["utcOffsetSec"] | 0L;
            // Ignore zero, malformed dates and invalid UTC offsets instead of
            // replacing a synchronized clock with time since boot.
            int64_t base;
            if (clockBaseFromSync(ts, utcOff, millis() / 1000, base)) {
                start_time = (time_t)base;
                queueInitialClockRefresh();
                postCommand(CMD_SYNC_PUSH);
            }
        }

        const char* action = doc["action"] | "";
        if (strcmp(action, "SET_LIGHT") == 0) {
            if (doc["enabled"].is<bool>()) setClockLight(doc["enabled"].as<bool>());
            return;
        }
        if (strcmp(action, "GET_LIGHT") == 0) {
            notifyLightStatus();
            return;
        }
        if (strcmp(action, "REFRESH_DISPLAY") == 0) {
            postCommand(CMD_DISPLAY_REFRESH);
            return;
        }
        if (strcmp(action, "SET_DISPLAY_REFRESH") == 0) {
            if (doc["minutes"].is<unsigned>()) setDisplayRefreshMinutes(doc["minutes"].as<unsigned>());
            return;
        }

        if (strcmp(action, A_WIFI_CONFIG) == 0) {
            const char* ssid = doc["ssid"] | "";
            const char* password = doc["password"] | "";
            if (strlen(ssid) > 0) queueWifiCredentials(ssid, password);
            return;
        }

        if (strcmp(action, "SET_VOLUME") == 0) {
            if (doc["level"].is<int>()) requestAlarmVolume(doc["level"].as<int>());
            return;
        }
        if (strcmp(action, "TEST_RINGTONE") == 0) {
            requestAudioTest(doc["filename"] | "", false);
            return;
        }
        if (strcmp(action, "STOP_TEST_RINGTONE") == 0) {
            requestAudioTest("", true);
            return;
        }
        if (strcmp(action, "GET_VOLUME") == 0) {
            notifyVolumeStatus();
            return;
        }

        /* ---- SYNC -------------------------------------------- */
        if (strcmp(action, A_SYNC) == 0 && doc.containsKey("alarms")) {
            if (doc["displayRefreshMinutes"].is<unsigned>())
                setDisplayRefreshMinutes(doc["displayRefreshMinutes"].as<unsigned>());
            if (doc["volumeLevel"].is<int>()) requestAlarmVolume(doc["volumeLevel"].as<int>());
            alarms.clear();
            for (JsonObject obj : doc["alarms"].as<JsonArray>())
                parseAndStoreAlarm(obj);
            postCommand(CMD_SYNC_PUSH);
            notifyWifiStatus();
            notifyLightStatus();
            return;
        }

        /* ---- STOP_ALARM ------------------------------------------- */
        if (strcmp(action, A_STOP_ALARM) == 0) {
            const char* alarmId  = doc["alarmId"]  | "";
            RingtoneType rt      = ringtoneFromWire(doc["ringtone"] | "");

            /* Guard: only stop if it is the alarm that is currently ringing.
               An empty alarmId means "stop anything" (legacy / test). */
            bool matches = selectAlarmControlTarget(alarmId, rt);
            if (matches) {
                isRinging = false;
                postCommand(CMD_STOP);
            }
            return;
        }

        /* ---- SNOOZE_ALARM ----------------------------------------- */
        if (strcmp(action, A_SNOOZE_ALARM) == 0) {
            const char* alarmId = doc["alarmId"] | "";
            RingtoneType rt     = ringtoneFromWire(doc["ringtone"] | "");
            int minutes         = doc["minutes"] | 10;
            if (minutes < 1 || minutes > 1440) return;

            bool matches = selectAlarmControlTarget(alarmId, rt);
            if (matches) {
                isRinging = false;
                postCommand(CMD_SNOOZE, minutes);
            }
            return;
        }

        /* ---- DISMISS_NEXT ----------------------------------------- */
        if (strcmp(action, A_DISMISS_NEXT) == 0) {
            const char* alarmId  = doc["alarmId"]  | "";
            RingtoneType rt      = ringtoneFromWire(doc["ringtone"] | "");
            bool dismissed       = doc["dismissed"] | true;

            if (strlen(alarmId) > 0 && rt != RT_UNKNOWN) {
                auto it = alarms.find(std::string(alarmId));
                if (it != alarms.end()) {
                    it->second.setDismissed(rt, dismissed);
                    postCommand(CMD_SYNC_PUSH);
                }
            }
            return;
        }

        /* ---- PUSH_RINGTONE ---------------------------------------- */
        if (strcmp(action, A_PUSH_RINGTONE) == 0) {
            const char* alarmId = doc["alarmId"] | "";
            RingtoneType rt     = ringtoneFromWire(doc["ringtone"] | "");

            /* Set firingAlarmId so the ringtone file can be looked up */
            firingAlarmId  = std::string(alarmId);
            firingRingtone = (rt != RT_UNKNOWN) ? rt : RT_WAKEUP;
            startRinging();
            return;
        }

        /* ---- UPLOAD_BEGIN ----------------------------------------- */
        if (strcmp(action, A_UPLOAD_BEGIN) == 0) {
            const char* fname = doc["filename"] | "";
            uint32_t    size  = doc["size"]     | 0u;
            if (strlen(fname) == 0 || size == 0) return;

            if (uploadSession.active && uploadSession.file)
                uploadSession.file.close();

            String path = String("/") + fname;
            uploadSession.file = LittleFS.open(path, "w");
            if (!uploadSession.file) { uploadSession.active = false; notifyPhone("UPLOAD_ERR"); return; }
            uploadSession.active        = true;
            uploadSession.filename      = path;
            uploadSession.expectedIndex = 0;
            uploadSession.totalSize     = size;
            uploadSession.receivedSize  = 0;
            uploadCrc                   = 0xFFFFFFFFu;
            return;
        }

        /* ---- UPLOAD_CHUNK ----------------------------------------- */
        if (strcmp(action, A_UPLOAD_CHUNK) == 0) {
            if (!uploadSession.active) return;
            int         idx = doc["index"] | -1;
            const char* b64 = doc["data"]  | "";
            // Re-ACK a retry after a lost reply, without appending data twice.
            if (idx >= 0 && idx == uploadSession.expectedIndex - 1) {
                char ack[32];
                snprintf(ack, sizeof(ack), "CHUNK_ACK:%d", idx);
                notifyPhone(ack);
                return;
            }
            if (idx != uploadSession.expectedIndex) return;

            uint8_t buf[512];
            int     len = b64Decode(b64, strlen(b64), buf, sizeof(buf));
            if (len <= 0 || (uint32_t)len > uploadSession.totalSize - uploadSession.receivedSize) {
                notifyPhone("UPLOAD_ERR"); return;
            }

            if (uploadSession.file.write(buf, (size_t)len) != (size_t)len) {
                uploadSession.file.close();
                uploadSession.active = false;
                LittleFS.remove(uploadSession.filename);
                notifyPhone("UPLOAD_ERR");
                return;
            }
            uploadSession.receivedSize += (uint32_t)len;
            crcUpdate(buf, (size_t)len);
            uploadSession.expectedIndex++;

            char ack[32];
            snprintf(ack, sizeof(ack), "CHUNK_ACK:%d", idx);
            notifyPhone(ack);
            return;
        }

        /* ---- UPLOAD_END ------------------------------------------- */
        if (strcmp(action, A_UPLOAD_END) == 0) {
            if (!uploadSession.active) return;
            uploadSession.file.close();
            uploadSession.active = false;

            uint32_t expected = doc["crc"] | 0u;
            if (uploadSession.receivedSize == uploadSession.totalSize && expected == crcFinalise()) {
                notifyPhone("UPLOAD_OK");
            } else {
                LittleFS.remove(uploadSession.filename);
                notifyPhone("UPLOAD_ERR");
            }
            return;
        }

        /* ---- DELETE_RINGTONE -------------------------------------- */
        if (strcmp(action, A_DELETE_RINGTONE) == 0) {
            const char* fname = doc["filename"] | "";
            if (strlen(fname) > 0)
                LittleFS.remove(String("/") + fname);
            return;
        }
}

static QueueHandle_t incomingBluetoothQueue;

void serviceIncomingBluetooth() {
    char* raw = nullptr;
    while (incomingBluetoothQueue &&
           xQueueReceive(incomingBluetoothQueue, &raw, 0) == pdTRUE) {
        processIncomingCommand(String(raw));
        free(raw);
    }
}

class RXCallbacks : public BLECharacteristicCallbacks {
    void onWrite(BLECharacteristic* ch) override {
        const String value = ch->getValue();
        if (!incomingBluetoothQueue || value.length() == 0 || value.length() > 8192) return;
        char* raw = strdup(value.c_str());
        if (raw && xQueueSend(incomingBluetoothQueue, &raw, 0) != pdTRUE) free(raw);
    }
};

/* -------------------------------------------------------------------------
 * BLE server callbacks
 * -------------------------------------------------------------------------*/
class ServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer*)    override {
        postCommand(CMD_BLE_CONNECTED);
        notifyWifiStatus();
    }
    void onDisconnect(BLEServer*) override {
        postCommand(CMD_BLE_DISCONNECTED);
        BLEDevice::startAdvertising();
    }
};

/* -------------------------------------------------------------------------
 * initBluetoothServer
 * -------------------------------------------------------------------------*/
void initBluetoothServer() {
    incomingBluetoothQueue = xQueueCreate(8, sizeof(char*));
    BLEDevice::init(DEVICE_NAME);

    BLEServer* server = BLEDevice::createServer();
    server->setCallbacks(new ServerCallbacks());

    BLEService* service = server->createService(SERVICE_UUID);

    BLECharacteristic* rx = service->createCharacteristic(
        RX_UUID, BLECharacteristic::PROPERTY_WRITE);
    rx->setCallbacks(new RXCallbacks());

    txCharacteristic = service->createCharacteristic(
        TX_UUID, BLECharacteristic::PROPERTY_NOTIFY);
    txCharacteristic->addDescriptor(new BLE2902());

    service->start();

    BLEAdvertising* adv = BLEDevice::getAdvertising();
    adv->addServiceUUID(SERVICE_UUID);
    adv->setScanResponse(true);
    BLEDevice::startAdvertising();
}
