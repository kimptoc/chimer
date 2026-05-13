package net.kimptoc.timerwithauto.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.runBlocking
import net.kimptoc.timerwithauto.TimerApp

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED) return

        val container = (context.applicationContext as TimerApp).container
        val snap = runBlocking { container.timerRepository.readSnapshot() }
        val deadline = snap.deadlineEpochMs ?: return
        val duration = snap.durationMinutes ?: return
        val now = container.clock.nowEpochMs()

        when {
            deadline > now -> {
                container.scheduler.schedule(deadline)
            }
            now - deadline <= MISSED_GRACE_MS -> {
                // Recently missed — clear and ring immediately.
                runBlocking { container.timerRepository.cancelTimer() }
                val svc = Intent(context, AlarmService::class.java).apply {
                    putExtra(AlarmService.EXTRA_DURATION_MINUTES, duration)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(svc)
                } else {
                    context.startService(svc)
                }
            }
            else -> {
                // Old enough that the user has moved on.
                runBlocking { container.timerRepository.cancelTimer() }
            }
        }
    }

    companion object {
        private const val MISSED_GRACE_MS = 60L * 60L * 1000L  // 1 hour
    }
}
