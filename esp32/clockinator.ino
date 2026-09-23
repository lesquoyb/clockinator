#include <BLEDevice.h>
#include <BLEServer.h>
#include <BLEUtils.h>
#include <BLE2902.h>

#include <GxEPD2_BW.h>
#include <Fonts/FreeMonoBold12pt7b.h>
#include <ArduinoJson.h>

#include <time.h>

// The screen
#define EPD_CS   5
#define EPD_DC   16
#define EPD_RST  17
#define EPD_BUSY 4

GxEPD2_BW<GxEPD2_370_GDEY037T03,
          GxEPD2_370_GDEY037T03::HEIGHT>
display(
    GxEPD2_370_GDEY037T03(
        EPD_CS,
        EPD_DC,
        EPD_RST,
        EPD_BUSY
    )
);


// Bluetooth
#define SERVICE_UUID "12345678-1234-1234-1234-123456789abc"
#define RX_UUID      "12345678-1234-1234-1234-123456789abd"
#define TX_UUID      "12345678-1234-1234-1234-123456789abe"

const char* device_name = "ESP32-Bapresearch";


// clock
time_t start_time = 0;
time_t next_alarm = 0;


// Speaker
#include "driver/i2s.h"
#include <math.h>
#define I2S_PORT I2S_NUM_0

#define I2S_BCLK 26
#define I2S_LRC  25
#define I2S_DOUT 27

#define SAMPLE_RATE 44100



void show(const char *line1, const char *line2 = "") {
    display.setFullWindow();
    display.firstPage();

    do {
        display.fillScreen(GxEPD_WHITE);
        display.setTextColor(GxEPD_BLACK);
        display.setFont(&FreeMonoBold12pt7b);

        display.setCursor(10, 50);
        display.print(line1);

        display.setCursor(10, 90);
        display.print(line2);

    } while (display.nextPage());
}

void drawClock() {

    time_t now = start_time + (millis() / 1000);

    struct tm timeinfo;
    localtime_r(&now, &timeinfo);

    char buffer[6];
    strftime(buffer, sizeof(buffer), "%H:%M", &timeinfo);

    // Partial window
    display.setPartialWindow(
        0,
        0,
        display.width(),
        display.height()
    );

    display.firstPage();

    do {
        display.fillScreen(GxEPD_WHITE);

        display.setTextColor(GxEPD_BLACK);
        display.setFont(NULL);
        display.setTextSize(13);

        display.setCursor(20, 50);
        display.print(buffer);

        display.setTextSize(4);
        // display.setCursor(20, 160);
        // display.print(next_alarm);
        display.setCursor(20, 180);
        char alarm_buff[13];
        struct tm alarm_timeinfo;
        localtime_r(&next_alarm, &alarm_timeinfo);
        strftime(alarm_buff, sizeof(alarm_buff), "Alarm: %H:%M", &alarm_timeinfo);
        display.print(alarm_buff);

    } while (display.nextPage());

}


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

BLECharacteristic *txCharacteristic;
const char* ALARM_PUSH_CMD = "SYNC_PUSH";
const char* ACTION_FIELD = "action";
const char* ALARMS_FIELD = "alarms";
const char* TIMESTAMP_FIELD = "timestamp";
const char* UTCOFFSET_FIELD = "utcOffsetSeconds";

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



void setup() {

    // Speaker 
    setupI2S();

    // Display
    display.init(115200);
    display.setRotation(1); // Landscape

    show("BOOT", "starting");

    // Server
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


// global variable ensuring the screen and alarms are only refreshed when we reach a new minute
time_t last_update; 
void loop() {

    time_t now = start_time + millis()/1000;
    // Only update clock and trigger alarms every minute
    if (now/60 - last_update/60 >= 1){
        // Refresh clock
        drawClock();

        if (next_alarm > 0 && now >= next_alarm){
            playTone(523.25, 300);  // Do
            delay(100);

            playTone(659.25, 300);  // Mi
            delay(100);

            playTone(783.99, 500);  // Sol
            delay(100);
            
            playTone(659.25, 300);  // Mi
            delay(100);

            playTone(523.25, 300);  // Do
            delay(100);

            next_alarm = 0; // reset the alarm
        }
        last_update = now;
    }

    delay(1000); // 1s between loops is enough for what we do

}


void setupI2S()
{
    i2s_config_t config = {
        .mode = (i2s_mode_t)(
            I2S_MODE_MASTER |
            I2S_MODE_TX
        ),

        .sample_rate = SAMPLE_RATE,

        .bits_per_sample =
            I2S_BITS_PER_SAMPLE_16BIT,

        .channel_format =
            I2S_CHANNEL_FMT_RIGHT_LEFT,

        .communication_format =
            I2S_COMM_FORMAT_I2S_MSB,

        .intr_alloc_flags = 0,

        .dma_buf_count = 8,
        .dma_buf_len = 64,

        .use_apll = false,
        .tx_desc_auto_clear = true,
        .fixed_mclk = 0
    };

    i2s_pin_config_t pins = {
        .bck_io_num = I2S_BCLK,
        .ws_io_num = I2S_LRC,
        .data_out_num = I2S_DOUT,
        .data_in_num = I2S_PIN_NO_CHANGE
    };

    i2s_driver_install(
        I2S_PORT,
        &config,
        0,
        nullptr
    );

    i2s_set_pin(
        I2S_PORT,
        &pins
    );

    i2s_zero_dma_buffer(I2S_PORT);
}
void playTone(float frequency, int durationMs) {

    const int samples = SAMPLE_RATE * durationMs / 1000;

    int16_t buffer[256];

    int generated = 0;

    while (generated < samples) {

        int count =
            min(128, samples - generated);

        for (int i = 0; i < count; i++) {

            float t =
                (float)(generated + i)
                / SAMPLE_RATE;

            int16_t sample =
                10000 *
                sinf(2.0 * PI * frequency * t);

            buffer[2 * i]     = sample;
            buffer[2 * i + 1] = sample;
        }

        size_t bytesWritten;

        i2s_write(
            I2S_PORT,
            buffer,
            count * 2 * sizeof(int16_t),
            &bytesWritten,
            portMAX_DELAY
        );

        generated += count;
    }
}





