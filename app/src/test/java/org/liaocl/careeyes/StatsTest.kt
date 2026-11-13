package org.liaocl.careeyes

import org.junit.Assert.assertEquals
import org.junit.Test
import org.liaocl.careeyes.data.DailyStats
import org.liaocl.careeyes.data.StatsData
import java.time.LocalDate

class StatsTest {
    private val today = LocalDate.of(2026, 11, 13)

    @Test
    fun focusSecondsAccumulateForSameDay() {
        val stats = StatsData()
            .recordFocus(60, today)
            .recordFocus(90, today)
        assertEquals(150L, stats.day(today).focusSeconds)
        assertEquals(1, stats.days.size)
    }

    @Test
    fun restCountIgnoresLegacyNaturalBreaks() {
        val day = DailyStats(
            date = today.toString(),
            breaksCompleted = 2,
            naturalBreaks = 5,
        )
        assertEquals(2, day.restCount)
    }

    @Test
    fun breakKindsAreCountedSeparately() {
        val stats = StatsData()
            .recordBreak(completed = true, date = today)
            .recordBreak(completed = false, date = today)
        val day = stats.day(today)
        assertEquals(1, day.breaksCompleted)
        assertEquals(1, day.breaksSkipped)
        assertEquals(1, day.restCount)
    }

    @Test
    fun weeklyFillsMissingDaysAndOrdersAscending() {
        val stats = StatsData()
            .recordFocus(120, today.minusDays(2))
            .recordFocus(60, today)
        val week = stats.weekly(today)
        assertEquals(7, week.size)
        assertEquals(today.minusDays(6).toString(), week.first().date)
        assertEquals(today.toString(), week.last().date)
        assertEquals(120L, week[4].focusSeconds)
        assertEquals(60L, week[6].focusSeconds)
        assertEquals(0L, week[0].focusSeconds)
    }

    @Test
    fun streakStartsFromTodayWhenTodayHasRest() {
        val stats = StatsData()
            .recordBreak(completed = true, date = today)
            .recordBreak(completed = true, date = today.minusDays(1))
            .recordBreak(completed = true, date = today.minusDays(2))
        assertEquals(3, stats.streakDays(today))
    }

    @Test
    fun streakStartsFromYesterdayWhenTodayHasNoRest() {
        val stats = StatsData()
            .recordBreak(completed = true, date = today.minusDays(1))
            .recordBreak(completed = true, date = today.minusDays(2))
        assertEquals(2, stats.streakDays(today))
    }

    @Test
    fun statsAreTrimmedToMaxDays() {
        var stats = StatsData()
        repeat(400) { offset ->
            stats = stats.recordFocus(1, today.minusDays(offset.toLong()))
        }
        assertEquals(365, stats.days.size)
        assertEquals(today.toString(), stats.days.last().date)
    }
}
