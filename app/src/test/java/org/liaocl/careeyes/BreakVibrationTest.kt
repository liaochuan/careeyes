package org.liaocl.careeyes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.liaocl.careeyes.util.shouldVibrateOnBreakEnd

class BreakVibrationTest {
    @Test
    fun vibratesOnlyWhenEnabledAndAvailable() {
        assertTrue(shouldVibrateOnBreakEnd(enabled = true, hasVibrator = true))
        assertFalse(shouldVibrateOnBreakEnd(enabled = false, hasVibrator = true))
        assertFalse(shouldVibrateOnBreakEnd(enabled = true, hasVibrator = false))
        assertFalse(shouldVibrateOnBreakEnd(enabled = false, hasVibrator = false))
    }
}
