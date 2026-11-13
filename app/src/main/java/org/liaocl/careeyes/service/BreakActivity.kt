package org.liaocl.careeyes.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import org.liaocl.careeyes.R
import org.liaocl.careeyes.appContainer
import org.liaocl.careeyes.engine.TimerMode
import org.liaocl.careeyes.ui.components.ProgressRing
import org.liaocl.careeyes.ui.theme.CareEyesTheme
import kotlin.math.ceil

class BreakActivity : ComponentActivity() {
    companion object {
        const val ACTION_DISMISS = "org.liaocl.careeyes.action.DISMISS_BREAK"
    }

    private var visibilityRegistered = false

    private val dismissReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_DISMISS) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val breakDuration = appContainer.engine.state.value.breakDurationMs
        if (breakDuration in 1L..60_000L) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        hideSystemBars()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                skipBreak()
            }
        })

        val container = appContainer
        setContent {
            val settings by container.settingsRepository.settings.collectAsStateWithLifecycle()
            CareEyesTheme(settings) {
                val runtime by container.engine.state.collectAsStateWithLifecycle()
                var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
                val lifecycleOwner = LocalLifecycleOwner.current

                LaunchedEffect(runtime.mode, runtime.deadlineElapsedMs, lifecycleOwner) {
                    lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                        while (isActive) {
                            now = SystemClock.elapsedRealtime()
                            delay(1000L)
                        }
                    }
                }

                if (runtime.mode != TimerMode.BREAK) {
                    LaunchedEffect(runtime.mode) { finish() }
                    return@CareEyesTheme
                }

                val remaining = runtime.remainingAt(now)
                val seconds = ceil(remaining / 1000.0).toInt().coerceAtLeast(0)
                val progress = 1f - runtime.progressAt(now)

                BreakContent(
                    presetName = runtime.presetName,
                    seconds = seconds,
                    progress = progress,
                    onSkip = ::skipBreak,
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.onScreenStarted()
        visibilityRegistered = true
        if (appContainer.engine.state.value.mode != TimerMode.BREAK) {
            finish()
            return
        }
        ContextCompat.registerReceiver(
            this,
            dismissReceiver,
            IntentFilter(ACTION_DISMISS),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }

    override fun onStop() {
        runCatching { unregisterReceiver(dismissReceiver) }
        if (visibilityRegistered) {
            visibilityRegistered = false
            AppVisibility.onScreenStopped()
        }
        super.onStop()
    }

    private fun skipBreak() {
        EyeCareService.start(this, EyeCareService.ACTION_SKIP_BREAK)
        finish()
    }

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}

@Composable
private fun BreakContent(
    presetName: String,
    seconds: Int,
    progress: Float,
    onSkip: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        colorScheme.primaryContainer,
                        colorScheme.background,
                        colorScheme.surface,
                    ),
                ),
            )
            .padding(horizontal = 32.dp, vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = MaterialTheme.typography.labelLarge,
                color = colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = presetName,
                style = MaterialTheme.typography.bodyMedium,
                color = colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))
            ProgressRing(
                progress = progress.coerceIn(0f, 1f),
                strokeWidth = 12.dp,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = seconds.toString(),
                        fontSize = 72.sp,
                        fontWeight = FontWeight.Light,
                        color = colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = stringResource(R.string.break_seconds_unit),
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
            Text(
                text = stringResource(R.string.break_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.break_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(36.dp))
            Button(onClick = onSkip) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.break_skip))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = stringResource(R.string.break_next_auto),
                style = MaterialTheme.typography.bodySmall,
                color = colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
