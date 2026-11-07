/* Standalone sketch: replaces clock firmware while testing.
 * Libraries: GxEPD2, Adafruit GFX. See ../HARDWARE_TESTS.md for wiring.
 * Darkness selects FAIBLE; light selects MAXIMUM. No off mode.
 * No serial commands, serial diagnostics, Wi-Fi, BLE or audio.
 */
#include <Arduino.h>
#include <GxEPD2_BW.h>

// Classic ESP32. Use a separate 330-ohm resistor for EACH colour.
constexpr int RED_PIN = 13, GREEN_PIN = 12, BLUE_PIN = 14;
constexpr int SENSOR_PIN = 33;
constexpr bool COMMON_ANODE = false; // true: common to 3.3 V; false: common to GND
constexpr bool DARK_IS_LOW = false;

constexpr int LOW_PWM = 10; // 10/255, about 4% duty cycle

// Keep the cyan colour used in the current wiring test. 0 disables a channel.
constexpr int RED = 0, GREEN = 255, BLUE = 255;
GxEPD2_BW<GxEPD2_370_GDEY037T03, GxEPD2_370_GDEY037T03::HEIGHT>
    display(GxEPD2_370_GDEY037T03(5, 16, 17, 4));

int intensity = 0;
bool dark = false;
bool stableSensor = HIGH, candidateSensor = HIGH;
unsigned long candidateSince = 0, lastRefresh = 0;
bool displayDirty = true;

void writeChannel(int pin, int colour) {
    const int duty = (colour * intensity + 127) / 255;
    analogWrite(pin, COMMON_ANODE ? 255 - duty : duty);
}

void updateLed() {
    intensity = dark ? LOW_PWM : 255;
    writeChannel(RED_PIN, RED);
    writeChannel(GREEN_PIN, GREEN);
    writeChannel(BLUE_PIN, BLUE);
}

void drawTest() {
    display.setFullWindow();
    display.firstPage();
    do {
        display.fillScreen(GxEPD_WHITE);
        display.setTextColor(GxEPD_BLACK);
        display.setFont(nullptr);
        display.setTextSize(2);
        display.setCursor(15, 15);
        display.print("Clockinator - lighting test");
        display.setTextSize(8);
        display.setCursor(20, 60);
        display.print("12:34");
        display.setTextSize(2);
        display.setCursor(15, 146);
        display.print(dark ? "Mode: FAIBLE" : "Mode: MAXIMUM");
        display.setCursor(15, 169);
        display.print(dark ? "Sensor: DARK" : "Sensor: LIGHT");
        display.setCursor(15, 192);
        display.printf("PWM: %d/255", intensity);
        display.drawRect(150, 190, 202, 20, GxEPD_BLACK);
        if (intensity > 0) display.fillRect(151, 191, intensity * 200 / 255, 18, GxEPD_BLACK);
        display.setCursor(15, 219);
        display.print("Dark: LOW / Light: MAX");
    } while (display.nextPage());
    lastRefresh = millis();
    displayDirty = false;
}

void setup() {
    pinMode(SENSOR_PIN, INPUT_PULLUP);
    const int pins[] = {RED_PIN, GREEN_PIN, BLUE_PIN};
    for (int pin : pins) {
        pinMode(pin, OUTPUT);
        analogWrite(pin, COMMON_ANODE ? 255 : 0);
    }
    stableSensor = candidateSensor = digitalRead(SENSOR_PIN);
    dark = DARK_IS_LOW ? !stableSensor : stableSensor;
    updateLed();
    display.init(0); // Disable GxEPD2's serial diagnostics as well.
    display.setRotation(1);
    drawTest();
}

void loop() {
    const unsigned long now = millis();
    const bool raw = digitalRead(SENSOR_PIN);
    if (raw != candidateSensor) { candidateSensor = raw; candidateSince = now; }
    // Suppress flicker around the comparator's threshold. This is not a lux reading.
    if (candidateSensor != stableSensor && now - candidateSince >= 150) {
        stableSensor = candidateSensor;
        dark = DARK_IS_LOW ? !stableSensor : stableSensor;
        updateLed();
        displayDirty = true;
    }
    // E-paper follows at most every 5 seconds; its SPI refresh itself is blocking.
    if (displayDirty && millis() - lastRefresh >= 5000) drawTest();
    delay(5);
}
