package net.kimptoc.timerwithauto.alarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.support.v4.media.session.MediaSessionCompat
import androidx.car.app.notification.CarAppExtender
import androidx.car.app.notification.CarNotificationManager
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import net.kimptoc.timerwithauto.MainActivity
import net.kimptoc.timerwithauto.R

object Notifier {

    const val CHANNEL_RINGING = "alarm_ringing"
    const val CHANNEL_RUNNING = "timer_running"
    const val NOTIF_ID_RINGING = 1
    const val NOTIF_ID_RUNNING = 2

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (nm.getNotificationChannel(CHANNEL_RINGING) == null) {
            val channel = NotificationChannel(
                CHANNEL_RINGING,
                context.getString(R.string.notif_channel_ringing),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notif_channel_ringing_desc)
                setSound(null, null) // we play audio ourselves
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(channel)
            // Make Auto host aware of the channel too (needed for heads-up on car screen)
            val compatChannel = NotificationChannelCompat.Builder(
                CHANNEL_RINGING,
                NotificationManager.IMPORTANCE_HIGH,
            )
                .setName(context.getString(R.string.notif_channel_ringing))
                .setDescription(context.getString(R.string.notif_channel_ringing_desc))
                .setSound(null, null)
                .setVibrationEnabled(false)
                .build()
            CarNotificationManager.from(context).createNotificationChannel(compatChannel)
        }

        if (nm.getNotificationChannel(CHANNEL_RUNNING) == null) {
            // LOW so it doesn't make sound on every state update. The MediaSession
            // is what triggers the system to render the Now Bar / status-bar pill.
            val channel = NotificationChannel(
                CHANNEL_RUNNING,
                context.getString(R.string.notif_channel_running),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.notif_channel_running_desc)
                setSound(null, null)
                enableVibration(false)
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            nm.createNotificationChannel(channel)
        }
    }

    fun buildRunningNotification(
        context: Context,
        durationMinutes: Int,
        sessionToken: MediaSessionCompat.Token,
        cancelIntent: PendingIntent,
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            REQ_RUNNING_CONTENT,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
            .setMediaSession(sessionToken)
            .setShowActionsInCompactView(0)

        return NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.notif_running_title))
            .setContentText(context.getString(R.string.notif_running_text_set, durationMinutes))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                context.getString(R.string.notif_action_cancel),
                cancelIntent,
            )
            .setStyle(mediaStyle)
            .build()
    }

    private const val REQ_RUNNING_CONTENT = 3001

    fun buildRingingNotification(context: Context, stopIntent: PendingIntent): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val carExtender = CarAppExtender.Builder()
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText(context.getString(R.string.notif_action_stop))
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setImportance(NotificationManager.IMPORTANCE_HIGH)
            .addAction(
                android.R.drawable.ic_media_pause,
                context.getString(R.string.notif_action_stop),
                stopIntent,
            )
            .build()

        return NotificationCompat.Builder(context, CHANNEL_RINGING)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(context.getString(R.string.notif_title))
            .setContentText(context.getString(R.string.notif_channel_ringing))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(contentIntent, true)
            .setContentIntent(contentIntent)
            .addAction(android.R.drawable.ic_media_pause,
                       context.getString(R.string.notif_action_stop),
                       stopIntent)
            .extend(carExtender)
            .build()
    }
}
