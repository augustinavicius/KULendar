package io.github.augustinavicius.kulendar.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.work.WorkInfo
import io.github.augustinavicius.kulendar.KulendarApp
import io.github.augustinavicius.kulendar.data.calendar.DeviceCalendar
import io.github.augustinavicius.kulendar.data.ku.KuInvalidCredentialsException
import io.github.augustinavicius.kulendar.data.ku.KuNetworkException
import io.github.augustinavicius.kulendar.data.ku.KuServerException
import io.github.augustinavicius.kulendar.data.security.AccountInfo
import io.github.augustinavicius.kulendar.data.settings.AppSettings
import io.github.augustinavicius.kulendar.data.settings.SelectedCalendar
import io.github.augustinavicius.kulendar.data.settings.SettingsRepository
import io.github.augustinavicius.kulendar.data.settings.SyncTrigger
import io.github.augustinavicius.kulendar.sync.SyncScheduler
import io.github.augustinavicius.kulendar.system.BackgroundHealth
import io.github.augustinavicius.kulendar.system.BackgroundHealthChecker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface SignInState {
    data object Idle : SignInState
    data object InProgress : SignInState
    data class Failed(val error: SignInError, val detail: String? = null) : SignInState
}

enum class SignInError { INVALID_CREDENTIALS, NETWORK, SERVER, UNKNOWN }

data class PendingCalendarChange(val calendar: DeviceCalendar, val previous: SelectedCalendar, val previousEventCount: Int)

sealed interface UiMessage {
    data class EventsRemoved(val count: Int) : UiMessage
}

/** Device state that is re-read whenever the app comes to the foreground. */
data class DeviceState(
    val loaded: Boolean = false,
    val hasCalendarPermission: Boolean = false,
    val calendars: List<DeviceCalendar> = emptyList(),
    val syncEnabledByCalendarId: Map<Long, Boolean> = emptyMap(),
    val health: BackgroundHealth = BackgroundHealth(),
)

data class SyncWorkState(val running: Boolean = false, val queued: Boolean = false, val nextSyncAt: Long? = null)

data class TransientState(
    val signIn: SignInState = SignInState.Idle,
    val pendingCalendarChange: PendingCalendarChange? = null,
    val message: UiMessage? = null,
)

data class MainUiState(
    val account: AccountInfo? = null,
    val settings: AppSettings = AppSettings(),
    val device: DeviceState = DeviceState(),
    val work: SyncWorkState = SyncWorkState(),
    val transient: TransientState = TransientState(),
) {
    val selectedCalendar: DeviceCalendar? = settings.calendar?.let { selected ->
        device.calendars.firstOrNull {
            it.id == selected.id && it.accountName == selected.accountName && it.accountType == selected.accountType
        }
    }
    val selectedCalendarMissing: Boolean
        get() = device.loaded && device.hasCalendarPermission && settings.calendar != null && selectedCalendar == null
    val isConfigured: Boolean
        get() = account != null && settings.calendar != null
}

class MainViewModel(private val app: KulendarApp) : ViewModel() {

    private val container = app.container
    private val device = MutableStateFlow(DeviceState())
    private val transient = MutableStateFlow(TransientState())

    val state: StateFlow<MainUiState> = combine(
        container.credentials.account,
        container.settings.settings,
        device,
        SyncScheduler.workInfos(app),
        transient,
    ) { account, settings, device, workInfos, transient ->
        MainUiState(account, settings, device, workInfos.toWorkState(), transient)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())

    /** Re-reads permissions, calendars and battery settings, which may change while the app is in the background. */
    fun refreshDeviceState() {
        viewModelScope.launch {
            val calendars = container.calendars
            val hasPermission = calendars.hasPermission()
            val available = if (hasPermission) runCatching { calendars.writableCalendars() }.getOrDefault(emptyList()) else emptyList()
            device.value = DeviceState(
                loaded = true,
                hasCalendarPermission = hasPermission,
                calendars = available,
                syncEnabledByCalendarId = available.associate { it.id to calendars.isSyncEnabled(it) },
                health = BackgroundHealthChecker.check(app),
            )
        }
    }

    fun signIn(uid: String, password: String) {
        if (transient.value.signIn == SignInState.InProgress) return
        transient.update { it.copy(signIn = SignInState.InProgress) }
        viewModelScope.launch {
            val result = try {
                container.auth.signIn(uid.trim(), password)
                SignInState.Idle
            } catch (e: CancellationException) {
                throw e
            } catch (e: KuInvalidCredentialsException) {
                SignInState.Failed(SignInError.INVALID_CREDENTIALS)
            } catch (e: KuNetworkException) {
                SignInState.Failed(SignInError.NETWORK)
            } catch (e: KuServerException) {
                SignInState.Failed(SignInError.SERVER)
            } catch (e: Exception) {
                SignInState.Failed(SignInError.UNKNOWN, e.message)
            }
            transient.update { it.copy(signIn = result) }
            if (result == SignInState.Idle) onSyncSettingsChanged()
        }
    }

    fun signOut() {
        viewModelScope.launch {
            SyncScheduler.cancelAll(app)
            container.auth.signOut()
            SyncScheduler.updatePeriodicSync(app, container)
        }
    }

    /** Selects [calendar], asking first what to do with events synced into the previous calendar. */
    fun requestCalendarChange(calendar: DeviceCalendar) {
        viewModelScope.launch {
            val previous = container.settings.current().calendar
            val previousCount = if (previous != null && previous.id != calendar.id && container.calendars.hasPermission()) {
                runCatching { container.calendars.managedEvents(previous.id).size }.getOrDefault(0)
            } else {
                0
            }
            if (previous != null && previousCount > 0) {
                transient.update { it.copy(pendingCalendarChange = PendingCalendarChange(calendar, previous, previousCount)) }
            } else {
                applyCalendarChange(calendar, removeFrom = null)
            }
        }
    }

    fun confirmCalendarChange(removePreviousEvents: Boolean) {
        val pending = transient.value.pendingCalendarChange ?: return
        transient.update { it.copy(pendingCalendarChange = null) }
        viewModelScope.launch {
            applyCalendarChange(pending.calendar, removeFrom = pending.previous.takeIf { removePreviousEvents })
        }
    }

    fun cancelCalendarChange() {
        transient.update { it.copy(pendingCalendarChange = null) }
    }

    private suspend fun applyCalendarChange(calendar: DeviceCalendar, removeFrom: SelectedCalendar?) {
        if (removeFrom != null) runCatching { container.calendars.removeManagedEvents(removeFrom.id) }
        container.settings.setCalendar(
            SelectedCalendar(calendar.id, calendar.displayName, calendar.accountName, calendar.accountType),
        )
        onSyncSettingsChanged()
    }

    fun setPastDays(days: Int) = updateSettings(resync = true) { setPastDays(days) }

    fun setFutureDays(days: Int) = updateSettings(resync = true) { setFutureDays(days) }

    fun setAutoSync(enabled: Boolean) = updateSettings(resync = enabled) { setAutoSync(enabled) }

    fun setIntervalMinutes(minutes: Int) = updateSettings(resync = false) { setIntervalMinutes(minutes) }

    fun setShowAllCalendars(show: Boolean) = updateSettings(resync = false) { setShowAllCalendars(show) }

    fun syncNow() {
        SyncScheduler.syncNow(app, SyncTrigger.MANUAL)
    }

    fun removeSyncedEvents() {
        viewModelScope.launch {
            val calendar = container.settings.current().calendar ?: return@launch
            val removed = runCatching { container.calendars.removeManagedEvents(calendar.id) }.getOrDefault(0)
            transient.update { it.copy(message = UiMessage.EventsRemoved(removed)) }
        }
    }

    fun messageShown() {
        transient.update { it.copy(message = null) }
    }

    private fun updateSettings(resync: Boolean, change: suspend SettingsRepository.() -> Unit) {
        viewModelScope.launch {
            container.settings.change()
            if (resync) onSyncSettingsChanged() else SyncScheduler.updatePeriodicSync(app, container)
        }
    }

    private suspend fun onSyncSettingsChanged() {
        SyncScheduler.updatePeriodicSync(app, container)
        if (container.settings.current().calendar != null && container.auth.isSignedIn()) {
            SyncScheduler.syncNow(app, SyncTrigger.SETTINGS_CHANGED, restart = true)
        }
    }

    private fun List<WorkInfo>.toWorkState(): SyncWorkState {
        val periodic = filter { SyncScheduler.TAG_PERIODIC in it.tags }
        val oneTime = filterNot { SyncScheduler.TAG_PERIODIC in it.tags }
        return SyncWorkState(
            running = any { it.state == WorkInfo.State.RUNNING },
            queued = oneTime.any { it.state == WorkInfo.State.ENQUEUED },
            nextSyncAt = periodic.firstOrNull { it.state == WorkInfo.State.ENQUEUED }
                ?.nextScheduleTimeMillis
                ?.takeIf { it != Long.MAX_VALUE },
        )
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { MainViewModel(this[APPLICATION_KEY] as KulendarApp) }
        }
    }
}
