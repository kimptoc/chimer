package net.kimptoc.timerwithauto.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import net.kimptoc.timerwithauto.alarm.AlarmReceiver

interface TimerScheduler {
    fun schedule(deadlineEpochMs: Long)
    fun cancel()
}

class AlarmManagerScheduler(private val context: Context) : TimerScheduler {

    private val alarmManager: AlarmManager =
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private val pendingIntent: PendingIntent =
        PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            Intent(context, AlarmReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    override fun schedule(deadlineEpochMs: Long) {
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                deadlineEpochMs,
                pendingIntent,
            )
        } catch (e: SecurityException) {
            // USE_EXACT_ALARM is install-granted for alarm-category apps; if the OEM disables it,
            // fall back to an inexact alarm. Better late than silent.
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                deadlineEpochMs,
                pendingIntent,
            )
        }
    }

    override fun cancel() {
        alarmManager.cancel(pendingIntent)
    }

    companion object {
        private const val REQUEST_CODE = 1001
    }
}
