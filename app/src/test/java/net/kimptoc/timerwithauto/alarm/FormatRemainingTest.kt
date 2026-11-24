package net.kimptoc.timerwithauto.alarm

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatRemainingTest {

    @Test fun `formats minutes and seconds`() {
        assertEquals("11:12", Notifier.formatRemaining(672_000L))
    }

    @Test fun `rounds partial seconds up`() {
        assertEquals("0:01", Notifier.formatRemaining(1L))
        assertEquals("12:00", Notifier.formatRemaining(719_001L))
    }

    @Test fun `zero and negative show 0-00`() {
        assertEquals("0:00", Notifier.formatRemaining(0L))
        assertEquals("0:00", Notifier.formatRemaining(-500L))
    }

    @Test fun `hour or more shows hours`() {
        assertEquals("1:05:09", Notifier.formatRemaining(3_909_000L))
    }
}
