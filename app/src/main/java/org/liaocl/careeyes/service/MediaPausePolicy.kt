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
