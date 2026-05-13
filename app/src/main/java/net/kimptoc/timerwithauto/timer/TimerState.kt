package net.kimptoc.timerwithauto.timer

/**
 * Public state of the timer subsystem. UI and Auto observe this via TimerRepository.state.
 */
sealed interface TimerState {
    /** No timer scheduled, alarm not ringing. */
    data object Idle : TimerState

    /** A future deadline is scheduled. */
    data class Running(val deadlineEpochMs: Long, val durationMinutes: Int) : TimerState

    /** AlarmService is currently playing the alarm. */
    data class Ringing(val durationMinutes: Int) : TimerState
}
