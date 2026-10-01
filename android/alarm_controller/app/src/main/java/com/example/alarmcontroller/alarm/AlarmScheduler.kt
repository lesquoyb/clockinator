package com.example.alarmcontroller.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.alarmcontroller.MainActivity
import java.util.Calendar

/**
 * Schedules and cancels exact Android alarms for each [AlarmItem].
 *
 * Each alarm has up to three slots: LOCK_PHONE, BEDTIME (optional), WAKEUP.
 * Each slot gets its own PendingIntent so they can be independently cancelled.
 * The BEDTIME slot is only scheduled when [RingtoneSlot.enabled] is true.
 *
 * Request-code scheme (avoids collisions):
 *   LOCK_PHONE : hash * 3
 *   BEDTIME    : hash * 3 + 1
 *   WAKEUP     : hash * 3 + 2
 *   SNOOZE     : hash + 9999
 */
class AlarmScheduler(private val context: Context) {

    companion object {
        private const val TAG = "AlarmScheduler"

        const val EXTRA_ALARM_ID      = "extra_alarm_id"
        const val EXTRA_ALARM_TITLE   = "extra_alarm_title"
        const val EXTRA_RINGTONE_TYPE = "extra_ringtone_type"   // RingtoneType.wire string
        const val EXTRA_IS_SNOOZE     = "extra_is_snooze"
    }

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    fun scheduleAlarm(alarm: AlarmItem) {
        if (!alarm.isEnabled) {
            cancelAlarm(alarm)
            return
        }
        scheduleSlot(alarm, RingtoneType.LOCK_PHONE)
        scheduleSlot(alarm, RingtoneType.BEDTIME)
        scheduleSlot(alarm, RingtoneType.WAKEUP)
    }

    fun cancelAlarm(alarm: AlarmItem) {
        RingtoneType.entries.forEach { cancelSlot(alarm.id, it) }
    }

    fun scheduleSnooze(alarmId: String, title: String, ringtone: RingtoneType, delayMinutes: Int = 10) {
        val triggerTime = System.currentTimeMillis() + delayMinutes * 60_000L

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM_TRIGGER
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_TITLE, title)
            putExtra(EXTRA_RINGTONE_TYPE, ringtone.wire)
            putExtra(EXTRA_IS_SNOOZE, true)
        }
        val requestCode = alarmId.hashCode() + 9999
        val pi = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val showPi = showPendingIntent(requestCode)
        try {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerTime, showPi), pi)
            Log.d(TAG, "Snooze scheduled for $delayMinutes min — alarm=$alarmId ringtone=${ringtone.wire}")
        } catch (e: SecurityException) {
            Log.e(TAG, "Exact alarm permission missing", e)
        }
    }

    // ------------------------------------------------------------------
    // Private helpers
    // ------------------------------------------------------------------

    private fun scheduleSlot(alarm: AlarmItem, type: RingtoneType) {
        val slot = alarm.slot(type)

        // BEDTIME is optional; skip if not enabled
        if (type == RingtoneType.BEDTIME && !slot.enabled) {
            cancelSlot(alarm.id, type)
            return
        }

        val triggerTime = computeNextTriggerTime(slot.hour, slot.minute, alarm.recurrence)

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_ALARM_TRIGGER
            putExtra(EXTRA_ALARM_ID, alarm.id)
            putExtra(EXTRA_ALARM_TITLE, alarm.title)
            putExtra(EXTRA_RINGTONE_TYPE, type.wire)
            putExtra(EXTRA_IS_SNOOZE, false)
        }
        val requestCode = getRequestCode(alarm.id, type)
        val pi          = PendingIntent.getBroadcast(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val showPi = showPendingIntent(requestCode)
        try {
            alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerTime, showPi), pi)
            Log.d(TAG, "Scheduled ${alarm.title} [${type.wire}] at $triggerTime")
        } catch (e: SecurityException) {
            Log.e(TAG, "Exact alarm permission missing", e)
        }
    }

    private fun cancelSlot(alarmId: String, type: RingtoneType) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val pi = PendingIntent.getBroadcast(
            context,
            getRequestCode(alarmId, type),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        alarmManager.cancel(pi)
        pi.cancel()
    }

    private fun showPendingIntent(requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            context, requestCode,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun getRequestCode(alarmId: String, type: RingtoneType): Int {
        val base = alarmId.hashCode() * 3
        return when (type) {
            RingtoneType.LOCK_PHONE -> base
            RingtoneType.BEDTIME    -> base + 1
            RingtoneType.WAKEUP     -> base + 2
        }
    }

    private fun computeNextTriggerTime(hour: Int, minute: Int, recurrence: Recurrence): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now   = System.currentTimeMillis()
        val today = cal.get(Calendar.DAY_OF_WEEK).let { if (it == Calendar.SUNDAY) 7 else it - 1 }

        val daysToAdd = when (recurrence) {
            Recurrence.Daily ->
                if (cal.timeInMillis <= now) 1 else 0
            is Recurrence.Weekly ->
                (0..7).first { offset ->
                    val day = (today - 1 + offset) % 7 + 1
                    day in recurrence.days && (offset > 0 || cal.timeInMillis > now)
                }
            is Recurrence.EveryDays ->
                if (cal.timeInMillis <= now) recurrence.interval else 0
        }
        cal.add(Calendar.DAY_OF_YEAR, daysToAdd)
        return cal.timeInMillis
    }
}
