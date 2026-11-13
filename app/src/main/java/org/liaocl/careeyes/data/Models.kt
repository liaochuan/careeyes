package org.liaocl.careeyes.data

import kotlinx.serialization.Serializable
import java.time.LocalDate

@Serializable
data class Preset(
    val id: String,
    val name: String,
    val workMinutes: Int,
    val breakSeconds: Int,
)

val DEFAULT_PRESETS: List<Preset> = listOf(
    Preset(id = "20-20-20", name = "20-20-20", workMinutes = 20, breakSeconds = 20),
    Preset(id = "45-5", name = "45-5", workMinutes = 45, breakSeconds = 300),
    Preset(id = "60-5", name = "60-5", workMinutes = 60, breakSeconds = 300),
)

const val MIN_WORK_MINUTES = 1
const val MAX_WORK_MINUTES = 180
const val MIN_BREAK_SECONDS = 5
const val MAX_BREAK_SECONDS = 1800
const val MAX_PRESET_NAME_LENGTH = 24

enum class PresetValidationError {
    NAME_BLANK,
    NAME_TOO_LONG,
    WORK_OUT_OF_RANGE,
    BREAK_OUT_OF_RANGE,
}

fun Preset.validationError(): PresetValidationError? = when {
    name.isBlank() -> PresetValidationError.NAME_BLANK
    name.trim().length > MAX_PRESET_NAME_LENGTH -> PresetValidationError.NAME_TOO_LONG
    workMinutes !in MIN_WORK_MINUTES..MAX_WORK_MINUTES -> PresetValidationError.WORK_OUT_OF_RANGE
    breakSeconds !in MIN_BREAK_SECONDS..MAX_BREAK_SECONDS -> PresetValidationError.BREAK_OUT_OF_RANGE
    else -> null
}

@Serializable
data class AppSettings(
    val running: Boolean = true,
    val selectedPresetId: String = DEFAULT_PRESETS.first().id,
    val presets: List<Preset> = DEFAULT_PRESETS,
    val showProgressNotification: Boolean = true,
    val hideFromRecents: Boolean = true,
    val pauseVideoDuringBreak: Boolean = false,
    val autoStartOnBoot: Boolean = true,
    val themeId: String = "jade",
    val customSeedArgb: Long? = null,
    val darkMode: String = "system",
    val pausedRemainingMs: Long = 0L,
    val pausedMode: String = "FOCUS",
    val notificationPermissionAsked: Boolean = false,
) {
    fun normalized(): AppSettings {
        val safePresets = presets.filter { it.validationError() == null }
            .ifEmpty { DEFAULT_PRESETS }
        val selected = safePresets.firstOrNull { it.id == selectedPresetId } ?: safePresets.first()
        return copy(presets = safePresets, selectedPresetId = selected.id)
    }

    fun currentPreset(): Preset =
        presets.firstOrNull { it.id == selectedPresetId } ?: presets.firstOrNull() ?: DEFAULT_PRESETS.first()
}

@Serializable
data class DailyStats(
    val date: String,
    val focusSeconds: Long = 0L,
    val breaksCompleted: Int = 0,
    val breaksSkipped: Int = 0,
    val naturalBreaks: Int = 0,
) {
    val restCount: Int get() = breaksCompleted + naturalBreaks
}

@Serializable
data class StatsData(
    val days: List<DailyStats> = emptyList(),
) {
    fun day(date: LocalDate = LocalDate.now()): DailyStats =
        days.firstOrNull { it.date == date.toString() } ?: DailyStats(date.toString())

    fun updatedDay(date: String, transform: (DailyStats) -> DailyStats): StatsData {
        val current = days.firstOrNull { it.date == date } ?: DailyStats(date)
        val next = transform(current)
        val merged = (days.filterNot { it.date == date } + next).sortedBy { it.date }
        return copy(days = merged.takeLast(MAX_STATS_DAYS))
    }

    fun recordFocus(seconds: Long, date: LocalDate = LocalDate.now()): StatsData {
        if (seconds <= 0L) return this
        return updatedDay(date.toString()) { it.copy(focusSeconds = it.focusSeconds + seconds) }
    }

    fun recordBreak(
        completed: Boolean,
        natural: Boolean = false,
        date: LocalDate = LocalDate.now(),
    ): StatsData = updatedDay(date.toString()) { day ->
        when {
            natural -> day.copy(naturalBreaks = day.naturalBreaks + 1)
            completed -> day.copy(breaksCompleted = day.breaksCompleted + 1)
            else -> day.copy(breaksSkipped = day.breaksSkipped + 1)
        }
    }

    fun weekly(today: LocalDate = LocalDate.now()): List<DailyStats> {
        val byDate = days.associateBy { it.date }
        return (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            byDate[date.toString()] ?: DailyStats(date.toString())
        }
    }

    fun streakDays(today: LocalDate = LocalDate.now()): Int {
        val restDates = days.filter { it.restCount > 0 }.map { it.date }.toHashSet()
        var cursor = if (restDates.contains(today.toString())) today else today.minusDays(1)
        var streak = 0
        while (restDates.contains(cursor.toString())) {
            streak += 1
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    fun totalFocusSeconds(): Long = days.sumOf { it.focusSeconds }

    fun totalCompletedBreaks(): Int = days.sumOf { it.breaksCompleted }

    fun totalNaturalBreaks(): Int = days.sumOf { it.naturalBreaks }

    fun totalRestCount(): Int = days.sumOf { it.restCount }
}

const val MAX_STATS_DAYS = 365
