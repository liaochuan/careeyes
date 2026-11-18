package org.liaocl.careeyes

import android.media.session.PlaybackState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.liaocl.careeyes.data.DEFAULT_PAUSE_APP_PACKAGES
import org.liaocl.careeyes.service.shouldPauseSession
import org.liaocl.careeyes.service.shouldPauseState
import org.liaocl.careeyes.service.shouldRecordPausedState
import org.liaocl.careeyes.service.shouldResumeState

class MediaPausePolicyTest {
    @Test
    fun pausesOnlyTheForegroundAppSession() {
        assertTrue(shouldPauseSession("com.video.app", "com.video.app", setOf("com.video.app")))
    }

    @Test
    fun leavesBackgroundMusicPlaying() {
        assertFalse(shouldPauseSession("com.music.app", "com.video.app", setOf("com.video.app")))
    }

    @Test
    fun pausesNothingWhenForegroundIsUnknown() {
        assertFalse(shouldPauseSession("com.music.app", null, setOf("com.music.app")))
    }

    @Test
    fun pausesOnlyPackagesInTheAllowList() {
        assertTrue(shouldPauseSession("com.video.app", "com.video.app", setOf("com.video.app")))
        assertFalse(shouldPauseSession("com.music.app", "com.music.app", setOf("com.video.app")))
        assertFalse(shouldPauseSession("com.video.app", "com.video.app", emptySet()))
    }

    @Test
    fun defaultsCoverCommonVideoApps() {
        assertTrue("com.ss.android.ugc.aweme.lite" in DEFAULT_PAUSE_APP_PACKAGES)
        assertTrue("tv.danmaku.bili" in DEFAULT_PAUSE_APP_PACKAGES)
        assertFalse("com.salt.music" in DEFAULT_PAUSE_APP_PACKAGES)
    }

    @Test
    fun pausesPlayingSessions() {
        assertTrue(shouldPauseState(PlaybackState.STATE_PLAYING))
        assertTrue(shouldPauseState(PlaybackState.STATE_BUFFERING))
    }

    /** Video apps such as Douyin keep their session in STOPPED/NONE while a video plays. */
    @Test
    fun pausesSessionsWhoseStateIsUnreliable() {
        assertTrue(shouldPauseState(PlaybackState.STATE_STOPPED))
        assertTrue(shouldPauseState(PlaybackState.STATE_NONE))
        assertTrue(shouldPauseState(null))
    }

    @Test
    fun skipsSessionsThatAreAlreadyPausedOrBroken() {
        assertFalse(shouldPauseState(PlaybackState.STATE_PAUSED))
        assertFalse(shouldPauseState(PlaybackState.STATE_ERROR))
    }

    @Test
    fun resumesOnlySessionsThatAreStillPaused() {
        assertTrue(shouldResumeState(PlaybackState.STATE_PAUSED))
    }

    @Test
    fun leavesPlayingBufferingAndEndedSessionsAlone() {
        assertFalse(shouldResumeState(PlaybackState.STATE_PLAYING))
        assertFalse(shouldResumeState(PlaybackState.STATE_BUFFERING))
        assertFalse(shouldResumeState(PlaybackState.STATE_STOPPED))
        assertFalse(shouldResumeState(PlaybackState.STATE_NONE))
        assertFalse(shouldResumeState(PlaybackState.STATE_ERROR))
        assertFalse(shouldResumeState(null))
    }

    @Test
    fun recordsOnlySessionsThatWerePlaying() {
        assertTrue(shouldRecordPausedState(PlaybackState.STATE_PLAYING))
        assertTrue(shouldRecordPausedState(PlaybackState.STATE_BUFFERING))
        assertFalse(shouldRecordPausedState(PlaybackState.STATE_PAUSED))
        assertFalse(shouldRecordPausedState(PlaybackState.STATE_STOPPED))
        assertFalse(shouldRecordPausedState(PlaybackState.STATE_NONE))
        assertFalse(shouldRecordPausedState(PlaybackState.STATE_ERROR))
        assertFalse(shouldRecordPausedState(null))
    }
}
