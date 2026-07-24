package net.kimptoc.timerwithauto.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class RepeatAccelerationTest {

    @Test fun `nextDelayMs decreases by the step`() {
        assertEquals(360L, RepeatAcceleration.nextDelayMs(400L))
    }

    @Test fun `nextDelayMs clamps at the minimum`() {
        assertEquals(60L, RepeatAcceleration.nextDelayMs(70L))
        assertEquals(60L, RepeatAcceleration.nextDelayMs(60L))
    }
}
