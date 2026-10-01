package com.example.alarmcontroller.alarm

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import java.util.Calendar

/**
 * Persists alarms and app preferences in SharedPreferences as JSON.
 * Single source of truth for all alarm data on the phone.
 */
class AlarmRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME                 = "alarm_controller_prefs"
        private const val KEY_ALARMS                 = "saved_alarms_json"
        private const val KEY_LAST_BLE_MAC           = "last_ble_mac"
        private const val KEY_AUTO_SYNC_HOURS        = "auto_sync_hours"
        private const val KEY_RINGTONE_FILES         = "uploaded_ringtone_files"
        private const val KEY_FOCUS_ONBOARDING_DONE  = "focus_onboarding_done"
        private const val DEFAULT_AUTO_SYNC_HOURS    = 0
    }

    // ------------------------------------------------------------------
    // Alarm CRUD
    // ------------------------------------------------------------------

    fun getAlarms(): List<AlarmItem> {
        val raw = prefs.getString(KEY_ALARMS, null) ?: return defaultAlarms()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { AlarmItem.fromJson(arr.getJSONObject(it)) }
        } catch (_: Exception) { defaultAlarms() }
    }

    fun saveAlarms(alarms: List<AlarmItem>) {
        val arr = JSONArray()
        alarms.forEach { arr.put(it.toJson()) }
        prefs.edit().putString(KEY_ALARMS, arr.toString()).apply()
    }

    fun addOrUpdateAlarm(alarm: AlarmItem) {
        val list = getAlarms().toMutableList()
        val idx  = list.indexOfFirst { it.id == alarm.id }
        if (idx >= 0) list[idx] = alarm else list.add(alarm)
        saveAlarms(list)
    }

    fun deleteAlarm(id: String) {
        saveAlarms(getAlarms().filterNot { it.id == id })
    }

    fun getAlarmById(id: String): AlarmItem? = getAlarms().find { it.id == id }

    fun toggleAlarm(id: String, enabled: Boolean) {
        saveAlarms(getAlarms().map {
            if (it.id == id) it.copy(isEnabled = enabled, dismissedRingtones = emptySet(),
                isDismissedForNext = false)
            else it
        })
    }

    /**
     * Applies a dismiss (or un-dismiss) for a specific ringtone slot.
     * Persists the change and returns the updated alarm.
     */
    fun setDismissed(id: String, ringtone: RingtoneType, dismissed: Boolean): AlarmItem? {
        val list    = getAlarms().toMutableList()
        val idx     = list.indexOfFirst { it.id == id }
        if (idx < 0) return null
        val updated = list[idx].withDismissed(ringtone, dismissed)
        list[idx]   = updated
        saveAlarms(list)
        return updated
    }

    // ------------------------------------------------------------------
    // Upcoming-alarm helpers (used by NfcActionHandler)
    // ------------------------------------------------------------------

    /**
     * Returns the enabled alarm whose [type] slot fires soonest from now.
     */
    fun findUpcomingSlot(type: RingtoneType): AlarmItem? {
        val nowMinutes = nowInMinutes()
        return getAlarms().filter { it.isEnabled }.minByOrNull { alarm ->
            val slot  = alarm.slot(type)
            val total = slot.hour * 60 + slot.minute
            val diff  = total - nowMinutes
            if (diff <= 0) diff + 24 * 60 else diff
        }
    }

    // Kept for backward-compatibility with any remaining call sites.
    fun findUpcomingAlarm():      AlarmItem? = findUpcomingSlot(RingtoneType.LOCK_PHONE)
    fun findUpcomingStartAlarm(): AlarmItem? = findUpcomingSlot(RingtoneType.LOCK_PHONE)
    fun findUpcomingEndAlarm():   AlarmItem? = findUpcomingSlot(RingtoneType.WAKEUP)

    // ------------------------------------------------------------------
    // BLE auto-connect MAC
    // ------------------------------------------------------------------

    fun saveLastBleMac(mac: String) { prefs.edit().putString(KEY_LAST_BLE_MAC, mac).apply() }
    fun getLastBleMac(): String?    = prefs.getString(KEY_LAST_BLE_MAC, null)
    fun clearLastBleMac()           { prefs.edit().remove(KEY_LAST_BLE_MAC).apply() }

    // ------------------------------------------------------------------
    // Periodic auto-sync
    // ------------------------------------------------------------------

    fun getAutoSyncHours(): Int = prefs.getInt(KEY_AUTO_SYNC_HOURS, DEFAULT_AUTO_SYNC_HOURS)
    fun setAutoSyncHours(hours: Int) {
        prefs.edit().putInt(KEY_AUTO_SYNC_HOURS, hours.coerceAtLeast(0)).apply()
    }

    // ------------------------------------------------------------------
    // Focus-mode onboarding
    // ------------------------------------------------------------------

    fun isFocusOnboardingDone(): Boolean = prefs.getBoolean(KEY_FOCUS_ONBOARDING_DONE, false)
    fun markFocusOnboardingDone() { prefs.edit().putBoolean(KEY_FOCUS_ONBOARDING_DONE, true).apply() }

    // ------------------------------------------------------------------
    // Ringtone file registry (WAV files on ESP32)
    // ------------------------------------------------------------------

    fun getUploadedRingtones(): Set<String> =
        prefs.getStringSet(KEY_RINGTONE_FILES, emptySet()) ?: emptySet()

    fun addUploadedRingtone(filename: String) {
        prefs.edit().putStringSet(KEY_RINGTONE_FILES,
            getUploadedRingtones().toMutableSet().also { it.add(filename) }).apply()
    }

    fun removeUploadedRingtone(filename: String) {
        prefs.edit().putStringSet(KEY_RINGTONE_FILES,
            getUploadedRingtones().toMutableSet().also { it.remove(filename) }).apply()
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private fun nowInMinutes(): Int {
        val cal = Calendar.getInstance()
        return cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
    }

    private fun defaultAlarms(): List<AlarmItem> = listOf(
        AlarmItem(
            title     = "Main Alarm",
            isEnabled = true,
            lockPhone = RingtoneSlot(hour = 22, minute = 0),
            bedtime   = RingtoneSlot(hour = 22, minute = 0, enabled = false),
            wakeup    = RingtoneSlot(hour = 7,  minute = 0)
        )
    )
}
