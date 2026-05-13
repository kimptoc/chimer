package net.kimptoc.timerwithauto.alarm

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import net.kimptoc.timerwithauto.R

interface AudioPlayer {
    /** Starts looping the alarm sound. Safe to call again — re-starts cleanly. */
    fun startLoop()
    fun stop()
}

class MediaPlayerAudioPlayer(private val context: Context) : AudioPlayer {

    private var player: MediaPlayer? = null

    override fun startLoop() {
        stop()
        player = MediaPlayer.create(context, R.raw.alarm)?.apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            isLooping = true
            start()
        }
    }

    override fun stop() {
        player?.apply {
            try { if (isPlaying) stop() } catch (_: IllegalStateException) {}
            release()
        }
        player = null
    }
}
