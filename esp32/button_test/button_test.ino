/* Standalone switch test. Each switch connects its GPIO to GND.
 * No diode is necessary for individually wired switches. Serial: 115200.
 */
#include <Arduino.h>
#include "DebouncedButton.h"

constexpr int PINS[] = {32, 33, 21};
DebouncedButton buttons[3];

void setup() {
    Serial.begin(115200);
    for (int pin : PINS) pinMode(pin, INPUT_PULLUP);
    Serial.println("Switches ready: GPIO -> switch -> GND. Pressed = LOW.");
}

void loop() {
    for (int i = 0; i < 3; ++i) {
        const int event = buttons[i].update(digitalRead(PINS[i]) == LOW, millis());
        if (event != 0) Serial.printf("Button %d: %s\n", i + 1, event == 1 ? "pressed" : "released");
        // In the clock sketch: on a press, postCommand(CMD_STOP) or
        // postCommand(CMD_SNOOZE, 10). Poll during playback too, not only between WAVs.
    }
    delay(1);
}
