package net.kimptoc.timerwithauto.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.runBlocking
import net.kimptoc.timerwithauto.TimerApp

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "onReceive: alarm fired")
        val app = context.applicationContext as TimerApp
        val snapshot = runBlocking { app.container.timerRepository.readSnapshot() }
        val duration = snapshot.durationMinutes
        if (duration == null) {
            Log.w(TAG, "onReceive: no duration in snapshot — dropping (race with cancel?)")
            return
        }
        Log.i(TAG, "onReceive: durationMinutes=$duration, starting AlarmService")
        val svc = Intent(context, AlarmService::class.java).apply {
            putExtra(AlarmService.EXTRA_DURATION_MINUTES, duration)
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(svc)
            } else {
                context.startService(svc)
            }
            Log.i(TAG, "onReceive: startForegroundService returned")
        } catch (t: Throwable) {
            Log.e(TAG, "onReceive: failed to start AlarmService", t)
        }
    }

    companion object {
        private const val TAG = "TimerAlarmReceiver"
    }
}
