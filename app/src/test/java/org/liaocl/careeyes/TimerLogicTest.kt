package org.liaocl.careeyes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.liaocl.careeyes.engine.FrozenTimer
import org.liaocl.careeyes.engine.PausedMode
import org.liaocl.careeyes.engine.ScreenReturn
import org.liaocl.careeyes.engine.remainingMs
import org.liaocl.careeyes.engine.screenReturn

class TimerLogicTest {
    @Test
    fun remainingNeverGoesNegative() {
        assertEquals(0L, remainingMs(1_000L, 2_000L))
        assertEquals(500L, remainingMs(2_000L, 1_500L))
    }

    @Test
    fun exactBreakDurationResetsFocus() {
        val frozen = FrozenTimer(PausedMode.FOCUS, 120_000L)
        assertTrue(screenReturn(20_000L, 20_000L, frozen) is ScreenReturn.ResetFocus)
    }

    @Test
    fun shorterOffDurationRestoresRemaining() {
        val frozen = FrozenTimer(PausedMode.FOCUS, 120_000L)
        val result = screenReturn(19_999L, 20_000L, frozen)
        assertTrue(result is ScreenReturn.Restore)
        assertEquals(frozen, (result as ScreenReturn.Restore).frozen)
    }

    @Test
    fun breakPausedModeIsPreserved() {
        val frozen = FrozenTimer(PausedMode.BREAK, 8_000L)
        val result = screenReturn(3_000L, 20_000L, frozen)
        assertEquals(PausedMode.BREAK, (result as ScreenReturn.Restore).frozen.pausedMode)
    }
}
