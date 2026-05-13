package net.kimptoc.timerwithauto.timer

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class TimerRepositoryTest {

    private lateinit var context: Context
    private lateinit var fakeScheduler: FakeScheduler
    private lateinit var fakeClock: FakeClock

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        fakeScheduler = FakeScheduler()
        fakeClock = FakeClock(initial = 1_000_000L)
    }

    @After fun tearDown() {
        File(context.filesDir, "datastore").deleteRecursively()
    }

    private fun TestScope.newRepo(): TimerRepositoryImpl =
        TimerRepositoryImpl(
            context = context,
            scheduler = fakeScheduler,
            clock = fakeClock,
            scope = backgroundScope,
            dataStoreName = "test_${System.nanoTime()}",
        )

    @Test fun `state starts Idle and recents seeded`() = runTest(UnconfinedTestDispatcher()) {
        val repo = newRepo()
        advanceUntilIdle()
        assertEquals(TimerState.Idle, repo.state.value)
        assertEquals(listOf(5, 12, 60), repo.recents.value)
    }

    @Test fun `startTimer schedules and transitions to Running`() = runTest(UnconfinedTestDispatcher()) {
        val repo = newRepo()
        repo.state.test {
            assertEquals(TimerState.Idle, awaitItem())
            repo.startTimer(durationMinutes = 10)
            val running = awaitItem() as TimerState.Running
            assertEquals(1_000_000L + 10 * 60_000L, running.deadlineEpochMs)
            assertEquals(10, running.durationMinutes)
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(1, fakeScheduler.scheduleCalls.size)
        assertEquals(1_000_000L + 10 * 60_000L, fakeScheduler.scheduleCalls.first())
    }

    @Test fun `startTimer updates recents MRU`() = runTest(UnconfinedTestDispatcher()) {
        val repo = newRepo()
        advanceUntilIdle()
        repo.startTimer(7)
        advanceUntilIdle()
        assertEquals(listOf(7, 5, 12, 60), repo.recents.value)
        repo.startTimer(12)
        advanceUntilIdle()
        assertEquals(listOf(12, 7, 5, 60), repo.recents.value)
    }

    @Test fun `cancelTimer clears deadline and calls scheduler`() = runTest(UnconfinedTestDispatcher()) {
        val repo = newRepo()
        repo.startTimer(5)
        advanceUntilIdle()
        repo.cancelTimer()
        advanceUntilIdle()
        assertEquals(TimerState.Idle, repo.state.value)
        assertEquals(1, fakeScheduler.cancelCalls)
        val snap = repo.readSnapshot()
        assertNull(snap.deadlineEpochMs)
        assertNull(snap.durationMinutes)
    }

    @Test fun `setRinging(true) clears persisted deadline and emits Ringing`() = runTest(UnconfinedTestDispatcher()) {
        val repo = newRepo()
        repo.startTimer(3)
        advanceUntilIdle()
        repo.setRinging(isRinging = true, durationMinutes = 3)
        advanceUntilIdle()
        assertEquals(TimerState.Ringing(3), repo.state.first { it is TimerState.Ringing })
        assertNull(repo.readSnapshot().deadlineEpochMs)
    }

    @Test fun `setRinging(false) returns to Idle`() = runTest(UnconfinedTestDispatcher()) {
        val repo = newRepo()
        repo.setRinging(true, 3)
        advanceUntilIdle()
        repo.setRinging(false, 3)
        advanceUntilIdle()
        assertEquals(TimerState.Idle, repo.state.value)
    }

    @Test fun `startTimer is no-op while Ringing`() = runTest(UnconfinedTestDispatcher()) {
        val repo = newRepo()
        repo.setRinging(true, 3)
        advanceUntilIdle()
        assertEquals(TimerState.Ringing(3), repo.state.first { it is TimerState.Ringing })
        repo.startTimer(10)
        advanceUntilIdle()
        assertEquals(TimerState.Ringing(3), repo.state.value)
        assertTrue(fakeScheduler.scheduleCalls.isEmpty())
    }

    private class FakeScheduler : TimerScheduler {
        val scheduleCalls = mutableListOf<Long>()
        var cancelCalls = 0
        override fun schedule(deadlineEpochMs: Long) { scheduleCalls += deadlineEpochMs }
        override fun cancel() { cancelCalls++ }
    }

    private class FakeClock(initial: Long) : Clock {
        var nowMs = initial
        override fun nowEpochMs(): Long = nowMs
    }
}
