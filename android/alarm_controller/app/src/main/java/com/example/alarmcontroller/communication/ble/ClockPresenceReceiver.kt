package com.example.alarmcontroller.communication.ble

import android.app.NotificationManager
import android.app.PendingIntent
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Migration: retire the presence scan and its repeating notification. */
class ClockPresenceReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == BluetoothAdapter.ACTION_STATE_CHANGED ||
            intent.action == "com.example.alarmcontroller.CLOCK_PRESENCE") watch(context)
    }
    companion object {
        @Volatile var visible = false
        fun dismiss(context: Context) {
            context.getSystemService(NotificationManager::class.java).cancel(5051)
        }
        fun watch(context: Context) {
            dismiss(context)
            val pending = PendingIntent.getBroadcast(context, 5051,
                Intent(context, ClockPresenceReceiver::class.java)
                    .setAction("com.example.alarmcontroller.CLOCK_PRESENCE"),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_MUTABLE)
            if (pending != null) {
                try {
                    context.getSystemService(BluetoothManager::class.java)
                        .adapter?.bluetoothLeScanner?.stopScan(pending)
                } catch (_: SecurityException) {}
                catch (_: IllegalStateException) {}
                pending.cancel()
            }
            BackgroundClockSync.schedule(context)
        }
    }
}
