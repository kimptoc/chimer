package net.kimptoc.timerwithauto.alarm

import net.kimptoc.timerwithauto.R

data class RingProfile(val soundRes: Int, val autoStopMs: Long) {
    companion object {
        fun forCarConnected(isCarConnected: Boolean): RingProfile =
            if (isCarConnected) {
                RingProfile(soundRes = R.raw.alarm_car, autoStopMs = 15L * 1000L)
            } else {
                RingProfile(soundRes = R.raw.alarm, autoStopMs = 2L * 60L * 1000L)
            }
    }
}
