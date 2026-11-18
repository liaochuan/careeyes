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

const val MIN_TIMED_PAUSE_MINUTES = 1
const val MAX_TIMED_PAUSE_MINUTES = 24 * 60
const val DEFAULT_TIMED_PAUSE_MINUTES = 30

/** Parses the settings input for the timed pause length; null means the value is not usable. */
fun parseTimedPauseMinutes(raw: String): Int? =
    raw.trim().toIntOrNull()?.takeIf { it in MIN_TIMED_PAUSE_MINUTES..MAX_TIMED_PAUSE_MINUTES }

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
    val showProgressNotification: Boolean = false,
    val showNotificationActions: Boolean = false,
    val hideFromRecents: Boolean = false,
    val pauseVideoDuringBreak: Boolean = false,
    val pauseAppPackages: Set<String> = DEFAULT_PAUSE_APP_PACKAGES,
    val mediaPauseDebugLog: Boolean = false,
    val autoStartOnBoot: Boolean = true,
    val themeId: String = "jade",
    val customSeedArgb: Long? = null,
    val darkMode: String = "system",
    val pausedRemainingMs: Long = 0L,
    val pausedMode: String = "FOCUS",
    /** Wall-clock time when a timed pause resumes; 0 means the pause lasts until resumed by hand. */
    val pausedUntilEpochMs: Long = 0L,
    val timedPauseMinutes: Int = DEFAULT_TIMED_PAUSE_MINUTES,
    /** Packages whose media CareEyes paused for the current break, so it can resume them. */
    val pausedMediaPackages: Set<String> = emptySet(),
    val onboardingCompleted: Boolean = false,
    val dismissedPermissionKeys: Set<String> = emptySet(),
) {
    fun normalized(): AppSettings {
        val safePresets = presets.filter { it.validationError() == null }
            .ifEmpty { DEFAULT_PRESETS }
        val selected = safePresets.firstOrNull { it.id == selectedPresetId } ?: safePresets.first()
        return copy(
            presets = safePresets,
            selectedPresetId = selected.id,
            pausedUntilEpochMs = pausedUntilEpochMs.coerceAtLeast(0L),
            timedPauseMinutes = timedPauseMinutes
                .coerceIn(MIN_TIMED_PAUSE_MINUTES, MAX_TIMED_PAUSE_MINUTES),
        )
    }

    fun currentPreset(): Preset =
        presets.firstOrNull { it.id == selectedPresetId } ?: presets.firstOrNull() ?: DEFAULT_PRESETS.first()
}

/**
 * The service is also needed after a reboot when a timed pause is still pending, so it can
 * resume on time; an indefinite pause stays silent until the user starts the app.
 */
fun shouldRestoreAfterBoot(settings: AppSettings): Boolean =
    settings.autoStartOnBoot && (settings.running || settings.pausedUntilEpochMs > 0L)

@Serializable
data class DailyStats(
    val date: String,
    val focusSeconds: Long = 0L,
    val breaksCompleted: Int = 0,
    val breaksSkipped: Int = 0,
    /**
     * Legacy: screen-off periods were once counted here. Kept so previously stored stats still
     * decode, but the value is never displayed, incremented, or counted as rest.
     */
    val naturalBreaks: Int = 0,
) {
    /** Only explicit breaks count as rest; a screen-off reset is not a rest. */
    val restCount: Int get() = breaksCompleted
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
        date: LocalDate = LocalDate.now(),
    ): StatsData = updatedDay(date.toString()) { day ->
        if (completed) {
            day.copy(breaksCompleted = day.breaksCompleted + 1)
        } else {
            day.copy(breaksSkipped = day.breaksSkipped + 1)
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

    fun totalRestCount(): Int = days.sumOf { it.restCount }
}

const val MAX_STATS_DAYS = 365
