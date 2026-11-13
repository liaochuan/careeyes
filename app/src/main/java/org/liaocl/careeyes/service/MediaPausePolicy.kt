package org.liaocl.careeyes.service

/**
 * Decides whether a media session may be paused when a break starts. Only the app that is
 * currently on screen is paused, so music playing in the background keeps going.
 */
fun shouldPauseSession(sessionPackage: String, foregroundPackage: String?): Boolean =
    foregroundPackage != null && sessionPackage == foregroundPackage
