package io.github.augustinavicius.kulendar.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.augustinavicius.kulendar.R
import io.github.augustinavicius.kulendar.data.calendar.CalendarRepository
import io.github.augustinavicius.kulendar.system.BackgroundHealthChecker

@OptIn(ExperimentalMaterial3Api::class, ExperimentalComposeUiApi::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel = viewModel(factory = MainViewModel.Factory),
    updatesViewModel: UpdatesViewModel = viewModel(factory = UpdatesViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val updates by updatesViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LifecycleResumeEffect(Unit) {
        viewModel.refreshDeviceState()
        viewModel.startWatchingCalendar()
        updatesViewModel.onResume()
        onPauseOrDispose { viewModel.stopWatchingCalendar() }
    }

    var calendarPermissionDenied by rememberSaveable { mutableStateOf(false) }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        calendarPermissionDenied = results.values.any { granted -> !granted }
        viewModel.refreshDeviceState()
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (!granted) context.startActivitySafely(BackgroundHealthChecker.notificationSettingsIntent(context))
        viewModel.refreshDeviceState()
    }
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.refreshDeviceState() }

    val snackbarHostState = remember { SnackbarHostState() }
    val message = state.transient.message
    if (message is UiMessage.EventsRemoved) {
        val text = pluralStringResource(R.plurals.events_removed, message.count, message.count)
        LaunchedEffect(message) {
            snackbarHostState.showSnackbar(text)
            viewModel.messageShown()
        }
    }

    Scaffold(
        modifier = Modifier.semantics { testTagsAsResourceId = true },
        topBar = { TopAppBar(title = { Text(stringResource(R.string.app_name)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(padding)
                .testTag("main_list"),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                end = 16.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "status") {
                StatusSection(state = state, onSyncNow = viewModel::syncNow)
            }
            item(key = "account") {
                AccountSection(state = state, onSignIn = viewModel::signIn, onSignOut = viewModel::signOut)
            }
            item(key = "calendar") {
                CalendarSection(
                    state = state,
                    permissionDenied = calendarPermissionDenied,
                    onRequestPermission = {
                        calendarPermissionLauncher.launch(CalendarRepository.PERMISSIONS)
                    },
                    onOpenAppSettings = { settingsLauncher.launch(BackgroundHealthChecker.appDetailsIntent(context)) },
                    onChooseCalendar = viewModel::requestCalendarChange,
                    onShowAllCalendars = viewModel::setShowAllCalendars,
                    onOpenSyncSettings = { context.startActivitySafely(BackgroundHealthChecker.syncSettingsIntent()) },
                )
            }
            item(key = "range") {
                RangeSection(
                    settings = state.settings,
                    onPastDays = viewModel::setPastDays,
                    onFutureDays = viewModel::setFutureDays,
                )
            }
            item(key = "auto_sync") {
                AutoSyncSection(
                    state = state,
                    onAutoSync = viewModel::setAutoSync,
                    onIntervalMinutes = viewModel::setIntervalMinutes,
                )
            }
            item(key = "reliability") {
                ReliabilitySection(
                    health = state.device.health,
                    onFixBattery = {
                        runCatching { settingsLauncher.launch(BackgroundHealthChecker.batteryOptimizationIntent(context)) }
                            .onFailure { settingsLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                    },
                    onFixRestriction = { settingsLauncher.launch(BackgroundHealthChecker.appDetailsIntent(context)) },
                    onFixHibernation = {
                        runCatching { settingsLauncher.launch(BackgroundHealthChecker.hibernationIntent(context)) }
                            .onFailure { settingsLauncher.launch(BackgroundHealthChecker.appDetailsIntent(context)) }
                    },
                    onFixNotifications = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            context.startActivitySafely(BackgroundHealthChecker.notificationSettingsIntent(context))
                        }
                    },
                    onManufacturerInfo = {
                        context.startActivitySafely(BackgroundHealthChecker.dontKillMyAppIntent(state.device.health.manufacturer))
                    },
                )
            }
            item(key = "history") {
                HistorySection(history = state.settings.history)
            }
            item(key = "updates") {
                UpdatesSection(
                    state = updates,
                    onChannel = updatesViewModel::setChannel,
                    onAutoInstall = updatesViewModel::setAutoInstall,
                    onCheck = updatesViewModel::checkNow,
                    onInstall = updatesViewModel::install,
                    onAllowInstalls = {
                        runCatching { settingsLauncher.launch(installPermissionIntent(context)) }
                            .onFailure { settingsLauncher.launch(BackgroundHealthChecker.appDetailsIntent(context)) }
                    },
                    onOpenLink = { url -> context.startActivitySafely(Intent(Intent.ACTION_VIEW, url.toUri())) },
                )
            }
            state.settings.calendar?.let { calendar ->
                item(key = "maintenance") {
                    MaintenanceSection(calendarName = calendar.displayName, onRemoveEvents = viewModel::removeSyncedEvents)
                }
            }
        }
    }

    state.transient.pendingCalendarChange?.let { pending ->
        AlertDialog(
            onDismissRequest = viewModel::cancelCalendarChange,
            title = { Text(stringResource(R.string.move_events_title)) },
            text = {
                Text(stringResource(R.string.move_events_text, pending.previousEventCount, pending.previous.displayName))
            },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmCalendarChange(removePreviousEvents = true) }) {
                    Text(stringResource(R.string.action_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.confirmCalendarChange(removePreviousEvents = false) }) {
                    Text(stringResource(R.string.action_keep))
                }
            },
        )
    }
}
