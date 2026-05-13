package net.kimptoc.timerwithauto.alarm

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

interface VibratorWrapper {
    fun startLoop()
    fun stop()
}

class SystemVibratorWrapper(context: Context) : VibratorWrapper {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    override fun startLoop() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val pattern = longArrayOf(0L, 500L, 500L)  // 500ms on, 500ms off, repeating
        v.vibrate(VibrationEffect.createWaveform(pattern, 0))
    }

    override fun stop() {
        vibrator?.cancel()
    }
}
