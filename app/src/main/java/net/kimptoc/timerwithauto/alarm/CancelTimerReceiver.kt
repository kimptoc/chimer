package net.kimptoc.timerwithauto.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.runBlocking
import net.kimptoc.timerwithauto.TimerApp

/**
 * Handles the Cancel action on the running-timer notification.
 * Cancels the scheduled alarm via TimerRepository.cancelTimer().
 */
class CancelTimerReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "onReceive: cancel from notification")
        try {
            val app = context.applicationContext as TimerApp
            runBlocking { app.container.timerRepository.cancelTimer() }
        } catch (t: Throwable) {
            Log.e(TAG, "cancel failed", t)
        }
    }

    companion object {
        const val ACTION = "net.kimptoc.timerwithauto.action.CANCEL_TIMER"
        private const val TAG = "TimerCancelReceiver"
    }
}
