package org.liaocl.careeyes.engine

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.liaocl.careeyes.data.Preset
import java.util.concurrent.TimeUnit

data class RuntimeState(
    val mode: TimerMode = TimerMode.IDLE,
    val presetId: String = "",
    val presetName: String = "",
    val focusDurationMs: Long = 0L,
    val breakDurationMs: Long = 0L,
    val deadlineElapsedMs: Long = 0L,
    val remainingMs: Long = 0L,
    val pausedMode: PausedMode = PausedMode.FOCUS,
) {
    val isRunning: Boolean get() = mode == TimerMode.FOCUS || mode == TimerMode.BREAK

    fun remainingAt(nowElapsedMs: Long): Long = when (mode) {
        TimerMode.FOCUS, TimerMode.BREAK -> remainingMs(deadlineElapsedMs, nowElapsedMs)
        TimerMode.PAUSED -> remainingMs
        TimerMode.IDLE -> 0L
    }

    fun progressAt(nowElapsedMs: Long): Float {
        val duration = when (mode) {
            TimerMode.FOCUS -> focusDurationMs
            TimerMode.BREAK -> breakDurationMs
            TimerMode.PAUSED -> if (pausedMode == PausedMode.FOCUS) focusDurationMs else breakDurationMs
            TimerMode.IDLE -> 0L
        }
        if (duration <= 0L) return 0f
        val remaining = remainingAt(nowElapsedMs)
        return ((duration - remaining).toDouble() / duration.toDouble()).toFloat().coerceIn(0f, 1f)
    }
}

class EyeCareEngine(
    private val clock: () -> Long = { SystemClock.elapsedRealtime() },
) {
    private val _state = MutableStateFlow(RuntimeState())
    val state: StateFlow<RuntimeState> = _state.asStateFlow()

    private var preset: Preset? = null

    fun prepare(
        preset: Preset,
        running: Boolean,
        savedRemainingMs: Long = 0L,
        savedPausedMode: PausedMode = PausedMode.FOCUS,
    ) {
        this.preset = preset
        if (running) {
            startFocus()
            return
        }
        val maxRemaining = if (savedPausedMode == PausedMode.FOCUS) {
            workDurationMs(preset)
        } else {
            breakDurationMs(preset)
        }
        val remaining = savedRemainingMs.coerceIn(0L, maxRemaining).takeIf { it > 0L } ?: maxRemaining
        _state.value = RuntimeState(
            mode = TimerMode.PAUSED,
            presetId = preset.id,
            presetName = preset.name,
            focusDurationMs = workDurationMs(preset),
            breakDurationMs = breakDurationMs(preset),
            remainingMs = remaining,
            pausedMode = savedPausedMode,
        )
    }

    fun applyPreset(preset: Preset, startImmediately: Boolean = true) {
        this.preset = preset
        if (startImmediately) {
            startFocus()
        } else {
            prepare(preset, running = false)
        }
    }

    fun startFocus() {
        val current = preset ?: return
        val work = workDurationMs(current)
        _state.value = RuntimeState(
            mode = TimerMode.FOCUS,
            presetId = current.id,
            presetName = current.name,
            focusDurationMs = work,
            breakDurationMs = breakDurationMs(current),
            deadlineElapsedMs = clock() + work,
            remainingMs = work,
        )
    }

    fun startBreak() {
        val current = preset ?: return
        val state = _state.value
        if (state.mode == TimerMode.BREAK) return
        val duration = breakDurationMs(current)
        _state.value = state.copy(
            mode = TimerMode.BREAK,
            presetId = current.id,
            presetName = current.name,
            focusDurationMs = workDurationMs(current),
            breakDurationMs = duration,
            deadlineElapsedMs = clock() + duration,
            remainingMs = duration,
            pausedMode = PausedMode.BREAK,
        )
    }

    fun pause(): FrozenTimer? {
        val state = _state.value
        if (state.mode != TimerMode.FOCUS && state.mode != TimerMode.BREAK) return null
        val pausedMode = if (state.mode == TimerMode.FOCUS) PausedMode.FOCUS else PausedMode.BREAK
        val remaining = state.remainingAt(clock())
        val frozen = FrozenTimer(pausedMode, remaining)
        _state.value = state.copy(
            mode = TimerMode.PAUSED,
            deadlineElapsedMs = 0L,
            remainingMs = remaining,
            pausedMode = pausedMode,
        )
        return frozen
    }

    fun resume() {
        val state = _state.value
        if (state.mode != TimerMode.PAUSED || state.remainingMs <= 0L) return
        _state.value = state.copy(
            mode = if (state.pausedMode == PausedMode.FOCUS) TimerMode.FOCUS else TimerMode.BREAK,
            deadlineElapsedMs = clock() + state.remainingMs,
        )
    }

    fun restore(frozen: FrozenTimer) {
        val state = _state.value
        _state.value = state.copy(
            mode = if (frozen.pausedMode == PausedMode.FOCUS) TimerMode.FOCUS else TimerMode.BREAK,
            deadlineElapsedMs = clock() + frozen.remainingMs,
            remainingMs = frozen.remainingMs,
            pausedMode = frozen.pausedMode,
        )
    }

    fun reset() = startFocus()

    fun completeBreak() = startFocus()

    fun skipBreak() = startFocus()

    fun snapshot(now: Long = clock()): RuntimeState =
        _state.value.copy(remainingMs = _state.value.remainingAt(now))

    private fun workDurationMs(preset: Preset): Long =
        TimeUnit.MINUTES.toMillis(preset.workMinutes.toLong())

    private fun breakDurationMs(preset: Preset): Long =
        TimeUnit.SECONDS.toMillis(preset.breakSeconds.toLong())
}
