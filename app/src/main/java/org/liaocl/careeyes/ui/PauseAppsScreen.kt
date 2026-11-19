package org.liaocl.careeyes.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.liaocl.careeyes.R
import org.liaocl.careeyes.data.AppSettings
import org.liaocl.careeyes.data.DEFAULT_PAUSE_APP_PACKAGES
import org.liaocl.careeyes.ui.components.AppCard
import org.liaocl.careeyes.ui.components.ScreenScaffold
import org.liaocl.careeyes.ui.components.SwitchRow
import org.liaocl.careeyes.util.InstalledApp
import org.liaocl.careeyes.util.loadInstalledLaunchableApps

@Composable
fun PauseAppsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onUpdateSettings: ((AppSettings) -> AppSettings) -> Unit,
) {
    val context = LocalContext.current
    // Null while the launcher-app query runs on the IO dispatcher; the list simply appears later.
    val apps by produceState<List<InstalledApp>?>(initialValue = null) {
        value = loadInstalledLaunchableApps(context)
    }
    var query by rememberSaveable { mutableStateOf("") }
    val selected = settings.pauseAppPackages
    val visibleApps = remember(apps, query, selected) {
        apps?.let { loaded ->
            val needle = query.trim().lowercase()
            val filtered = if (needle.isEmpty()) {
                loaded
            } else {
                loaded.filter {
                    it.label.lowercase().contains(needle) || it.packageName.lowercase().contains(needle)
                }
            }
            // Enabled apps float to the top; sortedByDescending is stable, so label order is kept.
            filtered.sortedByDescending { it.packageName in selected }
        }
    }

    ScreenScaffold(title = stringResource(R.string.settings_pause_apps_title), onBack = onBack) { padding ->
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
            item {
                Text(
                    text = stringResource(R.string.settings_pause_apps_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text(stringResource(R.string.pause_apps_search)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
            val listedApps = visibleApps
            if (listedApps != null) {
                if (listedApps.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.pause_apps_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(listedApps, key = { it.packageName }) { app ->
                    AppCard {
                        SwitchRow(
                            title = app.label,
                            description = app.packageName,
                            checked = app.packageName in selected,
                            onCheckedChange = { checked ->
                                onUpdateSettings { current ->
                                    current.copy(
                                        pauseAppPackages = if (checked) {
                                            current.pauseAppPackages + app.packageName
                                        } else {
                                            current.pauseAppPackages - app.packageName
                                        },
                                    )
                                }
                            },
                        )
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = {
                        onUpdateSettings { it.copy(pauseAppPackages = DEFAULT_PAUSE_APP_PACKAGES) }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.pause_apps_restore_defaults))
                }
            }
        }
    }
}
