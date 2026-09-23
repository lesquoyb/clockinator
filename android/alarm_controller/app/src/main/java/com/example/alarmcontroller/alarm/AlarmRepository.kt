package com.example.alarmcontroller.alarm

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import java.util.Calendar

class AlarmRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("alarm_controller_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ALARMS = "saved_alarms_json"
    }

    fun getAlarms(): List<AlarmItem> {
        val rawJson = prefs.getString(KEY_ALARMS, null) ?: return defaultAlarms()
        return try {
            val array = JSONArray(rawJson)
            val list = mutableListOf<AlarmItem>()
            for (i in 0 until array.length()) {
                list.add(AlarmItem.fromJson(array.getJSONObject(i)))
            }
            list
        } catch (e: Exception) {
            defaultAlarms()
        }
    }

    fun saveAlarms(alarms: List<AlarmItem>) {
        val array = JSONArray()
        for (alarm in alarms) {
            array.put(alarm.toJson())
        }
        prefs.edit().putString(KEY_ALARMS, array.toString()).apply()
    }

    fun addOrUpdateAlarm(alarm: AlarmItem) {
        val list = getAlarms().toMutableList()
        val index = list.indexOfFirst { it.id == alarm.id }
        if (index >= 0) {
            list[index] = alarm
        } else {
            list.add(alarm)
        }
        saveAlarms(list)
    }

    fun deleteAlarm(id: String) {
        val list = getAlarms().filterNot { it.id == id }
        saveAlarms(list)
    }

    fun toggleAlarm(id: String, enabled: Boolean) {
        val list = getAlarms().map {
            if (it.id == id) it.copy(isEnabled = enabled, isDismissedForNext = false) else it
        }
        saveAlarms(list)
    }

    fun dismissNext(id: String, dismissed: Boolean = true) {
        val list = getAlarms().map {
            if (it.id == id) it.copy(isDismissedForNext = dismissed) else it
        }
        saveAlarms(list)
    }

    fun getAlarmById(id: String): AlarmItem? {
        return getAlarms().find { it.id == id }
    }

    /**
     * Trouve la prochaine alarme active planifiée pour pouvoir la dismisser (ex: via NFC).
     */
    fun findUpcomingAlarm(): AlarmItem? {
        val now = Calendar.getInstance()
        val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        val activeAlarms = getAlarms().filter { it.isEnabled }
        if (activeAlarms.isEmpty()) return null

        // Cherche l'alarme la plus proche aujourd'hui plus tard, sinon la première demain
        return activeAlarms.minByOrNull { alarm ->
            val startTotal = alarm.startHour * 60 + alarm.startMinute
            var diff = startTotal - currentMinutes
            if (diff <= 0) diff += 24 * 60 // demain
            diff
        }
    }

    private fun defaultAlarms(): List<AlarmItem> {
        return listOf(
            AlarmItem(
                title = "Alarme Principale",
                startHour = 7,
                startMinute = 0,
                endHour = 8,
                endMinute = 0,
                isEnabled = true,
                isDismissedForNext = false
            )
        )
    }
}
