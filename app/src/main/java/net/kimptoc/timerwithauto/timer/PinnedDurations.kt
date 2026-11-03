package net.kimptoc.timerwithauto.timer

/**
 * Pure helpers for the user's pinned durations: a small, user-curated set of
 * minute values that are always shown, independent of the recents MRU list.
 */
object PinnedDurations {

    /** Maximum number of pinned durations. */
    const val MAX_SIZE: Int = 8

    /**
     * Adds [value] to [list], keeping it de-duplicated and sorted ascending.
     * A no-op if [value] is already pinned or the list is already at [MAX_SIZE].
     */
    fun add(list: List<Int>, value: Int): List<Int> =
        if (value in list || list.size >= MAX_SIZE) list
        else (list + value).sorted()

    /** Removes [value] from [list]; a no-op if it isn't pinned. */
    fun remove(list: List<Int>, value: Int): List<Int> = list.filter { it != value }

    /** Recents with any pinned values removed, so the two rows don't repeat each other. */
    fun excludeFrom(recents: List<Int>, pinned: List<Int>): List<Int> =
        recents.filter { it !in pinned }

    /** Parses the persisted comma-joined form, normalising to sorted, distinct, capped. */
    fun parse(text: String): List<Int> =
        RecentDurations.parse(text).distinct().sorted().take(MAX_SIZE)

    fun format(list: List<Int>): String = RecentDurations.format(list)
}
