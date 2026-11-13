package com.example.alarmcontroller.alarm

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray

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
        private const val KEY_LAST_ESP_WIFI_IP       = "last_esp_wifi_ip"
        private const val KEY_LAST_WIFI_SSID         = "last_wifi_ssid"
        private const val KEY_AUTO_SYNC_HOURS        = "auto_sync_hours"
        private const val KEY_RINGTONE_FILES         = "uploaded_ringtone_files"
        private const val KEY_FOCUS_ONBOARDING_DONE  = "focus_onboarding_done"
        private const val KEY_SETUP_COMPLETED       = "setup_completed"
        private const val DEFAULT_AUTO_SYNC_HOURS    = 0
    }

    // ------------------------------------------------------------------
    // Alarm CRUD
    // ------------------------------------------------------------------

    fun getAlarms(): List<AlarmItem> {
        val raw = prefs.getString(KEY_ALARMS, null) ?: return emptyList()
        var needsIntervalMigration = false
        val loaded = try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val json = arr.getJSONObject(it)
                val recurrence = json.optJSONObject("recurrence")
                if (recurrence?.optString("type") == "interval" && !recurrence.has("anchorEpochDay")) {
                    needsIntervalMigration = true
                }
                AlarmItem.fromJson(json)
            }
        } catch (_: Exception) {
            needsIntervalMigration = false
            emptyList()
        }
        val normalized = loaded.map { it.withRequiredSoundOutputs() }
        // Persist the date anchor when migrating an existing interval routine.
        if (normalized != loaded || needsIntervalMigration) {
            saveAlarms(normalized)
        }
        return normalized
    }

    fun isSetupRequired(): Boolean {
        if (prefs.contains(KEY_SETUP_COMPLETED)) {
            return !prefs.getBoolean(KEY_SETUP_COMPLETED, false)
        }

        val existingInstall = prefs.contains(KEY_ALARMS) || prefs.contains(KEY_LAST_BLE_MAC)
        if (existingInstall) prefs.edit().putBoolean(KEY_SETUP_COMPLETED, true).apply()
        return !existingInstall
    }

    fun markSetupCompleted() {
        prefs.edit().putBoolean(KEY_SETUP_COMPLETED, true).apply()
    }

    fun saveAlarms(alarms: List<AlarmItem>) {
        val arr = JSONArray()
        alarms.forEach { arr.put(it.withRequiredSoundOutputs().toJson()) }
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
        val now = System.currentTimeMillis()
        return getAlarms().filter { it.isEnabled && it.slot(type).enabled }.minByOrNull { alarm ->
            val slot  = alarm.slot(type)
            alarm.recurrence.nextOccurrenceTime(slot.hour, slot.minute, now)
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
    fun saveLastEspWifiIp(ip: String) { prefs.edit().putString(KEY_LAST_ESP_WIFI_IP, ip).apply() }
    fun getLastEspWifiIp(): String? = prefs.getString(KEY_LAST_ESP_WIFI_IP, null)
    fun saveLastWifiSsid(ssid: String) { prefs.edit().putString(KEY_LAST_WIFI_SSID, ssid).apply() }
    fun getLastWifiSsid(): String? = prefs.getString(KEY_LAST_WIFI_SSID, null)

    // ------------------------------------------------------------------
    // Periodic auto-sync
    // ------------------------------------------------------------------

    fun getRingtoneVolume(): Int = prefs.getInt("ringtone_volume_level", 3).coerceIn(1, 5)
    fun setRingtoneVolume(level: Int) {
        prefs.edit().putInt("ringtone_volume_level", level.coerceIn(1, 5)).apply()
    }

    fun getAutoSyncHours(): Int = prefs.getInt(KEY_AUTO_SYNC_HOURS, DEFAULT_AUTO_SYNC_HOURS)
    fun backgroundSyncEnabled(): Boolean = prefs.getBoolean("background_sync", true)
    fun ringTimeoutMinutes(): Int = prefs.getInt("ring_timeout_minutes", 20).coerceIn(1, 120)
    fun setRingTimeoutMinutes(minutes: Int) {
        require(minutes in 1..120)
        prefs.edit().putInt("ring_timeout_minutes", minutes).apply()
    }
    fun displayRefreshMinutes(): Int = prefs.getInt("display_refresh_minutes", 0).coerceIn(0, 1440)
    fun setDisplayRefreshMinutes(minutes: Int) { prefs.edit().putInt("display_refresh_minutes", minutes.coerceIn(0, 1440)).apply() }
    fun wifiSyncEnabled(): Boolean = prefs.getBoolean("wifi_sync", true)
    fun setWifiSyncEnabled(enabled: Boolean) { prefs.edit().putBoolean("wifi_sync", enabled).apply() }
    fun setBackgroundSyncEnabled(enabled: Boolean) { prefs.edit().putBoolean("background_sync", enabled).apply() }
    fun clockStatusEnabled(): Boolean = prefs.getBoolean("clock_status", false)
    fun setClockStatusEnabled(enabled: Boolean) { prefs.edit().putBoolean("clock_status", enabled).apply() }
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

}
