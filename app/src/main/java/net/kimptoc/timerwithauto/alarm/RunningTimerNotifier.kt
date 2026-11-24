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
 * Running  -> if promotable, post the promoted-ongoing notification once; otherwise
 *             activate the media session with PlaybackState PLAYING and re-post the
 *             media-style notification every second (the title is the countdown), held
 *             alive by [CountdownService]
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
                        if (Notifier.canPromote(context)) {
                            // Android 16+ Live Update chip: the system ticks the
                            // chronometer itself, so one post is enough.
                            postPromoted(state)
                        } else {
                            // The countdown is the title (and One UI's chip text), so re-post
                            // on each whole-second boundary. This also keeps the Now Bar pill
                            // alive: One UI drops it once the session's interpolated position
                            // passes duration, and some launchers GC un-refreshed ongoing
                            // notifications. CountdownService stops the process being frozen
                            // in the background, which would stall this loop.
                            startKeepAlive()
                            while (isActive) {
                                val remainingMs = state.deadlineEpochMs - clock.nowEpochMs()
                                postMediaStyle(state, remainingMs)
                                if (remainingMs <= 0L) break
                                val toNextSecond = remainingMs % 1000L
                                delay(if (toNextSecond == 0L) 1000L else toNextSecond)
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
    fun currentNotification(state: TimerState.Running): Notification {
        val remainingMs = state.deadlineEpochMs - clock.nowEpochMs()
        return if (Notifier.canPromote(context)) {
            Notifier.buildRunningPromotedNotification(
                context = context,
                durationMinutes = state.durationMinutes,
                deadlineEpochMs = state.deadlineEpochMs,
                cancelIntent = cancelPendingIntent(),
            )
        } else {
            Notifier.buildRunningNotification(
                context = context,
                durationMinutes = state.durationMinutes,
                remainingMs = remainingMs,
                sessionToken = mediaSession.sessionToken,
                cancelIntent = cancelPendingIntent(),
            )
        }
    }

    private fun postMediaStyle(state: TimerState.Running, remainingMs: Long) {
        if (!nm.areNotificationsEnabled()) return
        val totalMs = state.durationMinutes * 60_000L
        val elapsedMs = (clock.nowEpochMs() - (state.deadlineEpochMs - totalMs))
            .coerceIn(0L, totalMs)
        mediaSession.activateRunning(
            durationMs = totalMs,
            elapsedMs = elapsedMs,
            title = Notifier.formatRemaining(remainingMs),
        )
        val cancelPi = cancelPendingIntent()
        val notif = Notifier.buildRunningNotification(
            context = context,
            durationMinutes = state.durationMinutes,
            remainingMs = remainingMs,
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

    private fun postPromoted(state: TimerState.Running) {
        if (!nm.areNotificationsEnabled()) return
        val notif = Notifier.buildRunningPromotedNotification(
            context = context,
            durationMinutes = state.durationMinutes,
            deadlineEpochMs = state.deadlineEpochMs,
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
    }
}
