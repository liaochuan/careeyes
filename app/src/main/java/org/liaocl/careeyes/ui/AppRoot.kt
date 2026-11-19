package org.liaocl.careeyes.ui

import android.content.Intent
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.core.content.FileProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.liaocl.careeyes.data.AppContainer
import org.liaocl.careeyes.data.AppSettings
import org.liaocl.careeyes.data.Preset
import org.liaocl.careeyes.R
import org.liaocl.careeyes.service.EyeCareService
import org.liaocl.careeyes.util.MediaPauseJournal
import org.liaocl.careeyes.util.PermissionUtils
import org.liaocl.careeyes.util.pruneDismissed
import org.liaocl.careeyes.util.shouldShowPermissionCard

private enum class AppPage { ONBOARDING, HOME, PRESETS, STATS, SETTINGS, PAUSE_APPS }

/** Depth drives slide direction: deeper pages enter from the trailing edge, shallower ones return. */
private val AppPage.depth: Int
    get() = when (this) {
        AppPage.HOME -> 0
        AppPage.PRESETS, AppPage.STATS, AppPage.SETTINGS, AppPage.ONBOARDING -> 1
        AppPage.PAUSE_APPS -> 2
    }

private const val PAGE_ENTER_MILLIS = 240
private const val PAGE_EXIT_MILLIS = 180

/**
 * Short push transition. Only the two adjacent screens exist while it runs (~240ms);
 * nothing is retained afterwards, so steady-state memory is unchanged.
 */
private fun pageTransition(forward: Boolean): ContentTransform {
    val enterOffset: (Int) -> Int = { width -> if (forward) width / 6 else -width / 6 }
    val exitOffset: (Int) -> Int = { width -> if (forward) -width / 8 else width / 8 }
    val enter = slideInHorizontally(
        animationSpec = tween(PAGE_ENTER_MILLIS, easing = FastOutSlowInEasing),
        initialOffsetX = enterOffset,
    ) + fadeIn(tween(PAGE_ENTER_MILLIS))
    val exit = slideOutHorizontally(
        animationSpec = tween(PAGE_EXIT_MILLIS, easing = LinearOutSlowInEasing),
        targetOffsetX = exitOffset,
    ) + fadeOut(tween(PAGE_EXIT_MILLIS))
    return enter togetherWith exit
}

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
            pageName = if (page == AppPage.PAUSE_APPS) AppPage.SETTINGS.name else AppPage.HOME.name
        }
    }

    val updateSettings: ((AppSettings) -> AppSettings) -> Unit = { transform ->
        scope.launch {
            container.settingsRepository.update(transform)
            EyeCareService.start(context, EyeCareService.ACTION_RELOAD)
        }
    }

    val exportMediaLog: () -> Unit = {
        if (MediaPauseJournal.read(context).isBlank()) {
            Toast.makeText(context, R.string.log_export_empty, Toast.LENGTH_SHORT).show()
        } else {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                MediaPauseJournal.logFile(context),
            )
            val share = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.log_export_title))
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(share, context.getString(R.string.log_export_title))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(chooser) }
                .onFailure { Toast.makeText(context, R.string.log_export_failed, Toast.LENGTH_SHORT).show() }
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

    // Backplate keeps the brief cross-fade opaque instead of showing the window behind both screens.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        AnimatedContent(
            targetState = page,
            modifier = Modifier.fillMaxSize(),
            transitionSpec = { pageTransition(targetState.depth > initialState.depth) },
            contentAlignment = Alignment.TopStart,
            label = "app-page",
        ) { current ->
            when (current) {
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
                    onOpenPauseApps = { pageName = AppPage.PAUSE_APPS.name },
                    onExportMediaLog = exportMediaLog,
                )

                AppPage.PAUSE_APPS -> PauseAppsScreen(
                    settings = settings,
                    onBack = { pageName = AppPage.SETTINGS.name },
                    onUpdateSettings = updateSettings,
                )
            }
        }
    }
}
