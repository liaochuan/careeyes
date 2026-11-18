package org.liaocl.careeyes.util

import android.content.Context
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * On-device copy of the media pause diagnostics.
 *
 * Logcat is a rolling system buffer that needs a computer to read, so while diagnostics are on
 * the same lines are also appended here and can be shared straight from the settings page.
 * Nothing is uploaded; the file only leaves the app when the user picks a share target.
 */
object MediaPauseJournal {
    private const val DIR = "logs"
    private const val FILE_NAME = "media-pause.log"
    private const val MAX_LINES = 2000

    private val formatter: DateTimeFormatter = DateTimeFormatter
        .ofPattern("MM-dd HH:mm:ss.SSS")
        .withZone(ZoneId.systemDefault())

    fun logFile(context: Context): File = File(File(context.filesDir, DIR), FILE_NAME)

    @Synchronized
    fun append(context: Context, message: String, now: Long = System.currentTimeMillis()) {
        runCatching {
            val file = logFile(context)
            file.parentFile?.mkdirs()
            file.appendText("${formatter.format(Instant.ofEpochMilli(now))} $message\n")
            val lines = file.readLines()
            if (lines.size > MAX_LINES) {
                file.writeText(trimmedJournalLines(lines, MAX_LINES).joinToString("\n", postfix = "\n"))
            }
        }
    }

    @Synchronized
    fun read(context: Context): String = runCatching {
        val file = logFile(context)
        if (file.exists()) file.readText() else ""
    }.getOrDefault("")

    @Synchronized
    fun clear(context: Context) {
        runCatching { logFile(context).delete() }
    }
}

/** Keeps the newest [maxLines] entries so the journal cannot grow without a bound. */
internal fun trimmedJournalLines(lines: List<String>, maxLines: Int): List<String> =
    if (lines.size <= maxLines) lines else lines.takeLast(maxLines)
