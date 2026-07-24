package net.kimptoc.timerwithauto.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import net.kimptoc.timerwithauto.TimerApp

class AlarmService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var isRinging = false

    private lateinit var audio: AudioPlayer
    private lateinit var vibrator: VibratorWrapper
    private lateinit var carConnectionChecker: CarConnectionChecker
    private lateinit var alarmManager: AlarmManager

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "onCreate")
        val container = (application as TimerApp).container
        audio = container.audioPlayer
        vibrator = container.vibratorWrapper
        carConnectionChecker = container.carConnectionChecker
        alarmManager = getSystemService(ALARM_SERVICE) as AlarmManager
        Notifier.ensureChannel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand action=${intent?.action} isRinging=$isRinging")
        when (intent?.action) {
            ACTION_STOP -> { stopRinging(); return START_NOT_STICKY }
        }

        if (isRinging) return START_NOT_STICKY

        val durationMinutes = intent?.getIntExtra(EXTRA_DURATION_MINUTES, 0) ?: 0
        isRinging = true

        val stopPi = buildStopPendingIntent()
        val notif = Notifier.buildRingingNotification(this, stopPi)

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    Notifier.NOTIF_ID_RINGING,
                    notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                )
            } else {
                startForeground(Notifier.NOTIF_ID_RINGING, notif)
            }
            Log.i(TAG, "startForeground OK")
        } catch (t: Throwable) {
            Log.e(TAG, "startForeground failed", t)
            isRinging = false
            stopSelf()
            return START_NOT_STICKY
        }

        scope.launch {
            (application as TimerApp).container.timerRepository
                .setRinging(isRinging = true, durationMinutes = durationMinutes)
        }

        val ringProfile = RingProfile.forCarConnected(carConnectionChecker.isConnected())
        Log.i(TAG, "ringProfile=$ringProfile")

        try { audio.startLoop(ringProfile.soundRes) } catch (t: Throwable) { Log.e(TAG, "audio.startLoop failed", t) }
        try { vibrator.startLoop() } catch (t: Throwable) { Log.e(TAG, "vibrator.startLoop failed", t) }

        scheduleAutoStop(stopPi, ringProfile.autoStopMs)

        return START_NOT_STICKY
    }

    private fun buildStopPendingIntent(): PendingIntent = PendingIntent.getService(
        this,
        STOP_REQ_CODE,
        Intent(this, AlarmService::class.java).apply { action = ACTION_STOP },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun scheduleAutoStop(stopPi: PendingIntent, delayMs: Long) {
        val triggerAtMs = SystemClock.elapsedRealtime() + delayMs
        try {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMs, stopPi)
        } catch (e: SecurityException) {
            // USE_EXACT_ALARM is install-granted for alarm-category apps; if the OEM disables it,
            // fall back to an inexact alarm. Better late than silent.
            alarmManager.setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAtMs, stopPi)
        }
    }

    private fun stopRinging() {
        if (!isRinging) return
        Log.i(TAG, "stopRinging")
        isRinging = false
        alarmManager.cancel(buildStopPendingIntent())
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
        private const val TAG = "TimerAlarmService"
        const val EXTRA_DURATION_MINUTES = "duration_minutes"
        const val ACTION_STOP = "net.kimptoc.timerwithauto.action.STOP_ALARM"
        private const val STOP_REQ_CODE = 2001
    }
}
