package io.github.augustinavicius.kulendar.sync

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.augustinavicius.kulendar.BuildConfig
import io.github.augustinavicius.kulendar.KulendarApp
import io.github.augustinavicius.kulendar.data.settings.SyncTrigger
import kotlinx.coroutines.launch

/**
 * Re-arms syncing after a reboot or app update and catches up on what was missed in the meantime.
 * After an update it also confirms the installation the updater started.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val trigger = when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED -> SyncTrigger.BOOT
            Intent.ACTION_MY_PACKAGE_REPLACED -> SyncTrigger.APP_UPDATED
            else -> return
        }
        val container = (context.applicationContext as KulendarApp).container
        val pendingResult = goAsync()
        container.applicationScope.launch {
            try {
                if (trigger == SyncTrigger.APP_UPDATED) container.updater.onAppUpdated(BuildConfig.VERSION_NAME)
                if (SyncScheduler.updatePeriodicSync(context, container)) {
                    SyncScheduler.syncNow(context, trigger)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
