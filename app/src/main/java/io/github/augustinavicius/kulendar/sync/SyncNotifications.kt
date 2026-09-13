package io.github.augustinavicius.kulendar.sync

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.ForegroundInfo
import io.github.augustinavicius.kulendar.MainActivity
import io.github.augustinavicius.kulendar.R
import io.github.augustinavicius.kulendar.data.settings.SyncRecord
import io.github.augustinavicius.kulendar.data.settings.SyncStatus

object SyncNotifications {
    private const val CHANNEL_PROBLEMS = "sync_problems"
    private const val CHANNEL_PROGRESS = "sync_progress"
    private const val ID_PROBLEM = 1
    private const val ID_PROGRESS = 2

    fun createChannels(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannels(
            listOf(
                NotificationChannel(
                    CHANNEL_PROBLEMS,
                    context.getString(R.string.channel_problems),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply { description = context.getString(R.string.channel_problems_description) },
                NotificationChannel(
                    CHANNEL_PROGRESS,
                    context.getString(R.string.channel_progress),
                    NotificationManager.IMPORTANCE_MIN,
                ),
            ),
        )
    }

    /** Tells the user once when syncing gets stuck on something only they can fix, and clears it after a success. */
    fun onSyncFinished(context: Context, previous: SyncRecord?, record: SyncRecord) {
        when {
            record.status == SyncStatus.SUCCESS -> NotificationManagerCompat.from(context).cancel(ID_PROBLEM)
            record.status.needsAttention && record.status != previous?.status -> showProblem(context, record.status)
        }
    }

    @SuppressLint("MissingPermission") // Checked through areNotificationsEnabled().
    private fun showProblem(context: Context, status: SyncStatus) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val openApp = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val text = context.getString(status.messageRes())
        val notification = NotificationCompat.Builder(context, CHANNEL_PROBLEMS)
            .setSmallIcon(R.drawable.ic_stat_sync)
            .setContentTitle(context.getString(R.string.notification_problem_title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
        manager.notify(ID_PROBLEM, notification)
    }

    fun foregroundInfo(context: Context): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, CHANNEL_PROGRESS)
            .setSmallIcon(R.drawable.ic_stat_sync)
            .setContentTitle(context.getString(R.string.notification_syncing))
            .setOngoing(true)
            .setSilent(true)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(ID_PROGRESS, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(ID_PROGRESS, notification)
        }
    }
}

@StringRes
fun SyncStatus.messageRes(): Int = when (this) {
    SyncStatus.SUCCESS -> R.string.status_success
    SyncStatus.NOT_SIGNED_IN -> R.string.status_not_signed_in
    SyncStatus.NO_CALENDAR -> R.string.status_no_calendar
    SyncStatus.INVALID_CREDENTIALS -> R.string.status_invalid_credentials
    SyncStatus.CALENDAR_MISSING -> R.string.status_calendar_missing
    SyncStatus.NO_PERMISSION -> R.string.status_no_permission
    SyncStatus.PROTOCOL_ERROR -> R.string.status_protocol_error
    SyncStatus.NETWORK_ERROR -> R.string.status_network_error
    SyncStatus.SERVER_ERROR -> R.string.status_server_error
    SyncStatus.FAILED -> R.string.status_failed
}
