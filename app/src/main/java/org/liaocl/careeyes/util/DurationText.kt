package org.liaocl.careeyes.util

import android.content.Context
import org.liaocl.careeyes.R

fun durationText(context: Context, seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val secs = safe % 60
    return when {
        hours > 0 && minutes > 0 -> context.getString(R.string.duration_hours_minutes, hours, minutes)
        hours > 0 -> context.getString(R.string.duration_hours, hours)
        minutes > 0 -> context.getString(R.string.duration_minutes, minutes)
        else -> context.getString(R.string.duration_seconds, secs)
    }
}
