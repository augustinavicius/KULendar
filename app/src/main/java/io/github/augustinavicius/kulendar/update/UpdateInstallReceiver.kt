package io.github.augustinavicius.kulendar.update

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import io.github.augustinavicius.kulendar.KulendarApp
import io.github.augustinavicius.kulendar.system.AppVisibility

/** Receives the outcome of an installation started by [ApkInstaller]. */
class UpdateInstallReceiver : BroadcastReceiver() {

    // The launched intent is the system installer's confirmation prompt, delivered to this non-exported receiver.
    @SuppressLint("UnsafeIntentLaunch")
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_STATUS) return
        val updater = (context.applicationContext as KulendarApp).container.updater
        val versionName = intent.getStringExtra(EXTRA_VERSION_NAME).orEmpty()
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java) ?: return
                val shownNow = AppVisibility.isVisible &&
                    runCatching { context.startActivity(confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
                if (!shownNow) UpdateNotifications.showConfirmInstall(context, versionName, confirmIntent)
            }
            // Usually not delivered, because installing the update replaces this process.
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> {
                val cancelledByUser = status == PackageInstaller.STATUS_FAILURE_ABORTED
                updater.onInstallFailed(cancelledByUser, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
                if (!cancelledByUser) UpdateNotifications.showFailed(context, versionName)
            }
        }
    }

    companion object {
        const val ACTION_INSTALL_STATUS = "io.github.augustinavicius.kulendar.action.INSTALL_STATUS"
        const val EXTRA_VERSION_NAME = "io.github.augustinavicius.kulendar.extra.VERSION_NAME"
    }
}
