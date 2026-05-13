package net.kimptoc.timerwithauto.timer

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

interface TimerRepository {

    val state: StateFlow<TimerState>

    val recents: StateFlow<List<Int>>

    suspend fun startTimer(durationMinutes: Int)

    suspend fun cancelTimer()

    suspend fun setRinging(isRinging: Boolean, durationMinutes: Int)

    suspend fun readSnapshot(): PersistedSnapshot

    data class PersistedSnapshot(
        val deadlineEpochMs: Long?,
        val durationMinutes: Int?,
    )
}

class TimerRepositoryImpl(
    context: Context,
    private val scheduler: TimerScheduler,
    private val clock: Clock,
    scope: CoroutineScope,
    dataStoreName: String = "timer_with_auto",
) : TimerRepository {

    private val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile(dataStoreName) },
    )

    private val isRingingFlow = MutableStateFlow(false)
    private val ringingDurationFlow = MutableStateFlow(0)

    private val persistedFlow = dataStore.data.map { prefs ->
        TimerRepository.PersistedSnapshot(
            deadlineEpochMs = prefs[KEY_DEADLINE],
            durationMinutes = prefs[KEY_DURATION],
        )
    }

    override val state: StateFlow<TimerState> = combine(
        persistedFlow,
        isRingingFlow,
        ringingDurationFlow,
    ) { snap, ringing, ringingMinutes ->
        when {
            ringing -> TimerState.Ringing(ringingMinutes)
            snap.deadlineEpochMs != null && snap.durationMinutes != null ->
                TimerState.Running(snap.deadlineEpochMs, snap.durationMinutes)
            else -> TimerState.Idle
        }
    }.stateIn(scope, SharingStarted.Eagerly, TimerState.Idle)

    override val recents: StateFlow<List<Int>> = dataStore.data
        .map { prefs ->
            val raw = prefs[KEY_RECENTS]
            if (raw == null) RecentDurations.SEED else RecentDurations.parse(raw)
        }
        .stateIn(scope, SharingStarted.Eagerly, RecentDurations.SEED)

    override suspend fun startTimer(durationMinutes: Int) {
        if (isRingingFlow.value) return
        val deadline = clock.nowEpochMs() + durationMinutes * 60_000L
        dataStore.edit { prefs ->
            prefs[KEY_DEADLINE] = deadline
            prefs[KEY_DURATION] = durationMinutes
            val current = prefs[KEY_RECENTS]?.let { RecentDurations.parse(it) } ?: RecentDurations.SEED
            prefs[KEY_RECENTS] = RecentDurations.format(RecentDurations.update(current, durationMinutes))
        }
        scheduler.schedule(deadline)
    }

    override suspend fun cancelTimer() {
        scheduler.cancel()
        dataStore.edit { prefs ->
            prefs.remove(KEY_DEADLINE)
            prefs.remove(KEY_DURATION)
        }
    }

    override suspend fun setRinging(isRinging: Boolean, durationMinutes: Int) {
        if (isRinging) {
            dataStore.edit { prefs ->
                prefs.remove(KEY_DEADLINE)
                prefs.remove(KEY_DURATION)
            }
            ringingDurationFlow.value = durationMinutes
            isRingingFlow.value = true
        } else {
            isRingingFlow.value = false
        }
    }

    override suspend fun readSnapshot(): TimerRepository.PersistedSnapshot =
        persistedFlow.first()

    companion object {
        private val KEY_DEADLINE = longPreferencesKey("timer_deadline_epoch_ms")
        private val KEY_DURATION = intPreferencesKey("timer_duration_minutes")
        private val KEY_RECENTS = stringPreferencesKey("recent_durations_minutes")
    }
}
