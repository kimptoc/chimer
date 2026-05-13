package net.kimptoc.timerwithauto.timer

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentDurationsTest {

    @Test fun `update prepends new value`() {
        assertEquals(listOf(7, 5, 12, 60), RecentDurations.update(listOf(5, 12, 60), 7))
    }

    @Test fun `update moves existing value to front (dedupe)`() {
        assertEquals(listOf(12, 5, 60), RecentDurations.update(listOf(5, 12, 60), 12))
    }

    @Test fun `update caps at five entries`() {
        assertEquals(
            listOf(99, 1, 2, 3, 4),
            RecentDurations.update(listOf(1, 2, 3, 4, 5), 99)
        )
    }

    @Test fun `update with value already at head returns equal list`() {
        assertEquals(listOf(5, 12, 60), RecentDurations.update(listOf(5, 12, 60), 5))
    }

    @Test fun `update on empty list returns single-element list`() {
        assertEquals(listOf(10), RecentDurations.update(emptyList(), 10))
    }

    @Test fun `seed returns the documented initial list`() {
        assertEquals(listOf(5, 12, 60), RecentDurations.SEED)
    }

    @Test fun `parse handles empty string as empty list`() {
        assertEquals(emptyList<Int>(), RecentDurations.parse(""))
    }

    @Test fun `parse and format are inverses`() {
        val list = listOf(7, 5, 12, 60)
        assertEquals(list, RecentDurations.parse(RecentDurations.format(list)))
    }

    @Test fun `parse drops malformed tokens`() {
        assertEquals(listOf(5, 12), RecentDurations.parse("5,nope,12,"))
    }
}
