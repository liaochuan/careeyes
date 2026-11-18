package org.liaocl.careeyes.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import org.liaocl.careeyes.MainActivity
import org.liaocl.careeyes.R
import org.liaocl.careeyes.engine.RuntimeState
import org.liaocl.careeyes.engine.TimerMode
import org.liaocl.careeyes.util.TimeUtils
import org.liaocl.careeyes.util.durationText
import java.text.DateFormat
import java.util.Date

object NotificationHelper {
    const val CHANNEL_MAIN = "care_eyes_status"
    const val CHANNEL_PROGRESS = "care_eyes_progress"
    const val CHANNEL_BREAK = "care_eyes_break"

    const val ID_MAIN = 1001
    const val ID_PROGRESS = 1002
    const val ID_BREAK = 1003

    fun ensureChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val main = NotificationChannel(
            CHANNEL_MAIN,
            context.getString(R.string.notif_channel_main),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notif_channel_main_desc)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }

        val progress = NotificationChannel(
            CHANNEL_PROGRESS,
            context.getString(R.string.notif_channel_progress),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.notif_channel_progress_desc)
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
        }

        val breakChannel = NotificationChannel(
            CHANNEL_BREAK,
            context.getString(R.string.notif_channel_break),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.notif_channel_break_desc)
            setShowBadge(true)
            enableVibration(true)
        }

        manager.createNotificationChannels(listOf(main, progress, breakChannel))
    }

    fun buildMain(
        context: Context,
        state: RuntimeState,
        showActions: Boolean = true,
        pausedUntilEpochMs: Long = 0L,
    ): Notification {
        val builder = NotificationCompat.Builder(context, CHANNEL_MAIN)
            .setSmallIcon(R.drawable.ic_notification_eye)
            .setColor(context.getColor(R.color.ic_launcher_background))
            .setContentIntent(mainActivityIntent(context))
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

        when (state.mode) {
            TimerMode.FOCUS -> {
                builder
                    .setContentTitle(context.getString(R.string.notif_focus_title))
                    .setContentText(
                        context.getString(
                            R.string.notif_focus_tip,
                            state.presetName,
                        ),
                    )
                if (showActions) {
                    builder
                        .addAction(0, context.getString(R.string.notif_action_pause), serviceAction(context, EyeCareService.ACTION_PAUSE, 11))
                        .addAction(0, context.getString(R.string.notif_action_rest_now), serviceAction(context, EyeCareService.ACTION_START_BREAK, 12))
                }
                builder.applyChronometer(state)
            }

            TimerMode.BREAK -> {
                builder
                    .setContentTitle(context.getString(R.string.notif_break_title))
                    .setContentText(context.getString(R.string.notif_break_tip))
                if (showActions) {
                    builder.addAction(0, context.getString(R.string.notif_action_skip), serviceAction(context, EyeCareService.ACTION_SKIP_BREAK, 13))
                }
                builder.applyChronometer(state)
            }

            TimerMode.PAUSED -> {
                builder.setContentTitle(context.getString(R.string.notif_paused_title))
                if (pausedUntilEpochMs > 0L) {
                    builder
                        .setContentText(
                            context.getString(
                                R.string.notif_paused_until_text,
                                DateFormat.getTimeInstance(DateFormat.SHORT)
                                    .format(Date(pausedUntilEpochMs)),
                            ),
                        )
                        .setWhen(pausedUntilEpochMs)
                        .setShowWhen(true)
                        .setUsesChronometer(true)
                        .setChronometerCountDown(true)
                } else {
                    builder
                        .setContentText(
                            context.getString(
                                R.string.notif_paused_text,
                                TimeUtils.formatMmSs(state.remainingMs),
                            ),
                        )
                        .setShowWhen(false)
                }
                if (showActions) {
                    builder.addAction(0, context.getString(R.string.notif_action_resume), serviceAction(context, EyeCareService.ACTION_RESUME, 14))
                }
            }

            TimerMode.IDLE -> {
                builder
                    .setContentTitle(context.getString(R.string.notif_idle_title))
                    .setContentText(context.getString(R.string.notif_idle_text))
                    .setShowWhen(false)
                if (showActions) {
                    builder.addAction(0, context.getString(R.string.home_start), serviceAction(context, EyeCareService.ACTION_START, 15))
                }
            }
        }

        return builder.build()
    }

    fun buildProgress(context: Context, state: RuntimeState, percent: Int): Notification {
        val safePercent = percent.coerceIn(0, 100)
        val isBreak = state.mode == TimerMode.BREAK
        val title = if (isBreak) {
            context.getString(R.string.notif_progress_break_title, safePercent)
        } else {
            context.getString(R.string.notif_progress_title, safePercent)
        }
        val text = if (isBreak) {
            context.getString(R.string.notif_progress_break_text, TimeUtils.formatMmSs(state.remainingMs))
        } else {
            context.getString(
                R.string.notif_progress_text,
                TimeUtils.formatMmSs(state.remainingMs),
                state.presetName,
            )
        }
        return NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification_eye)
            .setColor(context.getColor(R.color.ic_launcher_background))
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(mainActivityIntent(context))
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, safePercent, false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    fun buildBreakFallback(context: Context, state: RuntimeState): Notification {
        val open = PendingIntent.getActivity(
            context,
            21,
            Intent(context, BreakActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_BREAK)
            .setSmallIcon(R.drawable.ic_notification_eye)
            .setColor(context.getColor(R.color.ic_launcher_background))
            .setContentTitle(context.getString(R.string.notif_break_alert_title))
            .setContentText(
                context.getString(
                    R.string.notif_break_alert_text,
                    state.presetName,
                    durationText(context, state.breakDurationMs / 1000L),
                ),
            )
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(0, context.getString(R.string.notif_action_start_rest), open)
            .addAction(0, context.getString(R.string.notif_action_skip), serviceAction(context, EyeCareService.ACTION_SKIP_BREAK, 22))
            .build()
    }

    private fun NotificationCompat.Builder.applyChronometer(state: RuntimeState): NotificationCompat.Builder {
        val remaining = state.remainingAt(android.os.SystemClock.elapsedRealtime())
        setWhen(System.currentTimeMillis() + remaining)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
        return this
    }

    private fun mainActivityIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        10,
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun serviceAction(context: Context, action: String, requestCode: Int): PendingIntent =
        PendingIntent.getService(
            context,
            requestCode,
            Intent(context, EyeCareService::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
