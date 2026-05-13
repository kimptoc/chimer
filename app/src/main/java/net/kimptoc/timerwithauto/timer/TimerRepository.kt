package net.kimptoc.timerwithauto.timer

import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for the running timer.
 *
 * Internally combines:
 *  - Persisted snapshot {deadlineEpochMs, durationMinutes} from DataStore
 *  - In-memory isRinging flag set by AlarmService
 * ... into a single [TimerState] StateFlow observed by UI and Auto.
 */
interface TimerRepository {

    val state: StateFlow<TimerState>

    val recents: StateFlow<List<Int>>

    /** Schedule a new timer. No-op if currently ringing. */
    suspend fun startTimer(durationMinutes: Int)

    /** Clears persisted deadline and cancels the scheduled alarm. */
    suspend fun cancelTimer()

    /**
     * Called by AlarmService when it begins playing / stops playing.
     * On start (isRinging=true), the persisted deadline is also cleared (alarm consumed it).
     */
    suspend fun setRinging(isRinging: Boolean, durationMinutes: Int)

    /**
     * Read the current persisted snapshot synchronously (suspending).
     * Used by AlarmReceiver and BootReceiver which run outside the long-lived flow.
     */
    suspend fun readSnapshot(): PersistedSnapshot

    data class PersistedSnapshot(
        val deadlineEpochMs: Long?,
        val durationMinutes: Int?,
    )
}
