package net.kimptoc.timerwithauto.alarm

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.kimptoc.timerwithauto.R
import net.kimptoc.timerwithauto.TimerApp
import net.kimptoc.timerwithauto.timer.TimerState

/**
 * Foreground service held for the length of a countdown. It does no work itself: it only
 * keeps the process out of the cached/frozen state so [RunningTimerNotifier]'s
 * once-per-second re-post keeps the notification countdown (title + status-bar chip) live.
 *
 * It shares [Notifier.NOTIF_ID_RUNNING] with the running notification, so the notifier's
 * re-posts update this service's notification in place. The service watches the timer
 * state and stops itself when it leaves Running; nothing else stops it, which avoids a
 * stop racing ahead of startForeground().
 */
class CountdownService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watchJob: Job? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = (application as TimerApp).container
        val state = container.timerRepository.state.value

        // startForeground() must always be called after startForegroundService(), even if
        // the timer has already ended, or the system crashes the app.
        val notif = if (state is TimerState.Running) {
            container.runningTimerNotifier.currentNotification(state)
        } else {
            NotificationCompat.Builder(this, Notifier.CHANNEL_RUNNING)
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle(getString(R.string.notif_running_title))
                .build()
        }
        try {
            startForeground(
                Notifier.NOTIF_ID_RUNNING,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } catch (t: Throwable) {
            Log.e(TAG, "startForeground failed", t)
            stopSelf()
            return START_NOT_STICKY
        }

        if (watchJob == null) {
            watchJob = scope.launch {
                container.timerRepository.state.first { it !is TimerState.Running }
                Log.i(TAG, "timer no longer running; stopping")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private companion object {
        const val TAG = "CountdownService"
    }
}
