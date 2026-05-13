package net.kimptoc.timerwithauto

import android.app.Application
import net.kimptoc.timerwithauto.di.AppContainer

class TimerApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
