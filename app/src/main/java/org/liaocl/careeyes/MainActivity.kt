package org.liaocl.careeyes

import android.Manifest
import android.app.ActivityManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.liaocl.careeyes.service.AppVisibility
import org.liaocl.careeyes.service.EyeCareService
import org.liaocl.careeyes.ui.AppRoot
import org.liaocl.careeyes.ui.theme.CareEyesTheme
import org.liaocl.careeyes.util.PermissionUtils

class MainActivity : ComponentActivity() {
    private var hideFromRecents: Boolean = true

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val settings by appContainer.settingsRepository.settings.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val dark = when (settings.darkMode) {
                "light" -> false
                "dark" -> true
                else -> systemDark
            }
            SideEffect {
                WindowInsetsControllerCompat(window, window.decorView).apply {
                    isAppearanceLightStatusBars = !dark
                    isAppearanceLightNavigationBars = !dark
                }
            }
            LaunchedEffect(settings.hideFromRecents) {
                hideFromRecents = settings.hideFromRecents
                applyRecentsSetting()
            }

            CareEyesTheme(settings) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppRoot(
                        container = appContainer,
                        onRequestNotifications = ::requestNotificationPermission,
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.onScreenStarted()
        EyeCareService.start(this, EyeCareService.ACTION_ENSURE)
    }

    override fun onPostResume() {
        super.onPostResume()
        applyRecentsSetting()
    }

    override fun onStop() {
        AppVisibility.onScreenStopped()
        super.onStop()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !PermissionUtils.hasNotificationPermission(this)
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            PermissionUtils.openNotificationSettings(this)
        }
    }

    private fun applyRecentsSetting() {
        runCatching {
            getSystemService(ActivityManager::class.java)?.appTasks?.forEach { task ->
                // Only the main task follows the setting; the break task stays hidden.
                val baseActivity = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    task.taskInfo?.baseActivity?.className
                } else {
                    null
                }
                if (baseActivity == null || baseActivity == MainActivity::class.java.name) {
                    task.setExcludeFromRecents(hideFromRecents)
                } else {
                    task.setExcludeFromRecents(true)
                }
            }
        }
    }
}
