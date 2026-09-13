package io.github.augustinavicius.kulendar.sync

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import io.github.augustinavicius.kulendar.KulendarApp
import io.github.augustinavicius.kulendar.data.settings.SyncStatus
import io.github.augustinavicius.kulendar.data.settings.SyncTrigger

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as KulendarApp).container
        val trigger = inputData.getString(SyncScheduler.KEY_TRIGGER)
            ?.let { name -> SyncTrigger.entries.firstOrNull { it.name == name } }
            ?: SyncTrigger.PERIODIC
        val previous = container.settings.current().lastRecord
        val record = container.syncEngine.sync(trigger)
        SyncNotifications.onSyncFinished(applicationContext, previous, record)
        return when {
            record.status == SyncStatus.SUCCESS -> Result.success()
            record.status.isTransient && runAttemptCount < MAX_ATTEMPTS -> Result.retry()
            else -> Result.failure()
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = SyncNotifications.foregroundInfo(applicationContext)

    private companion object {
        const val MAX_ATTEMPTS = 4
    }
}
