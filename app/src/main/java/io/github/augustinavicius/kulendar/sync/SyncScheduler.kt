package io.github.augustinavicius.kulendar.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import io.github.augustinavicius.kulendar.AppContainer
import io.github.augustinavicius.kulendar.data.settings.AppSettings
import io.github.augustinavicius.kulendar.data.settings.SyncTrigger
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Schedules syncs with WorkManager. Scheduled work survives app restarts, reboots and updates, and
 * with the battery optimization exemption it also runs while the phone dozes.
 */
object SyncScheduler {
    private const val PERIODIC_WORK = "kulendar-periodic-sync"
    private const val ONE_TIME_WORK = "kulendar-sync"
    const val TAG_PERIODIC = "kulendar-periodic"
    internal const val KEY_TRIGGER = "trigger"
    private const val BACKOFF_SECONDS = 30L

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * Schedules or cancels the periodic sync to match the settings.
     *
     * @return whether automatic syncing is active.
     */
    suspend fun updatePeriodicSync(context: Context, container: AppContainer): Boolean {
        val settings = container.settings.current()
        val workManager = WorkManager.getInstance(context)
        val active = settings.autoSync && settings.calendar != null && container.auth.isSignedIn()
        if (!active) {
            workManager.cancelUniqueWork(PERIODIC_WORK)
            return false
        }
        val interval = settings.intervalMinutes.coerceAtLeast(AppSettings.MIN_INTERVAL_MINUTES).toLong()
        val request = PeriodicWorkRequestBuilder<SyncWorker>(interval, TimeUnit.MINUTES)
            // Whatever enables syncing also starts a one-time sync, so the first periodic run can wait.
            .setInitialDelay(interval, TimeUnit.MINUTES)
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .setInputData(workDataOf(KEY_TRIGGER to SyncTrigger.PERIODIC.name))
            .addTag(TAG_PERIODIC)
            .build()
        // UPDATE keeps the current schedule position when only the settings changed.
        workManager.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        return true
    }

    /** Syncs as soon as there is network. With [restart], a sync that is already running is restarted. */
    fun syncNow(context: Context, trigger: SyncTrigger, restart: Boolean = false) {
        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraints)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, BACKOFF_SECONDS, TimeUnit.SECONDS)
            .setInputData(workDataOf(KEY_TRIGGER to trigger.name))
            .build()
        val policy = if (restart) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
        WorkManager.getInstance(context).enqueueUniqueWork(ONE_TIME_WORK, policy, request)
    }

    fun cancelAll(context: Context) {
        WorkManager.getInstance(context).apply {
            cancelUniqueWork(PERIODIC_WORK)
            cancelUniqueWork(ONE_TIME_WORK)
        }
    }

    fun workInfos(context: Context): Flow<List<WorkInfo>> {
        val workManager = WorkManager.getInstance(context)
        return combine(
            workManager.getWorkInfosForUniqueWorkFlow(PERIODIC_WORK),
            workManager.getWorkInfosForUniqueWorkFlow(ONE_TIME_WORK),
        ) { periodic, oneTime -> periodic + oneTime }
    }
}
