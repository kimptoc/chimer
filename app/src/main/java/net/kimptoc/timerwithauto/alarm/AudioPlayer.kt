package net.kimptoc.timerwithauto.alarm

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
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
        try {
            val afd: AssetFileDescriptor = context.resources.openRawResourceFd(R.raw.alarm)
                ?: run {
                    Log.e(TAG, "alarm resource not found")
                    return
                }
            val mp = MediaPlayer()
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            mp.isLooping = true
            // Belt-and-braces: if the framework ignores isLooping on this codec/device,
            // seek and restart on completion.
            mp.setOnCompletionListener { p ->
                try {
                    p.seekTo(0)
                    p.start()
                } catch (t: Throwable) {
                    Log.w(TAG, "completion-restart failed", t)
                }
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.w(TAG, "MediaPlayer error what=$what extra=$extra")
                false
            }
            mp.prepare()
            mp.start()
            player = mp
            Log.i(TAG, "startLoop: playing R.raw.alarm, isLooping=true, USAGE_ALARM")
        } catch (t: Throwable) {
            Log.e(TAG, "startLoop failed", t)
            try { player?.release() } catch (_: Throwable) {}
            player = null
        }
    }

    override fun stop() {
        player?.apply {
            try { if (isPlaying) stop() } catch (_: IllegalStateException) {}
            try { release() } catch (_: Throwable) {}
        }
        player = null
    }

    companion object {
        private const val TAG = "TimerAudioPlayer"
    }
}
