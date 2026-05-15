package net.kimptoc.timerwithauto.alarm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.kimptoc.timerwithauto.R
import net.kimptoc.timerwithauto.timer.Clock
import net.kimptoc.timerwithauto.timer.TimerRepository
import net.kimptoc.timerwithauto.timer.TimerState

/**
 * Observes [TimerRepository.state] and posts / cancels the running-timer
 * notification accordingly. Uses a MediaSession-backed MediaStyle
 * notification so the system surfaces it in Samsung's Now Bar pill and
 * the lockscreen / status-bar media chip.
 *
 * Running  -> activate session with PlaybackState PLAYING, post media-style notification
 * Idle     -> deactivate session, cancel notification
 * Ringing  -> deactivate session, cancel notification (AlarmService's ringing notification takes over)
 */
class RunningTimerNotifier(
    private val context: Context,
    private val repository: TimerRepository,
    private val clock: Clock,
    private val mediaSession: TimerMediaSession,
    private val scope: CoroutineScope,
) {
    private val nm = NotificationManagerCompat.from(context)

    fun start() {
        Notifier.ensureChannel(context)
        scope.launch {
            repository.state.collectLatest { state ->
                when (state) {
                    is TimerState.Running -> {
                        // One UI's Now Bar pill drops the entry once the MediaSession's
                        // interpolated position passes duration, and some launchers GC the
                        // ongoing notification if it isn't re-posted. Refresh both
                        // periodically to keep the pills alive for the full countdown.
                        while (true) {
                            post(state)
                            delay(REFRESH_INTERVAL_MS)
                        }
                    }
                    is TimerState.Idle, is TimerState.Ringing -> cancel()
                }
            }
        }
    }

    private fun post(state: TimerState.Running) {
        if (!nm.areNotificationsEnabled()) return
        val totalMs = state.durationMinutes * 60_000L
        val elapsedMs = (clock.nowEpochMs() - (state.deadlineEpochMs - totalMs))
            .coerceIn(0L, totalMs)
        mediaSession.activateRunning(
            durationMs = totalMs,
            elapsedMs = elapsedMs,
            title = context.getString(R.string.notif_running_title),
        )
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
            durationMinutes = state.durationMinutes,
            sessionToken = mediaSession.sessionToken,
            cancelIntent = cancelPi,
        )
        try {
            nm.notify(Notifier.NOTIF_ID_RUNNING, notif)
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS denied — accept silently.
        }
    }

    private fun cancel() {
        mediaSession.deactivate()
        nm.cancel(Notifier.NOTIF_ID_RUNNING)
    }

    companion object {
        private const val REQ_CANCEL = 4001
        private const val REFRESH_INTERVAL_MS = 20_000L
    }
}
