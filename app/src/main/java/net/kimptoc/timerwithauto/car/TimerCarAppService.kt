package net.kimptoc.timerwithauto.car

import android.content.Intent
import androidx.car.app.CarAppService
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator

class TimerCarAppService : CarAppService() {

    override fun createHostValidator(): HostValidator =
        HostValidator.ALLOW_ALL_HOSTS_VALIDATOR

    override fun onCreateSession(sessionInfo: SessionInfo): Session = TimerSession()

    private class TimerSession : Session() {
        override fun onCreateScreen(intent: Intent): Screen = TimerCarScreen(carContext)
    }
}
