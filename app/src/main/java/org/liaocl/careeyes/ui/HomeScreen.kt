package org.liaocl.careeyes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BarChart
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.liaocl.careeyes.R
import org.liaocl.careeyes.data.AppSettings
import org.liaocl.careeyes.data.StatsData
import org.liaocl.careeyes.engine.RuntimeState
import org.liaocl.careeyes.engine.TimerMode
import org.liaocl.careeyes.ui.components.AppCard
import org.liaocl.careeyes.ui.components.ProgressRing
import org.liaocl.careeyes.ui.components.StatCard
import org.liaocl.careeyes.util.PermissionUtils
import org.liaocl.careeyes.util.TimeUtils
import org.liaocl.careeyes.util.durationText

@Composable
fun HomeScreen(
    runtime: RuntimeState,
    settings: AppSettings,
    stats: StatsData,
    now: Long,
    onStart: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onReset: () -> Unit,
    onRestNow: () -> Unit,
    onSkipBreak: () -> Unit,
    onSelectPreset: (String) -> Unit,
    onManagePresets: () -> Unit,
    onOpenStats: () -> Unit,
    onOpenSettings: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    val context = LocalContext.current
    var permissionVersion by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionVersion += 1 }

    val notificationGranted = remember(permissionVersion) { PermissionUtils.hasNotificationPermission(context) }
    val overlayGranted = remember(permissionVersion) { PermissionUtils.canDrawOverlays(context) }
    val batteryGranted = remember(permissionVersion) {
        PermissionUtils.isIgnoringBatteryOptimizations(context)
    }

    val remaining = when (runtime.mode) {
        TimerMode.FOCUS, TimerMode.BREAK -> runtime.remainingAt(now)
        TimerMode.PAUSED -> runtime.remainingMs
        TimerMode.IDLE -> settings.currentPreset().workMinutes * 60_000L
    }
    val progress = if (runtime.mode == TimerMode.IDLE) 0f else runtime.progressAt(now)
    val today = stats.day()
    val liveFocusSeconds = if (runtime.mode == TimerMode.FOCUS) {
        ((runtime.focusDurationMs - runtime.remainingAt(now)) / 1000L).coerceAtLeast(0L)
    } else {
        0L
    }
    val todayFocusSeconds = today.focusSeconds + liveFocusSeconds
    val streak = stats.streakDays()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = "20-20-20",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    IconButton(onClick = onOpenStats) {
                        Icon(Icons.Rounded.BarChart, contentDescription = stringResource(R.string.stats_title))
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.action_settings))
                    }
                }
            }

            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = statusLabel(runtime.mode),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    ProgressRing(progress = progress) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = TimeUtils.formatMmSs(remaining),
                                fontSize = 44.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground,
                                textAlign = TextAlign.Center,
                            )
                            Text(
                                text = if (runtime.presetName.isBlank()) {
                                    settings.currentPreset().name
                                } else {
                                    runtime.presetName
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            if (runtime.mode == TimerMode.PAUSED) {
                                Text(
                                    text = stringResource(R.string.home_status_paused),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(settings.presets, key = { it.id }) { preset ->
                            FilterChip(
                                selected = preset.id == settings.selectedPresetId,
                                onClick = { onSelectPreset(preset.id) },
                                label = { Text(preset.name) },
                            )
                        }
                        item {
                            AssistChip(
                                onClick = onManagePresets,
                                label = { Text(stringResource(R.string.action_manage)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.Edit,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                },
                            )
                        }
                    }
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = when (runtime.mode) {
                                TimerMode.FOCUS -> onPause
                                TimerMode.BREAK -> onSkipBreak
                                TimerMode.PAUSED -> onResume
                                TimerMode.IDLE -> onStart
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = when (runtime.mode) {
                                    TimerMode.FOCUS -> Icons.Rounded.Pause
                                    TimerMode.PAUSED -> Icons.Rounded.PlayArrow
                                    else -> Icons.Rounded.Timer
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = when (runtime.mode) {
                                    TimerMode.FOCUS -> stringResource(R.string.home_pause)
                                    TimerMode.BREAK -> stringResource(R.string.break_skip)
                                    TimerMode.PAUSED -> stringResource(R.string.home_resume)
                                    TimerMode.IDLE -> stringResource(R.string.home_start)
                                },
                            )
                        }
                        OutlinedButton(
                            onClick = if (runtime.mode == TimerMode.BREAK) onPause else onRestNow,
                            modifier = Modifier.weight(1f),
                        ) {
                            Icon(
                                imageVector = if (runtime.mode == TimerMode.BREAK) {
                                    Icons.Rounded.Pause
                                } else {
                                    Icons.Rounded.Visibility
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (runtime.mode == TimerMode.BREAK) {
                                    stringResource(R.string.home_pause)
                                } else {
                                    stringResource(R.string.home_rest_now)
                                },
                            )
                        }
                    }
                    if (runtime.mode != TimerMode.IDLE) {
                        TextButton(
                            onClick = onReset,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                        ) {
                            Icon(
                                Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.home_reset))
                        }
                    }
                }
            }

            item {
                Text(
                    text = stringResource(R.string.home_stats_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(
                        label = stringResource(R.string.home_today_focus),
                        value = durationText(context, todayFocusSeconds),
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = stringResource(R.string.home_today_rests),
                        value = today.restCount.toString(),
                        modifier = Modifier.weight(1f),
                    )
                    StatCard(
                        label = stringResource(R.string.home_streak),
                        value = pluralStringResource(R.plurals.stats_streak_value, streak, streak),
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            val missingPermissions = !notificationGranted || !overlayGranted || !batteryGranted
            if (missingPermissions) {
                item {
                    AppCard {
                        Text(
                            text = stringResource(R.string.home_permission_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        if (!notificationGranted) {
                            PermissionItem(
                                icon = Icons.Rounded.Notifications,
                                title = stringResource(R.string.home_permission_notification_title),
                                body = stringResource(R.string.home_permission_notification_body),
                                action = stringResource(R.string.action_grant),
                                onClick = onRequestNotifications,
                            )
                        }
                        if (!overlayGranted) {
                            PermissionItem(
                                icon = Icons.Rounded.Visibility,
                                title = stringResource(R.string.home_permission_overlay_title),
                                body = stringResource(R.string.home_permission_overlay_body),
                                action = stringResource(R.string.action_grant),
                                onClick = { PermissionUtils.openOverlaySettings(context) },
                            )
                        }
                        if (!batteryGranted) {
                            PermissionItem(
                                icon = Icons.Rounded.BatteryFull,
                                title = stringResource(R.string.home_permission_battery_title),
                                body = stringResource(R.string.home_permission_battery_body),
                                action = stringResource(R.string.action_grant),
                                onClick = { PermissionUtils.requestIgnoreBatteryOptimizations(context) },
                            )
                        }
                    }
                }
            }

            item {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.home_tip_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(R.string.home_tip_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun statusLabel(mode: TimerMode): String = when (mode) {
    TimerMode.FOCUS -> stringResource(R.string.home_status_focus)
    TimerMode.BREAK -> stringResource(R.string.home_status_break)
    TimerMode.PAUSED -> stringResource(R.string.home_status_paused)
    TimerMode.IDLE -> stringResource(R.string.home_status_idle)
}

@Composable
private fun PermissionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    body: String,
    action: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onClick) { Text(action) }
    }
}
