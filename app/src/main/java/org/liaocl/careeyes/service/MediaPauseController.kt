package org.liaocl.careeyes.service

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import org.liaocl.careeyes.util.MediaPauseJournal

object MediaPauseController {
    private const val TAG = "CareEyes"

    /**
     * Pauses the media session of the app currently on screen, but only for the packages the
     * user allowed on the "apps to pause" page.
     *
     * Only the foreground app is touched so background music is not interrupted. Requires
     * notification access to list sessions and usage access to know the foreground app;
     * without either, nothing is paused. [debugLog] turns on diagnostics for the pause
     * decisions; when it is off this method writes no log output.
     *
     * Returns the packages whose sessions accepted pause while actually playing, so they can be
     * resumed when the break ends.
     */
    fun pausePlaying(context: Context, pausePackages: Set<String>, debugLog: Boolean): Set<String> {
        val log: (String) -> Unit = { message ->
            if (debugLog) {
                Log.i(TAG, "mediaPause: $message")
                MediaPauseJournal.append(context, message)
            }
        }
        if (pausePackages.isEmpty()) {
            log("no apps enabled")
            return emptySet()
        }
        if (!NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)) {
            log("notification access missing")
            return emptySet()
        }
        val foregroundPackage = ForegroundApp.packageName(context)
        if (foregroundPackage == null) {
            log("foreground unknown")
            return emptySet()
        }
        val manager = context.getSystemService(MediaSessionManager::class.java)
        if (manager == null) {
            log("no session manager")
            return emptySet()
        }
        val listener = ComponentName(context, MediaPauseService::class.java)
        val sessions = try {
            manager.getActiveSessions(listener)
        } catch (error: Exception) {
            if (debugLog) {
                Log.w(TAG, "mediaPause: unable to list media sessions", error)
                MediaPauseJournal.append(context, "unable to list media sessions: ${error.javaClass.simpleName}")
            }
            return emptySet()
        }
        val resumable = mutableSetOf<String>()
        var pausedCount = 0
        sessions.forEach { controller: MediaController ->
            val state = runCatching { controller.playbackState?.state }.getOrNull()
            val allowed = shouldPauseSession(controller.packageName, foregroundPackage, pausePackages)
            if (allowed && shouldPauseState(state)) {
                val accepted = runCatching { controller.transportControls.pause() }.isSuccess
                log("pause ${controller.packageName} state=$state ok=$accepted")
                if (accepted) {
                    pausedCount += 1
                    if (shouldRecordPausedState(state)) resumable += controller.packageName
                }
            } else {
                log("skip ${controller.packageName} state=$state allowed=$allowed")
            }
        }
        log(
            "foreground=$foregroundPackage sessions=${sessions.size} " +
                "paused=$pausedCount resumable=${resumable.size}",
        )
        return resumable
    }

    /**
     * Plays the sessions of [packages] that CareEyes paused when the break started. Sessions that
     * already play again are skipped, so apps that resume on their own are not disturbed.
     *
     * Returns the packages whose sessions accepted the play request.
     */
    fun resumePlaying(context: Context, packages: Set<String>, debugLog: Boolean): Set<String> {
        val log: (String) -> Unit = { message ->
            if (debugLog) {
                Log.i(TAG, "mediaResume: $message")
                MediaPauseJournal.append(context, message)
            }
        }
        if (packages.isEmpty()) return emptySet()
        if (!NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)) {
            log("notification access missing")
            return emptySet()
        }
        val manager = context.getSystemService(MediaSessionManager::class.java)
        if (manager == null) {
            log("no session manager")
            return emptySet()
        }
        val listener = ComponentName(context, MediaPauseService::class.java)
        val sessions = try {
            manager.getActiveSessions(listener)
        } catch (error: Exception) {
            if (debugLog) {
                Log.w(TAG, "mediaResume: unable to list media sessions", error)
                MediaPauseJournal.append(context, "unable to list media sessions: ${error.javaClass.simpleName}")
            }
            return emptySet()
        }
        val resumed = mutableSetOf<String>()
        sessions.forEach { controller: MediaController ->
            if (controller.packageName !in packages) return@forEach
            val state = runCatching { controller.playbackState?.state }.getOrNull()
            if (!shouldResumeState(state)) {
                log("keep ${controller.packageName} state=$state")
                return@forEach
            }
            val accepted = runCatching { controller.transportControls.play() }.isSuccess
            log("resume ${controller.packageName} state=$state ok=$accepted")
            if (accepted) resumed += controller.packageName
        }
        log("packages=${packages.size} sessions=${sessions.size} resumed=${resumed.size}")
        return resumed
    }
}
