package io.github.augustinavicius.kulendar.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import io.github.augustinavicius.kulendar.BuildConfig
import io.github.augustinavicius.kulendar.R
import io.github.augustinavicius.kulendar.update.AvailableUpdate
import io.github.augustinavicius.kulendar.update.UpdateChannel
import io.github.augustinavicius.kulendar.update.UpdateErrorKind
import io.github.augustinavicius.kulendar.update.UpdateState

/** System screen where the user allows KULendar to install its own updates. */
fun installPermissionIntent(context: Context) =
    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())

val releasesPageUrl: String = "https://github.com/${BuildConfig.UPDATE_REPOSITORY}/releases"

@Composable
fun UpdatesSection(
    state: UpdatesUiState,
    onChannel: (UpdateChannel) -> Unit,
    onAutoInstall: (Boolean) -> Unit,
    onCheck: () -> Unit,
    onInstall: (AvailableUpdate) -> Unit,
    onAllowInstalls: () -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val settings = state.settings
    var choosingChannel by rememberSaveable { mutableStateOf(false) }

    SectionCard(title = stringResource(R.string.section_updates)) {
        Column {
            Text(
                stringResource(R.string.update_installed_version, state.installedVersionName, buildLabel(state.buildChannel)),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.testTag("installed_version"),
            )
            if (state.commit.isNotEmpty()) {
                Text(
                    stringResource(R.string.update_built_from, state.commit.take(7)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (settings != null) {
            Column {
                ValueRow(
                    label = stringResource(R.string.update_channel),
                    value = channelLabel(settings.channel),
                    onClick = { choosingChannel = true },
                    modifier = Modifier.testTag("update_channel"),
                )
                Text(
                    stringResource(
                        if (settings.channel == UpdateChannel.STABLE) R.string.update_channel_stable_hint else R.string.update_channel_development_hint,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .toggleable(value = settings.autoInstall, role = Role.Switch, onValueChange = onAutoInstall)
                    .testTag("auto_install"),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.update_auto_install), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.update_auto_install_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.size(12.dp))
                Switch(checked = settings.autoInstall, onCheckedChange = null)
            }
        }
        if (!state.canInstallPackages) {
            HealthRow(
                ok = false,
                title = stringResource(R.string.update_permission_title),
                text = stringResource(R.string.update_permission_text),
                actionLabel = stringResource(R.string.action_allow),
                onAction = onAllowInstalls,
                modifier = Modifier.testTag("update_permission"),
            )
        }
        UpdateStatus(state = state, onCheck = onCheck, onInstall = onInstall, onOpenLink = onOpenLink)
    }

    if (choosingChannel && settings != null) {
        ChoiceDialog(
            title = stringResource(R.string.update_channel),
            options = UpdateChannel.entries.map { it to channelLabel(it) },
            selected = settings.channel,
            onSelect = {
                choosingChannel = false
                onChannel(it)
            },
            onDismiss = { choosingChannel = false },
        )
    }
}

@Composable
private fun UpdateStatus(
    state: UpdatesUiState,
    onCheck: () -> Unit,
    onInstall: (AvailableUpdate) -> Unit,
    onOpenLink: (String) -> Unit,
) {
    val context = LocalContext.current
    when (val current = state.state) {
        UpdateState.Checking -> ProgressLine(stringResource(R.string.update_checking))
        is UpdateState.Installing -> ProgressLine(stringResource(R.string.update_installing, current.update.versionName))
        is UpdateState.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.update_downloading, current.update.versionName, (current.progress * 100).toInt().coerceIn(0, 100)),
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(progress = { current.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
        }
        is UpdateState.Available -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                stringResource(R.string.update_available, current.update.versionName),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.testTag("update_available"),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { onInstall(current.update) }, modifier = Modifier.testTag("install_update")) {
                    Text(stringResource(R.string.update_install))
                }
                current.update.releaseUrl?.let { url ->
                    TextButton(onClick = { onOpenLink(url) }) { Text(stringResource(R.string.update_whats_new)) }
                }
            }
        }
        is UpdateState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                errorText(current.kind),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.testTag("update_error"),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                val update = current.update
                if (update != null && current.kind != UpdateErrorKind.SIGNATURE_MISMATCH) {
                    Button(onClick = { onInstall(update) }) { Text(stringResource(R.string.update_retry)) }
                } else {
                    OutlinedButton(onClick = onCheck, modifier = Modifier.testTag("check_updates")) { Text(stringResource(R.string.update_check)) }
                }
                if (current.kind == UpdateErrorKind.SIGNATURE_MISMATCH) {
                    TextButton(onClick = { onOpenLink(releasesPageUrl) }) { Text(stringResource(R.string.update_open_releases)) }
                }
            }
        }
        UpdateState.Idle -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val last = state.settings?.lastCheck
            val lastVersionCode = last?.latestVersionCode
            Text(
                when {
                    last == null -> stringResource(R.string.update_never_checked)
                    last.error != null -> stringResource(R.string.update_last_check_failed, formatDateTime(context, last.checkedAt))
                    else -> stringResource(R.string.update_up_to_date, formatDateTime(context, last.checkedAt))
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.testTag("update_status"),
            )
            if (last?.error == null && lastVersionCode != null && lastVersionCode < state.installedVersionCode) {
                Text(
                    stringResource(R.string.update_ahead_of_channel, last.latestVersionName.orEmpty()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onCheck, modifier = Modifier.testTag("check_updates")) { Text(stringResource(R.string.update_check)) }
        }
    }
}

@Composable
private fun ProgressLine(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun channelLabel(channel: UpdateChannel): String = stringResource(
    when (channel) {
        UpdateChannel.STABLE -> R.string.update_channel_stable
        UpdateChannel.DEVELOPMENT -> R.string.update_channel_development
    },
)

@Composable
private fun buildLabel(buildChannel: String): String = stringResource(
    when (buildChannel) {
        UpdateChannel.STABLE.key -> R.string.update_build_stable
        UpdateChannel.DEVELOPMENT.key -> R.string.update_build_development
        else -> R.string.update_build_local
    },
)

@Composable
private fun errorText(kind: UpdateErrorKind): String = stringResource(
    when (kind) {
        UpdateErrorKind.NETWORK -> R.string.update_error_network
        UpdateErrorKind.RATE_LIMITED -> R.string.update_error_rate_limited
        UpdateErrorKind.SERVER -> R.string.update_error_server
        UpdateErrorKind.PROTOCOL -> R.string.update_error_protocol
        UpdateErrorKind.VERIFICATION -> R.string.update_error_verification
        UpdateErrorKind.SIGNATURE_MISMATCH -> R.string.update_error_signature
        UpdateErrorKind.INSTALL_NOT_ALLOWED -> R.string.update_error_not_allowed
        UpdateErrorKind.INSTALL -> R.string.update_error_install
    },
)
