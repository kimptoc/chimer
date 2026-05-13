package net.kimptoc.timerwithauto.alarm

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.kimptoc.timerwithauto.TimerApp

class AlarmService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val handler = Handler(Looper.getMainLooper())
    private var stopRunnable: Runnable? = null
    private var isRinging = false

    private lateinit var audio: AudioPlayer
    private lateinit var vibrator: VibratorWrapper

    override fun onCreate() {
        super.onCreate()
        val container = (application as TimerApp).container
        audio = container.audioPlayer
        vibrator = container.vibratorWrapper
        Notifier.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> { stopRinging(); return START_NOT_STICKY }
        }

        if (isRinging) return START_NOT_STICKY

        val durationMinutes = intent?.getIntExtra(EXTRA_DURATION_MINUTES, 0) ?: 0
        isRinging = true

        val stopPi = PendingIntent.getService(
            this,
            STOP_REQ_CODE,
            Intent(this, AlarmService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = Notifier.buildRingingNotification(this, stopPi)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                Notifier.NOTIF_ID_RINGING,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } else {
            startForeground(Notifier.NOTIF_ID_RINGING, notif)
        }

        scope.launch {
            (application as TimerApp).container.timerRepository
                .setRinging(isRinging = true, durationMinutes = durationMinutes)
        }

        audio.startLoop()
        vibrator.startLoop()

        stopRunnable = Runnable { stopRinging() }
        handler.postDelayed(stopRunnable!!, AUTO_STOP_MS)

        return START_NOT_STICKY
    }

    private fun stopRinging() {
        if (!isRinging) return
        isRinging = false
        stopRunnable?.let { handler.removeCallbacks(it) }
        stopRunnable = null
        audio.stop()
        vibrator.stop()
        scope.launch {
            (application as TimerApp).container.timerRepository
                .setRinging(isRinging = false, durationMinutes = 0)
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_DURATION_MINUTES = "duration_minutes"
        const val ACTION_STOP = "net.kimptoc.timerwithauto.action.STOP_ALARM"
        const val AUTO_STOP_MS = 2L * 60L * 1000L  // 2 minutes
        private const val STOP_REQ_CODE = 2001
    }
}
