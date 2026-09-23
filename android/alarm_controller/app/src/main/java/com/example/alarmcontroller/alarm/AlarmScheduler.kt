package com.example.alarmcontroller.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.example.alarmcontroller.MainActivity
import java.util.Calendar

class AlarmScheduler(private val context: Context) {

    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    companion object {
        private const val TAG = "AlarmScheduler"
        const val EXTRA_ALARM_ID = "extra_alarm_id"
        const val EXTRA_ALARM_TITLE = "extra_alarm_title"
        const val EXTRA_IS_START = "extra_is_start"
        const val EXTRA_IS_SNOOZE = "extra_is_snooze"
    }

    fun scheduleAlarm(alarm: AlarmItem) {
        if (!alarm.isEnabled) {
            cancelAlarm(alarm)
            return
        }

        scheduleTime(alarm, isStart = true)
        scheduleTime(alarm, isStart = false)
    }

    private fun scheduleTime(alarm: AlarmItem, isStart: Boolean) {
        val hour = if (isStart) alarm.startHour else alarm.endHour
        val minute = if (isStart) alarm.startMinute else alarm.endMinute

        val triggerTime = computeNextTriggerTime(hour, minute, alarm.recurrence)

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.alarmcontroller.ALARM_TRIGGER"
            putExtra(EXTRA_ALARM_ID, alarm.id)
            putExtra(EXTRA_ALARM_TITLE, alarm.title)
            putExtra(EXTRA_IS_START, isStart)
            putExtra(EXTRA_IS_SNOOZE, false)
        }

        val requestCode = getRequestCode(alarm.id, isStart)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            Log.d(TAG, "Alarme programmée [${alarm.title}] ${if (isStart) "Début" else "Fin"} à ${triggerTime}ms")
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission d'alarme exacte manquante", e)
        }
    }

    fun scheduleSnooze(alarmId: String, title: String, delayMinutes: Int = 10) {
        val triggerTime = System.currentTimeMillis() + (delayMinutes * 60 * 1000L)

        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = "com.example.alarmcontroller.ALARM_TRIGGER"
            putExtra(EXTRA_ALARM_ID, alarmId)
            putExtra(EXTRA_ALARM_TITLE, title)
            putExtra(EXTRA_IS_START, true)
            putExtra(EXTRA_IS_SNOOZE, true)
        }

        val requestCode = alarmId.hashCode() + 9999
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val showIntent = Intent(context, MainActivity::class.java)
        val showPendingIntent = PendingIntent.getActivity(
            context,
            requestCode,
            showIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            val alarmClockInfo = AlarmManager.AlarmClockInfo(triggerTime, showPendingIntent)
            alarmManager.setAlarmClock(alarmClockInfo, pendingIntent)
            Log.d(TAG, "Snooze programmé pour $delayMinutes minutes")
        } catch (e: SecurityException) {
            Log.e(TAG, "Erreur programmation snooze", e)
        }
    }

    fun cancelAlarm(alarm: AlarmItem) {
        cancelTime(alarm.id, isStart = true)
        cancelTime(alarm.id, isStart = false)
    }

    private fun cancelTime(alarmId: String, isStart: Boolean) {
        val intent = Intent(context, AlarmReceiver::class.java)
        val requestCode = getRequestCode(alarmId, isStart)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        )
        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    private fun computeNextTriggerTime(hour: Int, minute: Int, recurrence: Recurrence): Long {
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        val now = System.currentTimeMillis()
        val today = calendar.get(Calendar.DAY_OF_WEEK).let { if (it == Calendar.SUNDAY) 7 else it - 1 }
        val daysToAdd = when (recurrence) {
            Recurrence.Daily -> if (calendar.timeInMillis <= now) 1 else 0
            is Recurrence.Weekly -> (0..7).first { offset ->
                val day = (today - 1 + offset) % 7 + 1
                day in recurrence.days && (offset > 0 || calendar.timeInMillis > now)
            }
            is Recurrence.EveryDays -> if (calendar.timeInMillis <= now) recurrence.interval else 0
        }
        calendar.add(Calendar.DAY_OF_YEAR, daysToAdd)

        return calendar.timeInMillis
    }

    private fun getRequestCode(alarmId: String, isStart: Boolean): Int {
        val hash = alarmId.hashCode()
        return if (isStart) hash * 2 else hash * 2 + 1
    }
}
