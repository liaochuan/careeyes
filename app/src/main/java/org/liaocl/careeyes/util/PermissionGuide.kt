package org.liaocl.careeyes.util

/**
 * Permissions CareEyes guides the user through. [key] is persisted in settings so dismissals
 * survive renames of the enum entries.
 */
enum class CarePermission(val key: String) {
    NOTIFICATIONS("notifications"),
    OVERLAY("overlay"),
    BATTERY("battery"),
    MEDIA_ACCESS("media_access"),
    USAGE_ACCESS("usage_access"),
    ;

    /** Optional permissions are never required to finish onboarding. */
    val isOptional: Boolean get() = this == MEDIA_ACCESS || this == USAGE_ACCESS
}

/** Snapshot of the permissions the app cares about, taken when the UI resumes. */
data class PermissionSnapshot(
    val notifications: Boolean = false,
    val overlay: Boolean = false,
    val battery: Boolean = false,
    val mediaAccess: Boolean = false,
    val usageAccess: Boolean = false,
    /** Not part of the guide list: Android 14+ may withhold full-screen notifications. */
    val fullScreenIntent: Boolean = true,
) {
    fun isGranted(permission: CarePermission): Boolean = when (permission) {
        CarePermission.NOTIFICATIONS -> notifications
        CarePermission.OVERLAY -> overlay
        CarePermission.BATTERY -> battery
        CarePermission.MEDIA_ACCESS -> mediaAccess
        CarePermission.USAGE_ACCESS -> usageAccess
    }

    /** Missing permissions in guide order; optional ones come last. */
    fun missing(): List<CarePermission> = CarePermission.entries.filterNot { isGranted(it) }
}

/**
 * The home card stays hidden once its permissions were dismissed, and comes back only when a
 * permission outside the dismissed set goes missing (for example after the user revokes it).
 */
fun shouldShowPermissionCard(
    snapshot: PermissionSnapshot,
    dismissedKeys: Set<String>,
): Boolean {
    val missing = snapshot.missing()
    if (missing.isEmpty()) return false
    return missing.any { it.key !in dismissedKeys }
}

/**
 * Drops dismissal records for permissions that are currently granted (and unknown keys). A key
 * that is dropped this way can prompt again if the user revokes the permission later.
 */
fun pruneDismissed(
    snapshot: PermissionSnapshot,
    dismissedKeys: Set<String>,
): Set<String> = dismissedKeys.filterTo(mutableSetOf()) { key ->
    CarePermission.entries.any { it.key == key && !snapshot.isGranted(it) }
}
