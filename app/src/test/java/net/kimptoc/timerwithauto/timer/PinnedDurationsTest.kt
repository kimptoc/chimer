package net.kimptoc.timerwithauto.timer

import org.junit.Assert.assertEquals
import org.junit.Test

class PinnedDurationsTest {

    @Test fun `add inserts in ascending order`() {
        assertEquals(listOf(5, 10, 30), PinnedDurations.add(listOf(5, 30), 10))
    }

    @Test fun `add to empty list`() {
        assertEquals(listOf(10), PinnedDurations.add(emptyList(), 10))
    }

    @Test fun `add ignores duplicates`() {
        assertEquals(listOf(5, 10), PinnedDurations.add(listOf(5, 10), 10))
    }

    @Test fun `add is a no-op when full`() {
        val full = (1..PinnedDurations.MAX_SIZE).toList()
        assertEquals(full, PinnedDurations.add(full, 99))
    }

    @Test fun `remove drops the value`() {
        assertEquals(listOf(5, 30), PinnedDurations.remove(listOf(5, 10, 30), 10))
    }

    @Test fun `remove of missing value is a no-op`() {
        assertEquals(listOf(5, 30), PinnedDurations.remove(listOf(5, 30), 10))
    }

    @Test fun `excludeFrom hides pinned values from recents, preserving order`() {
        assertEquals(listOf(12, 5), PinnedDurations.excludeFrom(listOf(12, 10, 5, 60), listOf(10, 60)))
    }

    @Test fun `parse normalises to sorted distinct capped`() {
        assertEquals(listOf(1, 5, 10), PinnedDurations.parse("10,5,x,5,1"))
        assertEquals(PinnedDurations.MAX_SIZE, PinnedDurations.parse((1..20).joinToString(",")).size)
        assertEquals(emptyList<Int>(), PinnedDurations.parse(""))
    }

    @Test fun `format round-trips through parse`() {
        assertEquals(listOf(3, 15, 45), PinnedDurations.parse(PinnedDurations.format(listOf(3, 15, 45))))
    }
}
