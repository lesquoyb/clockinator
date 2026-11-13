# ESP32 firmware

[Back to the project](../README.md) · [Build guide](build-guide.md)

## Flash the ESP32 clock

The Arduino firmware is in `esp32/clockinator`. Open `clockinator.ino` with Arduino IDE, install ESP32 board support and the **ArduinoJson 7**, **GxEPD2**, and **Adafruit GFX Library** libraries, select a compatible ESP32 board, and upload the sketch.

For a classic ESP32 with 4 MB flash, select **Partition Scheme → No OTA (2MB APP/2MB SPIFFS)**. With ESP32 core 3.3.12, the firmware is about 1.81 MB and exceeds the default 1.3 MB application partition. Changing partitions may require uploading existing WAV files again.

By default, the clock advertises over BLE as `Clockinator`. Change this name in `esp32/clockinator/bluetooth.ino`. The app uses the BLE service and characteristics defined in the same file. The protocol carries JSON messages for sync, stop, snooze, skip, and optional Wi-Fi setup, as well as audio-file data. The Wi-Fi server uses TCP port `4210` and starts only after the ESP32 joins the configured network.

**Clock → Automatic stop** sets the maximum time the ESP32 rings for each alarm occurrence. The default is 20 minutes. The clock stores the setting in flash and applies it without a phone connection.

Ringtone files uploaded to the clock must be PCM WAV, mono or stereo, 8- or 16-bit. The firmware stores them in LittleFS and plays them over I²S. A built-in melody is used when no file is assigned to a routine.

**Clock → Clock ringtone volume** offers five non-mute levels, device confirmation, and a five-second ringtone preview with a stop button. The volume is retained on the phone and ESP32 and shown on e-paper. The MAX98357 GAIN pin keeps its existing wiring; volume is controlled in the digital audio samples.


