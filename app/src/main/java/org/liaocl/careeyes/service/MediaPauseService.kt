package org.liaocl.careeyes.service

import android.service.notification.NotificationListenerService

/**
 * Exists only to make this app eligible to read active MediaSessions.
 * No notification content is read, stored, or transmitted.
 */
class MediaPauseService : NotificationListenerService()
