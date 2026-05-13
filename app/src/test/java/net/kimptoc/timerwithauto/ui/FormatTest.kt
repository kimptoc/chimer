package net.kimptoc.timerwithauto.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test fun `remainingMillis returns deadline minus now when positive`() {
        assertEquals(10_000L, remainingMillis(deadlineEpochMs = 100_000L, nowEpochMs = 90_000L))
    }

    @Test fun `remainingMillis clamps negative values to zero`() {
        assertEquals(0L, remainingMillis(deadlineEpochMs = 50_000L, nowEpochMs = 90_000L))
    }

    @Test fun `formatRemaining renders MM_SS under one hour`() {
        assertEquals("00:00", formatRemaining(0L))
        assertEquals("00:01", formatRemaining(1_000L))
        assertEquals("00:59", formatRemaining(59_000L))
        assertEquals("01:00", formatRemaining(60_000L))
        assertEquals("12:34", formatRemaining(12 * 60_000L + 34_000L))
        assertEquals("59:59", formatRemaining(59 * 60_000L + 59_000L))
    }

    @Test fun `formatRemaining renders H_MM_SS at or above one hour`() {
        assertEquals("1:00:00", formatRemaining(60 * 60_000L))
        assertEquals("1:23:45", formatRemaining(1 * 3_600_000L + 23 * 60_000L + 45_000L))
        assertEquals("3:00:00", formatRemaining(3 * 3_600_000L))
    }

    @Test fun `formatRemaining rounds up sub-second remainder to avoid showing zero early`() {
        // 12.4 seconds remaining should display 00:13, not 00:12
        assertEquals("00:13", formatRemaining(12_400L))
        assertEquals("00:01", formatRemaining(1L))
    }
}
