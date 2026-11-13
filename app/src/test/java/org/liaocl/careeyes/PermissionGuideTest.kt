package org.liaocl.careeyes

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.liaocl.careeyes.util.CarePermission
import org.liaocl.careeyes.util.PermissionSnapshot
import org.liaocl.careeyes.util.pruneDismissed
import org.liaocl.careeyes.util.shouldShowPermissionCard

class PermissionGuideTest {
    private val allGranted = PermissionSnapshot(
        notifications = true,
        overlay = true,
        battery = true,
        mediaAccess = true,
        usageAccess = true,
    )

    @Test
    fun noCardWhenEverythingGranted() {
        assertFalse(shouldShowPermissionCard(allGranted, emptySet()))
    }

    @Test
    fun cardShowsWhenRequiredPermissionMissing() {
        assertTrue(shouldShowPermissionCard(allGranted.copy(overlay = false), emptySet()))
    }

    @Test
    fun cardHiddenWhenMissingKeysWereDismissed() {
        val snapshot = allGranted.copy(notifications = false, battery = false)
        assertFalse(shouldShowPermissionCard(snapshot, setOf("notifications", "battery")))
    }

    @Test
    fun cardReappearsWhenANewPermissionGoesMissing() {
        val snapshot = allGranted.copy(overlay = false, battery = false)
        assertTrue(shouldShowPermissionCard(snapshot, setOf("overlay")))
    }

    @Test
    fun cardStaysHiddenWhenMissingSetShrinks() {
        val snapshot = allGranted.copy(overlay = false)
        assertFalse(shouldShowPermissionCard(snapshot, setOf("overlay", "battery")))
    }

    @Test
    fun missingKeepsRequiredFirstAndOptionalLast() {
        val snapshot = PermissionSnapshot()
        assertEquals(
            listOf(
                CarePermission.NOTIFICATIONS,
                CarePermission.OVERLAY,
                CarePermission.BATTERY,
                CarePermission.MEDIA_ACCESS,
                CarePermission.USAGE_ACCESS,
            ),
            snapshot.missing(),
        )
    }

    @Test
    fun bothMediaPermissionsAreOptional() {
        val snapshot = allGranted.copy(mediaAccess = false, usageAccess = false)
        assertEquals(
            listOf(CarePermission.MEDIA_ACCESS, CarePermission.USAGE_ACCESS),
            snapshot.missing(),
        )
    }

    @Test
    fun optionalPermissionFailureAloneStillCountsAsMissing() {
        val snapshot = allGranted.copy(mediaAccess = false)
        assertEquals(listOf(CarePermission.MEDIA_ACCESS), snapshot.missing())
    }

    @Test
    fun dismissedKeysDropPermissionsThatBecameGranted() {
        val missingAll = PermissionSnapshot()
        val dismissed = missingAll.missing().map { it.key }.toSet()

        val granted = missingAll.copy(overlay = true)

        assertEquals(
            setOf("notifications", "battery", "media_access", "usage_access"),
            pruneDismissed(granted, dismissed),
        )
    }

    @Test
    fun revokedPermissionShowsCardAgainAfterPruning() {
        val missingAll = PermissionSnapshot()
        var dismissed = missingAll.missing().map { it.key }.toSet()

        dismissed = pruneDismissed(missingAll.copy(overlay = true), dismissed)

        val revokedAgain = missingAll.copy(overlay = false)
        assertTrue(shouldShowPermissionCard(revokedAgain, dismissed))
    }

    @Test
    fun pruningUnknownKeysDropsThem() {
        assertEquals(emptySet<String>(), pruneDismissed(allGranted, setOf("legacy_key")))
    }

    @Test
    fun snapshotDefaultsToNothingGranted() {
        val defaults = PermissionSnapshot()
        assertFalse(defaults.isGranted(CarePermission.NOTIFICATIONS))
        assertEquals(5, defaults.missing().size)
    }
}
