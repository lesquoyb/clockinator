# Development and tests

[Back to the project](../README.md) · [Android build](android.md) · [Firmware](firmware.md)

## Android

From the repository root, with the documented SDK and JDK installed:

```sh
cd android/alarm_controller
bash gradlew :app:assembleDebug :app:testDebugUnitTest
```

`local.properties` currently contains a Windows-specific SDK path. On another
machine, set `ANDROID_HOME` to the installed SDK and pass
`-Pandroid.injected.sdk.dir="$ANDROID_HOME"` to Gradle. This override was verified
in the cloud environment and avoids committing a machine-specific replacement.

The debug APK build and all **18 Android unit tests** passed during cloud
validation. The packaged application label was checked as **Clockinator**.
Hardware communication still needs a phone and ESP32 for validation.

## Firmware host tests

These tests use g++ and Arduino stubs; no ESP32 is needed. From the repository root:

```sh
for test in alarm volume audio_test clock_time display_refresh; do
    g++ -std=c++17 -Wall -Wextra -I esp32/tests/stubs \
        "esp32/tests/${test}_test.cpp" -o "/tmp/clockinator-${test}-test" || exit
    "/tmp/clockinator-${test}-test" || exit
done
python3 scripts/test_release_version.py
```

These five C++ tests and the two Python release tests passed during onboarding.
The additional `controls_test.cpp` currently includes the missing
`../button_test/DebouncedButton.h` and does not compile. This is a known source
issue, not a passing or skipped validation. See the [test descriptions](../esp32/tests/README.md).

Host tests do not validate the complete firmware build, BLE/TCP communication,
NFC hardware, real-time cross-core delivery, display output or physical audio.
Those need the ESP32 toolchain and, for functional validation, real hardware.

## Plate geometry

With CadQuery 2.7.0, run:

```sh
python3 case-v3/fusion-work/export_plate_headless.py
```

This rebuilds the current v3 plate, replaces it in the STEP and verifies the
exported geometry and STL. It checks the continuous rear rib and top skin,
switch openings, mounting holes, mesh closure and print-bed height. The
[export notes](../case-v3/output/README.md) distinguish these outputs from the
native Fusion archive.
