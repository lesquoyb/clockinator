package com.example.alarmcontroller.alarm.nfc

import android.content.Context
import com.example.alarmcontroller.alarm.AlarmItem
import com.example.alarmcontroller.alarm.AlarmRepository
import com.example.alarmcontroller.alarm.AlarmRingtoneService
import com.example.alarmcontroller.alarm.RingtoneType
import com.example.alarmcontroller.lock.LockModeManager

/**
 * Maps physical NFC tag scans to alarm and lock-mode actions.
 *
 * Tag payloads (NDEF TEXT record content, trimmed + uppercased):
 *
 *   "ODODO"   -> bedtime/lock-phone side
 *                - If an alarm is ringing on the lock-phone or bedtime slot: stops it.
 *                - Otherwise: toggles the dismiss-for-next on the next lock-phone slot.
 *                - Always activates the phone's focus / lock mode.
 *
 *   "OBOULO"  -> wakeup side
 *                - If an alarm is ringing on the wakeup slot: stops it.
 *                - Otherwise: toggles the dismiss-for-next on the next wakeup slot.
 *                - Always releases the phone's focus / lock mode.
 *
 *   Anything else -> NfcResult.UnknownTag (no-op).
 *
 * The ViewModel is responsible for sending the matching BLE command to the
 * ESP32 after a stop or dismiss action.
 */
class NfcActionHandler(private val context: Context) {

    companion object {
        const val TAG_PAYLOAD_ODODO  = "ODODO"   // bedtime / lock-phone tag
        const val TAG_PAYLOAD_OBOULO = "OBOULO"  // wakeup tag
    }

    private val repository   = AlarmRepository(context)
    val lockModeManager      = LockModeManager(context)

    // ------------------------------------------------------------------
    // Result types
    // ------------------------------------------------------------------

    sealed interface NfcResult {
        /** An alarm that was actively ringing has been stopped. */
        data class StoppedRinging(
            val alarmId: String,
            val ringtone: RingtoneType,
            val message: String
        ) : NfcResult

        /** The next occurrence of a slot has been dismissed (or un-dismissed). */
        data class DismissedNext(
            val alarmId: String,
            val ringtone: RingtoneType,
            val dismissed: Boolean,
            val alarmTitle: String,
            val time: String
        ) : NfcResult

        /** Tag was recognised but no actionable alarm state was found. */
        data class NoAction(val message: String) : NfcResult

        /** Tag payload was not one of the two configured values. */
        data class UnknownTag(val payload: String?) : NfcResult
    }

    // ------------------------------------------------------------------
    // Public entry point
    // ------------------------------------------------------------------

    fun handleNfcTagScanned(tagPayload: String? = null): NfcResult {
        val payload = tagPayload?.trim()?.uppercase()
        return when (payload) {
            TAG_PAYLOAD_ODODO  -> handleOdodo()
            TAG_PAYLOAD_OBOULO -> handleOboulo()
            else               -> NfcResult.UnknownTag(tagPayload)
        }
    }

    // ------------------------------------------------------------------
    // ODODO tag: lock-phone / bedtime side
    // ------------------------------------------------------------------

    private fun handleOdodo(): NfcResult {
        lockModeManager.activate()

        // Determine which ringtone slot is currently ringing (lock_phone or bedtime)
        val ringingSlot = currentlyRingingSlot()
        if (ringingSlot == RingtoneType.LOCK_PHONE || ringingSlot == RingtoneType.BEDTIME) {
            val id    = AlarmRingtoneService.activeAlarmId ?: ""
            val title = AlarmRingtoneService.ringingAlarmTitle.value ?: "Alarm"
            AlarmRingtoneService.stopAlarm(context)
            return NfcResult.StoppedRinging(id, ringingSlot,
                "ODODO: '${ringingSlot.wire}' alarm '$title' stopped. Focus mode ON.")
        }

        // Nothing ringing: toggle dismiss on the next lock_phone slot
        val upcoming = repository.findUpcomingSlot(RingtoneType.LOCK_PHONE)
        if (upcoming != null) {
            val newDismiss = !upcoming.isDismissed(RingtoneType.LOCK_PHONE)
            val updated    = upcoming.withDismissed(RingtoneType.LOCK_PHONE, newDismiss)
            repository.addOrUpdateAlarm(updated)
            return NfcResult.DismissedNext(
                alarmId      = upcoming.id,
                ringtone     = RingtoneType.LOCK_PHONE,
                dismissed    = newDismiss,
                alarmTitle   = upcoming.title,
                time         = upcoming.lockPhoneFormatted
            )
        }
        return NfcResult.NoAction("ODODO: no active lock-phone alarm found.")
    }

    // ------------------------------------------------------------------
    // OBOULO tag: wakeup side
    // ------------------------------------------------------------------

    private fun handleOboulo(): NfcResult {
        lockModeManager.release()

        val ringingSlot = currentlyRingingSlot()
        if (ringingSlot == RingtoneType.WAKEUP) {
            val id    = AlarmRingtoneService.activeAlarmId ?: ""
            val title = AlarmRingtoneService.ringingAlarmTitle.value ?: "Alarm"
            AlarmRingtoneService.stopAlarm(context)
            return NfcResult.StoppedRinging(id, RingtoneType.WAKEUP,
                "OBOULO: wakeup alarm '$title' stopped. Focus mode OFF.")
        }

        val upcoming = repository.findUpcomingSlot(RingtoneType.WAKEUP)
        if (upcoming != null) {
            val newDismiss = !upcoming.isDismissed(RingtoneType.WAKEUP)
            val updated    = upcoming.withDismissed(RingtoneType.WAKEUP, newDismiss)
            repository.addOrUpdateAlarm(updated)
            return NfcResult.DismissedNext(
                alarmId      = upcoming.id,
                ringtone     = RingtoneType.WAKEUP,
                dismissed    = newDismiss,
                alarmTitle   = upcoming.title,
                time         = upcoming.wakeupFormatted
            )
        }
        return NfcResult.NoAction("OBOULO: no active wakeup alarm found.")
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /**
     * Returns the [RingtoneType] that is currently ringing on the phone,
     * or null if no alarm is active.
     */
    private fun currentlyRingingSlot(): RingtoneType? {
        if (!AlarmRingtoneService.isRinging.value) return null
        return AlarmRingtoneService.activeRingtoneType
    }
}
