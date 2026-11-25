package net.kimptoc.timerwithauto.alarm

import android.app.Application
import android.content.Context
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.test.core.app.ApplicationProvider
import net.kimptoc.timerwithauto.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class TimerMediaSessionTest {

    private lateinit var context: Context
    private lateinit var session: TimerMediaSession
    private lateinit var controller: MediaControllerCompat
    private var cancelCount = 0

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        session = TimerMediaSession(context) { cancelCount++ }
        controller = MediaControllerCompat(context, session.sessionToken)
    }

    @After fun tearDown() {
        session.release()
    }

    // Robolectric relays the session's playback state to a controller, but not its metadata,
    // extras or custom-action dispatch, so those are checked via the builders / callback.

    @Test fun `running metadata carries countdown title and duration`() {
        val metadata = TimerMediaSession.runningMetadata(durationMs = 600_000L, title = "9:00")
        assertEquals("9:00", metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE))
        assertEquals(600_000L, metadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION))
    }

    @Test fun `activateRunning reports a playing state at the elapsed position`() {
        session.activateRunning(durationMs = 600_000L, elapsedMs = 60_000L, title = "9:00")

        val playback = controller.playbackState
        assertEquals(PlaybackStateCompat.STATE_PLAYING, playback.state)
        assertEquals(60_000L, playback.position)
        assertEquals(1.0f, playback.playbackSpeed)
        assertEquals("no transport actions", 0L, playback.actions)
    }

    @Test fun `elapsed position is clamped to the duration`() {
        session.activateRunning(durationMs = 60_000L, elapsedMs = 90_000L, title = "0:00")
        assertEquals(60_000L, controller.playbackState.position)
        session.activateRunning(durationMs = 60_000L, elapsedMs = -5L, title = "1:00")
        assertEquals(0L, controller.playbackState.position)
    }

    @Test fun `Cancel is offered as a custom action`() {
        session.activateRunning(durationMs = 60_000L, elapsedMs = 0L, title = "1:00")
        val actions = controller.playbackState.customActions
        assertEquals(1, actions.size)
        assertEquals(TimerMediaSession.CUSTOM_ACTION_CANCEL, actions[0].action)
        assertEquals(context.getString(R.string.notif_action_cancel), actions[0].name.toString())
    }

    @Test fun `prev and next slots are reserved so Cancel only shows when expanded`() {
        val extras = TimerMediaSession.slotReservationExtras()
        assertTrue(extras.getBoolean("android.media.playback.ALWAYS_RESERVE_SPACE_FOR.ACTION_SKIP_TO_PREVIOUS"))
        assertTrue(extras.getBoolean("android.media.playback.ALWAYS_RESERVE_SPACE_FOR.ACTION_SKIP_TO_NEXT"))
    }

    @Test fun `the Cancel custom action invokes onCancel`() {
        session.callback.onCustomAction(TimerMediaSession.CUSTOM_ACTION_CANCEL, null)
        assertEquals(1, cancelCount)
    }

    @Test fun `other custom actions are ignored`() {
        session.callback.onCustomAction("something.else", null)
        session.callback.onCustomAction(null, null)
        assertEquals(0, cancelCount)
    }

    @Test fun `deactivate reports stopped`() {
        session.activateRunning(durationMs = 60_000L, elapsedMs = 0L, title = "1:00")
        session.deactivate()
        assertEquals(PlaybackStateCompat.STATE_STOPPED, controller.playbackState.state)
    }
}
