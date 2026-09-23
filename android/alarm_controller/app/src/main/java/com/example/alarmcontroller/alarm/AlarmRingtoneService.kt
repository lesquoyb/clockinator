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
import com.example.alarmcontroller.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AlarmRingtoneService : Service() {

    companion object {
        private const val TAG = "AlarmRingtoneService"
        private const val CHANNEL_ID = "alarm_ringtone_channel"
        private const val NOTIFICATION_ID = 4242

        const val ACTION_START = "ACTION_START_ALARM"
        const val ACTION_STOP = "ACTION_STOP_ALARM"
        const val ACTION_SNOOZE = "ACTION_SNOOZE_ALARM"

        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_ID = "extra_id"

        private val _isRinging = MutableStateFlow(false)
        val isRinging: StateFlow<Boolean> = _isRinging.asStateFlow()

        private val _ringingAlarmTitle = MutableStateFlow<String?>(null)
        val ringingAlarmTitle: StateFlow<String?> = _ringingAlarmTitle.asStateFlow()

        private var activeAlarmId: String? = null

        fun startAlarm(context: Context, alarmId: String, title: String) {
            val intent = Intent(context, AlarmRingtoneService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_ID, alarmId)
                putExtra(EXTRA_TITLE, title)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopAlarm(context: Context) {
            val intent = Intent(context, AlarmRingtoneService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }

        fun snoozeAlarm(context: Context, delayMinutes: Int = 10) {
            val intent = Intent(context, AlarmRingtoneService::class.java).apply {
                action = ACTION_SNOOZE
                putExtra("delay_minutes", delayMinutes)
            }
            context.startService(intent)
        }
    }

    private var mediaPlayer: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val alarmId = intent.getStringExtra(EXTRA_ID) ?: "default"
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Alarme"
                activeAlarmId = alarmId
                _ringingAlarmTitle.value = title
                _isRinging.value = true

                startForeground(NOTIFICATION_ID, buildNotification(title))
                startSoundAndVibration()
            }
            ACTION_STOP -> {
                stopSelfAndCleanup()
            }
            ACTION_SNOOZE -> {
                val delay = intent.getIntExtra("delay_minutes", 10)
                val id = activeAlarmId ?: "default"
                val title = _ringingAlarmTitle.value ?: "Alarme"

                AlarmScheduler(this).scheduleSnooze(id, title, delay)
                stopSelfAndCleanup()
            }
        }

        return START_NOT_STICKY
    }

    private fun startSoundAndVibration() {
        // Audio
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
            Log.e(TAG, "Erreur démarrage sonnerie", e)
        }

        // Vibration
        try {
            val pattern = longArrayOf(0, 800, 400, 800, 400)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                vibrator?.vibrate(pattern, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erreur vibration", e)
        }
    }

    private fun stopSelfAndCleanup() {
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Erreur arrêt media player", e)
        } finally {
            mediaPlayer = null
        }

        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Erreur arrêt vibration", e)
        }

        _isRinging.value = false
        _ringingAlarmTitle.value = null
        activeAlarmId = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(title: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop Action
        val stopIntent = Intent(this, AlarmRingtoneService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Snooze Action
        val snoozeIntent = Intent(this, AlarmRingtoneService::class.java).apply {
            action = ACTION_SNOOZE
            putExtra("delay_minutes", 10)
        }
        val snoozePendingIntent = PendingIntent.getService(
            this,
            2,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("⏰ Alarme : $title")
            .setContentText("L'alarme sonne. Appuyez pour arrêter ou répéter.")
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Arrêter", stopPendingIntent)
            .addAction(android.R.drawable.ic_popup_sync, "Répéter (10 min)", snoozePendingIntent)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sonneries d'alarme",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications pour les alarmes en cours de sonnerie"
                setSound(null, null) // Le son est géré directement par MediaPlayer
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSelfAndCleanup()
    }
}
