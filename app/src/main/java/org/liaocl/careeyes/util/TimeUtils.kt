package org.liaocl.careeyes.util

import java.util.Locale
import java.util.concurrent.TimeUnit

object TimeUtils {
    fun formatMmSs(milliseconds: Long): String {
        val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(milliseconds.coerceAtLeast(0L))
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    fun formatCompactDuration(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0L)
        val hours = safe / 3600
        val minutes = (safe % 3600) / 60
        return when {
            hours > 0 && minutes > 0 -> String.format(Locale.US, "%dh %02dm", hours, minutes)
            hours > 0 -> String.format(Locale.US, "%dh", hours)
            else -> String.format(Locale.US, "%dm", minutes)
        }
    }
}
