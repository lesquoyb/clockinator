package com.example.alarmcontroller.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            com.example.alarmcontroller.communication.ble.ClockPresenceReceiver.watch(context)
            Log.d("BootReceiver", "Téléphone redémarré. Reprogrammation des alarmes...")
            val repository = AlarmRepository(context)
            val scheduler = AlarmScheduler(context)

            val alarms = repository.getAlarms()
            for (alarm in alarms) {
                if (alarm.isEnabled) {
                    scheduler.scheduleAlarm(alarm)
                }
            }
        }
    }
}
