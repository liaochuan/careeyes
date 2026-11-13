package org.liaocl.careeyes.engine

enum class TimerMode { IDLE, FOCUS, PAUSED, BREAK }

enum class PausedMode { FOCUS, BREAK }

data class FrozenTimer(
    val pausedMode: PausedMode,
    val remainingMs: Long,
)

sealed interface ScreenReturn {
    data object ResetFocus : ScreenReturn
    data class Restore(val frozen: FrozenTimer) : ScreenReturn
}

fun remainingMs(deadlineElapsedMs: Long, nowElapsedMs: Long): Long =
    (deadlineElapsedMs - nowElapsedMs).coerceAtLeast(0L)

/**
 * Decides what to do when the screen comes back on.
 *
 * If the screen stayed off for at least one full break, the user has rested
 * enough and a fresh focus cycle starts. Otherwise the frozen state resumes.
 */
fun screenReturn(
    offDurationMs: Long,
    breakDurationMs: Long,
    frozen: FrozenTimer,
): ScreenReturn {
    val threshold = breakDurationMs.coerceAtLeast(1L)
    return if (offDurationMs >= threshold) ScreenReturn.ResetFocus else ScreenReturn.Restore(frozen)
}
