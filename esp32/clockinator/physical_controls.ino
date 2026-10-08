#include "globals.h"
#include "DebouncedButton.h"

// Each switch connects its GPIO to GND. No matrix or diodes are needed.
constexpr int STOP_BUTTON_PIN = 32;
constexpr int SNOOZE_BUTTON_PIN = 33;
constexpr int LIGHT_BUTTON_PIN = 34;
constexpr int LIGHT_RED_PIN = 13, LIGHT_GREEN_PIN = 12, LIGHT_BLUE_PIN = 14;
constexpr bool LIGHT_COMMON_ANODE = false;
static DebouncedButton stopButton, snoozeButton, lightButton;
static bool lightOn = false;
static int physicalAction = 0; // 1 = stop (priority), 2 = cycle snooze

static void writeClockLight() {
    const int duty = lightOn ? 255 : 0;
    const int output = LIGHT_COMMON_ANODE ? 255 - duty : duty;
    analogWrite(LIGHT_RED_PIN, output);
    analogWrite(LIGHT_GREEN_PIN, output);
    analogWrite(LIGHT_BLUE_PIN, output);
}

void notifyLightStatus() {
    notifyPhone(lightOn ? "{\"action\":\"LIGHT_STATUS\",\"enabled\":true}"
                        : "{\"action\":\"LIGHT_STATUS\",\"enabled\":false}");
}

void setClockLight(bool enabled) {
    lightOn = enabled;
    writeClockLight();
    notifyLightStatus();
}

void initPhysicalControls() {
    pinMode(STOP_BUTTON_PIN, INPUT_PULLUP);
    pinMode(SNOOZE_BUTTON_PIN, INPUT_PULLUP);
    pinMode(LIGHT_BUTTON_PIN, INPUT_PULLUP);
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
    if (lightButton.update(digitalRead(LIGHT_BUTTON_PIN) == LOW, now) == 1) {
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
