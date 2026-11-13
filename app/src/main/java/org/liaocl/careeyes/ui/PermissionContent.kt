package org.liaocl.careeyes.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import org.liaocl.careeyes.R
import org.liaocl.careeyes.util.CarePermission

val CarePermission.icon: ImageVector
    get() = when (this) {
        CarePermission.NOTIFICATIONS -> Icons.Rounded.Notifications
        CarePermission.OVERLAY -> Icons.Rounded.Visibility
        CarePermission.BATTERY -> Icons.Rounded.BatteryFull
        CarePermission.MEDIA_ACCESS -> Icons.Rounded.Pause
        CarePermission.USAGE_ACCESS -> Icons.Rounded.Insights
    }

@Composable
fun CarePermission.title(): String = stringResource(
    when (this) {
        CarePermission.NOTIFICATIONS -> R.string.permission_notifications_title
        CarePermission.OVERLAY -> R.string.permission_overlay_title
        CarePermission.BATTERY -> R.string.permission_battery_title
        CarePermission.MEDIA_ACCESS -> R.string.permission_media_title
        CarePermission.USAGE_ACCESS -> R.string.permission_usage_title
    },
)

@Composable
fun CarePermission.description(): String = stringResource(
    when (this) {
        CarePermission.NOTIFICATIONS -> R.string.permission_notifications_desc
        CarePermission.OVERLAY -> R.string.permission_overlay_desc
        CarePermission.BATTERY -> R.string.permission_battery_desc
        CarePermission.MEDIA_ACCESS -> R.string.permission_media_desc
        CarePermission.USAGE_ACCESS -> R.string.permission_usage_desc
    },
)
