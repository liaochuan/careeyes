package org.liaocl.careeyes.service

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import androidx.core.app.NotificationManagerCompat

object MediaPauseController {
    /**
     * Pauses every remote media session that is currently playing.
     *
     * Requires notification access, otherwise MediaSessionManager throws a
     * SecurityException. Returns the number of sessions that accepted pause.
     */
    fun pausePlaying(context: Context): Int {
        if (!NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)) {
            return 0
        }
        val manager = context.getSystemService(MediaSessionManager::class.java) ?: return 0
        val listener = ComponentName(context, MediaPauseService::class.java)
        val sessions = runCatching { manager.getActiveSessions(listener) }.getOrNull() ?: return 0
        var paused = 0
        sessions.forEach { controller: MediaController ->
            val playing = runCatching {
                controller.playbackState?.state == PlaybackState.STATE_PLAYING
            }.getOrDefault(false)
            if (playing) {
                val accepted = runCatching { controller.transportControls.pause() }.isSuccess
                if (accepted) paused += 1
            }
        }
        return paused
    }
}
