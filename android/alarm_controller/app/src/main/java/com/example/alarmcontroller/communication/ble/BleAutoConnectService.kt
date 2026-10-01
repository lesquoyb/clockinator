package com.example.alarmcontroller.communication.ble

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.alarmcontroller.MainActivity
import com.example.alarmcontroller.alarm.AlarmRepository

/**
 * Lightweight foreground service that keeps the BLE autoConnect GATT session
 * alive when the app is in the background or closed.
 *
 * Android requires a foreground service of type `connectedDevice` to maintain
 * a GATT connection (or autoConnect watch) while the app is not visible.
 * Without it, the OS may kill the GATT callback after a few minutes.
 *
 * Lifecycle:
 *  - Started from MainActivity.onResume() if a saved MAC exists.
 *  - Stopped from MainActivity.onDestroy() only when the user explicitly disconnects.
 *  - The service itself does not hold a BleManager; it just keeps the process alive.
 *    The BleManager singleton (via the Application) owns the GATT connection.
 */
class BleAutoConnectService : Service() {

    companion object {
        private const val TAG             = "BleAutoConnectService"
        private const val CHANNEL_ID      = "ble_autoconnect_channel"
        private const val NOTIFICATION_ID = 5050

        fun start(context: Context) {
            val intent = Intent(context, BleAutoConnectService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, BleAutoConnectService::class.java))
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val savedMac = AlarmRepository(this).getLastBleMac()
        val subtitle = if (savedMac != null) "Waiting for device…" else "No device paired"
        Log.d(TAG, "BleAutoConnectService started: $subtitle")
        startForeground(NOTIFICATION_ID, buildNotification(subtitle))
        // START_STICKY: if the OS kills the service, restart it to maintain the watch
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "BleAutoConnectService stopped")
    }

    private fun buildNotification(subtitle: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Clockinator")
            .setContentText(subtitle)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "BLE auto-connect",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the Bluetooth connection alive in the background"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }
}
