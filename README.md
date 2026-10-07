# Clockinator

**A smart alarm clock synced with your phone, so you can leave your phone outside the bedroom.**

Clockinator combines an Android app, an ESP32-based alarm clock, and NFC tags. The app provides flexible Android-style alarms, including scheduled times, recurrence, snooze, and one-off skips. The clock can ring in the bedroom without the phone being there.

The goal is to make doomscrolling before bed and after waking up less automatic. Leave the phone somewhere you choose, ideally next to its charger. In the morning, getting to it requires getting out of bed. NFC tags provide concrete checkpoints that help anchor those habits.

## One routine, two or three events

A Clockinator routine can contain these events:

1. **Focus**: starts the evening routine. The phone must ring; the alarm clock can ring too. Place the `ODODO` tag where you want to leave your phone, out of reach and preferably beside its charger.
2. **Bedtime** *(optional)*: a reminder to go to bed. It can ring on the phone, the alarm clock, or both. At least one output must be selected when it is enabled, so the alarm clock can be the primary output.
3. **Wake-up**: the morning alarm. The Clockinator must ring; the phone can ring as well. Place the `OBOULO` tag near the first action you want to take, such as going to the kitchen, balcony, or bathroom.

Configure events in the app and sync them to the clock. Routines can repeat daily, weekly, or at an interval of days. Each event can be skipped once from the app; the next event in chronological order is offered first.

### NFC tags

Tags contain an NDEF Text record with one of these values:

- `ODODO`: activates Focus Mode on the phone. If the app currently detects a ringing Focus or Bedtime event, scanning the tag stops it and sends the stop command to the clock. When no event is ringing, it toggles the next Focus occurrence's skip state.
- `OBOULO`: releases Focus Mode. If the app currently detects a ringing Wake-up event, scanning the tag stops it and sends the stop command to the clock. When no event is ringing, it toggles the next Wake-up occurrence's skip state.

You can program tags from the app's settings. Tags already written with these values also work. Common rewritable NDEF tags such as NTAG213, NTAG215, and NTAG216 are suitable.

> For a tag-triggered stop or skip to be mirrored to the alarm clock, BLE must be available. Currently, tags act on events tracked by the Android app; they do not independently detect an alarm that is ringing only on the ESP32.

## How it works

- **Android is the source of truth** for routines and recurrence. Phone alarms are scheduled locally with `AlarmManager` and do not depend on an internet connection.
- **The phone synchronizes routines, time, alarm actions, and ringtone uploads** over Wi-Fi when connected, with BLE available for pairing and fallback.
- **BLE remains available for pairing and fallback.** During setup, you can optionally enter a local Wi-Fi SSID and password; the app sends them to the ESP32 over BLE.
- **When configured, the ESP32 joins the Wi-Fi network** and opens a TCP server on port `4210`. Supported bands depend on the chip: classic ESP32 variants are generally 2.4 GHz only, while ESP32-C5 supports 2.4 and 5 GHz. The app connects to its address announced over BLE and falls back to BLE if TCP is unavailable.
- **The ESP32 calculates and displays the next event**, then plays a built-in melody or a WAV file uploaded from the app. It can notify the phone when an alarm is stopped, snoozed, or skipped so both devices stay in sync.
- **After the alarm clock restarts**, the phone sends routines again when BLE reconnects. The firmware keeps the routine list in RAM; it is not persisted to flash.
- **Android Focus Mode** can enable Do Not Disturb and, when the required system permission is available, grayscale. Pausing individual apps is configured through Digital Wellbeing; Android does not let this app pause apps directly without device-management privileges.

The project requires no account, cloud service, or internet access. Wi-Fi communication requires the phone and ESP32 to be on the same local network, with client-to-client traffic allowed. The ESP32 stores Wi-Fi credentials in NVS; the app does not persist them. Wi-Fi provisioning and TCP commands are currently not encrypted or authenticated, so use a trusted local network.

## Hardware

The firmware is intended for a compatible ESP32 board connected to:

- a **3.7-inch GDEY037T03 black-and-white e-paper display**;
- an I²S audio amplifier and speaker;
- one or more NFC tags for the phone drop-off and morning checkpoints.

Pins defined by the firmware:

| Function | ESP32 GPIO |
| --- | ---: |
| E-paper CS | 5 |
| E-paper DC | 16 |
| E-paper RST | 17 |
| E-paper BUSY | 4 |
| I²S BCLK | 26 |
| I²S LRC / WS | 25 |
| I²S data to amplifier | 27 |

Check the power requirements and exact pinout of your modules before wiring. The repository does not prescribe a specific ESP32 board or amplifier model.

## Build and run the Android app

Requirements: Android Studio with Android SDK 35 and JDK 21 for the Gradle daemon (configured in `gradle/gradle-daemon-jvm.properties`). The app compiles to Java/Kotlin 17, targets Android 15 (API 35), and supports Android 8.0 (API 26) and later.

Open `android/alarm_controller` in Android Studio and run the `app` configuration on an Android phone. To build a debug APK from the repository root:

```powershell
cd android/alarm_controller
.\gradlew.bat :app:assembleDebug
```

On macOS or Linux:

```sh
cd android/alarm_controller
./gradlew :app:assembleDebug
```

The APK is written to `android/alarm_controller/app/build/outputs/apk/debug/app-debug.apk`.

On first launch:

1. Grant Bluetooth access and enable Bluetooth if Android asks.
2. Optionally configure Wi-Fi during pairing or later in **Settings → Local Wi-Fi**. The current network name can be filled in automatically; Android still requires the password to be entered.
3. Turn on the alarm clock and keep it nearby, then select it in setup.
4. Create the first routine and choose its repeat days.
5. To program tags, open **More settings → NFC Tags**, choose `ODODO` or `OBOULO`, then present a compatible tag when the phone says it is ready.

NFC support depends on the phone hardware. Do Not Disturb access must be granted separately in Android settings. Grayscale requires the `WRITE_SECURE_SETTINGS` system permission, which is not granted by default on a standard phone.

## Flash the ESP32 clock

The Arduino firmware is in `esp32/clockinator`. Open `clockinator.ino` with Arduino IDE, install ESP32 board support and the **ArduinoJson 7**, **GxEPD2**, and **Adafruit GFX Library** libraries, select a compatible ESP32 board, and upload the sketch.

By default, the clock advertises over BLE as `ESP32-Bapresearch`. Change this name in `esp32/clockinator/bluetooth.ino`. The app uses the BLE service and characteristics defined in the same file. The protocol carries JSON messages for sync, stop, snooze, skip, and optional Wi-Fi setup, as well as audio-file data. The Wi-Fi server uses TCP port `4210` and starts only after the ESP32 joins the configured network.

Ringtone files uploaded to the clock must be PCM WAV, mono or stereo, 8- or 16-bit. The firmware stores them in LittleFS and plays them over I²S. A built-in melody is used when no file is assigned to a routine.

## Repository layout

```text
android/alarm_controller/   Android app (Kotlin / Jetpack Compose)
esp32/clockinator/          ESP32 Arduino firmware
```

The Android app owns recurrence calculations and local routine state. The ESP32 receives configuration over BLE or local Wi-Fi, displays the current time and next event on e-paper, and handles audio playback in the bedroom.
