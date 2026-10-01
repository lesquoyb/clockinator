package com.example.alarmcontroller.alarm

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale
import java.util.UUID

// ---------------------------------------------------------------------------
// RingtoneType
// ---------------------------------------------------------------------------

/**
 * The three distinct ringtone slots of an alarm, in chronological order.
 *
 *   lock_phone  - First ring. Activates the phone's lock / focus mode.
 *                 If bedtime is disabled this slot is merged with bedtime
 *                 (same physical time, same ring).
 *
 *   bedtime     - Second ring. Optional. Signals "time to sleep": lights up
 *                 the ESP LED strip, can also ring on phone and/or device.
 *                 When disabled, lock_phone takes its place.
 *
 *   wakeup      - Third ring. Releases the phone's lock mode and wakes the
 *                 user up. Corresponds to the old "end" alarm.
 *
 * JSON value strings are the canonical wire format used in SYNC and
 * all STOP / SNOOZE / DISMISS_NEXT commands.
 */
enum class RingtoneType(val wire: String) {
    LOCK_PHONE("lock_phone"),
    BEDTIME("bedtime"),
    WAKEUP("wakeup");

    companion object {
        fun fromWire(s: String?): RingtoneType? = when (s) {
            "lock", "lock_phone" -> LOCK_PHONE
            "bed", "bedtime" -> BEDTIME
            "wake", "wakeup" -> WAKEUP
            else -> null
        }
    }
}

// ---------------------------------------------------------------------------
// RingtoneSlot
// ---------------------------------------------------------------------------

/**
 * Configuration for one of the three ringtone slots.
 *
 * @property hour        Trigger hour   (0-23)
 * @property minute      Trigger minute (0-59)
 * @property ringOnPhone Whether the phone plays its ringtone at this time
 * @property ringOnDevice Whether the ESP32 plays its ringtone at this time
 * @property enabled     Only relevant for [RingtoneType.BEDTIME]; always true
 *                       for LOCK_PHONE and WAKEUP.
 */
data class RingtoneSlot(
    val hour: Int,
    val minute: Int,
    val ringOnPhone: Boolean  = true,
    val ringOnDevice: Boolean = true,
    val enabled: Boolean      = true
) {
    val formatted: String
        get() = String.format(Locale.getDefault(), "%02d:%02d", hour, minute)

    fun toStorageJson(): JSONObject = JSONObject().apply {
        put("hour", hour)
        put("minute", minute)
        put("h", hour)
        put("m", minute)
        put("ringOnPhone", ringOnPhone)
        put("ringOnDevice", ringOnDevice)
        put("ringPhone", ringOnPhone)
        put("ringDevice", ringOnDevice)
        put("enabled", enabled)
    }

    fun toWireJson(type: RingtoneType): JSONObject = JSONObject().apply {
        put("h", hour)
        put("m", minute)
        if (type == RingtoneType.LOCK_PHONE || type == RingtoneType.BEDTIME) {
            put("ringDevice", ringOnDevice)
        }
        if (type == RingtoneType.WAKEUP || type == RingtoneType.BEDTIME) {
            put("ringPhone", ringOnPhone)
        }
    }

    fun toJson(type: RingtoneType): JSONObject = toWireJson(type)

    companion object {
        fun fromJson(json: JSONObject): RingtoneSlot = RingtoneSlot(
            hour         = if (json.has("hour")) json.optInt("hour", 0) else json.optInt("h", 0),
            minute       = if (json.has("minute")) json.optInt("minute", 0) else json.optInt("m", 0),
            ringOnPhone  = if (json.has("ringOnPhone")) json.optBoolean("ringOnPhone", true) else json.optBoolean("ringPhone", true),
            ringOnDevice = if (json.has("ringOnDevice")) json.optBoolean("ringOnDevice", true) else json.optBoolean("ringDevice", true),
            enabled      = json.optBoolean("enabled", true)
        )
    }
}

// ---------------------------------------------------------------------------
// AlarmItem
// ---------------------------------------------------------------------------

/**
 * Represents a single alarm with three optional ringtone slots:
 *   lock_phone -> bedtime (optional) -> wakeup
 *
 * Migration from the previous two-slot model (start/end) is handled
 * transparently in [fromJson] via the back-compat block.
 *
 * @property id               UUID
 * @property title            Human-readable name
 * @property isEnabled        Global on/off
 * @property recurrence       Recurrence rule
 * @property lockPhone        LOCK_PHONE slot (always present)
 * @property bedtime          BEDTIME slot; set [RingtoneSlot.enabled] = false to skip
 * @property wakeup           WAKEUP slot (always present)
 * @property snoozeDurations  Allowed snooze durations in minutes
 * @property ringtoneFile     WAV filename on the ESP32 FS (null = built-in melody)
 *
 * Dismiss semantics: [dismissedRingtones] is the set of [RingtoneType]s that
 * are skipped for the very next occurrence. Cleared automatically after firing.
 */
data class AlarmItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val isEnabled: Boolean = true,
    val recurrence: Recurrence = Recurrence.Daily,
    val lockPhone: RingtoneSlot,
    val bedtime: RingtoneSlot,
    val wakeup: RingtoneSlot,
    val snoozeDurations: List<Int> = DEFAULT_SNOOZE_DURATIONS,
    val ringtoneFile: String? = null,
    /** Ringtone types dismissed for the next single occurrence. */
    val dismissedRingtones: Set<RingtoneType> = emptySet(),
    // Legacy field kept so AlarmRepository.findUpcoming* still compiles.
    // Points to the lock_phone slot for backward compatibility.
    val isDismissedForNext: Boolean = false
) {
    // Convenience formatted strings
    val lockPhoneFormatted: String get() = lockPhone.formatted
    val bedtimeFormatted:   String get() = bedtime.formatted
    val wakeupFormatted:    String get() = wakeup.formatted

    /** True when bedtime is active (not merged into lock_phone). */
    val isBedtimeActive: Boolean get() = bedtime.enabled

    /** Returns the slot for [type]. */
    fun slot(type: RingtoneType): RingtoneSlot = when (type) {
        RingtoneType.LOCK_PHONE -> lockPhone
        RingtoneType.BEDTIME    -> bedtime
        RingtoneType.WAKEUP     -> wakeup
    }

    /** True if [type] is dismissed for the next occurrence. */
    fun isDismissed(type: RingtoneType): Boolean = type in dismissedRingtones

    fun withDismissed(type: RingtoneType, dismissed: Boolean): AlarmItem {
        val updated = if (dismissed) dismissedRingtones + type
                      else           dismissedRingtones - type
        return copy(
            dismissedRingtones = updated,
            isDismissedForNext = RingtoneType.LOCK_PHONE in updated
        )
    }

    fun clearAllDismissals(): AlarmItem =
        copy(dismissedRingtones = emptySet(), isDismissedForNext = false)

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("title", title)
        put("isEnabled", isEnabled)
        put("recurrence", recurrence.toJson())
        put("lockPhone", lockPhone.toStorageJson())
        put("bedtime",   bedtime.toStorageJson())
        put("wakeup",    wakeup.toStorageJson())
        val durArray = JSONArray()
        snoozeDurations.forEach { durArray.put(it) }
        put("snoozeDurations", durArray)
        if (ringtoneFile != null) put("ringtoneFile", ringtoneFile)
        val dismissArray = JSONArray()
        dismissedRingtones.forEach { dismissArray.put(it.wire) }
        put("dismissedRingtones", dismissArray)
    }

    companion object {
        val DEFAULT_SNOOZE_DURATIONS = listOf(5, 10, 30, 60)

        fun fromJson(json: JSONObject): AlarmItem {
            val durArray = json.optJSONArray("snoozeDurations")
            val durations = if (durArray != null)
                (0 until durArray.length()).mapNotNull { durArray.optInt(it).takeIf { v -> v > 0 } }
                    .ifEmpty { DEFAULT_SNOOZE_DURATIONS }
            else DEFAULT_SNOOZE_DURATIONS

            val dismissArray = json.optJSONArray("dismissedRingtones")
            val dismissed: Set<RingtoneType> = if (dismissArray != null)
                (0 until dismissArray.length())
                    .mapNotNull { RingtoneType.fromWire(dismissArray.optString(it)) }
                    .toSet()
            else emptySet()

            // ---- Migration from old two-slot format (start/end) ----------
            // Old format used: startHour, startMinute, endHour, endMinute,
            // ringOnDevice, ringOnPhone at the root level.
            val hasNewFormat = json.has("lockPhone")
            val lockPhone: RingtoneSlot
            val bedtime: RingtoneSlot
            val wakeup: RingtoneSlot

            if (hasNewFormat) {
                lockPhone = RingtoneSlot.fromJson(json.optJSONObject("lockPhone") ?: JSONObject())
                bedtime   = RingtoneSlot.fromJson(json.optJSONObject("bedtime")   ?: JSONObject())
                wakeup    = RingtoneSlot.fromJson(json.optJSONObject("wakeup")    ?: JSONObject())
            } else {
                // Back-compat: old start = lock_phone, old end = wakeup
                val startH = json.optInt("startHour", 22)
                val startM = json.optInt("startMinute", 0)
                val endH   = json.optInt("endHour", 7)
                val endM   = json.optInt("endMinute", 0)
                val ringDev   = json.optBoolean("ringOnDevice", true)
                val ringPhone = json.optBoolean("ringOnPhone", true)
                lockPhone = RingtoneSlot(startH, startM, ringOnPhone = ringPhone, ringOnDevice = ringDev)
                bedtime   = RingtoneSlot(startH, startM, ringOnPhone = false, ringOnDevice = false, enabled = false)
                wakeup    = RingtoneSlot(endH, endM, ringOnPhone = ringPhone, ringOnDevice = ringDev)
            }
            // ---------------------------------------------------------------

            val isDismissedLegacy = json.optBoolean("isDismissedForNext", false)
            val finalDismissed = if (isDismissedLegacy && dismissed.isEmpty())
                setOf(RingtoneType.LOCK_PHONE)
            else dismissed

            return AlarmItem(
                id                 = json.optString("id", UUID.randomUUID().toString()),
                title              = json.optString("title", "Alarm"),
                isEnabled          = json.optBoolean("isEnabled", true),
                recurrence         = Recurrence.fromJson(json.optJSONObject("recurrence")),
                lockPhone          = lockPhone,
                bedtime            = bedtime,
                wakeup             = wakeup,
                snoozeDurations    = durations,
                ringtoneFile       = if (json.has("ringtoneFile")) json.getString("ringtoneFile") else null,
                dismissedRingtones = finalDismissed,
                isDismissedForNext = RingtoneType.LOCK_PHONE in finalDismissed
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Recurrence (unchanged)
// ---------------------------------------------------------------------------

sealed class Recurrence {
    data object Daily : Recurrence()
    data class Weekly(val days: Set<Int>) : Recurrence() {
        init { require(days.isNotEmpty() && days.all { it in 1..7 }) }
    }
    data class EveryDays(val interval: Int) : Recurrence() {
        init { require(interval in 1..365) }
    }

    fun toJson(): JSONObject = JSONObject().apply {
        when (this@Recurrence) {
            Daily        -> put("type", "daily")
            is Weekly    -> { put("type", "weekly"); put("days", days.sorted().joinToString(",")) }
            is EveryDays -> { put("type", "interval"); put("intervalDays", interval) }
        }
    }

    fun summary(): String = when (this) {
        Daily        -> "Every day"
        is Weekly    -> if (days.size == 7) "Every day"
                        else days.sorted().joinToString(", ") { DAY_LABELS[it - 1] }
        is EveryDays -> "Every $interval days"
    }

    companion object {
        private val DAY_LABELS = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")

        fun fromJson(json: JSONObject?): Recurrence {
            if (json == null) return Daily
            return when (json.optString("type", "daily")) {
                "weekly" -> {
                    val days = json.optString("days")
                        .split(",").mapNotNull { it.toIntOrNull() }
                        .filter { it in 1..7 }.toSet()
                    if (days.isEmpty()) Daily else Weekly(days)
                }
                "interval" -> EveryDays(json.optInt("intervalDays", 1).coerceIn(1, 365))
                else       -> Daily
            }
        }
    }
}
