package org.liaocl.careeyes.util

import android.Manifest
import android.app.AppOpsManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object PermissionUtils {
    fun snapshot(context: Context): PermissionSnapshot = PermissionSnapshot(
        notifications = hasNotificationPermission(context),
        overlay = canDrawOverlays(context),
        battery = isIgnoringBatteryOptimizations(context),
        mediaAccess = hasNotificationAccess(context),
        usageAccess = hasUsageAccess(context),
        fullScreenIntent = canUseFullScreenIntent(context),
    )

    fun hasNotificationPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun canDrawOverlays(context: Context): Boolean = Settings.canDrawOverlays(context)

    fun hasNotificationAccess(context: Context): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        val manager = context.getSystemService(PowerManager::class.java) ?: return false
        return manager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Special access granted from Settings > Special app access > Usage access. */
    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openUsageAccessSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_USAGE_ACCESS_SETTINGS,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startSafely(context, intent, Settings.ACTION_USAGE_ACCESS_SETTINGS)
    }

    /** Android 14+ requires the user to allow full-screen notifications for non-alarm apps. */
    fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.canUseFullScreenIntent()
    }

    fun openFullScreenIntentSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            openNotificationSettings(context)
            return
        }
        val intent = Intent(
            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (!runCatching { context.startActivity(intent) }.isSuccess) {
            openNotificationSettings(context)
        }
    }

    fun openOverlaySettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startSafely(context, intent, Settings.ACTION_MANAGE_OVERLAY_PERMISSION)
    }

    fun openNotificationAccessSettings(context: Context) {
        startSafely(
            context,
            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
        )
    }

    fun openNotificationSettings(context: Context) {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startSafely(context, intent, Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    }

    fun requestIgnoreBatteryOptimizations(context: Context) {
        val direct = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val fallback = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val started = runCatching { context.startActivity(direct) }.isSuccess
        if (!started) {
            runCatching { context.startActivity(fallback) }
        }
    }

    fun openAppDetails(context: Context) {
        startSafely(
            context,
            Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        )
    }

    private fun startSafely(context: Context, intent: Intent, fallbackAction: String) {
        val started = runCatching { context.startActivity(intent) }.isSuccess
        if (!started) {
            runCatching {
                context.startActivity(Intent(fallbackAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
