package com.example.alarmcontroller.alarm

import org.json.JSONObject
import java.util.Locale
import java.util.UUID

/**
 * Modèle représentant une alarme avec horaires de début et de fin.
 *
 * @property id Identifiant unique
 * @property title Nom descriptif de l'alarme
 * @property startHour Heure de début (0-23)
 * @property startMinute Minute de début (0-59)
 * @property endHour Heure de fin (0-23)
 * @property endMinute Minute de fin (0-59)
 * @property isEnabled Si l'alarme est active de façon générale
 * @property isDismissedForNext Si vrai : "Cette fois tu ne te déclenches pas" (l'alarme est ignorée pour le prochain cycle)
 */
data class AlarmItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int,
    val isEnabled: Boolean = true,
    val isDismissedForNext: Boolean = false,
    val recurrence: Recurrence = Recurrence.Daily
) {
    val startFormatted: String
        get() = String.format(Locale.getDefault(), "%02d:%02d", startHour, startMinute)

    val endFormatted: String
        get() = String.format(Locale.getDefault(), "%02d:%02d", endHour, endMinute)

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            put("startHour", startHour)
            put("startMinute", startMinute)
            put("endHour", endHour)
            put("endMinute", endMinute)
            put("isEnabled", isEnabled)
            put("isDismissedForNext", isDismissedForNext)
            put("recurrence", recurrence.toJson())
            put("start", startFormatted)
            put("end", endFormatted)
        }
    }

    companion object {
        fun fromJson(json: JSONObject): AlarmItem {
            return AlarmItem(
                id = json.optString("id", UUID.randomUUID().toString()),
                title = json.optString("title", "Alarme"),
                startHour = json.optInt("startHour", 7),
                startMinute = json.optInt("startMinute", 0),
                endHour = json.optInt("endHour", 8),
                endMinute = json.optInt("endMinute", 0),
                isEnabled = json.optBoolean("isEnabled", true),
                isDismissedForNext = json.optBoolean("isDismissedForNext", false),
                recurrence = Recurrence.fromJson(json.optJSONObject("recurrence"))
            )
        }
    }
}

sealed class Recurrence {
    data object Daily : Recurrence()

    data class Weekly(val days: Set<Int>) : Recurrence() {
        init {
            require(days.isNotEmpty() && days.all { it in 1..7 })
        }
    }

    data class EveryDays(val interval: Int) : Recurrence() {
        init {
            require(interval in 1..365)
        }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        when (this@Recurrence) {
            Daily -> put("type", "daily")
            is Weekly -> {
                put("type", "weekly")
                put("days", days.sorted().joinToString(","))
            }
            is EveryDays -> {
                put("type", "interval")
                put("intervalDays", interval)
            }
        }
    }

    fun summary(): String = when (this) {
        Daily -> "Tous les jours"
        is Weekly -> if (days.size == 7) "Tous les jours" else days.sorted().joinToString(", ") { DAY_LABELS[it - 1] }
        is EveryDays -> "Tous les $interval jours"
    }

    companion object {
        private val DAY_LABELS = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")

        fun fromJson(json: JSONObject?): Recurrence {
            if (json == null) return Daily
            return when (json.optString("type", "daily")) {
                "weekly" -> {
                    val days = json.optString("days")
                        .split(",")
                        .mapNotNull { it.toIntOrNull() }
                        .filter { it in 1..7 }
                        .toSet()
                    if (days.isEmpty()) Daily else Weekly(days)
                }
                "interval" -> EveryDays(json.optInt("intervalDays", 1).coerceIn(1, 365))
                else -> Daily
            }
        }
    }
}
