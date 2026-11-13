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
import androidx.lifecycle.compose.LifecycleEventEffect
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
import org.liaocl.careeyes.util.pruneDismissed
import org.liaocl.careeyes.util.shouldShowPermissionCard

private enum class AppPage { ONBOARDING, HOME, PRESETS, STATS, SETTINGS }

@Composable
fun AppRoot(
    container: AppContainer,
    onRequestNotifications: () -> Unit,
    permissionRefreshKey: Int = 0,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
    val stats by container.statsRepository.stats.collectAsStateWithLifecycle()
    val runtime by container.engine.state.collectAsStateWithLifecycle()

    var pageName by rememberSaveable { mutableStateOf(AppPage.HOME.name) }
    var guideReturnPage by rememberSaveable { mutableStateOf(AppPage.HOME.name) }
    // Not saveable on purpose: every recreation re-reads persisted settings before rendering.
    var initialized by remember { mutableStateOf(false) }
    val page = runCatching { AppPage.valueOf(pageName) }.getOrDefault(AppPage.HOME)

    var permissionSnapshot by remember { mutableStateOf(PermissionUtils.snapshot(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        permissionSnapshot = PermissionUtils.snapshot(context)
    }
    LaunchedEffect(permissionRefreshKey) {
        permissionSnapshot = PermissionUtils.snapshot(context)
    }

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
        if (!initialized) {
            val current = container.settingsRepository.load()
            if (!current.onboardingCompleted) pageName = AppPage.ONBOARDING.name
            initialized = true
        }
    }

    val finishOnboarding: () -> Unit = {
        scope.launch {
            container.settingsRepository.update { it.copy(onboardingCompleted = true) }
        }
        pageName = runCatching { AppPage.valueOf(guideReturnPage) }
            .getOrDefault(AppPage.HOME)
            .name
    }

    val openPermissionGuide: () -> Unit = {
        guideReturnPage = page.name
        pageName = AppPage.ONBOARDING.name
    }

    val showPermissionCard = shouldShowPermissionCard(permissionSnapshot, settings.dismissedPermissionKeys)
    val missingPermissions = permissionSnapshot.missing()
        .filter { it.key !in settings.dismissedPermissionKeys }

    val dismissPermissions: () -> Unit = {
        val dismissed = permissionSnapshot.missing().map { it.key }.toSet()
        scope.launch {
            container.settingsRepository.update { it.copy(dismissedPermissionKeys = dismissed) }
        }
    }

    // A granted permission leaves the dismissed set, so revoking it later prompts again.
    LaunchedEffect(permissionSnapshot, settings.dismissedPermissionKeys) {
        val pruned = pruneDismissed(permissionSnapshot, settings.dismissedPermissionKeys)
        if (pruned != settings.dismissedPermissionKeys) {
            container.settingsRepository.update { it.copy(dismissedPermissionKeys = pruned) }
        }
    }

    BackHandler(enabled = initialized && page != AppPage.HOME) {
        if (page == AppPage.ONBOARDING) {
            finishOnboarding()
        } else {
            pageName = AppPage.HOME.name
        }
    }

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

    if (!initialized) return

    when (page) {
        AppPage.ONBOARDING -> OnboardingScreen(
            snapshot = permissionSnapshot,
            onFinish = finishOnboarding,
            onRequestNotifications = onRequestNotifications,
        )

        AppPage.HOME -> HomeScreen(
            runtime = runtime,
            settings = settings,
            stats = stats,
            now = now,
            showPermissionCard = showPermissionCard,
            missingPermissions = missingPermissions,
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
            onOpenPermissionGuide = openPermissionGuide,
            onDismissPermissions = dismissPermissions,
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
            permissionSnapshot = permissionSnapshot,
            onBack = { pageName = AppPage.HOME.name },
            onUpdateSettings = updateSettings,
            onRequestNotifications = onRequestNotifications,
            onOpenPermissionGuide = openPermissionGuide,
        )
    }
}
