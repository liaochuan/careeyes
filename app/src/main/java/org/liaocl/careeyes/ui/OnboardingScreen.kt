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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.liaocl.careeyes.R
import org.liaocl.careeyes.ui.components.AppCard
import org.liaocl.careeyes.ui.components.ScreenScaffold
import org.liaocl.careeyes.ui.components.SectionHeader
import org.liaocl.careeyes.util.CarePermission
import org.liaocl.careeyes.util.PermissionSnapshot
import org.liaocl.careeyes.util.PermissionUtils

@Composable
fun OnboardingScreen(
    snapshot: PermissionSnapshot,
    onFinish: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    val context = LocalContext.current

    ScreenScaffold(title = stringResource(R.string.onboarding_title)) { padding ->
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
                AppCard {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = stringResource(R.string.onboarding_intro_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(R.string.onboarding_intro_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item { SectionHeader(stringResource(R.string.onboarding_permissions_title)) }

            item {
                Text(
                    text = stringResource(R.string.onboarding_permissions_body),
                    modifier = Modifier.padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            items(CarePermission.entries, key = { it.key }) { permission ->
                PermissionGuideCard(
                    permission = permission,
                    granted = snapshot.isGranted(permission),
                    onOpen = {
                        when (permission) {
                            CarePermission.NOTIFICATIONS -> onRequestNotifications()
                            CarePermission.OVERLAY -> PermissionUtils.openOverlaySettings(context)
                            CarePermission.BATTERY ->
                                PermissionUtils.requestIgnoreBatteryOptimizations(context)
                            CarePermission.MEDIA_ACCESS ->
                                PermissionUtils.openNotificationAccessSettings(context)
                            CarePermission.USAGE_ACCESS ->
                                PermissionUtils.openUsageAccessSettings(context)
                        }
                    },
                )
            }

            item { Spacer(Modifier.height(4.dp)) }

            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = onFinish, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.onboarding_skip))
                    }
                    Button(onClick = onFinish, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.onboarding_done))
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionGuideCard(
    permission: CarePermission,
    granted: Boolean,
    onOpen: () -> Unit,
) {
    AppCard {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = permission.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = permission.title(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                    )
                    if (permission.isOptional) {
                        Spacer(Modifier.width(6.dp))
                        OptionalTag()
                    }
                }
                Text(
                    text = permission.description(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        if (granted) R.string.settings_granted else R.string.settings_not_granted,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (granted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                )
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = onOpen) {
                Text(
                    stringResource(
                        if (granted) R.string.action_open_settings else R.string.action_grant,
                    ),
                )
            }
        }
    }
}

@Composable
private fun OptionalTag() {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = stringResource(R.string.permission_optional),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}
