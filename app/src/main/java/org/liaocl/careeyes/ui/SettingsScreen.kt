package org.liaocl.careeyes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.liaocl.careeyes.BuildConfig
import org.liaocl.careeyes.R
import org.liaocl.careeyes.data.AppSettings
import org.liaocl.careeyes.data.parseTimedPauseMinutes
import org.liaocl.careeyes.ui.components.AppCard
import org.liaocl.careeyes.ui.components.LinkRow
import org.liaocl.careeyes.ui.components.NavRow
import org.liaocl.careeyes.ui.components.ScreenScaffold
import org.liaocl.careeyes.ui.components.SectionHeader
import org.liaocl.careeyes.ui.components.SwitchRow
import org.liaocl.careeyes.ui.theme.BUILT_IN_PALETTES
import org.liaocl.careeyes.ui.theme.ColorPickerDialog
import org.liaocl.careeyes.ui.theme.paletteSeed
import org.liaocl.careeyes.util.PermissionSnapshot
import org.liaocl.careeyes.util.PermissionUtils
import org.liaocl.careeyes.util.installedLaunchableApps

@Composable
fun SettingsScreen(
    settings: AppSettings,
    permissionSnapshot: PermissionSnapshot,
    onBack: () -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onRequestNotifications: () -> Unit,
    onOpenPermissionGuide: () -> Unit,
    onOpenPauseApps: () -> Unit,
    onExportMediaLog: () -> Unit,
) {
    val context = LocalContext.current
    var showColorPicker by remember { mutableStateOf(false) }
    var showPauseDurationDialog by remember { mutableStateOf(false) }
    var pauseDurationInput by remember { mutableStateOf("") }

    val notificationGranted = permissionSnapshot.notifications
    val overlayGranted = permissionSnapshot.overlay
    val batteryGranted = permissionSnapshot.battery
    val mediaAccessGranted = permissionSnapshot.mediaAccess
    val usageAccessGranted = permissionSnapshot.usageAccess
    val fullScreenIntentGranted = permissionSnapshot.fullScreenIntent
    val customColor = remember(settings.themeId, settings.customSeedArgb) {
        settings.customSeedArgb?.let { Color(it.toInt()) } ?: paletteSeed(settings.themeId, null)
    }
    // Count only apps the user can actually see and toggle on the "apps to pause" page.
    val enabledPauseAppCount = remember(settings.pauseAppPackages) {
        val installed = installedLaunchableApps(context).mapTo(HashSet()) { it.packageName }
        settings.pauseAppPackages.count { it in installed }
    }

    ScreenScaffold(title = stringResource(R.string.settings_title), onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { SectionHeader(stringResource(R.string.settings_section_eye)) }
            item {
                AppCard {
                    SwitchRow(
                        title = stringResource(R.string.settings_progress_notification),
                        description = stringResource(R.string.settings_progress_notification_desc),
                        checked = settings.showProgressNotification,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(showProgressNotification = checked) }
                        },
                    )
                    SwitchRow(
                        title = stringResource(R.string.settings_notification_actions),
                        description = stringResource(R.string.settings_notification_actions_desc),
                        checked = settings.showNotificationActions,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(showNotificationActions = checked) }
                        },
                    )
                    SwitchRow(
                        title = stringResource(R.string.settings_pause_video),
                        description = stringResource(R.string.settings_pause_video_desc),
                        checked = settings.pauseVideoDuringBreak &&
                            mediaAccessGranted &&
                            usageAccessGranted,
                        enabled = mediaAccessGranted && usageAccessGranted,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(pauseVideoDuringBreak = checked) }
                        },
                    )
                    NavRow(
                        title = stringResource(R.string.settings_pause_apps_title),
                        description = stringResource(
                            R.string.settings_pause_apps_count,
                            enabledPauseAppCount,
                        ),
                        onClick = onOpenPauseApps,
                    )
                    SettingsValueRow(
                        title = stringResource(R.string.settings_timed_pause_duration),
                        description = stringResource(R.string.settings_timed_pause_duration_desc),
                        actionLabel = stringResource(
                            R.string.duration_minutes,
                            settings.timedPauseMinutes,
                        ),
                        onClick = {
                            pauseDurationInput = settings.timedPauseMinutes.toString()
                            showPauseDurationDialog = true
                        },
                    )
                    SwitchRow(
                        title = stringResource(R.string.settings_break_vibration),
                        description = stringResource(R.string.settings_break_vibration_desc),
                        checked = settings.vibrateWhenBreakEnds,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(vibrateWhenBreakEnds = checked) }
                        },
                    )
                    SwitchRow(
                        title = stringResource(R.string.settings_autostart),
                        description = stringResource(R.string.settings_autostart_desc),
                        checked = settings.autoStartOnBoot,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(autoStartOnBoot = checked) }
                        },
                    )
                    SwitchRow(
                        title = stringResource(R.string.settings_hide_recents),
                        description = stringResource(R.string.settings_hide_recents_desc),
                        checked = settings.hideFromRecents,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(hideFromRecents = checked) }
                        },
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.settings_section_permissions)) }
            item {
                AppCard {
                    LinkRow(
                        title = stringResource(R.string.permission_notifications_title),
                        description = stringResource(R.string.permission_notifications_desc),
                        status = grantStatus(notificationGranted),
                        statusOk = notificationGranted,
                        onClick = {
                            if (notificationGranted) {
                                PermissionUtils.openNotificationSettings(context)
                            } else {
                                onRequestNotifications()
                            }
                        },
                    )
                    LinkRow(
                        title = stringResource(R.string.permission_overlay_title),
                        description = stringResource(R.string.permission_overlay_desc),
                        status = grantStatus(overlayGranted),
                        statusOk = overlayGranted,
                        onClick = { PermissionUtils.openOverlaySettings(context) },
                    )
                    LinkRow(
                        title = stringResource(R.string.permission_battery_title),
                        description = stringResource(R.string.permission_battery_desc),
                        status = grantStatus(batteryGranted),
                        statusOk = batteryGranted,
                        onClick = { PermissionUtils.requestIgnoreBatteryOptimizations(context) },
                    )
                    LinkRow(
                        title = stringResource(R.string.permission_media_title),
                        description = stringResource(R.string.permission_media_desc),
                        status = grantStatus(mediaAccessGranted),
                        statusOk = mediaAccessGranted,
                        onClick = { PermissionUtils.openNotificationAccessSettings(context) },
                    )
                    LinkRow(
                        title = stringResource(R.string.permission_usage_title),
                        description = stringResource(R.string.permission_usage_desc),
                        status = grantStatus(usageAccessGranted),
                        statusOk = usageAccessGranted,
                        onClick = { PermissionUtils.openUsageAccessSettings(context) },
                    )
                    LinkRow(
                        title = stringResource(R.string.settings_permission_fullscreen),
                        description = stringResource(R.string.settings_permission_fullscreen_desc),
                        status = grantStatus(fullScreenIntentGranted),
                        statusOk = fullScreenIntentGranted,
                        onClick = { PermissionUtils.openFullScreenIntentSettings(context) },
                    )
                }
            }
            item {
                OutlinedButton(
                    onClick = onOpenPermissionGuide,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.settings_open_permission_guide))
                }
            }

            item { SectionHeader(stringResource(R.string.settings_section_appearance)) }
            item {
                AppCard {
                    Text(
                        text = stringResource(R.string.settings_theme),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        items(BUILT_IN_PALETTES, key = { it.id }) { palette ->
                            PaletteItem(
                                label = paletteLabel(palette.id),
                                color = palette.seed,
                                selected = settings.themeId == palette.id,
                                onClick = {
                                    onUpdateSettings { it.copy(themeId = palette.id) }
                                },
                            )
                        }
                        item {
                            PaletteItem(
                                label = stringResource(R.string.theme_custom),
                                color = customColor,
                                selected = settings.themeId == "custom",
                                onClick = { showColorPicker = true },
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.medium)
                            .clickable { showColorPicker = true }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(customColor),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_custom_color),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                text = stringResource(R.string.settings_custom_color_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text(
                            text = stringResource(R.string.action_edit),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }

                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.settings_dark_mode),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        DarkModeChip(
                            label = stringResource(R.string.settings_dark_system),
                            selected = settings.darkMode == "system",
                            onClick = { onUpdateSettings { it.copy(darkMode = "system") } },
                        )
                        DarkModeChip(
                            label = stringResource(R.string.settings_dark_light),
                            selected = settings.darkMode == "light",
                            onClick = { onUpdateSettings { it.copy(darkMode = "light") } },
                        )
                        DarkModeChip(
                            label = stringResource(R.string.settings_dark_dark),
                            selected = settings.darkMode == "dark",
                            onClick = { onUpdateSettings { it.copy(darkMode = "dark") } },
                        )
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.settings_section_diagnostics)) }
            item {
                AppCard {
                    SwitchRow(
                        title = stringResource(R.string.settings_media_debug_log),
                        description = stringResource(R.string.settings_media_debug_log_desc),
                        checked = settings.mediaPauseDebugLog,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(mediaPauseDebugLog = checked) }
                        },
                    )
                    NavRow(
                        title = stringResource(R.string.settings_export_media_log),
                        description = stringResource(R.string.settings_export_media_log_desc),
                        actionLabel = stringResource(R.string.action_export),
                        onClick = onExportMediaLog,
                    )
                }
            }

            item { SectionHeader(stringResource(R.string.settings_section_about)) }
            item {
                AppCard {
                    Text(
                        text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.settings_privacy),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Text(
                        text = stringResource(R.string.settings_privacy_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.settings_language_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            initial = customColor,
            onDismiss = { showColorPicker = false },
            onConfirm = { color ->
                onUpdateSettings {
                    it.copy(themeId = "custom", customSeedArgb = color.toArgb().toLong())
                }
                showColorPicker = false
            },
        )
    }

    if (showPauseDurationDialog) {
        val parsed = parseTimedPauseMinutes(pauseDurationInput)
        AlertDialog(
            onDismissRequest = { showPauseDurationDialog = false },
            title = { Text(stringResource(R.string.settings_timed_pause_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = pauseDurationInput,
                        onValueChange = { pauseDurationInput = it.filter(Char::isDigit).take(4) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        label = { Text(stringResource(R.string.settings_timed_pause_dialog_hint)) },
                        isError = parsed == null && pauseDurationInput.isNotEmpty(),
                    )
                    if (parsed == null && pauseDurationInput.isNotEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_timed_pause_invalid),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = parsed != null,
                    onClick = {
                        parsed?.let { minutes ->
                            onUpdateSettings { it.copy(timedPauseMinutes = minutes) }
                            showPauseDurationDialog = false
                        }
                    },
                ) {
                    Text(stringResource(R.string.action_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showPauseDurationDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun PaletteItem(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(color)
                .then(
                    if (selected) {
                        Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                    } else {
                        Modifier
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DarkModeChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
    )
}

/** Row that opens a settings value dialog; mirrors the custom colour row. */
@Composable
private fun SettingsValueRow(
    title: String,
    description: String,
    actionLabel: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = actionLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun grantStatus(granted: Boolean): String =
    stringResource(if (granted) R.string.settings_granted else R.string.settings_not_granted)

@Composable
private fun paletteLabel(id: String): String = when (id) {
    "jade" -> stringResource(R.string.theme_jade)
    "ocean" -> stringResource(R.string.theme_ocean)
    "amber" -> stringResource(R.string.theme_amber)
    "lilac" -> stringResource(R.string.theme_lilac)
    "slate" -> stringResource(R.string.theme_slate)
    else -> stringResource(R.string.theme_custom)
}
