Run these host tests from the repository root with a C++ compiler:

```powershell
g++ -std=c++17 -Wall -Wextra -I esp32/tests/stubs esp32/tests/alarm_test.cpp -o "$env:TEMP/clockinator-alarm-test.exe"
& "$env:TEMP/clockinator-alarm-test.exe"
```

The tests compile the real `alarm.ino` and `globals.h` with stubs for unused
Arduino dependencies. They verify daily, weekly, interval and one-time skip
scheduling. They do not validate BLE, TCP, JSON parsing, audio, display drivers
or the full Arduino sketch; those still require an ESP32 toolchain and hardware.
