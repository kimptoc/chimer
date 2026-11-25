package net.kimptoc.timerwithauto.alarm

import net.kimptoc.timerwithauto.alarm.BootReceiver.BootAction
import org.junit.Assert.assertEquals
import org.junit.Test

class BootDecisionTest {

    private val now = 10_000_000L

    @Test fun `future deadline is rescheduled`() {
        assertEquals(BootAction.RESCHEDULE, BootReceiver.decide(deadline = now + 1, now = now))
    }

    @Test fun `deadline exactly now rings immediately`() {
        assertEquals(BootAction.RING_NOW, BootReceiver.decide(deadline = now, now = now))
    }

    @Test fun `deadline missed within grace rings immediately`() {
        assertEquals(BootAction.RING_NOW, BootReceiver.decide(deadline = now - 5 * 60_000L, now = now))
    }

    @Test fun `deadline missed by exactly the grace period still rings`() {
        assertEquals(
            BootAction.RING_NOW,
            BootReceiver.decide(deadline = now - BootReceiver.MISSED_GRACE_MS, now = now),
        )
    }

    @Test fun `deadline missed by more than the grace period is discarded`() {
        assertEquals(
            BootAction.DISCARD,
            BootReceiver.decide(deadline = now - BootReceiver.MISSED_GRACE_MS - 1, now = now),
        )
    }
}
