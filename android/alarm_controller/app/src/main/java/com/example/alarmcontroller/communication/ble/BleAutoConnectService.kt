package com.example.alarmcontroller.communication.ble

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder

/** Migration stub for installations that previously started a sticky waiting service. */
class BleAutoConnectService : Service() {
    companion object {
        fun stop(context: Context) {
            context.stopService(Intent(context, BleAutoConnectService::class.java))
            context.getSystemService(NotificationManager::class.java).cancel(5050)
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        stopForeground(STOP_FOREGROUND_REMOVE)
        getSystemService(NotificationManager::class.java).cancel(5050)
        stopSelf()
        return START_NOT_STICKY
    }
}
