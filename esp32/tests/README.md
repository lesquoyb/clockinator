Run these host tests from the repository root with a C++ compiler:

```powershell
g++ -std=c++17 -Wall -Wextra -I esp32/tests/stubs esp32/tests/alarm_test.cpp -o "$env:TEMP/clockinator-alarm-test.exe"
& "$env:TEMP/clockinator-alarm-test.exe"
g++ -std=c++17 -Wall -Wextra esp32/tests/controls_test.cpp -o "$env:TEMP/clockinator-controls-test.exe"
& "$env:TEMP/clockinator-controls-test.exe"
g++ -std=c++17 -Wall -Wextra -I esp32/tests/stubs esp32/tests/volume_test.cpp -o "$env:TEMP/clockinator-volume-test.exe"
& "$env:TEMP/clockinator-volume-test.exe"
g++ -std=c++17 -Wall -Wextra -I esp32/tests/stubs esp32/tests/audio_test_test.cpp -o "$env:TEMP/clockinator-audio-test.exe"
& "$env:TEMP/clockinator-audio-test.exe"
g++ -std=c++17 -Wall -Wextra esp32/tests/clock_time_test.cpp -o "$env:TEMP/clockinator-clock-time-test.exe"
& "$env:TEMP/clockinator-clock-time-test.exe"
g++ -std=c++17 -Wall -Wextra esp32/tests/display_refresh_test.cpp -o "$env:TEMP/clockinator-display-refresh-test.exe"
& "$env:TEMP/clockinator-display-refresh-test.exe"
```

The tests compile the real `alarm.ino` and `globals.h` with stubs for unused
Arduino dependencies. They verify daily, weekly, interval and one-time skip
scheduling. They do not validate BLE, TCP, JSON parsing, audio, display drivers
or the full Arduino sketch; those still require an ESP32 toolchain and hardware.

`controls_test.cpp` exercises the real PCM scaling and switch debounce helpers:
all five non-mute levels, full-scale signed samples, invalid level clamping,
contact bounce, press/release edges, held keys and `millis()` wraparound.

`volume_test.cpp` compiles the real `volume.ino` with an in-memory Preferences
stand-in. It verifies request validation, latest-value delivery, confirmed levels,
deferred screen updates, persistence reloads and avoiding redundant NVS writes.

`audio_test_test.cpp` verifies the real preview controller with queue/filesystem
stand-ins: five-second timeout, early stop, WAV selection, invalid paths, missing
files, rejecting tests during real alarms and letting scheduled alarms take priority.

`clock_time_test.cpp` checks invalid timestamps leave the existing clock base
untouched, validates UTC offsets, and checks local time immediately after sync
and after uptime advances. Cross-core message delivery and e-paper refreshes
still need hardware verification.

`display_refresh_test.cpp` verifies one automatic clean after the first sync,
waiting for connection initialization and a quiet period, no automatic cleans
on subsequent syncs, opt-in periodic cleans, disabling them, and millis rollover.
