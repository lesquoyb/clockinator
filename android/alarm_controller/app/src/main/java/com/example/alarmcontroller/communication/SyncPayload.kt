package com.example.alarmcontroller.communication

import com.example.alarmcontroller.alarm.AlarmItem
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object SyncPayload {

    /**
     * Construit le paquet JSON envoyé au périphérique distant lors de la synchronisation (Push).
     */
    fun buildPushSyncJson(alarms: List<AlarmItem>): String {
        val now = System.currentTimeMillis()
        val timeZone = TimeZone.getDefault()

        val root = JSONObject().apply {
            put("action", "SYNC_PUSH")
            put("timestamp", now / 1000)
            put("utcOffsetSeconds", timeZone.getOffset(now) / 1_000)

            val alarmsArray = JSONArray()
            for (alarm in alarms) {
                if (alarm.isEnabled){
                    val item = JSONObject().apply {
                        put("start", alarm.startFormatted)
                        put("end", alarm.endFormatted)
                        //put("dismissed", alarm.isDismissedForNext)
                        put("recurrence", alarm.recurrence.toJson())
                    }
                    alarmsArray.put(item)
                }
            }
            put("alarms", alarmsArray)
        }

        return root.toString()
    }

    /**
     * Commande standard pour ordonner au périphérique d'éteindre sa propre alarme.
     */
    const val CMD_STOP_REMOTE_ALARM = "STOP_REMOTE_ALARM"
}
