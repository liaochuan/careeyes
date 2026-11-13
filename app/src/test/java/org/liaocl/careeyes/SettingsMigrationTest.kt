package org.liaocl.careeyes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.liaocl.careeyes.data.isLegacySettings

class SettingsMigrationTest {
    @Test
    fun storedSettingsWithoutOnboardingFlagCountAsLegacy() {
        assertTrue(isLegacySettings("""{"running":true,"notificationPermissionAsked":true}"""))
    }

    @Test
    fun storedSettingsWithOnboardingFlagAreNotLegacy() {
        assertFalse(isLegacySettings("""{"running":true,"onboardingCompleted":true}"""))
        assertFalse(isLegacySettings("""{"running":true,"onboardingCompleted":false}"""))
    }

    @Test
    fun blankOrMissingSettingsAreNotLegacy() {
        assertFalse(isLegacySettings(null))
        assertFalse(isLegacySettings(""))
    }

    @Test
    fun malformedSettingsAreNotLegacy() {
        assertFalse(isLegacySettings("not a json object"))
    }
}
