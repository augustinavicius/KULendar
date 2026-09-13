package io.github.augustinavicius.kulendar.update

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import io.github.augustinavicius.kulendar.MainActivity
import io.github.augustinavicius.kulendar.R

object UpdateNotifications {
    private const val CHANNEL_UPDATES = "app_updates"
    private const val ID_UPDATE = 10

    fun createChannel(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_UPDATES, context.getString(R.string.channel_updates), NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = context.getString(R.string.channel_updates_description) },
        )
    }

    fun showAvailable(context: Context, versionName: String) = show(
        context,
        title = context.getString(R.string.notification_update_available, versionName),
        text = context.getString(R.string.notification_update_available_text),
        tapIntent = openAppIntent(context),
    )

    /** Android wants the user to confirm the installation; [confirmIntent] opens the system prompt. */
    fun showConfirmInstall(context: Context, versionName: String, confirmIntent: Intent) = show(
        context,
        title = context.getString(R.string.notification_update_confirm, versionName),
        text = context.getString(R.string.notification_update_confirm_text),
        tapIntent = PendingIntent.getActivity(context, 1, confirmIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
    )

    fun showFailed(context: Context, versionName: String) = show(
        context,
        title = context.getString(R.string.notification_update_failed, versionName),
        text = context.getString(R.string.notification_update_failed_text),
        tapIntent = openAppIntent(context),
    )

    fun showInstalled(context: Context, versionName: String) = show(
        context,
        title = context.getString(R.string.notification_update_installed, versionName),
        text = null,
        tapIntent = openAppIntent(context),
    )

    fun cancel(context: Context) {
        NotificationManagerCompat.from(context).cancel(ID_UPDATE)
    }

    @SuppressLint("MissingPermission") // Checked through areNotificationsEnabled().
    private fun show(context: Context, title: String, text: String?, tapIntent: PendingIntent) {
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return
        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_stat_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(tapIntent)
            .setAutoCancel(true)
            .build()
        manager.notify(ID_UPDATE, notification)
    }

    private fun openAppIntent(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
