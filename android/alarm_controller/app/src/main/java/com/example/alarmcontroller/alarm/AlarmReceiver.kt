package com.example.alarmcontroller.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Receives scheduled alarm broadcasts from [AlarmScheduler] and from boot.
 *
 * For each [RingtoneType] that fires:
 *   1. Guard checks: alarm exists, is enabled, not dismissed for this slot.
 *   2. If dismissed: clears the dismiss flag for that slot, re-schedules, returns.
 *   3. Otherwise: starts [AlarmRingtoneService] if [RingtoneSlot.ringOnPhone] is true,
 *      then re-schedules the alarm for the next cycle.
 *
 * BEDTIME is silently skipped when [RingtoneSlot.enabled] is false (the scheduler
 * never fires it in that case, but a leftover PendingIntent may still arrive after
 * a user disables it mid-cycle — handled here defensively).
 */
class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
        const val ACTION_ALARM_TRIGGER = "com.example.alarmcontroller.ALARM_TRIGGER"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_ALARM_TRIGGER) return

        val alarmId   = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID)      ?: return
        val title     = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_TITLE)   ?: "Alarm"
        val typeWire  = intent.getStringExtra(AlarmScheduler.EXTRA_RINGTONE_TYPE) ?: return
        val isSnooze  = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false)

        val ringtone = RingtoneType.fromWire(typeWire) ?: run {
            Log.w(TAG, "Unknown ringtone type: $typeWire")
            return
        }

        val repository = AlarmRepository(context)
        val alarm      = repository.getAlarmById(alarmId)

        if (alarm == null) {
            Log.w(TAG, "Alarm $alarmId not found, skipping")
            return
        }

        if (!alarm.isEnabled) {
            Log.d(TAG, "Alarm $title disabled, skipping")
            return
        }

        val slot = alarm.slot(ringtone)

        // BEDTIME guard: slot may have been disabled since it was scheduled
        if (ringtone == RingtoneType.BEDTIME && !slot.enabled) {
            Log.d(TAG, "Bedtime slot disabled for $title, skipping")
            return
        }

        // Dismiss-for-next guard
        if (!isSnooze && alarm.isDismissed(ringtone)) {
            Log.d(TAG, "Slot ${ringtone.wire} dismissed for $title this cycle")
            val updated = alarm.withDismissed(ringtone, false)
            repository.addOrUpdateAlarm(updated)
            AlarmScheduler(context).scheduleAlarm(updated)
            return
        }

        // Ring on phone if the slot has ringOnPhone set (or it's a snooze)
        if (slot.ringOnPhone || isSnooze) {
            val displayTitle = when {
                isSnooze                      -> "$title (Snooze)"
                ringtone == RingtoneType.LOCK_PHONE -> "$title - Lock"
                ringtone == RingtoneType.BEDTIME    -> "$title - Bedtime"
                ringtone == RingtoneType.WAKEUP     -> "$title - Wakeup"
                else                          -> title
            }
            AlarmRingtoneService.startAlarm(context, alarmId, displayTitle, ringtone)
        }

        // Re-schedule for next cycle (not for snooze — snooze is one-shot)
        if (!isSnooze) {
            AlarmScheduler(context).scheduleAlarm(alarm)
        }
    }
}
