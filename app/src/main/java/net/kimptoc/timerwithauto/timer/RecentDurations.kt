package net.kimptoc.timerwithauto.timer

object RecentDurations {

    /** First-launch seed values (minutes). */
    val SEED: List<Int> = listOf(5, 12, 60)

    /** Maximum entries kept in the recents list. */
    const val MAX_SIZE: Int = 5

    /**
     * MRU + dedupe: removes [value] from [list] if present, prepends it, caps at [MAX_SIZE].
     */
    fun update(list: List<Int>, value: Int): List<Int> =
        (listOf(value) + list.filter { it != value }).take(MAX_SIZE)

    /** Joins values with commas. Used to persist into a single Preferences string. */
    fun format(list: List<Int>): String = list.joinToString(",")

    /** Parses the comma-joined form, silently dropping malformed tokens. */
    fun parse(text: String): List<Int> =
        if (text.isEmpty()) emptyList()
        else text.split(",").mapNotNull { it.toIntOrNull() }
}
