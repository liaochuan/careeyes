package org.liaocl.careeyes.service

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context

/**
 * Best-effort "which app is on screen right now" lookup. Requires the Usage access special
 * permission; without it the query returns nothing and callers must not fall back to pausing
 * unrelated media.
 */
internal object ForegroundApp {
    private const val LOOKBACK_MS = 12L * 60L * 60L * 1000L

    fun packageName(context: Context, now: Long = System.currentTimeMillis()): String? {
        val manager = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val events = runCatching { manager.queryEvents(now - LOOKBACK_MS, now) }.getOrNull() ?: return null
        val event = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.ACTIVITY_RESUMED) {
                last = event.packageName
            }
        }
        return last
    }
}
