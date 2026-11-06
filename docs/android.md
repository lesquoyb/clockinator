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
5. To program tags, open **More settings → NFC Tags**, choose `ODODO` or `OBOULO`, then present a compatible tag when the phone says it is ready.

NFC support depends on the phone hardware. Do Not Disturb access must be granted separately in Android settings. Grayscale requires the `WRITE_SECURE_SETTINGS` system permission, which is not granted by default on a standard phone.

