package org.liaocl.careeyes.service

import android.app.KeyguardManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.liaocl.careeyes.appContainer
import org.liaocl.careeyes.data.AppSettings
import org.liaocl.careeyes.data.SettingsRepository
import org.liaocl.careeyes.data.StatsRepository
import org.liaocl.careeyes.engine.EyeCareEngine
import org.liaocl.careeyes.engine.FrozenTimer
import org.liaocl.careeyes.engine.PausedMode
import org.liaocl.careeyes.engine.ScreenReturn
import org.liaocl.careeyes.engine.TimerMode
import org.liaocl.careeyes.engine.screenReturn
import org.liaocl.careeyes.engine.timedPauseExpired
import org.liaocl.careeyes.util.PermissionUtils
import kotlin.math.roundToInt

class EyeCareService : Service() {
    companion object {
        const val ACTION_ENSURE = "org.liaocl.careeyes.action.ENSURE"
        const val ACTION_START = "org.liaocl.careeyes.action.START"
        const val ACTION_PAUSE = "org.liaocl.careeyes.action.PAUSE"
        const val ACTION_RESUME = "org.liaocl.careeyes.action.RESUME"
        const val ACTION_RESET = "org.liaocl.careeyes.action.RESET"
        const val ACTION_START_BREAK = "org.liaocl.careeyes.action.START_BREAK"
        const val ACTION_SKIP_BREAK = "org.liaocl.careeyes.action.SKIP_BREAK"
        const val ACTION_SET_PRESET = "org.liaocl.careeyes.action.SET_PRESET"
        const val ACTION_RELOAD = "org.liaocl.careeyes.action.RELOAD"
        const val EXTRA_PRESET_ID = "org.liaocl.careeyes.extra.PRESET_ID"
        const val EXTRA_PAUSE_MINUTES = "org.liaocl.careeyes.extra.PAUSE_MINUTES"
        const val EXTRA_SKIP_BREAK = "org.liaocl.careeyes.extra.SKIP_BREAK"

        private const val PAUSE_STOP_DELAY_MS = 15 * 60 * 1000L
        private const val PROGRESS_UPDATE_INTERVAL_MS = 30_000L
        private const val BREAK_FALLBACK_DELAY_MS = 1_500L
        private const val MINUTE_MS = 60_000L
        private val MEDIA_RESUME_DELAYS_MS = longArrayOf(0L, 400L, 1_200L, 2_500L)
        private const val TAG = "CareEyes"

        fun start(
            context: Context,
            action: String = ACTION_ENSURE,
            presetId: String? = null,
            pauseMinutes: Int? = null,
            skipBreak: Boolean = false,
        ) {
            val intent = Intent(context, EyeCareService::class.java).setAction(action)
            if (presetId != null) intent.putExtra(EXTRA_PRESET_ID, presetId)
            if (pauseMinutes != null) intent.putExtra(EXTRA_PAUSE_MINUTES, pauseMinutes)
            if (skipBreak) intent.putExtra(EXTRA_SKIP_BREAK, true)
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { Log.w("CareEyes", "Unable to start service for $action", it) }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val initMutex = Mutex()
    private val actionMutex = Mutex()

    private lateinit var engine: EyeCareEngine
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var statsRepository: StatsRepository

    private var settings = AppSettings()
    private var initialized = false
    private var isForeground = false

    private var deadlineJob: Job? = null
    private var progressJob: Job? = null
    private var pauseStopJob: Job? = null
    private var pauseEndJob: Job? = null
    private var mediaPauseJob: Job? = null
    private var breakFallbackJob: Job? = null

    private var focusSegmentStart: Long? = null
    private var frozenForScreenOff: FrozenTimer? = null
    private var screenOffAt: Long = 0L
    private var screenOffAccumMs: Long = 0L
    private var pendingBreak = false

    private val keyguardManager by lazy { getSystemService(KeyguardManager::class.java) }
    private val powerManager by lazy { getSystemService(PowerManager::class.java) }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_SCREEN_OFF -> onScreenOff()
                Intent.ACTION_SCREEN_ON -> {
                    accumulateScreenOffTime()
                    if (keyguardManager?.isKeyguardLocked == false) restoreAfterScreenOn()
                    resumeExpiredTimedPause()
                }
                Intent.ACTION_USER_PRESENT -> {
                    accumulateScreenOffTime()
                    if (keyguardManager?.isKeyguardLocked == false) restoreAfterScreenOn()
                    resumeExpiredTimedPause()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val container = appContainer
        engine = container.engine
        settingsRepository = container.settingsRepository
        statsRepository = container.statsRepository

        NotificationHelper.ensureChannels(this)
        startForeground(
            NotificationHelper.ID_MAIN,
            NotificationHelper.buildMain(this, engine.snapshot(), settings.showNotificationActions),
        )
        isForeground = true
        registerScreenReceiver()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_ENSURE
        val presetId = intent?.getStringExtra(EXTRA_PRESET_ID)
        val pauseMinutes = intent?.getIntExtra(EXTRA_PAUSE_MINUTES, 0)?.takeIf { it > 0 }
        val skipBreak = intent?.getBooleanExtra(EXTRA_SKIP_BREAK, false) == true
        scope.launch {
            actionMutex.withLock {
                ensureInitialized()
                when (action) {
                    ACTION_RELOAD -> reloadSettings()
                    else -> handleAction(action, presetId, pauseMinutes, skipBreak)
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        deadlineJob?.cancel()
        progressJob?.cancel()
        pauseStopJob?.cancel()
        pauseEndJob?.cancel()
        breakFallbackJob?.cancel()
        runCatching { unregisterReceiver(screenReceiver) }
        runCatching { NotificationManagerCompat.from(this).cancel(NotificationHelper.ID_PROGRESS) }
        scope.cancel()
        isForeground = false
        super.onDestroy()
    }

    private suspend fun ensureInitialized() {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return
            settings = settingsRepository.load()
            val pauseExpired = timedPauseExpired(
                settings.pausedUntilEpochMs,
                System.currentTimeMillis(),
            )
            engine.prepare(
                preset = settings.currentPreset(),
                running = settings.running || pauseExpired,
                savedRemainingMs = settings.pausedRemainingMs,
                savedPausedMode = parsePausedMode(settings.pausedMode),
            )
            if (pauseExpired) {
                // The timed pause ended while the process was gone: start a fresh focus cycle.
                persistRunning()
            }
            if (engine.state.value.mode == TimerMode.FOCUS &&
                (powerManager?.isInteractive == false || keyguardManager?.isKeyguardLocked == true)
            ) {
                frozenForScreenOff = engine.pause()
                focusSegmentStart = null
                if (powerManager?.isInteractive == false) screenOffAt = now()
            } else {
                focusSegmentStart = if (engine.state.value.mode == TimerMode.FOCUS) now() else null
            }
            initialized = true
            updateNotification()
            scheduleDeadline()
            updateProgressJob()
            if (engine.state.value.mode == TimerMode.PAUSED && frozenForScreenOff == null) {
                schedulePauseEnd(settings.pausedUntilEpochMs)
                schedulePauseStop()
            }
            // A focus or idle state cannot own a paused media session; drop leftovers from a kill.
            if (engine.state.value.mode == TimerMode.FOCUS || engine.state.value.mode == TimerMode.IDLE) {
                clearPausedMedia()
            }
        }
    }

    private suspend fun handleAction(
        action: String,
        presetId: String?,
        pauseMinutes: Int?,
        skipBreak: Boolean,
    ) {
        when (action) {
            ACTION_ENSURE -> {
                when (engine.state.value.mode) {
                    TimerMode.IDLE -> if (settings.running) startFreshFocus()
                    TimerMode.PAUSED -> {
                        val timedOut = timedPauseExpired(
                            settings.pausedUntilEpochMs,
                            System.currentTimeMillis(),
                        )
                        if ((settings.running || timedOut) &&
                            screenOffAt == 0L &&
                            powerManager?.isInteractive != false &&
                            keyguardManager?.isKeyguardLocked != true
                        ) {
                            resumeTimer()
                        }
                    }

                    TimerMode.BREAK -> launchBreakActivity()
                    else -> updateNotification()
                }
            }

            ACTION_START -> startFreshFocus()
            ACTION_PAUSE -> pauseTimer(pauseMinutes, skipBreak)
            ACTION_RESUME -> resumeTimer()
            ACTION_RESET -> {
                if (engine.state.value.mode == TimerMode.BREAK) {
                    finishBreak(completed = false)
                } else {
                    startFreshFocus()
                }
            }

            ACTION_START_BREAK -> startBreak()
            ACTION_SKIP_BREAK -> finishBreak(completed = false)
            ACTION_SET_PRESET -> if (presetId != null) applyPresetById(presetId)
            ACTION_RELOAD -> Unit
        }
    }

    private fun startFreshFocus() {
        val state = engine.state.value
        if (state.mode == TimerMode.BREAK) {
            finishBreak(completed = false)
            return
        }
        flushFocus()
        engine.startFocus()
        focusSegmentStart = now()
        pendingBreak = false
        persistRunning()
        dismissBreakActivity()
        resumePausedMedia()
        afterStateChange()
    }

    private fun pauseTimer(minutes: Int? = null, skipBreak: Boolean = false) {
        val mode = engine.state.value.mode
        if (mode == TimerMode.PAUSED || mode == TimerMode.IDLE) return
        if (mode == TimerMode.BREAK && skipBreak) {
            // Pausing from the break screen gives up this break; resume starts a full focus cycle.
            scope.launch { statsRepository.recordBreak(completed = false) }
            engine.startFocus()
        }
        flushFocus()
        val frozen = engine.pause() ?: return
        focusSegmentStart = null
        pendingBreak = false
        val pausedUntil = minutes?.let { System.currentTimeMillis() + it * MINUTE_MS } ?: 0L
        persistPaused(frozen, pausedUntil)
        cancelDeadline()
        dismissBreakActivity()
        updateNotification()
        updateProgressJob()
        schedulePauseEnd(pausedUntil)
        schedulePauseStop()
        if (skipBreak) resumePausedMedia()
    }

    private fun resumeTimer() {
        val state = engine.state.value
        if (state.mode != TimerMode.PAUSED) {
            if (state.mode == TimerMode.IDLE) startFreshFocus()
            return
        }
        pauseStopJob?.cancel()
        pauseEndJob?.cancel()
        if (state.remainingMs <= 0L) {
            if (state.pausedMode == PausedMode.FOCUS) {
                startBreak()
            } else {
                finishBreak(completed = true)
            }
            return
        }
        engine.resume()
        val resumed = engine.state.value
        focusSegmentStart = if (resumed.mode == TimerMode.FOCUS) now() else null
        persistRunning()
        if (resumed.mode == TimerMode.FOCUS) resumePausedMedia()
        afterStateChange()
        if (resumed.mode == TimerMode.BREAK) launchBreakActivity()
    }

    private fun startBreak() {
        val state = engine.state.value
        if (state.mode == TimerMode.BREAK) {
            launchBreakActivity()
            return
        }
        if (state.mode == TimerMode.IDLE) {
            engine.startFocus()
            focusSegmentStart = now()
        }
        flushFocus()
        focusSegmentStart = null
        pendingBreak = false
        engine.startBreak()
        persistRunning()
        afterStateChange()
        if (settings.pauseVideoDuringBreak) {
            val pausePackages = settings.pauseAppPackages
            val debugLog = settings.mediaPauseDebugLog
            mediaPauseJob = scope.launch {
                val paused = withContext(Dispatchers.IO) {
                    runCatching {
                        MediaPauseController.pausePlaying(applicationContext, pausePackages, debugLog)
                    }.getOrDefault(emptySet())
                }
                if (paused.isNotEmpty()) persistPausedMedia(paused)
            }
        }
        launchBreakActivity()
    }

    private fun finishBreak(completed: Boolean) {
        val state = engine.state.value
        val wasInBreak = state.mode == TimerMode.BREAK ||
            (state.mode == TimerMode.PAUSED && state.pausedMode == PausedMode.BREAK)
        if (!wasInBreak) return

        scope.launch { statsRepository.recordBreak(completed = completed) }

        pendingBreak = false
        engine.startFocus()
        focusSegmentStart = now()
        persistRunning()
        dismissBreakActivity()
        resumePausedMedia()
        afterStateChange()
    }

    private suspend fun applyPresetById(presetId: String) {
        val latest = settingsRepository.load()
        settings = latest
        val preset = latest.presets.firstOrNull { it.id == presetId } ?: return
        if (engine.state.value.presetId == preset.id && engine.state.value.mode != TimerMode.IDLE) {
            updateNotification()
            return
        }
        flushFocus()
        pendingBreak = false
        engine.applyPreset(preset, startImmediately = true)
        focusSegmentStart = now()
        persistRunning()
        dismissBreakActivity()
        resumePausedMedia()
        afterStateChange()
    }

    private suspend fun reloadSettings() {
        val next = settingsRepository.load()
        val oldPreset = settings.currentPreset()
        val newPreset = next.currentPreset()
        settings = next
        if (oldPreset != newPreset) {
            flushFocus()
            pendingBreak = false
            if (next.running && screenOffAt == 0L && powerManager?.isInteractive != false) {
                engine.applyPreset(newPreset, startImmediately = true)
                focusSegmentStart = now()
            } else {
                engine.applyPreset(newPreset, startImmediately = false)
                focusSegmentStart = null
            }
            dismissBreakActivity()
            resumePausedMedia()
        }
        updateNotification()
        scheduleDeadline()
        updateProgressJob()
    }

    private fun afterStateChange() {
        updateNotification()
        scheduleDeadline()
        updateProgressJob()
    }

    private fun scheduleDeadline() {
        deadlineJob?.cancel()
        val state = engine.state.value
        if (state.mode != TimerMode.FOCUS && state.mode != TimerMode.BREAK) return
        if (screenOffAt != 0L) return
        val capturedDeadline = state.deadlineElapsedMs
        val waitMs = state.remainingAt(now())
        if (waitMs <= 0L) {
            scope.launch { handleDeadline(capturedDeadline) }
            return
        }
        deadlineJob = scope.launch {
            delay(waitMs + 50L)
            handleDeadline(capturedDeadline)
        }
    }

    private fun handleDeadline(capturedDeadline: Long) {
        val state = engine.state.value
        if (state.deadlineElapsedMs != capturedDeadline) return
        if (screenOffAt != 0L) return
        when (state.mode) {
            TimerMode.FOCUS -> onFocusFinished()
            TimerMode.BREAK -> onBreakFinished()
            else -> Unit
        }
    }

    private fun onFocusFinished() {
        if (powerManager?.isInteractive == false) {
            freezeForScreenOff()
            return
        }
        if (keyguardManager?.isKeyguardLocked == true) {
            flushFocus()
            focusSegmentStart = null
            frozenForScreenOff = engine.pause()
            pendingBreak = true
            cancelDeadline()
            updateNotification()
            updateProgressJob()
            return
        }
        startBreak()
    }

    private fun onBreakFinished() {
        scope.launch { statsRepository.recordBreak(completed = true) }
        pendingBreak = false
        engine.completeBreak()
        focusSegmentStart = now()
        persistRunning()
        dismissBreakActivity()
        resumePausedMedia()
        afterStateChange()
    }

    private fun onScreenOff() {
        if (screenOffAt != 0L) return
        val state = engine.state.value
        if (state.mode == TimerMode.FOCUS || state.mode == TimerMode.BREAK) {
            flushFocus()
            val frozen = engine.pause() ?: return
            focusSegmentStart = null
            frozenForScreenOff = frozen
            cancelDeadline()
            dismissBreakActivity()
            updateNotification()
            updateProgressJob()
        } else if (frozenForScreenOff == null) {
            return
        }
        // Time spent on the lock screen while the display is on must not count as rest.
        screenOffAt = now()
    }

    private fun accumulateScreenOffTime() {
        if (screenOffAt == 0L) return
        screenOffAccumMs += (now() - screenOffAt).coerceAtLeast(0L)
        screenOffAt = 0L
    }

    private fun restoreAfterScreenOn() {
        val frozen = frozenForScreenOff
        if (frozen == null) {
            screenOffAccumMs = 0L
            if (pendingBreak) {
                pendingBreak = false
                startBreak()
            }
            return
        }
        val offDuration = screenOffAccumMs
        screenOffAccumMs = 0L
        val breakDuration = engine.state.value.breakDurationMs.coerceAtLeast(1L)
        when (screenReturn(offDuration, breakDuration, frozen)) {
            ScreenReturn.ResetFocus -> {
                frozenForScreenOff = null
                pendingBreak = false
                engine.startFocus()
                focusSegmentStart = now()
                persistRunning()
                dismissBreakActivity()
                clearPausedMediaAfterPauseSettles()
                afterStateChange()
            }

            is ScreenReturn.Restore -> {
                frozenForScreenOff = null
                if (pendingBreak) {
                    pendingBreak = false
                    startBreak()
                    return
                }
                if (frozen.pausedMode == PausedMode.BREAK && frozen.remainingMs <= 0L) {
                    finishBreak(completed = true)
                    return
                }
                engine.restore(frozen)
                focusSegmentStart = if (frozen.pausedMode == PausedMode.FOCUS) now() else null
                persistRunning()
                afterStateChange()
                if (frozen.pausedMode == PausedMode.BREAK) launchBreakActivity()
            }
        }
    }

    private fun freezeForScreenOff() {
        onScreenOff()
    }

    /**
     * delay() only advances while the CPU is awake, so a timed pause can expire during deep sleep
     * without its job firing. Screen-on and unlock are the reliable moments to catch up.
     */
    private fun resumeExpiredTimedPause() {
        if (engine.state.value.mode != TimerMode.PAUSED) return
        if (powerManager?.isInteractive == false) return
        if (keyguardManager?.isKeyguardLocked == true) return
        if (!timedPauseExpired(settings.pausedUntilEpochMs, System.currentTimeMillis())) return
        resumeTimer()
    }

    private fun updateNotification() {
        val state = engine.snapshot()
        val notification = NotificationHelper.buildMain(
            this,
            state,
            settings.showNotificationActions,
            settings.pausedUntilEpochMs,
        )
        if (isForeground) {
            notifySafely(NotificationHelper.ID_MAIN, notification)
        } else {
            startForeground(NotificationHelper.ID_MAIN, notification)
            isForeground = true
        }
    }

    private fun updateProgressJob() {
        progressJob?.cancel()
        progressJob = null
        val state = engine.snapshot()
        if (!settings.showProgressNotification || state.mode == TimerMode.IDLE || state.mode == TimerMode.PAUSED) {
            NotificationManagerCompat.from(this).cancel(NotificationHelper.ID_PROGRESS)
            return
        }
        progressJob = scope.launch {
            var lastPercent = -1
            while (isActive) {
                val current = engine.snapshot()
                if (current.mode != TimerMode.FOCUS && current.mode != TimerMode.BREAK) break
                val percent = (current.progressAt(now()) * 100).roundToInt().coerceIn(0, 100)
                if (percent != lastPercent) {
                    notifySafely(
                        NotificationHelper.ID_PROGRESS,
                        NotificationHelper.buildProgress(this@EyeCareService, current, percent),
                    )
                    lastPercent = percent
                }
                delay(PROGRESS_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun schedulePauseStop() {
        pauseStopJob?.cancel()
        // A timed pause must keep the service alive so it can resume on time.
        if (settings.pausedUntilEpochMs > 0L) return
        pauseStopJob = scope.launch {
            delay(PAUSE_STOP_DELAY_MS)
            if (engine.state.value.mode == TimerMode.PAUSED && initialized) {
                NotificationManagerCompat.from(this@EyeCareService).cancel(NotificationHelper.ID_PROGRESS)
                ServiceCompat.stopForeground(this@EyeCareService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                isForeground = false
                stopSelf()
            }
        }
    }

    private fun schedulePauseEnd(pausedUntilEpochMs: Long) {
        pauseEndJob?.cancel()
        pauseEndJob = null
        if (pausedUntilEpochMs <= 0L) return
        pauseEndJob = scope.launch {
            // delay() counts awake time only: deep sleep postpones expiry. The loop handles early
            // wake and wall-clock jumps; ENSURE, screen-on and unlock compensate after sleep.
            var waitMs = pausedUntilEpochMs - System.currentTimeMillis()
            while (waitMs > 0L && isActive) {
                delay(waitMs)
                waitMs = pausedUntilEpochMs - System.currentTimeMillis()
            }
            if (engine.state.value.mode == TimerMode.PAUSED) {
                resumeTimer()
                // The timed pause can end while the screen is off; keep the timer frozen as usual.
                if (engine.state.value.mode == TimerMode.FOCUS &&
                    (powerManager?.isInteractive == false || keyguardManager?.isKeyguardLocked == true)
                ) {
                    onScreenOff()
                }
            }
        }
    }

    private fun persistRunning() {
        settings = settings.copy(running = true, pausedUntilEpochMs = 0L)
        scope.launch {
            settingsRepository.update { it.copy(running = true, pausedUntilEpochMs = 0L) }
        }
    }

    private fun persistPaused(frozen: FrozenTimer, pausedUntilEpochMs: Long) {
        settings = settings.copy(
            running = false,
            pausedRemainingMs = frozen.remainingMs,
            pausedMode = frozen.pausedMode.name,
            pausedUntilEpochMs = pausedUntilEpochMs,
        )
        scope.launch {
            settingsRepository.update {
                it.copy(
                    running = false,
                    pausedRemainingMs = frozen.remainingMs,
                    pausedMode = frozen.pausedMode.name,
                    pausedUntilEpochMs = pausedUntilEpochMs,
                )
            }
        }
    }

    private fun flushFocus() {
        val start = focusSegmentStart ?: return
        focusSegmentStart = null
        val seconds = ((now() - start).coerceAtLeast(0L)) / 1000L
        if (seconds <= 0L) return
        scope.launch { statsRepository.addFocusSeconds(seconds) }
    }

    private fun persistPausedMedia(packages: Set<String>) {
        if (packages.isEmpty()) return
        settings = settings.copy(pausedMediaPackages = settings.pausedMediaPackages + packages)
        scope.launch {
            settingsRepository.update {
                it.copy(pausedMediaPackages = it.pausedMediaPackages + packages)
            }
        }
    }

    /**
     * Resumes what CareEyes paused when the break started. The video app only regains focus
     * after the break screen finishes, and some apps ignore a play request while backgrounded,
     * so the request waits until that app is back in the foreground, with a bounded retry window.
     */
    private fun resumePausedMedia() {
        val pauseJob = mediaPauseJob
        if (pauseJob == null && settings.pausedMediaPackages.isEmpty()) return
        val debugLog = settings.mediaPauseDebugLog
        scope.launch {
            // A break that ends immediately may still have its pause request in flight; wait for
            // it so the packages it paused are the ones resumed.
            pauseJob?.join()
            // A new break may have started while waiting; leave its fresh pause alone.
            if (engine.state.value.mode == TimerMode.BREAK) return@launch
            val packages = settings.pausedMediaPackages
            if (packages.isEmpty()) return@launch
            clearPausedMedia()
            for (waitMs in MEDIA_RESUME_DELAYS_MS) {
                if (waitMs > 0L) delay(waitMs)
                if (engine.state.value.mode == TimerMode.BREAK) return@launch
                val foreground = withContext(Dispatchers.IO) {
                    runCatching { ForegroundApp.packageName(applicationContext) }.getOrNull()
                }
                if (foreground == null || foreground !in packages) continue
                withContext(Dispatchers.IO) {
                    runCatching {
                        MediaPauseController.resumePlaying(applicationContext, packages, debugLog)
                    }
                }
                return@launch
            }
        }
    }

    /** Drops paused packages once the pause request already in flight has settled. */
    private fun clearPausedMediaAfterPauseSettles() {
        val pauseJob = mediaPauseJob
        if (pauseJob == null) {
            clearPausedMedia()
            return
        }
        scope.launch {
            pauseJob.join()
            clearPausedMedia()
        }
    }

    private fun clearPausedMedia() {
        if (settings.pausedMediaPackages.isEmpty()) return
        settings = settings.copy(pausedMediaPackages = emptySet())
        scope.launch {
            settingsRepository.update { it.copy(pausedMediaPackages = emptySet()) }
        }
    }

    private fun cancelDeadline() {
        deadlineJob?.cancel()
        deadlineJob = null
    }

    private fun launchBreakActivity() {
        if (engine.state.value.mode != TimerMode.BREAK) return
        val canAttempt = AppVisibility.isVisible || Settings.canDrawOverlays(this)
        if (!canAttempt) {
            Log.i(TAG, "break: no overlay permission and app not visible, posting notification")
            postBreakFallbackNotification()
            return
        }
        if (!startBreakActivity()) {
            Log.w(TAG, "break: startActivity failed, posting notification")
            postBreakFallbackNotification()
            return
        }
        // Android can silently deny a background activity start (startActivity still returns
        // normally), so verify that the break screen itself came to the foreground. Keep a single
        // pending check so repeated ENSURE actions cannot pile up coroutines.
        breakFallbackJob?.cancel()
        breakFallbackJob = scope.launch {
            delay(BREAK_FALLBACK_DELAY_MS)
            if (engine.state.value.mode == TimerMode.BREAK && !AppVisibility.isBreakVisible) {
                Log.w(TAG, "break: screen never became visible, posting notification")
                postBreakFallbackNotification()
            }
        }
    }

    private fun startBreakActivity(): Boolean {
        val intent = Intent(this, BreakActivity::class.java)
            .addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION,
            )
        return runCatching { startActivity(intent) }.isSuccess
    }

    private fun postBreakFallbackNotification() {
        notifySafely(
            NotificationHelper.ID_BREAK,
            NotificationHelper.buildBreakFallback(this, engine.snapshot()),
        )
    }

    private fun notifySafely(id: Int, notification: android.app.Notification) {
        if (!PermissionUtils.hasNotificationPermission(this)) return
        try {
            NotificationManagerCompat.from(this).notify(id, notification)
        } catch (_: SecurityException) {
            // Permission may be revoked between the check and the call.
        }
    }

    private fun dismissBreakActivity() {
        runCatching {
            sendBroadcast(
                Intent(BreakActivity.ACTION_DISMISS)
                    .setPackage(packageName),
            )
        }
        if (PermissionUtils.hasNotificationPermission(this)) {
            NotificationManagerCompat.from(this).cancel(NotificationHelper.ID_BREAK)
        }
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    private fun now(): Long = SystemClock.elapsedRealtime()

    private fun parsePausedMode(raw: String): PausedMode =
        runCatching { PausedMode.valueOf(raw) }.getOrDefault(PausedMode.FOCUS)
}
