package org.liaocl.careeyes.service

import android.media.session.PlaybackState

/**
 * Decides whether a media session may be paused when a break starts.
 *
 * Only the app that is currently on screen is paused, and only when the user allowed that app
 * on the "apps to pause" page, so music playing in the background keeps going.
 */
fun shouldPauseSession(
    sessionPackage: String,
    foregroundPackage: String?,
    pausePackages: Set<String>,
): Boolean = foregroundPackage != null &&
    sessionPackage == foregroundPackage &&
    foregroundPackage in pausePackages

/**
 * Decides whether the reported playback state still allows a pause request.
 *
 * Some video apps keep their session in [PlaybackState.STATE_STOPPED] or
 * [PlaybackState.STATE_NONE] while a video is actually playing, so those states cannot be
 * used to skip the request; only states that prove playback is already stopped are skipped.
 */
fun shouldPauseState(state: Int?): Boolean =
    state != PlaybackState.STATE_PAUSED && state != PlaybackState.STATE_ERROR

/**
 * Decides whether a session that accepted pause is remembered for resume.
 *
 * Only sessions that were actually playing are remembered, so a video that had already ended
 * is not restarted when the break finishes. Apps whose session never reports PLAYING/BUFFERING
 * may be paused but cannot be resumed automatically.
 */
fun shouldRecordPausedState(state: Int?): Boolean =
    state == PlaybackState.STATE_PLAYING || state == PlaybackState.STATE_BUFFERING

/**
 * Decides whether a remembered session may be resumed when the break ends.
 *
 * Only a session that is still paused is resumed. Sessions that already play again (Douyin
 * restarts by itself) or have ended meanwhile are left alone.
 */
fun shouldResumeState(state: Int?): Boolean =
    state == PlaybackState.STATE_PAUSED
