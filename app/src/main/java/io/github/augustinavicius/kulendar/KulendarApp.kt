package io.github.augustinavicius.kulendar

import android.app.Application
import io.github.augustinavicius.kulendar.sync.SyncNotifications
import io.github.augustinavicius.kulendar.sync.SyncScheduler
import io.github.augustinavicius.kulendar.update.UpdateNotifications
import io.github.augustinavicius.kulendar.update.UpdateWorker
import kotlinx.coroutines.launch

class KulendarApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        SyncNotifications.createChannels(this)
        UpdateNotifications.createChannel(this)
        UpdateWorker.schedule(this)
        container.applicationScope.launch {
            container.updatePreferences.rememberChannel()
            SyncScheduler.updatePeriodicSync(this@KulendarApp, container)
        }
    }
}
