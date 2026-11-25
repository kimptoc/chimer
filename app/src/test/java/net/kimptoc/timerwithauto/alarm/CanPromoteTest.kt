package net.kimptoc.timerwithauto.alarm

import android.os.Build
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CanPromoteTest {

    private val baklava = Build.VERSION_CODES.BAKLAVA

    @Test fun `below API 36 never promotes and does not ask the system`() {
        val result = Notifier.canPromote(baklava - 1, "Google") { error("should not be called") }
        assertFalse(result)
    }

    @Test fun `API 36 non-Samsung follows the system permission`() {
        assertTrue(Notifier.canPromote(baklava, "Google") { true })
        assertFalse(Notifier.canPromote(baklava, "Google") { false })
    }

    @Test fun `Samsung uses the media-style path even when promotion is allowed`() {
        assertFalse(Notifier.canPromote(baklava, "samsung") { true })
        assertFalse(Notifier.canPromote(baklava, "SAMSUNG") { true })
    }

    @Test fun `unknown manufacturer is not treated as Samsung`() {
        assertTrue(Notifier.canPromote(baklava, null) { true })
    }
}
