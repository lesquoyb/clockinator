package com.example.alarmcontroller.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.alarmcontroller.alarm.RingtoneType
import com.example.alarmcontroller.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Foreground service that plays the alarm ringtone and vibrates the device.
 * Exposes static StateFlow values so the ViewModel and NFC handler can observe
 * the ringing state without holding a reference to the service.
 */
class AlarmRingtoneService : Service() {

    companion object {
        private const val TAG             = "AlarmRingtoneService"
        private const val CHANNEL_ID      = "alarm_ringtone_channel"
        private const val NOTIFICATION_ID = 4242

        const val ACTION_START  = "ACTION_START_ALARM"
        const val ACTION_STOP   = "ACTION_STOP_ALARM"
        const val ACTION_SNOOZE = "ACTION_SNOOZE_ALARM"

        const val EXTRA_TITLE        = "extra_title"
        const val EXTRA_ID           = "extra_id"
        const val EXTRA_RINGTONE_TYPE = "extra_ringtone_type"

        private val _isRinging = MutableStateFlow(false)
        /** True while the alarm ringtone is playing. */
        val isRinging: StateFlow<Boolean> = _isRinging.asStateFlow()

        private val _ringingAlarmTitle = MutableStateFlow<String?>(null)
        /** Title of the currently ringing alarm, or null. */
        val ringingAlarmTitle: StateFlow<String?> = _ringingAlarmTitle.asStateFlow()

        /** ID of the alarm currently ringing; read by the ViewModel for snooze-duration lookup. */
        var activeAlarmId: String? = null
            private set

        /** Ringtone slot currently ringing. */
        var activeRingtoneType: RingtoneType? = null
            private set

        fun startAlarm(context: Context, alarmId: String, title: String, ringtone: RingtoneType) {
            val intent = Intent(context, AlarmRingtoneService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ID, alarmId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_RINGTONE_TYPE, ringtone.wire)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopAlarm(context: Context) {
            context.startService(
                Intent(context, AlarmRingtoneService::class.java).apply { action = ACTION_STOP }
            )
        }

        fun snoozeAlarm(context: Context, delayMinutes: Int = 10) {
            context.startService(
                Intent(context, AlarmRingtoneService::class.java).apply {
                    action = ACTION_SNOOZE
                    putExtra("delay_minutes", delayMinutes)
                }
            )
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val alarmId  = intent.getStringExtra(EXTRA_ID) ?: "default"
                val title    = intent.getStringExtra(EXTRA_TITLE) ?: "Alarm"
                val typeWire = intent.getStringExtra(EXTRA_RINGTONE_TYPE) ?: RingtoneType.WAKEUP.wire
                activeAlarmId         = alarmId
                activeRingtoneType    = RingtoneType.fromWire(typeWire) ?: RingtoneType.WAKEUP
                _ringingAlarmTitle.value = title
                _isRinging.value         = true
                startForeground(NOTIFICATION_ID, buildNotification(title))
                startSoundAndVibration()
            }
            ACTION_STOP -> stopSelfAndCleanup()
            ACTION_SNOOZE -> {
                val delay    = intent.getIntExtra("delay_minutes", 10)
                val id       = activeAlarmId ?: "default"
                val title    = _ringingAlarmTitle.value ?: "Alarm"
                val ringtone = activeRingtoneType ?: RingtoneType.WAKEUP
                AlarmScheduler(this).scheduleSnooze(id, title, ringtone, delay)
                stopSelfAndCleanup()
            }
        }
        return START_NOT_STICKY
    }

    private fun startSoundAndVibration() {
        // --- Audio ---
        try {
            val alertUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, alertUri)
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start ringtone", e)
        }

        // --- Vibration ---
        try {
            val pattern = longArrayOf(0, 800, 400, 800, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start vibration", e)
        }
    }

    private fun stopSelfAndCleanup() {
        try { mediaPlayer?.stop(); mediaPlayer?.release() }
        catch (e: Exception) { Log.e(TAG, "Error stopping media player", e) }
        finally { mediaPlayer = null }

        try { vibrator?.cancel() }
        catch (e: Exception) { Log.e(TAG, "Error stopping vibration", e) }

        _isRinging.value         = false
        _ringingAlarmTitle.value = null
        activeAlarmId            = null
        activeRingtoneType       = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(title: String): Notification {
        // Tapping the notification brings the user into the app where they can
        // stop or snooze.  No inline Stop/Snooze actions are provided on purpose.
        // Dismissing an alarm should require opening the app.
        val openAppPi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⏰ Alarm: $title")
            .setContentText("Tap to open the app and stop or snooze.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openAppPi)
            // No addAction() calls, users must open the app to interact
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Alarm ringtones",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for alarms currently ringing"
                setSound(null, null) // Sound is handled directly by MediaPlayer
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSelfAndCleanup()
    }
}
