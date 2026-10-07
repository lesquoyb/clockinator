package com.example.alarmcontroller.lock

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages "focus / lock" mode.
 *
 * Activated when the ODODO NFC tag is scanned, released on OBOULO.
 *
 * What this class does automatically:
 *  - Do-Not-Disturb (alarms allowed): requires notification policy access, granted
 *    by the user once via the DND settings screen we deep-link to.
 *  - Grayscale display: requires WRITE_SECURE_SETTINGS (adb one-liner or root).
 *    Silently skipped if the permission is absent.
 *
 * What it cannot do without root/MDM, handled via onboarding:
 *  - Block individual apps (social networks): use Android Digital Wellbeing Focus Mode.
 *    We deep-link to that screen and show a one-time onboarding explaining why.
 */
class LockModeManager(private val context: Context) {

    companion object {
        private const val TAG = "LockModeManager"
    }

    private val prefs = context.getSharedPreferences("clockinator_focus", Context.MODE_PRIVATE)
    private val _isLocked = MutableStateFlow(prefs.getBoolean("active", false))
    /** True while lock/focus mode is active. */
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    private var previousInterruptionFilter: Int
        get() = prefs.getInt("previous_filter", NotificationManager.INTERRUPTION_FILTER_ALL)
        set(value) { prefs.edit().putInt("previous_filter", value).apply() }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    fun activate() {
        if (_isLocked.value) return
        Log.d(TAG, "Activating lock mode")
        enableDnd()
        enableGrayscale()
        _isLocked.value = true
        prefs.edit().putBoolean("active", true).apply()
    }

    fun release() {
        if (!_isLocked.value) return
        Log.d(TAG, "Releasing lock mode")
        disableDnd()
        disableGrayscale()
        _isLocked.value = false
        prefs.edit().putBoolean("active", false).apply()
    }

    // ------------------------------------------------------------------
    // Deep-link helpers for features that require user action
    // ------------------------------------------------------------------

    /**
     * Returns true if the app holds the MANAGE_NOTIFICATION_POLICY permission
     * (needed to enable DND programmatically).
     */
    fun hasDndPermission(): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }

    /**
     * Builds the Intent that opens the system DND / Notification Policy settings screen.
     * The caller (UI layer) is responsible for starting it via an Activity context,
     * which is required on Android 10+ to launch activities from the background.
     */
    fun dndSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    /**
     * Builds the best available Intent to open Digital Wellbeing → Focus Mode.
     * Returns the first resolvable Intent from a priority list, or a generic
     * App Info fallback that is always available.
     */
    fun focusModeIntent(): Intent {
        val candidates = listOf(
            // Stock Android / Pixel 10+
            Intent("com.google.android.apps.wellbeing.ACTION_OPEN_FOCUS_MODE")
                .setPackage("com.google.android.apps.wellbeing"),
            // Samsung One UI
            Intent("com.samsung.android.wellbeing.FOCUS_MODE")
                .setPackage("com.samsung.android.wellbeing"),
            // Generic Digital Wellbeing main screen
            Intent().apply {
                setClassName(
                    "com.google.android.apps.wellbeing",
                    "com.google.android.apps.wellbeing.ui.MainActivity"
                )
            }
        )
        val pm = context.packageManager
        return candidates.firstOrNull {
            it.resolveActivity(pm) != null
        } ?: Intent(Settings.ACTION_APPLICATION_SETTINGS) // guaranteed fallback
    }

    // ------------------------------------------------------------------
    // Do Not Disturb
    // ------------------------------------------------------------------

    private fun enableDnd() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) {
            Log.w(TAG, "DND: permission not granted, skipping")
            return
        }
        previousInterruptionFilter = nm.currentInterruptionFilter
        nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
        prefs.edit().putBoolean("dnd_changed", true).apply()
    }

    private fun disableDnd() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted || !prefs.getBoolean("dnd_changed", false)) return
        // Android 15 creates an app-owned Zen rule. ALL deactivates that rule;
        // restoring a global filter from another app would leave our rule active.
        nm.setInterruptionFilter(if (Build.VERSION.SDK_INT >= 35)
            NotificationManager.INTERRUPTION_FILTER_ALL else previousInterruptionFilter)
        prefs.edit().putBoolean("dnd_changed", false).apply()
    }

    // ------------------------------------------------------------------
    // Grayscale (requires WRITE_SECURE_SETTINGS)
    // ------------------------------------------------------------------

    private fun enableGrayscale() {
        if (!hasWriteSecureSettings()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                prefs.edit()
                    .putInt("previous_grayscale_enabled", Settings.Secure.getInt(context.contentResolver,
                        "accessibility_display_daltonizer_enabled", 0))
                    .putInt("previous_daltonizer", Settings.Secure.getInt(context.contentResolver,
                        "accessibility_display_daltonizer", -1))
                    .putBoolean("grayscale_changed", true).apply()
                Settings.Secure.putInt(context.contentResolver,
                    "accessibility_display_daltonizer_enabled", 1)
                Settings.Secure.putInt(context.contentResolver,
                    "accessibility_display_daltonizer", 0) // 0 = grayscale
            }
        } catch (e: Exception) { Log.e(TAG, "Grayscale enable failed", e) }
    }

    private fun disableGrayscale() {
        if (!hasWriteSecureSettings() || !prefs.getBoolean("grayscale_changed", false)) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Settings.Secure.putInt(context.contentResolver,
                    "accessibility_display_daltonizer", prefs.getInt("previous_daltonizer", -1))
                Settings.Secure.putInt(context.contentResolver,
                    "accessibility_display_daltonizer_enabled", prefs.getInt("previous_grayscale_enabled", 0))
                prefs.edit().putBoolean("grayscale_changed", false).apply()
            }
        } catch (e: Exception) { Log.e(TAG, "Grayscale disable failed", e) }
    }

    private fun hasWriteSecureSettings(): Boolean =
        ContextCompat.checkSelfPermission(
            context, android.Manifest.permission.WRITE_SECURE_SETTINGS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}
