package net.kimptoc.timerwithauto.ui

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.kimptoc.timerwithauto.TimerApp
import net.kimptoc.timerwithauto.alarm.AlarmService
import net.kimptoc.timerwithauto.timer.PinnedDurations
import net.kimptoc.timerwithauto.timer.TimerRepository
import net.kimptoc.timerwithauto.timer.TimerState

class TimerViewModel(
    application: Application,
    private val repo: TimerRepository,
) : AndroidViewModel(application) {

    val state: StateFlow<TimerState> = repo.state
    val pinned: StateFlow<List<Int>> = repo.pinned

    /** Recents minus anything pinned, so the pinned row and recents row don't repeat. */
    val recents: StateFlow<List<Int>> = combine(repo.recents, repo.pinned, PinnedDurations::excludeFrom)
        .stateIn(
            viewModelScope,
            SharingStarted.Eagerly,
            PinnedDurations.excludeFrom(repo.recents.value, repo.pinned.value),
        )

    /** Most recently used duration, even if it's pinned (and so hidden from [recents]). */
    val lastUsedMinutes: Int? get() = repo.recents.value.firstOrNull()

    fun pin(durationMinutes: Int) {
        viewModelScope.launch { repo.pin(durationMinutes) }
    }

    fun unpin(durationMinutes: Int) {
        viewModelScope.launch { repo.unpin(durationMinutes) }
    }

    fun start(durationMinutes: Int) {
        viewModelScope.launch { repo.startTimer(durationMinutes) }
    }

    fun cancel() {
        viewModelScope.launch { repo.cancelTimer() }
    }

    fun stopAlarm() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, AlarmService::class.java).apply {
            action = AlarmService.ACTION_STOP
        }
        ctx.startService(intent)
    }

    class Factory(private val app: Application) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            val repo = (app as TimerApp).container.timerRepository
            return TimerViewModel(app, repo) as T
        }
    }
}
