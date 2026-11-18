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
     * Returns the number of sessions that accepted pause.
     */
    fun pausePlaying(context: Context, pausePackages: Set<String>, debugLog: Boolean): Int {
        val log: (String) -> Unit = { message ->
            if (debugLog) {
                Log.i(TAG, "mediaPause: $message")
                MediaPauseJournal.append(context, message)
            }
        }
        if (pausePackages.isEmpty()) {
            log("no apps enabled")
            return 0
        }
        if (!NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)) {
            log("notification access missing")
            return 0
        }
        val foregroundPackage = ForegroundApp.packageName(context)
        if (foregroundPackage == null) {
            log("foreground unknown")
            return 0
        }
        val manager = context.getSystemService(MediaSessionManager::class.java)
        if (manager == null) {
            log("no session manager")
            return 0
        }
        val listener = ComponentName(context, MediaPauseService::class.java)
        val sessions = try {
            manager.getActiveSessions(listener)
        } catch (error: Exception) {
            if (debugLog) {
                Log.w(TAG, "mediaPause: unable to list media sessions", error)
                MediaPauseJournal.append(context, "unable to list media sessions: ${error.javaClass.simpleName}")
            }
            return 0
        }
        var paused = 0
        sessions.forEach { controller: MediaController ->
            val state = runCatching { controller.playbackState?.state }.getOrNull()
            val allowed = shouldPauseSession(controller.packageName, foregroundPackage, pausePackages)
            if (allowed && shouldPauseState(state)) {
                val accepted = runCatching { controller.transportControls.pause() }.isSuccess
                log("pause ${controller.packageName} state=$state ok=$accepted")
                if (accepted) paused += 1
            } else {
                log("skip ${controller.packageName} state=$state allowed=$allowed")
            }
        }
        log("foreground=$foregroundPackage sessions=${sessions.size} paused=$paused")
        return paused
    }
}
