package net.kimptoc.timerwithauto.alarm

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.kimptoc.timerwithauto.R
import net.kimptoc.timerwithauto.timer.Clock
import net.kimptoc.timerwithauto.timer.TimerRepository
import net.kimptoc.timerwithauto.timer.TimerState

/**
 * Observes [TimerRepository.state] and posts / cancels the running-timer
 * notification accordingly. Branches on [Notifier.canPromote] — see the design spec
 * (docs/superpowers/specs/2026-07-31-status-bar-live-update-chip-design.md) for why.
 *
 * Running  -> if promotable, post the promoted-ongoing notification every second (the
 *             title carries the remaining time); otherwise
 *             activate the media session with PlaybackState PLAYING and post the
 *             media-style notification on a periodic refresh loop
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
                    is TimerState.Running -> coroutineScope {
                        startKeepAlive()
                        if (Notifier.canPromote(context)) {
                            // Android 16+ Live Update: the countdown in the title and the
                            // status-bar chip is plain text, so re-post on each whole-second
                            // boundary. CountdownService keeps the process from being frozen
                            // so this loop keeps running in the background.
                            while (isActive) {
                                val remainingMs = state.deadlineEpochMs - clock.nowEpochMs()
                                postPromoted(state, remainingMs)
                                if (remainingMs <= 0L) break
                                val toNextSecond = remainingMs % 1000L
                                delay(if (toNextSecond == 0L) 1000L else toNextSecond)
                            }
                        } else {
                            // One UI's Now Bar pill drops the entry once the MediaSession's
                            // interpolated position passes duration, and some launchers GC the
                            // ongoing notification if it isn't re-posted. Refresh
                            // periodically to keep the pill alive for the full countdown.
                            while (isActive) {
                                postMediaStyle(state)
                                delay(REFRESH_INTERVAL_MS)
                            }
                        }
                    }
                    is TimerState.Idle, is TimerState.Ringing -> cancel()
                }
            }
        }
    }

    /**
     * Starts [CountdownService] so the process stays out of the cached/frozen state while
     * the countdown runs. The service stops itself once the state leaves Running.
     * Best effort: a background start can be refused (e.g. state restored in a background
     * process); the notification is still posted, it just may not tick while frozen.
     */
    private fun startKeepAlive() {
        try {
            context.startForegroundService(Intent(context, CountdownService::class.java))
        } catch (e: IllegalStateException) {
            Log.w(TAG, "CountdownService start refused", e)
        }
    }

    /** The notification for [state] as it should look right now; used by [CountdownService]. */
    fun currentNotification(state: TimerState.Running): Notification =
        if (Notifier.canPromote(context)) {
            Notifier.buildRunningPromotedNotification(
                context = context,
                durationMinutes = state.durationMinutes,
                deadlineEpochMs = state.deadlineEpochMs,
                remainingMs = state.deadlineEpochMs - clock.nowEpochMs(),
                cancelIntent = cancelPendingIntent(),
            )
        } else {
            Notifier.buildRunningNotification(
                context = context,
                durationMinutes = state.durationMinutes,
                sessionToken = mediaSession.sessionToken,
                cancelIntent = cancelPendingIntent(),
            )
        }

    private fun postMediaStyle(state: TimerState.Running) {
        if (!nm.areNotificationsEnabled()) return
        val totalMs = state.durationMinutes * 60_000L
        val elapsedMs = (clock.nowEpochMs() - (state.deadlineEpochMs - totalMs))
            .coerceIn(0L, totalMs)
        mediaSession.activateRunning(
            durationMs = totalMs,
            elapsedMs = elapsedMs,
            title = context.getString(R.string.notif_running_title),
        )
        val cancelPi = cancelPendingIntent()
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

    private fun cancelPendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQ_CANCEL,
        Intent(context, CancelTimerReceiver::class.java).apply {
            action = CancelTimerReceiver.ACTION
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun postPromoted(state: TimerState.Running, remainingMs: Long) {
        if (!nm.areNotificationsEnabled()) return
        val notif = Notifier.buildRunningPromotedNotification(
            context = context,
            durationMinutes = state.durationMinutes,
            deadlineEpochMs = state.deadlineEpochMs,
            remainingMs = remainingMs,
            cancelIntent = cancelPendingIntent(),
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
        private const val TAG = "RunningTimerNotifier"
        private const val REQ_CANCEL = 4001
        // Guess; not benchmarked. Tighten if One UI still drops the pill.
        private const val REFRESH_INTERVAL_MS = 20_000L
    }
}
