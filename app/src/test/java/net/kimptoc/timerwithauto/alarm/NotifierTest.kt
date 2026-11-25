package net.kimptoc.timerwithauto.alarm

import android.app.Application
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.support.v4.media.session.MediaSessionCompat
import android.text.format.DateFormat
import androidx.test.core.app.ApplicationProvider
import net.kimptoc.timerwithauto.R
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Date

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = Application::class)
class NotifierTest {

    private lateinit var context: Context
    private lateinit var session: MediaSessionCompat
    private lateinit var cancelIntent: PendingIntent

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        session = MediaSessionCompat(context, "NotifierTest")
        cancelIntent = PendingIntent.getBroadcast(
            context, 0, Intent("test.CANCEL"), PendingIntent.FLAG_IMMUTABLE,
        )
    }

    @After fun tearDown() {
        session.release()
    }

    @Test fun `ensureChannel creates ringing and running channels`() {
        Notifier.ensureChannel(context)
        Notifier.ensureChannel(context) // idempotent
        val nm = context.getSystemService(NotificationManager::class.java)
        assertEquals(
            NotificationManager.IMPORTANCE_HIGH,
            nm.getNotificationChannel(Notifier.CHANNEL_RINGING).importance,
        )
        assertEquals(
            NotificationManager.IMPORTANCE_LOW,
            nm.getNotificationChannel(Notifier.CHANNEL_RUNNING).importance,
        )
    }

    @Test fun `canPromote is false below API 36`() {
        assertFalse(Notifier.canPromote(context))
    }

    @Test fun `media-style running notification shows countdown as title`() {
        val n = Notifier.buildRunningNotification(
            context = context,
            durationMinutes = 12,
            remainingMs = 672_000L,
            sessionToken = session.sessionToken,
            cancelIntent = cancelIntent,
        )
        assertEquals(Notifier.CHANNEL_RUNNING, n.channelId)
        assertEquals("11:12", n.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
        assertEquals(
            context.getString(R.string.notif_running_text_set, 12),
            n.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        assertTrue("ongoing", n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertTrue("only alert once", n.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertNotNull("content intent", n.contentIntent)
        assertNotNull("media session token", n.extras.getParcelable(Notification.EXTRA_MEDIA_SESSION))
        assertCancelAction(n)
    }

    @Test fun `promoted running notification shows end time and a countdown chronometer`() {
        val deadline = 1_800_000_000_000L
        val n = Notifier.buildRunningPromotedNotification(
            context = context,
            durationMinutes = 25,
            deadlineEpochMs = deadline,
            cancelIntent = cancelIntent,
        )
        val endTime = DateFormat.getTimeFormat(context).format(Date(deadline))
        assertEquals(Notifier.CHANNEL_RUNNING, n.channelId)
        assertEquals(
            context.getString(R.string.notif_running_title_ends, endTime),
            n.extras.getCharSequence(Notification.EXTRA_TITLE).toString(),
        )
        assertEquals(
            context.getString(R.string.notif_running_text_set, 25),
            n.extras.getCharSequence(Notification.EXTRA_TEXT).toString(),
        )
        assertEquals(deadline, n.`when`)
        assertTrue("chronometer", n.extras.getBoolean(Notification.EXTRA_SHOW_CHRONOMETER))
        assertTrue("counts down", n.extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertTrue("ongoing", n.flags and Notification.FLAG_ONGOING_EVENT != 0)
        assertCancelAction(n)
    }

    @Test fun `ringing notification is high priority with a stop action`() {
        val stopIntent = PendingIntent.getService(
            context, 0, Intent("test.STOP"), PendingIntent.FLAG_IMMUTABLE,
        )
        val n = Notifier.buildRingingNotification(context, stopIntent)
        assertEquals(Notifier.CHANNEL_RINGING, n.channelId)
        assertEquals(Notification.CATEGORY_ALARM, n.category)
        assertEquals(1, n.actions.size)
        assertSame(stopIntent, n.actions[0].actionIntent)
    }

    private fun assertCancelAction(n: Notification) {
        assertEquals(1, n.actions.size)
        assertEquals(context.getString(R.string.notif_action_cancel), n.actions[0].title.toString())
        assertSame(cancelIntent, n.actions[0].actionIntent)
    }
}
