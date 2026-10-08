package com.example.alarmcontroller.communication.ble

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.work.*
import com.example.alarmcontroller.ClockApplication
import com.example.alarmcontroller.alarm.AlarmRepository
import com.example.alarmcontroller.communication.ConnectionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

class BackgroundClockSync(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val repo = AlarmRepository(applicationContext)
        if (!repo.backgroundSyncEnabled() || ClockPresenceReceiver.visible || ClockStatusService.running)
            return Result.success()
        if (repo.getLastBleMac() == null && (!repo.wifiSyncEnabled() || repo.getLastEspWifiIp() == null)) return Result.success()
        if (Build.VERSION.SDK_INT >= 31 && ContextCompat.checkSelfPermission(applicationContext,
                Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return Result.success()
        withContext(Dispatchers.Main.immediate) {
            val model = (applicationContext as ClockApplication).clockModel()
            withTimeoutOrNull(60_000) {
                model.syncInBackground()
            } ?: false
        }
        // The next periodic run retries an absent device without a notification.
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val manager = WorkManager.getInstance(context)
            if (AlarmRepository(context).backgroundSyncEnabled()) {
                manager.enqueueUniquePeriodicWork("clock_background_sync", ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<BackgroundClockSync>(15, TimeUnit.MINUTES).build())
            } else manager.cancelUniqueWork("clock_background_sync")
        }
    }
}
