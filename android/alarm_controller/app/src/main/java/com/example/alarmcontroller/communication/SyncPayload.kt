package com.example.alarmcontroller.communication

import com.example.alarmcontroller.alarm.AlarmItem
import com.example.alarmcontroller.alarm.RingtoneType
import org.json.JSONArray
import org.json.JSONObject
import java.util.TimeZone

/**
 * Builds every payload exchanged between phone and ESP32 over BLE.
 *
 * The phone is the primary source of truth for alarm configuration.
 * Either device may initiate STOP / SNOOZE / DISMISS_NEXT; the recipient
 * must validate the (alarmId, ringtone) pair before acting to avoid
 * stale messages stopping the wrong alarm.
 *
 * ---- BLE message directions -----------------------------------------------
 * Phone -> ESP  :  SYNC, STOP_ALARM, SNOOZE_ALARM, DISMISS_NEXT,
 *                  PUSH_RINGTONE, UPLOAD_BEGIN/CHUNK/END, DELETE_RINGTONE
 * ESP   -> Phone:  STOP_ALARM, SNOOZE_ALARM, DISMISS_NEXT,
 *                  CHUNK_ACK:<n>, UPLOAD_OK, UPLOAD_ERR
 * --------------------------------------------------------------------------
 *
 * ---- Ringtone upload protocol ---------------------------------------------
 *   1. BEGIN  {"action":"UPLOAD_BEGIN",  "filename":"x.wav","size":<bytes>}
 *   2. CHUNK  {"action":"UPLOAD_CHUNK",  "index":<n>,"data":"<base64>"}
 *   3. END    {"action":"UPLOAD_END",    "filename":"x.wav","crc":<crc32>}
 * ESP ACKs each chunk with "CHUNK_ACK:<index>" and the whole transfer
 * with "UPLOAD_OK" or "UPLOAD_ERR".
 * Chunk size kept to <= 300 raw bytes so base64 fits within a 512-byte MTU.
 * --------------------------------------------------------------------------
 */
object SyncPayload {

    // ------------------------------------------------------------------
    // JSON action field values (used in both directions)
    // ------------------------------------------------------------------

    const val ACTION_SYNC        = "SYNC"
    const val ACTION_STOP_ALARM       = "STOP_ALARM"
    const val ACTION_SNOOZE_ALARM     = "SNOOZE_ALARM"
    const val ACTION_DISMISS_NEXT     = "DISMISS_NEXT"
    const val ACTION_PUSH_RINGTONE    = "PUSH_RINGTONE"
    const val ACTION_UPLOAD_BEGIN     = "UPLOAD_BEGIN"
    const val ACTION_UPLOAD_CHUNK     = "UPLOAD_CHUNK"
    const val ACTION_UPLOAD_END       = "UPLOAD_END"
    const val ACTION_DELETE_RINGTONE  = "DELETE_RINGTONE"
    const val ACTION_WIFI_CONFIG      = "WIFI_CONFIG"
    const val ACTION_WIFI_STATUS      = "WIFI_STATUS"

    // ACK strings sent from the ESP back to the phone
    const val ACK_CHUNK      = "CHUNK_ACK:"   // followed by chunk index
    const val ACK_UPLOAD_OK  = "UPLOAD_OK"
    const val ACK_UPLOAD_ERR = "UPLOAD_ERR"

    /** Raw bytes per chunk before base64 encoding. */
    const val CHUNK_SIZE_BYTES = 300

    fun buildWifiConfig(ssid: String, password: String): String =
        JSONObject().apply {
            put("action", ACTION_WIFI_CONFIG)
            put("ssid", ssid)
            put("password", password)
        }.toString()

    // ------------------------------------------------------------------
    // Phone -> ESP: full alarm sync
    // ------------------------------------------------------------------

    /**
     * Full time + alarm list push. The ESP clears its list on receipt.
     * Only enabled alarms are included; dismissed ringtones are passed
     * so the ESP can honour one-shot skips without a full re-sync.
     */
    fun buildPushSyncJson(alarms: List<AlarmItem>): String {
        val now = System.currentTimeMillis()
        val tz  = TimeZone.getDefault()
        return JSONObject().apply {
            put("action", ACTION_SYNC)
            put("timestamp", now / 1000)
            put("utcOffsetSec", tz.getOffset(now) / 1_000)
            val arr = JSONArray()
            for (alarm in alarms) {
                if (!alarm.isEnabled) continue
                arr.put(JSONObject().apply {
                    put("id", alarm.id)
                    put("recurrence", alarm.recurrence.toJson())
                    if (alarm.lockPhone.enabled){
                        put("lock", alarm.lockPhone.toJson(RingtoneType.LOCK_PHONE))
                    }
                    if (alarm.bedtime.enabled) {
                        put("bed", alarm.bedtime.toJson(RingtoneType.BEDTIME))
                    }
                    if (alarm.wakeup.enabled) {
                        put("wake", alarm.wakeup.toJson(RingtoneType.WAKEUP))
                    }
                    if (alarm.ringtoneFile != null) put("file", alarm.ringtoneFile)
                    // Dismissed set so ESP skips the right slots even without a full resync
                    val dismissArr = JSONArray()
                    alarm.dismissedRingtones.forEach { dismissArr.put(it.wire) }
                    put("dismissed", dismissArr)
                })
            }
            put("alarms", arr)
        }.toString()
    }

    // ------------------------------------------------------------------
    // Bidirectional: STOP_ALARM
    // Guards against stale messages by carrying the alarmId and ringtone.
    // The recipient silences only if it is currently playing exactly that
    // (alarmId, ringtone) pair.
    // ------------------------------------------------------------------

    fun buildStopAlarm(alarmId: String, ringtone: RingtoneType): String =
        JSONObject().apply {
            put("action", ACTION_STOP_ALARM)
            put("alarmId", alarmId)
            put("ringtone", ringtone.wire)
        }.toString()

    fun buildStopAlarm(): String =
        JSONObject().apply {
            put("action", ACTION_STOP_ALARM)
            put("alarmId", "")
            put("ringtone", "")
        }.toString()

    // ------------------------------------------------------------------
    // Bidirectional: SNOOZE_ALARM
    // ------------------------------------------------------------------

    fun buildSnoozeAlarm(alarmId: String, ringtone: RingtoneType, minutes: Int): String =
        JSONObject().apply {
            put("action", ACTION_SNOOZE_ALARM)
            put("alarmId", alarmId)
            put("ringtone", ringtone.wire)
            put("minutes", minutes)
        }.toString()

    fun buildSnoozeAlarm(minutes: Int): String =
        JSONObject().apply {
            put("action", ACTION_SNOOZE_ALARM)
            put("alarmId", "")
            put("ringtone", "")
            put("minutes", minutes)
        }.toString()

    // ------------------------------------------------------------------
    // Bidirectional: DISMISS_NEXT
    // Sent by whichever device toggles the skip; the other updates its own
    // state accordingly.
    // ------------------------------------------------------------------

    fun buildDismissNext(alarmId: String, ringtone: RingtoneType, dismissed: Boolean): String =
        JSONObject().apply {
            put("action", ACTION_DISMISS_NEXT)
            put("alarmId", alarmId)
            put("ringtone", ringtone.wire)
            put("dismissed", dismissed)
        }.toString()

    // ------------------------------------------------------------------
    // Phone -> ESP: push ringtone trigger
    // ------------------------------------------------------------------

    /** Tell the ESP to start ringing [ringtone] for [alarmId] immediately. */
    fun buildPushRingtoneJson(alarmId: String, ringtone: RingtoneType): String =
        JSONObject().apply {
            put("action", ACTION_PUSH_RINGTONE)
            put("alarmId", alarmId)
            put("ringtone", ringtone.wire)
        }.toString()

    // ------------------------------------------------------------------
    // Phone -> ESP: WAV file upload
    // ------------------------------------------------------------------

    fun buildUploadBegin(filename: String, size: Int): String =
        JSONObject().apply {
            put("action", ACTION_UPLOAD_BEGIN)
            put("filename", filename)
            put("size", size)
        }.toString()

    fun buildUploadChunk(index: Int, data: String): String =
        JSONObject().apply {
            put("action", ACTION_UPLOAD_CHUNK)
            put("index", index)
            put("data", data)
        }.toString()

    fun buildUploadEnd(filename: String, crc32: Long): String =
        JSONObject().apply {
            put("action", ACTION_UPLOAD_END)
            put("filename", filename)
            put("crc", crc32)
        }.toString()

    fun buildDeleteRingtone(filename: String): String =
        JSONObject().apply {
            put("action", ACTION_DELETE_RINGTONE)
            put("filename", filename)
        }.toString()

    // ------------------------------------------------------------------
    // Snooze prefix (kept for legacy BLE text format from the ESP)
    // ------------------------------------------------------------------

    const val CMD_SNOOZE_PREFIX = "SNOOZE:"
    fun buildSnoozeCommand(minutes: Int) = "$CMD_SNOOZE_PREFIX$minutes"
}
