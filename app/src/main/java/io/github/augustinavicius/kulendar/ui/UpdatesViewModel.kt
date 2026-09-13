package io.github.augustinavicius.kulendar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.augustinavicius.kulendar.BuildConfig
import io.github.augustinavicius.kulendar.KulendarApp
import io.github.augustinavicius.kulendar.update.AvailableUpdate
import io.github.augustinavicius.kulendar.update.UpdateChannel
import io.github.augustinavicius.kulendar.update.UpdateSettings
import io.github.augustinavicius.kulendar.update.UpdateState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class UpdatesUiState(
    val installedVersionName: String = BuildConfig.VERSION_NAME,
    val installedVersionCode: Long = BuildConfig.VERSION_CODE.toLong(),
    /** How this build was made: "stable", "development" or "local". */
    val buildChannel: String = BuildConfig.UPDATE_CHANNEL,
    val commit: String = BuildConfig.GIT_COMMIT,
    val settings: UpdateSettings? = null,
    val state: UpdateState = UpdateState.Idle,
    val canInstallPackages: Boolean = true,
)

class UpdatesViewModel(app: KulendarApp) : ViewModel() {

    private val container = app.container
    private val canInstallPackages = MutableStateFlow(container.apkInstaller.canInstallPackages)

    val state: StateFlow<UpdatesUiState> = combine(
        container.updatePreferences.settings,
        container.updater.state,
        canInstallPackages,
    ) { settings, updateState, canInstall ->
        UpdatesUiState(settings = settings, state = updateState, canInstallPackages = canInstall)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UpdatesUiState())

    /** Refreshes the install permission, and checks for updates if the last check is old or found an update. */
    fun onResume() {
        canInstallPackages.value = container.apkInstaller.canInstallPackages
        viewModelScope.launch {
            val current = container.updater.state.value
            if (current !is UpdateState.Idle && current !is UpdateState.Failed) return@launch
            val last = container.updatePreferences.current().lastCheck
            val stale = last == null || System.currentTimeMillis() - last.checkedAt > STALE_AFTER_MS
            // The update found before the app restarted has to be looked up again to be installable.
            val knownUpdate = current is UpdateState.Idle && (last?.latestVersionCode ?: 0) > BuildConfig.VERSION_CODE
            if (stale || knownUpdate) container.updater.check()
        }
    }

    fun checkNow() {
        viewModelScope.launch { container.updater.check() }
    }

    fun setChannel(channel: UpdateChannel) {
        viewModelScope.launch {
            container.updatePreferences.setChannel(channel)
            container.updater.check()
        }
    }

    fun setAutoInstall(enabled: Boolean) {
        viewModelScope.launch { container.updatePreferences.setAutoInstall(enabled) }
    }

    /** Runs in the application scope, so leaving the screen does not cancel the download. */
    fun install(update: AvailableUpdate) {
        container.applicationScope.launch { container.updater.downloadAndInstall(update) }
    }

    companion object {
        private const val STALE_AFTER_MS = 60 * 60 * 1000L

        val Factory = viewModelFactory {
            initializer { UpdatesViewModel(this[APPLICATION_KEY] as KulendarApp) }
        }
    }
}
