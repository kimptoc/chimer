package net.kimptoc.timerwithauto.alarm

import net.kimptoc.timerwithauto.R
import org.junit.Assert.assertEquals
import org.junit.Test

class RingProfileTest {

    @Test fun `not car-connected uses default sound and 2-minute auto-stop`() {
        val profile = RingProfile.forCarConnected(false)
        assertEquals(R.raw.alarm, profile.soundRes)
        assertEquals(2L * 60L * 1000L, profile.autoStopMs)
    }

    @Test fun `car-connected uses car chime and 15-second auto-stop`() {
        val profile = RingProfile.forCarConnected(true)
        assertEquals(R.raw.alarm_car, profile.soundRes)
        assertEquals(15L * 1000L, profile.autoStopMs)
    }
}
