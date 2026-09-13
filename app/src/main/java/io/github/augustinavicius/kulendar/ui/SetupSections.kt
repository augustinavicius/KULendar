package io.github.augustinavicius.kulendar.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.autofill.ContentType
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentType
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.augustinavicius.kulendar.R
import io.github.augustinavicius.kulendar.data.calendar.DeviceCalendar
import io.github.augustinavicius.kulendar.data.settings.SyncStatus
import io.github.augustinavicius.kulendar.sync.messageRes

@Composable
fun StatusSection(state: MainUiState, onSyncNow: () -> Unit) {
    val context = LocalContext.current
    val last = state.settings.lastRecord
    val lastSuccess = state.settings.lastSuccess
    val work = state.work
    val colors = MaterialTheme.colorScheme

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = colors.primaryContainer, contentColor = colors.onPrimaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                when {
                    work.running -> CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 3.dp)
                    last == null -> Icon(Icons.Filled.Info, contentDescription = null)
                    last.status == SyncStatus.SUCCESS -> Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colors.primary)
                    else -> Icon(Icons.Filled.Warning, contentDescription = null, tint = colors.error)
                }
                val title = when {
                    work.running -> stringResource(R.string.status_syncing)
                    work.queued -> stringResource(R.string.status_queued)
                    last == null -> stringResource(R.string.status_never_synced)
                    else -> stringResource(last.status.messageRes())
                }
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("status_title"))
            }
            if (last != null) {
                Text(stringResource(R.string.last_sync_at, formatDateTime(context, last.finishedAt)), style = MaterialTheme.typography.bodyMedium)
            }
            if (lastSuccess != null && last?.status != SyncStatus.SUCCESS) {
                Text(stringResource(R.string.last_success_at, formatDateTime(context, lastSuccess.finishedAt)), style = MaterialTheme.typography.bodyMedium)
            }
            if (lastSuccess != null) {
                Text(
                    stringResource(R.string.sync_summary, lastSuccess.fetched, lastSuccess.inserted, lastSuccess.updated, lastSuccess.deleted),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.testTag("sync_summary"),
                )
                if (!lastSuccess.complete) Text(stringResource(R.string.sync_incomplete_note), style = MaterialTheme.typography.bodySmall)
            }
            if (state.isConfigured) {
                val next = work.nextSyncAt
                when {
                    !state.settings.autoSync -> Text(stringResource(R.string.auto_sync_is_off), style = MaterialTheme.typography.bodySmall)
                    next != null -> Text(stringResource(R.string.next_sync_at, formatDateTime(context, next)), style = MaterialTheme.typography.bodySmall)
                }
            } else {
                Text(stringResource(R.string.setup_hint), style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = onSyncNow,
                enabled = state.isConfigured && !work.running,
                modifier = Modifier.fillMaxWidth().testTag("sync_now"),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.action_sync_now))
            }
        }
    }
}

@Composable
fun AccountSection(state: MainUiState, onSignIn: (String, String) -> Unit, onSignOut: () -> Unit) {
    SectionCard(title = stringResource(R.string.section_account), subtitle = stringResource(R.string.section_account_hint)) {
        val account = state.account
        if (account != null) {
            var confirmSignOut by rememberSaveable { mutableStateOf(false) }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.signed_in_as, account.displayName ?: account.uid),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.testTag("signed_in_as"),
                    )
                    if (account.displayName != null) {
                        Text(account.uid, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                OutlinedButton(onClick = { confirmSignOut = true }, modifier = Modifier.testTag("sign_out")) {
                    Text(stringResource(R.string.action_sign_out))
                }
            }
            if (state.settings.lastRecord?.status == SyncStatus.INVALID_CREDENTIALS) {
                Text(
                    stringResource(R.string.status_invalid_credentials),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (confirmSignOut) {
                ConfirmDialog(
                    title = stringResource(R.string.sign_out_confirm_title),
                    text = stringResource(R.string.sign_out_confirm_text),
                    confirmLabel = stringResource(R.string.action_sign_out),
                    onConfirm = {
                        confirmSignOut = false
                        onSignOut()
                    },
                    onDismiss = { confirmSignOut = false },
                )
            }
        } else {
            SignInForm(signIn = state.transient.signIn, onSignIn = onSignIn)
        }
    }
}

@Composable
private fun SignInForm(signIn: SignInState, onSignIn: (String, String) -> Unit) {
    var username by rememberSaveable { mutableStateOf("") }
    // Not saveable on purpose: the password should not end up in saved instance state.
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    val inProgress = signIn == SignInState.InProgress
    val canSubmit = !inProgress && username.isNotBlank() && password.isNotEmpty()
    val submit = { if (canSubmit) onSignIn(username, password) }

    OutlinedTextField(
        value = username,
        onValueChange = { username = it },
        label = { Text(stringResource(R.string.field_username)) },
        placeholder = { Text(stringResource(R.string.field_username_hint)) },
        singleLine = true,
        enabled = !inProgress,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("username")
            .semantics { contentType = ContentType.Username },
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text(stringResource(R.string.field_password)) },
        singleLine = true,
        enabled = !inProgress,
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            TextButton(onClick = { showPassword = !showPassword }) {
                Text(stringResource(if (showPassword) R.string.action_hide_password else R.string.action_show_password))
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("password")
            .semantics { contentType = ContentType.Password },
    )
    if (signIn is SignInState.Failed) {
        val error = when (signIn.error) {
            SignInError.INVALID_CREDENTIALS -> stringResource(R.string.error_invalid_credentials)
            SignInError.NETWORK -> stringResource(R.string.error_network)
            SignInError.SERVER -> stringResource(R.string.error_server)
            SignInError.UNKNOWN -> stringResource(R.string.error_unknown, signIn.detail.orEmpty())
        }
        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("sign_in_error"))
    }
    Button(onClick = submit, enabled = canSubmit, modifier = Modifier.fillMaxWidth().testTag("sign_in")) {
        if (inProgress) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Text(stringResource(R.string.action_sign_in))
        }
    }
    Text(
        stringResource(R.string.credentials_storage_note),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
fun CalendarSection(
    state: MainUiState,
    permissionDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onChooseCalendar: (DeviceCalendar) -> Unit,
    onShowAllCalendars: (Boolean) -> Unit,
    onOpenSyncSettings: () -> Unit,
    onTurnOnSync: () -> Unit,
    onCheckAccount: () -> Unit,
    onOpenGoogleCalendar: () -> Unit,
) {
    var picking by rememberSaveable { mutableStateOf(false) }
    val device = state.device

    SectionCard(title = stringResource(R.string.section_calendar), subtitle = stringResource(R.string.section_calendar_hint)) {
        when {
            !device.loaded -> Unit
            !device.hasCalendarPermission -> {
                Text(
                    stringResource(if (permissionDenied) R.string.calendar_permission_denied else R.string.calendar_permission_needed),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(
                    onClick = if (permissionDenied) onOpenAppSettings else onRequestPermission,
                    modifier = Modifier.testTag("grant_calendar"),
                ) {
                    Text(stringResource(if (permissionDenied) R.string.action_open_settings else R.string.action_grant_access))
                }
            }
            else -> {
                val selected = state.selectedCalendar
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { picking = true },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (selected != null) CalendarColorDot(selected.color)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            selected?.displayName ?: state.settings.calendar?.displayName ?: stringResource(R.string.calendar_none_selected),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.testTag("selected_calendar"),
                        )
                        (selected?.accountName ?: state.settings.calendar?.accountName)?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    TextButton(onClick = { picking = true }, modifier = Modifier.testTag("choose_calendar")) {
                        Text(stringResource(if (state.settings.calendar == null) R.string.action_choose_calendar else R.string.action_change))
                    }
                }
                val notice = state.calendarSyncNotice
                when {
                    state.selectedCalendarMissing && state.settings.calendar?.accountType == DeviceCalendar.GOOGLE_ACCOUNT_TYPE -> {
                        // Turning off Google Calendar's data sharing removes its calendars from the phone's calendar storage.
                        WarningText(stringResource(R.string.calendar_missing_google))
                        OpenGoogleCalendarButton(onOpenGoogleCalendar)
                    }
                    state.selectedCalendarMissing -> WarningText(stringResource(R.string.calendar_missing))
                    selected == null -> Unit
                    !selected.syncEvents -> WarningText(stringResource(R.string.calendar_not_synced))
                    selected.isLocal -> WarningText(stringResource(R.string.calendar_not_google))
                    notice != null -> CalendarSyncNoticeText(
                        notice = notice,
                        calendar = selected,
                        onTurnOnSync = onTurnOnSync,
                        onCheckAccount = onCheckAccount,
                        onOpenGoogleCalendar = onOpenGoogleCalendar,
                        onOpenSyncSettings = onOpenSyncSettings,
                    )
                }
            }
        }
    }

    if (picking) {
        CalendarPickerDialog(
            calendars = device.calendars,
            showAll = state.settings.showAllCalendars,
            selectedId = state.settings.calendar?.id,
            onShowAll = onShowAllCalendars,
            onOpenGoogleCalendar = onOpenGoogleCalendar,
            onSelect = {
                picking = false
                onChooseCalendar(it)
            },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun CalendarPickerDialog(
    calendars: List<DeviceCalendar>,
    showAll: Boolean,
    selectedId: Long?,
    onShowAll: (Boolean) -> Unit,
    onOpenGoogleCalendar: () -> Unit,
    onSelect: (DeviceCalendar) -> Unit,
    onDismiss: () -> Unit,
) {
    val visible = if (showAll) calendars else calendars.filter { it.isGoogle }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_calendar_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (calendars.none { it.isGoogle }) {
                    Text(stringResource(R.string.choose_calendar_empty), style = MaterialTheme.typography.bodyMedium)
                    OpenGoogleCalendarButton(onOpenGoogleCalendar)
                }
                LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                    visible.groupBy { it.accountName }.forEach { (accountName, accountCalendars) ->
                        item(key = "account:$accountName") {
                            Text(
                                accountName,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                            )
                        }
                        items(accountCalendars, key = { it.id }) { calendar ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .selectable(selected = calendar.id == selectedId, role = Role.RadioButton, onClick = { onSelect(calendar) })
                                    .padding(vertical = 6.dp)
                                    .testTag("calendar_${calendar.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                RadioButton(selected = calendar.id == selectedId, onClick = null)
                                CalendarColorDot(calendar.color)
                                Text(calendar.displayName, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(value = showAll, role = Role.Switch, onValueChange = onShowAll)
                        .testTag("show_all_calendars"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.show_all_calendars), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Switch(checked = showAll, onCheckedChange = null)
                }
                Text(
                    stringResource(R.string.choose_calendar_tip),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun CalendarColorDot(color: Int) {
    Box(
        modifier = Modifier
            .size(14.dp)
            .clip(CircleShape)
            .background(Color(color or 0xFF000000.toInt())),
    )
}

@Composable
private fun WarningText(text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
    }
}
