#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>
#include <ArduinoJson.h>
#include <time.h>


#define SERVICE_UUID "12345678-1234-1234-1234-123456789abc"
#define RX_UUID      "12345678-1234-1234-1234-123456789abd"
#define TX_UUID      "12345678-1234-1234-1234-123456789abe"

const char* device_name = "ESP32-Bapresearch";

BLECharacteristic *txCharacteristic;

const char* ALARM_PUSH_CMD = "SYNC_PUSH";
const char* ACTION_FIELD = "action";
const char* ALARMS_FIELD = "alarms";
const char* TIMESTAMP_FIELD = "timestamp";
const char* UTCOFFSET_FIELD = "utcOffsetSeconds";



time_t alarmTimestamp(const char* hhmm) {
    int hour, minute;

    sscanf(hhmm, "%d:%d", &hour, &minute);

    time_t now = start_time + millis()/1000;

    struct tm localTime;
    localtime_r(&now, &localTime);

    localTime.tm_hour = hour;
    localTime.tm_min  = minute;
    localTime.tm_sec  = 0;

    return mktime(&localTime);
}

class RXCallbacks : public BLECharacteristicCallbacks {

    void onWrite(BLECharacteristic *characteristic) override {

        String message = characteristic->getValue();
        JsonDocument doc;

        DeserializationError error = deserializeJson(doc, message);

        if (error) {
            show("Received invalid JSON", message.c_str());
            return;
        }

        start_time = (doc[TIMESTAMP_FIELD] | start_time) - millis()/1000 + (doc[UTCOFFSET_FIELD] | 0);

        const char* cmd = doc[ACTION_FIELD];

        if (strcmp(cmd, ALARM_PUSH_CMD) == 0 && doc.containsKey(ALARMS_FIELD)) {
            JsonArray alarms = doc[ALARMS_FIELD];
            for(JsonObject alarm : alarms){
                next_alarm = alarmTimestamp(alarm["start"]);
            }

        }
    }
};

class ServerCallbacks : public BLEServerCallbacks {
    void onConnect(BLEServer *server) override {
        show("CONNECTED");
    }

    void onDisconnect(BLEServer *server) override {
        show("DISCONNECTED");

        // Recommence à annoncer
        BLEDevice::startAdvertising();
    }

};


void initBluetoothServer(){
  
    BLEDevice::init(device_name);
    BLEServer *server = BLEDevice::createServer();

    server->setCallbacks(new ServerCallbacks());

    BLEService *service = server->createService(SERVICE_UUID);



    // -----------------------
    // RX : téléphone -> ESP32
    // -----------------------

    BLECharacteristic *rxCharacteristic =
        service->createCharacteristic(
            RX_UUID,
            BLECharacteristic::PROPERTY_WRITE
        );

    rxCharacteristic->setCallbacks(
        new RXCallbacks()
    );

    // -----------------------
    // TX : ESP32 -> téléphone
    // -----------------------

    txCharacteristic = service->createCharacteristic(
        TX_UUID,
        BLECharacteristic::PROPERTY_NOTIFY
    );

    txCharacteristic->addDescriptor(
        new BLE2902()
    );


    service->start();

    // =========================
    // ADVERTISING
    // =========================

    BLEAdvertising *advertising = BLEDevice::getAdvertising();

    advertising->addServiceUUID(SERVICE_UUID);
    advertising->setScanResponse(true);

    BLEDevice::startAdvertising();

    show("ADVERTISING", device_name);
}


