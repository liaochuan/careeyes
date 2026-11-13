package org.liaocl.careeyes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.liaocl.careeyes.data.Preset
import org.liaocl.careeyes.data.PresetValidationError
import org.liaocl.careeyes.data.validationError

class PresetValidationTest {
    @Test
    fun validPresetHasNoError() {
        val preset = Preset("custom", "Reading", 25, 30)
        assertNull(preset.validationError())
    }

    @Test
    fun blankNameIsRejected() {
        val preset = Preset("custom", "   ", 25, 30)
        assertEquals(PresetValidationError.NAME_BLANK, preset.validationError())
    }

    @Test
    fun tooLongNameIsRejected() {
        val preset = Preset("custom", "x".repeat(25), 25, 30)
        assertEquals(PresetValidationError.NAME_TOO_LONG, preset.validationError())
    }

    @Test
    fun workMinutesBoundariesAreRejected() {
        assertEquals(
            PresetValidationError.WORK_OUT_OF_RANGE,
            Preset("custom", "A", 0, 30).validationError(),
        )
        assertEquals(
            PresetValidationError.WORK_OUT_OF_RANGE,
            Preset("custom", "B", 181, 30).validationError(),
        )
    }

    @Test
    fun breakSecondsBoundariesAreRejected() {
        assertEquals(
            PresetValidationError.BREAK_OUT_OF_RANGE,
            Preset("custom", "A", 20, 4).validationError(),
        )
        assertEquals(
            PresetValidationError.BREAK_OUT_OF_RANGE,
            Preset("custom", "B", 20, 1801).validationError(),
        )
    }
}
