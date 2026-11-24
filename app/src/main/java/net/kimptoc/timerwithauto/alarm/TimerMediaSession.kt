package net.kimptoc.timerwithauto.alarm

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import net.kimptoc.timerwithauto.R

/**
 * A MediaSession that models a running countdown timer as audio "playback" so the
 * system surfaces it via the same UI it uses for music players — including Samsung
 * One UI's Now Bar pill on the lockscreen / status bar chip.
 *
 * The system tracks position via [PlaybackStateCompat.setState] using elapsedRealtime
 * as the reference timestamp, so we only need to update on state transitions, not every
 * tick — the system interpolates progress on its own.
 */
class TimerMediaSession(context: Context, onCancel: () -> Unit) {

    private val cancelLabel = context.getString(R.string.notif_action_cancel)

    private val session = MediaSessionCompat(context, "TimerWithAuto").apply {
        setFlags(0)
        // The Now Bar / media controls build their buttons from the session's playback
        // actions, not the notification's actions, so Cancel is offered as a custom action.
        // Reserving the prev/next slots (left empty) moves custom actions out of the compact
        // controls, so Cancel only shows once the pill / player is expanded.
        setExtras(Bundle().apply {
            putBoolean(SLOT_RESERVATION_SKIP_TO_PREV, true)
            putBoolean(SLOT_RESERVATION_SKIP_TO_NEXT, true)
        })
        setCallback(object : MediaSessionCompat.Callback() {
            override fun onCustomAction(action: String?, extras: Bundle?) {
                if (action == CUSTOM_ACTION_CANCEL) onCancel()
            }
        })
    }

    val sessionToken: MediaSessionCompat.Token = session.sessionToken

    /**
     * Mark the session active and report a "playing" countdown.
     * Position starts at 0 and advances toward [durationMs]. The progress
     * pill / chip will fill from 0 to 100% as the timer runs.
     */
    fun activateRunning(durationMs: Long, elapsedMs: Long, title: String) {
        val metadata = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, durationMs)
            .build()
        session.setMetadata(metadata)

        val playback = PlaybackStateCompat.Builder()
            .setActions(0L)
            .addCustomAction(
                CUSTOM_ACTION_CANCEL,
                cancelLabel,
                android.R.drawable.ic_menu_close_clear_cancel,
            )
            .setState(
                PlaybackStateCompat.STATE_PLAYING,
                elapsedMs.coerceIn(0L, durationMs),
                1.0f,
                SystemClock.elapsedRealtime(),
            )
            .build()
        session.setPlaybackState(playback)
        session.isActive = true
    }

    fun deactivate() {
        val stopped = PlaybackStateCompat.Builder()
            .setActions(0L)
            .setState(PlaybackStateCompat.STATE_STOPPED, 0L, 1.0f)
            .build()
        session.setPlaybackState(stopped)
        session.isActive = false
    }

    fun release() {
        session.release()
    }

    private companion object {
        const val CUSTOM_ACTION_CANCEL = "net.kimptoc.timerwithauto.action.SESSION_CANCEL"
        // androidx.media3 MediaConstants values; spelled out to avoid the media3 dependency.
        const val SLOT_RESERVATION_SKIP_TO_PREV =
            "android.media.playback.ALWAYS_RESERVE_SPACE_FOR.ACTION_SKIP_TO_PREVIOUS"
        const val SLOT_RESERVATION_SKIP_TO_NEXT =
            "android.media.playback.ALWAYS_RESERVE_SPACE_FOR.ACTION_SKIP_TO_NEXT"
    }
}
