package net.kimptoc.timerwithauto.car

import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.CarColor
import androidx.car.app.model.MessageTemplate
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.kimptoc.timerwithauto.TimerApp
import net.kimptoc.timerwithauto.alarm.AlarmService
import net.kimptoc.timerwithauto.timer.TimerState
import net.kimptoc.timerwithauto.ui.formatRemaining
import net.kimptoc.timerwithauto.ui.remainingMillis

class TimerCarScreen(carContext: CarContext) : Screen(carContext) {

    private val app = carContext.applicationContext as TimerApp
    private val scope = CoroutineScope(Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())
    private var collectJob: Job? = null

    private val tick = object : Runnable {
        override fun run() {
            invalidate()
            handler.postDelayed(this, 1_000L)
        }
    }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) {
                handler.post(tick)
                collectJob = scope.launch {
                    app.container.timerRepository.state.collectLatest { invalidate() }
                }
            }
            override fun onStop(owner: LifecycleOwner) {
                handler.removeCallbacks(tick)
                collectJob?.cancel()
                collectJob = null
            }
            override fun onDestroy(owner: LifecycleOwner) {
                scope.cancel()
            }
        })
    }

    override fun onGetTemplate(): Template {
        val state = app.container.timerRepository.state.value
        return when (state) {
            is TimerState.Idle -> idleTemplate()
            is TimerState.Running -> runningTemplate(state)
            is TimerState.Ringing -> ringingTemplate()
        }
    }

    private fun idleTemplate(): MessageTemplate =
        MessageTemplate.Builder("No timer running.\nStart one from your phone.")
            .setTitle("Timer With Auto")
            .build()

    private fun runningTemplate(state: TimerState.Running): MessageTemplate {
        val remaining = remainingMillis(state.deadlineEpochMs, app.container.clock.nowEpochMs())
        val cancel = Action.Builder()
            .setTitle("Cancel")
            .setOnClickListener {
                scope.launch { app.container.timerRepository.cancelTimer() }
            }
            .build()
        return MessageTemplate.Builder(formatRemaining(remaining))
            .setTitle("Timer running")
            .addAction(cancel)
            .build()
    }

    private fun ringingTemplate(): MessageTemplate {
        val stop = Action.Builder()
            .setTitle("STOP")
            .setBackgroundColor(CarColor.RED)
            .setOnClickListener {
                val intent = Intent(carContext, AlarmService::class.java).apply {
                    action = AlarmService.ACTION_STOP
                }
                carContext.startForegroundService(intent)
            }
            .build()
        return MessageTemplate.Builder("Timer finished")
            .setTitle("Timer With Auto")
            .addAction(stop)
            .build()
    }
}
