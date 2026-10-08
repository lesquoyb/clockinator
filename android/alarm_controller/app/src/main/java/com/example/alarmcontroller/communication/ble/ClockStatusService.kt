package com.example.alarmcontroller.communication.ble

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.example.alarmcontroller.ClockApplication
import com.example.alarmcontroller.MainActivity
import com.example.alarmcontroller.alarm.AlarmRepository
import com.example.alarmcontroller.communication.ConnectionState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

class ClockStatusService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observing = false
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!AlarmRepository(this).clockStatusEnabled()) { stopSelf(); return START_NOT_STICKY }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("clock_status", "Clock status",
            NotificationManager.IMPORTANCE_LOW))
        startForeground(5052, notification("Connecting to the clock"))
        running = true
        if (!observing) {
            observing = true
            val model = (application as ClockApplication).clockModel()
            model.retryBackgroundConnection()
            scope.launch {
                combine(model.connectionState, model.wifiConnectionState, model.alarms,
                    model.isPhoneAlarmRinging) { ble, wifi, alarms, ringing ->
                    val link = when {
                        wifi is ConnectionState.Connected -> "Wi-Fi connected"
                        ble is ConnectionState.Connected -> "Bluetooth connected"
                        else -> "Clock disconnected"
                    }
                    "$link · ${alarms.count { it.isEnabled }} enabled alarms" +
                        if (ringing) " · Phone alarm ringing" else ""
                }.distinctUntilChanged().collect { manager.notify(5052, notification(it)) }
            }
        }
        return START_STICKY
    }
    private fun notification(text: String): Notification {
        val open = PendingIntent.getActivity(this, 5052, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, "clock_status")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth).setContentTitle("Clockinator")
            .setContentText(text).setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).build()
    }
    override fun onDestroy() {
        running = false
        scope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    companion object {
        @Volatile var running = false
        fun apply(context: Context) {
            val intent = Intent(context, ClockStatusService::class.java)
            if (AlarmRepository(context).clockStatusEnabled()) {
                try { androidx.core.content.ContextCompat.startForegroundService(context, intent) }
                catch (_: IllegalStateException) { AlarmRepository(context).setClockStatusEnabled(false) }
                catch (_: SecurityException) { AlarmRepository(context).setClockStatusEnabled(false) }
            } else context.stopService(intent)
        }
    }
}
