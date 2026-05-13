package net.kimptoc.timerwithauto.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.runBlocking
import net.kimptoc.timerwithauto.TimerApp

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as TimerApp
        val snapshot = runBlocking { app.container.timerRepository.readSnapshot() }
        val duration = snapshot.durationMinutes ?: return  // race with cancel — drop
        val svc = Intent(context, AlarmService::class.java).apply {
            putExtra(AlarmService.EXTRA_DURATION_MINUTES, duration)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(svc)
        } else {
            context.startService(svc)
        }
    }
}
