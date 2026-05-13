package net.kimptoc.timerwithauto.ui

import kotlin.math.max

/**
 * Returns ms remaining until deadline, clamped at zero.
 */
fun remainingMillis(deadlineEpochMs: Long, nowEpochMs: Long): Long =
    max(0L, deadlineEpochMs - nowEpochMs)

/**
 * Formats remaining time. Rounds up to the next whole second so 12.4s reads "00:13"
 * (avoids showing 00:00 while the alarm hasn't fired yet).
 *
 * Returns `MM:SS` under one hour, `H:MM:SS` at or above one hour.
 */
fun formatRemaining(remainingMs: Long): String {
    val totalSeconds = ((remainingMs + 999L) / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
