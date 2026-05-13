package net.kimptoc.timerwithauto.timer

interface TimerScheduler {
    fun schedule(deadlineEpochMs: Long)
    fun cancel()
}
