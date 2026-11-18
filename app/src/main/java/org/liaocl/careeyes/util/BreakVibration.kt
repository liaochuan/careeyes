package org.liaocl.careeyes.util

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/** A single short vibration that marks the end of a break countdown. */
object BreakVibration {
    private const val DURATION_MS = 120L

    fun vibrateOnce(context: Context, enabled: Boolean) {
        if (!enabled) return
        runCatching {
            val vibrator = vibrator(context) ?: return
            if (!shouldVibrateOnBreakEnd(enabled, vibrator.hasVibrator())) return
            vibrator.vibrate(
                VibrationEffect.createOneShot(DURATION_MS, VibrationEffect.DEFAULT_AMPLITUDE),
            )
        }
    }

    private fun vibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
}

/** Only vibrate when the user asked for it and the device can actually vibrate. */
fun shouldVibrateOnBreakEnd(enabled: Boolean, hasVibrator: Boolean): Boolean =
    enabled && hasVibrator
