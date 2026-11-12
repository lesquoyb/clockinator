#include "globals.h"
#include "DebouncedButton.h"
#include <Preferences.h>

// Each switch connects its GPIO to GND. No matrix or diodes are needed.
constexpr int STOP_BUTTON_PIN = 32;
constexpr int SNOOZE_BUTTON_PIN = 33;
constexpr int LIGHT_BUTTON_PIN = 21;
constexpr int LIGHT_RED_PIN = 13, LIGHT_GREEN_PIN = 12, LIGHT_BLUE_PIN = 14;
constexpr bool LIGHT_COMMON_ANODE = false;
static DebouncedButton stopButton, snoozeButton, lightButton;
static int lastLightButtonLevel = HIGH;
static bool lightOn = false;
static int lightRed = 255, lightGreen = 255, lightBlue = 255;
static int physicalAction = 0; // 1 = stop (priority), 2 = cycle snooze

static void writeClockLight() {
    const int red = lightOn ? lightRed : 0;
    const int green = lightOn ? lightGreen : 0;
    const int blue = lightOn ? lightBlue : 0;
    analogWrite(LIGHT_RED_PIN, LIGHT_COMMON_ANODE ? 255 - red : red);
    analogWrite(LIGHT_GREEN_PIN, LIGHT_COMMON_ANODE ? 255 - green : green);
    analogWrite(LIGHT_BLUE_PIN, LIGHT_COMMON_ANODE ? 255 - blue : blue);
}

void notifyLightStatus() {
    char status[128];
    snprintf(status, sizeof(status), "{\"action\":\"LIGHT_STATUS\",\"enabled\":%s,\"red\":%d,\"green\":%d,\"blue\":%d}",
             lightOn ? "true" : "false", lightRed, lightGreen, lightBlue);
    notifyPhone(status);
}

void setClockLightColor(int red, int green, int blue) {
    if (red < 0 || red > 255 || green < 0 || green > 255 || blue < 0 || blue > 255) return;
    if (red != lightRed || green != lightGreen || blue != lightBlue) {
        lightRed = red; lightGreen = green; lightBlue = blue;
        Preferences prefs;
        if (prefs.begin("clock-light", false)) {
            prefs.putUInt("rgb", (red << 16) | (green << 8) | blue);
            prefs.end();
        }
    }
    writeClockLight();
    notifyLightStatus();
}

void setClockLight(bool enabled) {
    lightOn = enabled;
    writeClockLight();
    notifyLightStatus();
}

void initPhysicalControls() {
    Preferences prefs;
    if (prefs.begin("clock-light", true)) {
        const unsigned rgb = prefs.getUInt("rgb", 0xFFFFFF);
        lightRed = (rgb >> 16) & 255;
        lightGreen = (rgb >> 8) & 255;
        lightBlue = rgb & 255;
        prefs.end();
    }
    pinMode(STOP_BUTTON_PIN, INPUT_PULLUP);
    pinMode(SNOOZE_BUTTON_PIN, INPUT_PULLUP);
    pinMode(LIGHT_BUTTON_PIN, INPUT_PULLUP);
    lastLightButtonLevel = digitalRead(LIGHT_BUTTON_PIN);
    Serial.printf("[light] GPIO%d configured as INPUT_PULLUP, initial level=%d\n",
                  LIGHT_BUTTON_PIN, lastLightButtonLevel);
    pinMode(LIGHT_RED_PIN, OUTPUT);
    pinMode(LIGHT_GREEN_PIN, OUTPUT);
    pinMode(LIGHT_BLUE_PIN, OUTPUT);
    writeClockLight();
}

// Poll in the main loop AND between audio buffers, without refreshing the screen here.
void servicePhysicalControls() {
    const uint32_t now = millis();
    const bool stopPressed = stopButton.update(digitalRead(STOP_BUTTON_PIN) == LOW, now) == 1;
    const bool snoozePressed = snoozeButton.update(digitalRead(SNOOZE_BUTTON_PIN) == LOW, now) == 1;
    const int lightButtonLevel = digitalRead(LIGHT_BUTTON_PIN);
    if (lightButtonLevel != lastLightButtonLevel) {
        lastLightButtonLevel = lightButtonLevel;
        Serial.printf("[light] GPIO%d level=%d\n", LIGHT_BUTTON_PIN, lightButtonLevel);
    }
    if (lightButton.update(lightButtonLevel == LOW, now) == 1) {
        Serial.printf("[light] press accepted, LED %s\n", lightOn ? "off" : "on");
        setClockLight(!lightOn);
    }
    if (stopPressed && (isRinging || hasPendingSnooze())) {
        physicalAction = 1;
        isRinging = false; // let WAV/tone playback return immediately
    } else if (snoozePressed && physicalAction != 1 &&
               ((!firingAlarmId.empty() && firingRingtone != RT_UNKNOWN) || hasPendingSnooze())) {
        physicalAction = 2;
        isRinging = false;
    }
}

void applyPhysicalControls() {
    const int action = physicalAction;
    physicalAction = 0;
    if (action == 1) stopFromPhysicalButton();
    else if (action == 2) cyclePhysicalSnooze();
}
