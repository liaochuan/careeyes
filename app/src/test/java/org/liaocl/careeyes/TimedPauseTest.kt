package org.liaocl.careeyes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.liaocl.careeyes.data.AppSettings
import org.liaocl.careeyes.data.parseTimedPauseMinutes
import org.liaocl.careeyes.data.shouldRestoreAfterBoot
import org.liaocl.careeyes.engine.timedPauseExpired

class TimedPauseTest {
    @Test
    fun parseAcceptsMinutesInRange() {
        assertEquals(30, parseTimedPauseMinutes("30"))
        assertEquals(1, parseTimedPauseMinutes("1"))
        assertEquals(1440, parseTimedPauseMinutes("1440"))
    }

    @Test
    fun parseRejectsBlankOutOfRangeAndJunk() {
        assertNull(parseTimedPauseMinutes(""))
        assertNull(parseTimedPauseMinutes("0"))
        assertNull(parseTimedPauseMinutes("1441"))
        assertNull(parseTimedPauseMinutes("-5"))
        assertNull(parseTimedPauseMinutes("abc"))
    }

    @Test
    fun indefinitePauseNeverExpires() {
        assertFalse(timedPauseExpired(0L, 1_000L))
    }

    @Test
    fun futurePauseIsNotExpired() {
        assertFalse(timedPauseExpired(2_000L, 1_000L))
    }

    @Test
    fun pauseIsExpiredAtAndAfterItsDeadline() {
        assertTrue(timedPauseExpired(1_000L, 1_000L))
        assertTrue(timedPauseExpired(1_000L, 2_000L))
    }

    @Test
    fun bootRestoresRunningTimer() {
        assertTrue(shouldRestoreAfterBoot(AppSettings(autoStartOnBoot = true, running = true)))
    }

    @Test
    fun bootRestoresPendingTimedPause() {
        assertTrue(
            shouldRestoreAfterBoot(
                AppSettings(autoStartOnBoot = true, running = false, pausedUntilEpochMs = 5_000L),
            ),
        )
    }

    @Test
    fun bootSkipsIndefinitePauseAndDisabledAutostart() {
        assertFalse(
            shouldRestoreAfterBoot(
                AppSettings(autoStartOnBoot = true, running = false, pausedUntilEpochMs = 0L),
            ),
        )
        assertFalse(shouldRestoreAfterBoot(AppSettings(autoStartOnBoot = false, running = true)))
    }

    @Test
    fun normalizedClampsTimedPauseAndDeadline() {
        assertEquals(1, AppSettings(timedPauseMinutes = 0).normalized().timedPauseMinutes)
        assertEquals(1440, AppSettings(timedPauseMinutes = 99_999).normalized().timedPauseMinutes)
        assertEquals(0L, AppSettings(pausedUntilEpochMs = -5L).normalized().pausedUntilEpochMs)
    }

    @Test
    fun parseTrimsSurroundingSpaces() {
        assertEquals(30, parseTimedPauseMinutes(" 30 "))
    }
}
