package org.liaocl.careeyes

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.liaocl.careeyes.service.shouldPauseSession

class MediaPausePolicyTest {
    @Test
    fun pausesOnlyTheForegroundAppSession() {
        assertTrue(shouldPauseSession("com.video.app", "com.video.app"))
    }

    @Test
    fun leavesBackgroundMusicPlaying() {
        assertFalse(shouldPauseSession("com.music.app", "com.video.app"))
    }

    @Test
    fun pausesNothingWhenForegroundIsUnknown() {
        assertFalse(shouldPauseSession("com.music.app", null))
    }
}
