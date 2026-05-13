package net.kimptoc.timerwithauto.di

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.kimptoc.timerwithauto.alarm.AudioPlayer
import net.kimptoc.timerwithauto.alarm.MediaPlayerAudioPlayer
import net.kimptoc.timerwithauto.alarm.RunningTimerNotifier
import net.kimptoc.timerwithauto.alarm.SystemVibratorWrapper
import net.kimptoc.timerwithauto.alarm.TimerMediaSession
import net.kimptoc.timerwithauto.alarm.VibratorWrapper
import net.kimptoc.timerwithauto.timer.AlarmManagerScheduler
import net.kimptoc.timerwithauto.timer.Clock
import net.kimptoc.timerwithauto.timer.TimerRepository
import net.kimptoc.timerwithauto.timer.TimerRepositoryImpl
import net.kimptoc.timerwithauto.timer.TimerScheduler

class AppContainer(context: Context) {
    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val clock: Clock = Clock.System
    val scheduler: TimerScheduler = AlarmManagerScheduler(context.applicationContext)
    val timerRepository: TimerRepository = TimerRepositoryImpl(
        context = context.applicationContext,
        scheduler = scheduler,
        clock = clock,
        scope = applicationScope,
    )
    val audioPlayer: AudioPlayer = MediaPlayerAudioPlayer(context.applicationContext)
    val vibratorWrapper: VibratorWrapper = SystemVibratorWrapper(context.applicationContext)
    val timerMediaSession: TimerMediaSession = TimerMediaSession(context.applicationContext)
    val runningTimerNotifier: RunningTimerNotifier = RunningTimerNotifier(
        context = context.applicationContext,
        repository = timerRepository,
        clock = clock,
        mediaSession = timerMediaSession,
        scope = applicationScope,
    )
}
