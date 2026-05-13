package net.kimptoc.timerwithauto.alarm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.kimptoc.timerwithauto.timer.TimerRepository
import net.kimptoc.timerwithauto.timer.TimerState

/**
 * Observes [TimerRepository.state] and posts / cancels the running-timer
 * notification accordingly. Owned for the lifetime of the process via
 * AppContainer; started from TimerApp.onCreate().
 *
 * Running  -> post chronometer notification (countdown ticking on lockscreen + status bar)
 * Idle     -> cancel
 * Ringing  -> cancel (AlarmService's ringing notification takes over)
 */
class RunningTimerNotifier(
    private val context: Context,
    private val repository: TimerRepository,
    private val scope: CoroutineScope,
) {
    private val nm = NotificationManagerCompat.from(context)

    fun start() {
        Notifier.ensureChannel(context)
        scope.launch {
            repository.state.collectLatest { state ->
                when (state) {
                    is TimerState.Running -> post(state)
                    is TimerState.Idle, is TimerState.Ringing -> cancel()
                }
            }
        }
    }

    private fun post(state: TimerState.Running) {
        if (!nm.areNotificationsEnabled()) return
        val cancelPi = PendingIntent.getBroadcast(
            context,
            REQ_CANCEL,
            Intent(context, CancelTimerReceiver::class.java).apply {
                action = CancelTimerReceiver.ACTION
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notif = Notifier.buildRunningNotification(
            context = context,
            deadlineEpochMs = state.deadlineEpochMs,
            durationMinutes = state.durationMinutes,
            cancelIntent = cancelPi,
        )
        try {
            nm.notify(Notifier.NOTIF_ID_RUNNING, notif)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied — accept silently.
        }
    }

    private fun cancel() {
        nm.cancel(Notifier.NOTIF_ID_RUNNING)
    }

    companion object {
        private const val REQ_CANCEL = 4001
    }
}
