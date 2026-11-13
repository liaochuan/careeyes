package org.liaocl.careeyes.service

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.core.app.NotificationManagerCompat

object MediaPauseController {
    /**
     * Pauses the media session of the app currently on screen, if it is playing.
     *
     * Only the foreground app is touched so background music is not interrupted. Requires
     * notification access to list sessions and usage access to know the foreground app;
     * without either, nothing is paused. Returns the number of sessions that accepted pause.
     */
    fun pausePlaying(context: Context): Int {
        if (!NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)) {
            return 0
        }
        val foregroundPackage = ForegroundApp.packageName(context) ?: return 0
        val manager = context.getSystemService(MediaSessionManager::class.java) ?: return 0
        val listener = ComponentName(context, MediaPauseService::class.java)
        val sessions = runCatching { manager.getActiveSessions(listener) }.getOrNull() ?: return 0
        var paused = 0
        sessions.forEach { controller: MediaController ->
            val playing = runCatching {
                controller.playbackState?.state == PlaybackState.STATE_PLAYING
            }.getOrDefault(false)
            if (playing && shouldPauseSession(controller.packageName, foregroundPackage)) {
                val accepted = runCatching { controller.transportControls.pause() }.isSuccess
                if (accepted) paused += 1
            }
        }
        return paused
    }
}
