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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import org.liaocl.careeyes.BuildConfig
import org.liaocl.careeyes.R
import org.liaocl.careeyes.data.AppSettings
import org.liaocl.careeyes.ui.components.AppCard
import org.liaocl.careeyes.ui.components.LinkRow
import org.liaocl.careeyes.ui.components.ScreenScaffold
import org.liaocl.careeyes.ui.components.SectionHeader
import org.liaocl.careeyes.ui.components.SwitchRow
import org.liaocl.careeyes.ui.theme.BUILT_IN_PALETTES
import org.liaocl.careeyes.ui.theme.ColorPickerDialog
import org.liaocl.careeyes.ui.theme.paletteSeed
import org.liaocl.careeyes.util.PermissionUtils

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
    onRequestNotifications: () -> Unit,
) {
    val context = LocalContext.current
    var permissionVersion by remember { mutableIntStateOf(0) }
    var showColorPicker by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { permissionVersion += 1 }

    val notificationGranted = remember(permissionVersion) { PermissionUtils.hasNotificationPermission(context) }
    val overlayGranted = remember(permissionVersion) { PermissionUtils.canDrawOverlays(context) }
    val batteryGranted = remember(permissionVersion) {
        PermissionUtils.isIgnoringBatteryOptimizations(context)
    }
    val mediaAccessGranted = remember(permissionVersion) { PermissionUtils.hasNotificationAccess(context) }
    val customColor = remember(settings.themeId, settings.customSeedArgb) {
        settings.customSeedArgb?.let { Color(it.toInt()) } ?: paletteSeed(settings.themeId, null)
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
                        title = stringResource(R.string.settings_pause_video),
                        description = stringResource(R.string.settings_pause_video_desc),
                        checked = settings.pauseVideoDuringBreak && mediaAccessGranted,
                        enabled = mediaAccessGranted,
                        onCheckedChange = { checked ->
                            onUpdateSettings { it.copy(pauseVideoDuringBreak = checked) }
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
                        title = stringResource(R.string.settings_permission_notifications),
                        description = stringResource(R.string.home_permission_notification_body),
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
                        title = stringResource(R.string.settings_permission_overlay),
                        description = stringResource(R.string.settings_permission_overlay_desc),
                        status = grantStatus(overlayGranted),
                        statusOk = overlayGranted,
                        onClick = { PermissionUtils.openOverlaySettings(context) },
                    )
                    LinkRow(
                        title = stringResource(R.string.settings_permission_battery),
                        description = stringResource(R.string.settings_permission_battery_desc),
                        status = grantStatus(batteryGranted),
                        statusOk = batteryGranted,
                        onClick = { PermissionUtils.requestIgnoreBatteryOptimizations(context) },
                    )
                    LinkRow(
                        title = stringResource(R.string.settings_permission_media),
                        description = stringResource(R.string.settings_permission_media_desc),
                        status = grantStatus(mediaAccessGranted),
                        statusOk = mediaAccessGranted,
                        onClick = { PermissionUtils.openNotificationAccessSettings(context) },
                    )
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
