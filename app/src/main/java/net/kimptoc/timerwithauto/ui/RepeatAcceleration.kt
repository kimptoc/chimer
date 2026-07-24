package net.kimptoc.timerwithauto.ui

object RepeatAcceleration {
    const val INITIAL_DELAY_MS = 400L
    private const val MIN_DELAY_MS = 60L
    private const val STEP_MS = 40L

    fun nextDelayMs(currentDelayMs: Long): Long =
        (currentDelayMs - STEP_MS).coerceAtLeast(MIN_DELAY_MS)
}
