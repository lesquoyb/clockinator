package com.example.alarmcontroller.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class AlarmReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "AlarmReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val alarmId = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_ID) ?: return
        val title = intent.getStringExtra(AlarmScheduler.EXTRA_ALARM_TITLE) ?: "Alarme"
        val isStart = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_START, true)
        val isSnooze = intent.getBooleanExtra(AlarmScheduler.EXTRA_IS_SNOOZE, false)

        val repository = AlarmRepository(context)
        val alarm = repository.getAlarmById(alarmId)

        if (alarm == null && !isSnooze) {
            Log.w(TAG, "Alarme introuvable pour l'id $alarmId")
            return
        }

        // Si l'alarme a été désactivée entre-temps
        if (alarm != null && !alarm.isEnabled && !isSnooze) {
            Log.d(TAG, "Alarme $title est désactivée, ignorée.")
            return
        }

        // Vérification de la condition "Cette fois tu ne te déclenches pas" (Dismiss next)
        if (alarm != null && alarm.isDismissedForNext && !isSnooze) {
            Log.d(TAG, "Alarme $title ignorée pour cette fois (Dismissed for next occurrence).")
            // Réinitialiser le flag pour le prochain cycle
            repository.dismissNext(alarmId, dismissed = false)
            // Reprogrammer pour demain
            AlarmScheduler(context).scheduleAlarm(alarm.copy(isDismissedForNext = false))
            return
        }

        // Déclencher la sonnerie réelle sur le téléphone
        Log.d(TAG, "Déclenchement sonnerie pour $title (${if (isStart) "Début" else "Fin"})")
        val displayTitle = if (isSnooze) "$title (Répétition)" else "$title (${if (isStart) "Début" else "Fin"})"
        AlarmRingtoneService.startAlarm(context, alarmId, displayTitle)

        // Reprogrammer pour le jour suivant si ce n'est pas un snooze
        if (alarm != null && !isSnooze) {
            AlarmScheduler(context).scheduleAlarm(alarm)
        }
    }
}
