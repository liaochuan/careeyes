package org.liaocl.careeyes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.liaocl.careeyes.R
import org.liaocl.careeyes.data.DailyStats
import org.liaocl.careeyes.data.StatsData
import org.liaocl.careeyes.ui.components.AppCard
import org.liaocl.careeyes.ui.components.ScreenScaffold
import org.liaocl.careeyes.ui.components.SectionHeader
import org.liaocl.careeyes.ui.components.StatCard
import org.liaocl.careeyes.util.durationText
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun StatsScreen(
    stats: StatsData,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val today = stats.day()
    val week = stats.weekly()
    val streak = stats.streakDays()

    ScreenScaffold(title = stringResource(R.string.stats_title), onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                StatCard(
                    label = stringResource(R.string.stats_focus_time),
                    value = durationText(context, today.focusSeconds),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(
                        label = stringResource(R.string.stats_completed),
                        value = today.breaksCompleted.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = stringResource(R.string.stats_skipped),
                        value = today.breaksSkipped.toString(),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            if (stats.days.isEmpty()) {
                item {
                    AppCard {
                        Text(
                            text = stringResource(R.string.stats_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                item {
                    SectionHeader(stringResource(R.string.stats_trend_7d))
                }
                item {
                    AppCard {
                        WeeklyBars(week)
                    }
                }
                item {
                    SectionHeader(stringResource(R.string.stats_recent_7d))
                }
                item {
                    AppCard {
                        week.asReversed().forEachIndexed { index, day ->
                            RecentDayRow(day)
                            if (index != week.lastIndex) {
                                Spacer(Modifier.height(10.dp))
                            }
                        }
                    }
                }
            }

            item {
                SectionHeader(stringResource(R.string.stats_total))
            }
            item {
                AppCard {
                    TotalRow(
                        label = stringResource(R.string.stats_total_focus),
                        value = durationText(context, stats.totalFocusSeconds()),
                    )
                    Spacer(Modifier.height(10.dp))
                    TotalRow(
                        label = stringResource(R.string.stats_total_rests),
                        value = stats.totalRestCount().toString(),
                    )
                    Spacer(Modifier.height(10.dp))
                    TotalRow(
                        label = stringResource(R.string.stats_streak),
                        value = pluralStringResource(R.plurals.stats_streak_value, streak, streak),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeeklyBars(week: List<DailyStats>) {
    val maxSeconds = week.maxOfOrNull { it.focusSeconds }?.coerceAtLeast(60L) ?: 60L
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(148.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        week.forEach { day ->
            val fraction = (day.focusSeconds.toFloat() / maxSeconds.toFloat()).coerceIn(0f, 1f)
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Box(
                    modifier = Modifier
                        .height((fraction * 104f).coerceAtLeast(3f).dp)
                        .width(20.dp)
                        .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                        .background(
                            if (fraction > 0f) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = weekdayLabel(day.date),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RecentDayRow(day: DailyStats) {
    val context = LocalContext.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = dateLabel(day.date),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.width(56.dp),
        )
        Text(
            text = durationText(context, day.focusSeconds),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${day.restCount}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun TotalRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun weekdayLabel(date: String): String = runCatching {
    LocalDate.parse(date).format(DateTimeFormatter.ofPattern("E", Locale.getDefault()))
}.getOrDefault(date)

private fun dateLabel(date: String): String = runCatching {
    LocalDate.parse(date).format(DateTimeFormatter.ofPattern("MM/dd", Locale.US))
}.getOrDefault(date)
