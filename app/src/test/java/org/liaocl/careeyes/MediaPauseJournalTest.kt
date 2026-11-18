package org.liaocl.careeyes

import org.junit.Assert.assertEquals
import org.junit.Test
import org.liaocl.careeyes.util.trimmedJournalLines

class MediaPauseJournalTest {
    @Test
    fun keepsShortJournalsUntouched() {
        val lines = listOf("a", "b", "c")
        assertEquals(lines, trimmedJournalLines(lines, 5))
    }

    @Test
    fun keepsTheNewestEntriesWhenTheJournalIsFull() {
        val lines = (1..10).map { "line-$it" }
        assertEquals(listOf("line-8", "line-9", "line-10"), trimmedJournalLines(lines, 3))
    }
}
