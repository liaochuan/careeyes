package org.liaocl.careeyes.util

import android.content.Context
import android.content.Intent

data class InstalledApp(val packageName: String, val label: String)

/**
 * Apps with a launcher entry, shown on the "apps to pause" page. Reading other packages
 * requires the MAIN/LAUNCHER `<queries>` declaration in the manifest.
 */
fun installedLaunchableApps(context: Context): List<InstalledApp> {
    val packageManager = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolved = runCatching { packageManager.queryIntentActivities(intent, 0) }
        .getOrDefault(emptyList())
    return resolved
        .mapNotNull { info ->
            val applicationInfo = info.activityInfo?.applicationInfo ?: return@mapNotNull null
            InstalledApp(
                packageName = applicationInfo.packageName,
                label = runCatching { applicationInfo.loadLabel(packageManager).toString() }
                    .getOrDefault(applicationInfo.packageName),
            )
        }
        .filter { it.packageName != context.packageName }
        .distinctBy { it.packageName }
        .sortedWith(compareBy({ it.label.lowercase() }, { it.packageName }))
}
