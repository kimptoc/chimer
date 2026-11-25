package net.kimptoc.timerwithauto.alarm

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.kimptoc.timerwithauto.timer.Clock
import net.kimptoc.timerwithauto.timer.TimerRepository
import net.kimptoc.timerwithauto.timer.TimerState
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Runs at API 33, where [Notifier.canPromote] is false, so this covers the media-style
 * path (the one Samsung devices use): a re-post each second with the countdown as title.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class RunningTimerNotifierTest {

    private lateinit var context: Application
    private lateinit var nm: NotificationManager
    private lateinit var mediaSession: TimerMediaSession
    private lateinit var controller: MediaControllerCompat
    private val repo = FakeRepository()

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mediaSession = TimerMediaSession(context) {}
        controller = MediaControllerCompat(context, mediaSession.sessionToken)
    }

    @After fun tearDown() {
        mediaSession.release()
    }

    private fun TestScope.startNotifier() {
        val clock = Clock { BASE + testScheduler.currentTime }
        RunningTimerNotifier(context, repo, clock, mediaSession, backgroundScope).start()
        runCurrent()
    }

    private fun runningTitle(): String? = shadowOf(nm)
        .getNotification(Notifier.NOTIF_ID_RUNNING)
        ?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()

    @Test fun `idle posts nothing`() = runTest(StandardTestDispatcher()) {
        startNotifier()
        assertNull(runningTitle())
    }

    @Test fun `running posts countdown and ticks once per second`() = runTest(StandardTestDispatcher()) {
        startNotifier()
        repo.state.value = TimerState.Running(BASE + 10 * 60_000L, 10)
        runCurrent()
        assertEquals("10:00", runningTitle())

        advanceTimeBy(1_000L); runCurrent()
        assertEquals("9:59", runningTitle())

        advanceTimeBy(60_000L); runCurrent()
        assertEquals("8:59", runningTitle())
    }

    @Test fun `running activates the media session as playing`() = runTest(StandardTestDispatcher()) {
        startNotifier()
        repo.state.value = TimerState.Running(BASE + 60_000L, 1)
        runCurrent()
        assertEquals(PlaybackStateCompat.STATE_PLAYING, controller.playbackState.state)
        assertEquals(0L, controller.playbackState.position)

        advanceTimeBy(15_000L); runCurrent()
        assertEquals(15_000L, controller.playbackState.position)
    }

    @Test fun `ticks align to whole-second boundaries`() = runTest(StandardTestDispatcher()) {
        startNotifier()
        // 9:59.5 left: rounds up to 10:00, then the next post is 500 ms later at 9:59.
        repo.state.value = TimerState.Running(BASE + 599_500L, 10)
        runCurrent()
        assertEquals("10:00", runningTitle())

        advanceTimeBy(499L); runCurrent()
        assertEquals("10:00", runningTitle())

        advanceTimeBy(1L); runCurrent()
        assertEquals("9:59", runningTitle())
    }

    @Test fun `stops re-posting once the deadline is reached`() = runTest(StandardTestDispatcher()) {
        startNotifier()
        repo.state.value = TimerState.Running(BASE + 2_000L, 1)
        runCurrent()
        advanceTimeBy(2_000L); runCurrent()
        assertEquals("0:00", runningTitle())

        // If the loop were still running it would re-post this.
        nm.cancel(Notifier.NOTIF_ID_RUNNING)
        advanceTimeBy(5_000L); runCurrent()
        assertNull(runningTitle())
    }

    @Test fun `running starts the keep-alive CountdownService`() = runTest(StandardTestDispatcher()) {
        startNotifier()
        repo.state.value = TimerState.Running(BASE + 60_000L, 1)
        runCurrent()
        val started = shadowOf(context).nextStartedService
        assertEquals(CountdownService::class.java.name, started?.component?.className)
    }

    @Test fun `going idle cancels the notification and stops the session`() = runTest(StandardTestDispatcher()) {
        startNotifier()
        repo.state.value = TimerState.Running(BASE + 60_000L, 1)
        runCurrent()
        assertEquals("1:00", runningTitle())

        repo.state.value = TimerState.Idle
        runCurrent()
        assertNull(runningTitle())
        assertEquals(PlaybackStateCompat.STATE_STOPPED, controller.playbackState.state)

        // The tick loop was cancelled with the Running state.
        advanceTimeBy(5_000L); runCurrent()
        assertNull(runningTitle())
    }

    @Test fun `ringing cancels the running notification`() = runTest(StandardTestDispatcher()) {
        startNotifier()
        repo.state.value = TimerState.Running(BASE + 60_000L, 1)
        runCurrent()
        repo.state.value = TimerState.Ringing(1)
        runCurrent()
        assertNull(runningTitle())
        assertEquals(PlaybackStateCompat.STATE_STOPPED, controller.playbackState.state)
    }

    @Test fun `currentNotification matches the posted countdown`() = runTest(StandardTestDispatcher()) {
        val clock = Clock { BASE + testScheduler.currentTime }
        val notifier = RunningTimerNotifier(context, repo, clock, mediaSession, backgroundScope)
        Notifier.ensureChannel(context)
        val n = notifier.currentNotification(TimerState.Running(BASE + 90_000L, 2))
        assertEquals("1:30", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(Notifier.CHANNEL_RUNNING, n.channelId)
    }

    private class FakeRepository : TimerRepository {
        override val state = MutableStateFlow<TimerState>(TimerState.Idle)
        override val recents: StateFlow<List<Int>> = MutableStateFlow(emptyList())
        override val pinned: StateFlow<List<Int>> = MutableStateFlow(emptyList())
        override suspend fun pin(durationMinutes: Int) = Unit
        override suspend fun unpin(durationMinutes: Int) = Unit
        override suspend fun startTimer(durationMinutes: Int) = Unit
        override suspend fun cancelTimer() { state.value = TimerState.Idle }
        override suspend fun setRinging(isRinging: Boolean, durationMinutes: Int) = Unit
        override suspend fun readSnapshot() = TimerRepository.PersistedSnapshot(null, null)
    }

    private companion object {
        const val BASE = 1_700_000_000_000L
    }
}
