# Clockinator Mobile Design

## App Read
Clockinator is an Android-first alarm and ESP32 control utility. It uses native Jetpack Compose and Material 3, with a calm, clear visual language. The app favors quick alarm scanning and reliable one-handed controls over decoration.

**Dials:** design expression 5/10, motion intensity 3/10, visual density 4/10.

## Navigation Read

- Platform: Android smartphones first-class; larger formats are out of scope.
- Start: first-run setup for new installs; daily dashboard for migrated/existing installs.
- Tabs: none. Alarm management is the only frequent destination; device management and settings are secondary.
- `setup/device` → `setup/first-alarm` → `home`; both setup steps can be deferred.
- `home` → `alarm-editor` (full-screen create/edit), `device` (connection and diagnostics), `settings` (advanced configuration).
- Entry points: Bluetooth status icon → device; settings icon → settings; FAB → new alarm; alarm card → edit.
- Delete confirmation: alert dialog. Ringing: persistent in-app banner with stop/snooze actions.
- Deep links: none
- Android back: editor returns to its origin; first-alarm setup returns to device setup; secondary pages return home.
- Existing-install migration: saved alarm or remembered device preferences bypass setup; their data remains intact.
- Routine semantics: Focus and Wake-up are required; Bedtime is optional. Focus always rings on the phone, Wake-up always rings on the Clockinator, and an enabled Bedtime must ring on at least one.
- Bedtime sound choice: a three-option single-choice control (Phone, Clock, Both) has no silent state.
- Skip semantics: the dashboard skips the chronologically next, not-already-skipped Focus, Bedtime, or Wake-up occurrence; each skipped event can be undone from its row. Disabling a routine offers a one-cycle skip of all configured events or a pause until manually re-enabled.

The old Alarms/Device/Settings tab row is removed. Device diagnostics, NFC, Focus Mode, ringtone uploads, and sync remain available in secondary screens.

## Visual System

- Components: Jetpack Compose Material 3.
- Palette: eucalyptus green primary, cool neutral secondary, restrained warm terracotta tertiary; semantic status colors for connected, connecting, and error states.
- Android 12+: retain Material You dynamic color. Earlier versions use the explicit light/dark brand schemes in `android/alarm_controller/app/src/main/java/com/example/alarmcontroller/ui/theme/Theme.kt`.
- Typography: Material 3 scale using the platform sans-serif family. Times use tabular-feeling monospace numerals for quick comparison.
- Surfaces: use unframed settings groups, restrained 12dp component corners, and separators to establish hierarchy without nesting cards.
- Accessibility: keep meaningful labels on icon-only controls, avoid status-by-color alone, and retain Material's touch affordances.

## Screen Direction

- **Setup:** connect the single alarm clock, then create the first wake-up. Setup can be deferred without blocking daily use.
- **Dashboard:** show connection/sync status, each routine's active state, Focus time/output, optional Bedtime time/output, and Wake-up time/output. The primary actions are toggle, skip/undo next wake-up, edit, and add. Empty and ringing states have direct actions.
- **Device:** secondary diagnostics with reconnection, scan, remote control, and activity console.
- **Settings:** secondary advanced groups for NFC, Focus Mode, device tones, and sync; remove NFC test controls.
- **Routine editor:** full-screen with a fixed header and Save/Cancel footer; only the form body scrolls. Focus, optional Bedtime, Wake-up, and recurrence are visible in chronological order. Ringtone and snooze options are collapsed behind a summarized section. Required sound outputs cannot be turned off.

## Motion and Feedback

Use native Material press feedback and short size/visibility transitions for state changes. Android back follows the setup/editor stack. Preserve alarm, Bluetooth, NFC, upload, and sync behavior; add one-shot skip/undo for the next wake-up.

## Audit Notes

The source-level redesign baseline is `design-baseline.json`. The Android debug build passed before and during the redesign. A Pixel 9a was connected, so no APK was installed and no live-device screenshot review was performed. Recheck compact widths, large font scales, light/dark modes, and ringing states on a development emulator or a user-approved device before release.
