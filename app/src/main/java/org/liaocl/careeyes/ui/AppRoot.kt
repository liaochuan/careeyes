package org.liaocl.careeyes.ui

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.liaocl.careeyes.data.AppContainer
import org.liaocl.careeyes.data.AppSettings
import org.liaocl.careeyes.data.Preset
import org.liaocl.careeyes.service.EyeCareService
import org.liaocl.careeyes.util.PermissionUtils

private enum class AppPage { HOME, PRESETS, STATS, SETTINGS }

@Composable
fun AppRoot(
    container: AppContainer,
    onRequestNotifications: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
    val stats by container.statsRepository.stats.collectAsStateWithLifecycle()
    val runtime by container.engine.state.collectAsStateWithLifecycle()

    var pageName by rememberSaveable { mutableStateOf(AppPage.HOME.name) }
    val page = runCatching { AppPage.valueOf(pageName) }.getOrDefault(AppPage.HOME)

    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                now = SystemClock.elapsedRealtime()
                delay(1000L)
            }
        }
    }

    LaunchedEffect(Unit) {
        val current = container.settingsRepository.load()
        if (!current.notificationPermissionAsked && !PermissionUtils.hasNotificationPermission(context)) {
            onRequestNotifications()
            container.settingsRepository.update { it.copy(notificationPermissionAsked = true) }
        }
    }

    BackHandler(enabled = page != AppPage.HOME) { pageName = AppPage.HOME.name }

    val updateSettings: ((AppSettings) -> AppSettings) -> Unit = { transform ->
        scope.launch {
            container.settingsRepository.update(transform)
            EyeCareService.start(context, EyeCareService.ACTION_RELOAD)
        }
    }

    val selectPreset: (String) -> Unit = { presetId ->
        scope.launch {
            container.settingsRepository.update { it.copy(selectedPresetId = presetId) }
            EyeCareService.start(context, EyeCareService.ACTION_SET_PRESET, presetId)
        }
    }

    val savePreset: (Preset) -> Unit = { preset ->
        scope.launch {
            container.settingsRepository.update { current ->
                val exists = current.presets.any { it.id == preset.id }
                val presets = if (exists) {
                    current.presets.map { if (it.id == preset.id) preset else it }
                } else {
                    current.presets + preset
                }
                current.copy(presets = presets)
            }
            EyeCareService.start(context, EyeCareService.ACTION_RELOAD)
        }
    }

    val deletePreset: (Preset) -> Unit = { preset ->
        scope.launch {
            container.settingsRepository.update { current ->
                val remaining = current.presets.filterNot { it.id == preset.id }
                if (remaining.isEmpty()) {
                    current
                } else {
                    current.copy(
                        presets = remaining,
                        selectedPresetId = if (current.selectedPresetId == preset.id) {
                            remaining.first().id
                        } else {
                            current.selectedPresetId
                        },
                    )
                }
            }
            EyeCareService.start(context, EyeCareService.ACTION_RELOAD)
        }
    }

    when (page) {
        AppPage.HOME -> HomeScreen(
            runtime = runtime,
            settings = settings,
            stats = stats,
            now = now,
            onStart = { EyeCareService.start(context, EyeCareService.ACTION_START) },
            onPause = { EyeCareService.start(context, EyeCareService.ACTION_PAUSE) },
            onResume = { EyeCareService.start(context, EyeCareService.ACTION_RESUME) },
            onReset = { EyeCareService.start(context, EyeCareService.ACTION_RESET) },
            onRestNow = { EyeCareService.start(context, EyeCareService.ACTION_START_BREAK) },
            onSkipBreak = { EyeCareService.start(context, EyeCareService.ACTION_SKIP_BREAK) },
            onSelectPreset = selectPreset,
            onManagePresets = { pageName = AppPage.PRESETS.name },
            onOpenStats = { pageName = AppPage.STATS.name },
            onOpenSettings = { pageName = AppPage.SETTINGS.name },
            onRequestNotifications = onRequestNotifications,
        )

        AppPage.PRESETS -> PresetsScreen(
            settings = settings,
            onBack = { pageName = AppPage.HOME.name },
            onSelectPreset = selectPreset,
            onSavePreset = savePreset,
            onDeletePreset = deletePreset,
        )

        AppPage.STATS -> StatsScreen(
            stats = stats,
            onBack = { pageName = AppPage.HOME.name },
        )

        AppPage.SETTINGS -> SettingsScreen(
            settings = settings,
            onBack = { pageName = AppPage.HOME.name },
            onUpdateSettings = updateSettings,
            onRequestNotifications = onRequestNotifications,
        )
    }
}
