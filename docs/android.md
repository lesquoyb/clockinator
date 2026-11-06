# Android app

[Back to the project](../README.md)

## Build and run

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
5. To program tags, open **Settings → NFC Tags**, choose `ODODO` or `OBOULO`, then present a compatible tag when the phone says it is ready.

NFC support depends on the phone hardware. Do Not Disturb access must be granted separately in Android settings. Grayscale requires the `WRITE_SECURE_SETTINGS` system permission, which is not granted by default on a standard phone.


## Everyday use

- **Today** shows the next routine event and the following events, with their actual dates, sound outputs and routine names. Use **Skip … once** for an exception and **Undo** in the snackbar to reverse it. A pending snooze may ring before this routine schedule.
- **Routines** lists your routines. Create one with **+**, edit its hours, choose the repeat days and optionally enable Bedtime. Touch an hour for the native time picker; expand its sound-output row for Phone/Clock choices. **Sound & snooze** contains the clock sound library and report durations.
- **Clock** groups sound volume, preview, light, Wi-Fi, uploaded WAV files and display controls. The connection page contains scanning and a separate expandable diagnostics area. Hardware controls remain unavailable when disconnected; routine editing remains available.
- **Settings**, accessible from each main tab, contains appearance, NFC tags, Focus permissions and background synchronization.

## Appearance

Choose **System**, **Light** or **Dark** under **Settings → Appearance**. The choice is saved and applied immediately. Clockinator uses an ivory/lavender palette in light mode and an ink/lavender palette in dark mode. **Use phone colours**, available on Android 12+, opts into Material You colours. The app theme does not change the physical clock light.

## Editing and sounds

The editor keeps in-progress values through activity recreation. Cancel or Android Back asks before discarding changes. Saving keeps the existing alarm data format and synchronization protocol.

Upload WAV files through **Clock → Device ringtones**, then select one in the routine's sound picker. The built-in melody remains available. If an old routine refers to a file missing from the saved library, the editor shows a warning rather than silently replacing it. Preview audio is sent to the actual clock and requires a connection.
