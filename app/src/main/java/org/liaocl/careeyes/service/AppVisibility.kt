package org.liaocl.careeyes.service

/** Tracks whether any CareEyes screen is currently visible. */
object AppVisibility {
    @Volatile
    var isVisible: Boolean = false
        private set

    private var visibleCount: Int = 0

    @Synchronized
    fun onScreenStarted() {
        visibleCount += 1
        isVisible = visibleCount > 0
    }

    @Synchronized
    fun onScreenStopped() {
        visibleCount = (visibleCount - 1).coerceAtLeast(0)
        isVisible = visibleCount > 0
    }
}
