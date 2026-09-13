package io.github.augustinavicius.kulendar.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.augustinavicius.kulendar.R
import io.github.augustinavicius.kulendar.data.settings.AppSettings
import io.github.augustinavicius.kulendar.data.settings.SyncRecord
import io.github.augustinavicius.kulendar.data.settings.SyncStatus
import io.github.augustinavicius.kulendar.data.settings.SyncTrigger
import io.github.augustinavicius.kulendar.sync.SyncRange
import io.github.augustinavicius.kulendar.sync.UNIVERSITY_ZONE
import io.github.augustinavicius.kulendar.sync.messageRes
import io.github.augustinavicius.kulendar.system.BackgroundHealth
import io.github.augustinavicius.kulendar.system.HibernationState
import java.time.LocalDate

private val PAST_PRESETS = listOf(0, 7, 14, 30, 60, 90, 180, 365)
private val FUTURE_PRESETS = listOf(7, 14, 30, 60, 90, 120, 180, 365)
private val INTERVAL_PRESETS = listOf(15, 30, 60, 120, 180, 360, 720, 1440)
private const val CUSTOM = -1

private enum class RangeDialog { PAST, FUTURE, PAST_CUSTOM, FUTURE_CUSTOM }

@Composable
fun RangeSection(settings: AppSettings, onPastDays: (Int) -> Unit, onFutureDays: (Int) -> Unit) {
    val context = LocalContext.current
    var dialog by rememberSaveable { mutableStateOf<RangeDialog?>(null) }
    val range = SyncRange.around(LocalDate.now(UNIVERSITY_ZONE), settings.pastDays, settings.futureDays)

    SectionCard(title = stringResource(R.string.section_range)) {
        Column {
            ValueRow(
                label = stringResource(R.string.range_past),
                value = rangeLabel(settings.pastDays),
                onClick = { dialog = RangeDialog.PAST },
                modifier = Modifier.testTag("range_past"),
            )
            ValueRow(
                label = stringResource(R.string.range_future),
                value = rangeLabel(settings.futureDays),
                onClick = { dialog = RangeDialog.FUTURE },
                modifier = Modifier.testTag("range_future"),
            )
        }
        Text(
            stringResource(
                R.string.range_summary,
                formatDate(context, range.window.startMillis),
                formatDate(context, range.window.endMillisExclusive - 1),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(stringResource(R.string.range_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }

    val close = { dialog = null }
    when (dialog) {
        RangeDialog.PAST -> RangeChoiceDialog(
            title = stringResource(R.string.range_past_title),
            presets = PAST_PRESETS,
            current = settings.pastDays,
            onSelect = { days -> if (days == CUSTOM) dialog = RangeDialog.PAST_CUSTOM else onPastDays(days).also { close() } },
            onDismiss = close,
        )
        RangeDialog.FUTURE -> RangeChoiceDialog(
            title = stringResource(R.string.range_future_title),
            presets = FUTURE_PRESETS,
            current = settings.futureDays,
            onSelect = { days -> if (days == CUSTOM) dialog = RangeDialog.FUTURE_CUSTOM else onFutureDays(days).also { close() } },
            onDismiss = close,
        )
        RangeDialog.PAST_CUSTOM -> NumberInputDialog(
            title = stringResource(R.string.range_custom_title),
            label = stringResource(R.string.range_custom_label, AppSettings.MAX_RANGE_DAYS),
            initial = settings.pastDays,
            range = 0..AppSettings.MAX_RANGE_DAYS,
            onConfirm = { onPastDays(it).also { close() } },
            onDismiss = close,
        )
        RangeDialog.FUTURE_CUSTOM -> NumberInputDialog(
            title = stringResource(R.string.range_custom_title),
            label = stringResource(R.string.range_custom_label, AppSettings.MAX_RANGE_DAYS),
            initial = settings.futureDays,
            range = 0..AppSettings.MAX_RANGE_DAYS,
            onConfirm = { onFutureDays(it).also { close() } },
            onDismiss = close,
        )
        null -> Unit
    }
}

@Composable
private fun RangeChoiceDialog(title: String, presets: List<Int>, current: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    ChoiceDialog(
        title = title,
        options = presets.map { it to rangeLabel(it) } + (CUSTOM to stringResource(R.string.range_custom)),
        selected = current.takeIf { it in presets } ?: CUSTOM,
        onSelect = onSelect,
        onDismiss = onDismiss,
    )
}

@Composable
private fun rangeLabel(days: Int): String = when (days) {
    0 -> stringResource(R.string.range_none)
    7 -> stringResource(R.string.range_1_week)
    14 -> stringResource(R.string.range_2_weeks)
    30 -> stringResource(R.string.range_1_month)
    60 -> stringResource(R.string.range_2_months)
    90 -> stringResource(R.string.range_3_months)
    120 -> stringResource(R.string.range_4_months)
    180 -> stringResource(R.string.range_6_months)
    365 -> stringResource(R.string.range_1_year)
    else -> pluralStringResource(R.plurals.days_count, days, days)
}

@Composable
fun AutoSyncSection(state: MainUiState, onAutoSync: (Boolean) -> Unit, onIntervalMinutes: (Int) -> Unit) {
    val settings = state.settings
    var choosingInterval by rememberSaveable { mutableStateOf(false) }

    SectionCard(title = stringResource(R.string.section_auto_sync)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = settings.autoSync, role = Role.Switch, onValueChange = onAutoSync)
                .testTag("auto_sync"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.auto_sync), style = MaterialTheme.typography.bodyLarge)
                Text(stringResource(R.string.auto_sync_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.size(12.dp))
            Switch(checked = settings.autoSync, onCheckedChange = null)
        }
        ValueRow(
            label = stringResource(R.string.sync_interval),
            value = intervalLabel(settings.intervalMinutes),
            onClick = { choosingInterval = true },
            enabled = settings.autoSync,
            modifier = Modifier.testTag("sync_interval"),
        )
    }

    if (choosingInterval) {
        ChoiceDialog(
            title = stringResource(R.string.sync_interval_title),
            options = INTERVAL_PRESETS.map { it to intervalLabel(it) },
            selected = settings.intervalMinutes,
            onSelect = {
                choosingInterval = false
                onIntervalMinutes(it)
            },
            onDismiss = { choosingInterval = false },
        )
    }
}

@Composable
private fun intervalLabel(minutes: Int): String = stringResource(
    when (minutes) {
        15 -> R.string.interval_15_min
        30 -> R.string.interval_30_min
        120 -> R.string.interval_2_hours
        180 -> R.string.interval_3_hours
        360 -> R.string.interval_6_hours
        720 -> R.string.interval_12_hours
        1440 -> R.string.interval_24_hours
        else -> R.string.interval_1_hour
    },
)

@Composable
fun ReliabilitySection(
    health: BackgroundHealth,
    onFixBattery: () -> Unit,
    onFixRestriction: () -> Unit,
    onFixHibernation: () -> Unit,
    onFixNotifications: () -> Unit,
    onManufacturerInfo: () -> Unit,
) {
    SectionCard(title = stringResource(R.string.section_reliability), subtitle = stringResource(R.string.section_reliability_hint)) {
        HealthRow(
            ok = health.ignoringBatteryOptimizations,
            title = stringResource(R.string.battery_title),
            text = stringResource(if (health.ignoringBatteryOptimizations) R.string.battery_ok else R.string.battery_bad),
            actionLabel = stringResource(R.string.action_allow),
            onAction = onFixBattery,
            modifier = Modifier.testTag("health_battery"),
        )
        if (health.backgroundRestricted) {
            HealthRow(
                ok = false,
                title = stringResource(R.string.restricted_title),
                text = stringResource(R.string.restricted_bad),
                actionLabel = stringResource(R.string.action_open_settings),
                onAction = onFixRestriction,
                modifier = Modifier.testTag("health_restricted"),
            )
        }
        if (health.hibernation != HibernationState.UNAVAILABLE) {
            val off = health.hibernation == HibernationState.OFF
            HealthRow(
                ok = off,
                title = stringResource(R.string.hibernation_title),
                text = stringResource(if (off) R.string.hibernation_ok else R.string.hibernation_bad),
                actionLabel = stringResource(R.string.action_turn_off),
                onAction = onFixHibernation,
                modifier = Modifier.testTag("health_hibernation"),
            )
        }
        HealthRow(
            ok = health.notificationsEnabled,
            title = stringResource(R.string.notifications_title),
            text = stringResource(if (health.notificationsEnabled) R.string.notifications_ok else R.string.notifications_bad),
            actionLabel = stringResource(R.string.action_allow),
            onAction = onFixNotifications,
            modifier = Modifier.testTag("health_notifications"),
        )
        if (health.hasAggressiveManufacturer) {
            Text(
                stringResource(R.string.oem_tip, health.manufacturer.replaceFirstChar { it.titlecase() }),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onManufacturerInfo) { Text(stringResource(R.string.action_learn_more)) }
        }
    }
}

@Composable
fun HistorySection(history: List<SyncRecord>) {
    val context = LocalContext.current
    SectionCard(title = stringResource(R.string.section_history)) {
        if (history.isEmpty()) {
            Text(stringResource(R.string.history_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        history.take(HISTORY_ROWS).forEachIndexed { index, record ->
            if (index > 0) HorizontalDivider()
            val success = record.status == SyncStatus.SUCCESS
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(
                    imageVector = if (success) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    tint = if (success) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "${formatDateTime(context, record.finishedAt)} · ${triggerLabel(record.trigger)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        if (success) {
                            stringResource(R.string.history_counts, record.inserted, record.updated, record.deleted)
                        } else {
                            stringResource(record.status.messageRes())
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private const val HISTORY_ROWS = 10

@Composable
private fun triggerLabel(trigger: SyncTrigger): String = stringResource(
    when (trigger) {
        SyncTrigger.PERIODIC -> R.string.trigger_periodic
        SyncTrigger.MANUAL -> R.string.trigger_manual
        SyncTrigger.SETTINGS_CHANGED -> R.string.trigger_settings_changed
        SyncTrigger.BOOT -> R.string.trigger_boot
        SyncTrigger.APP_UPDATED -> R.string.trigger_app_updated
    },
)

@Composable
fun MaintenanceSection(calendarName: String, onRemoveEvents: () -> Unit) {
    var confirming by rememberSaveable { mutableStateOf(false) }
    SectionCard(title = stringResource(R.string.section_maintenance), subtitle = stringResource(R.string.remove_events_hint)) {
        OutlinedButton(
            onClick = { confirming = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
            modifier = Modifier.testTag("remove_events"),
        ) {
            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.action_remove_events))
        }
    }
    if (confirming) {
        ConfirmDialog(
            title = stringResource(R.string.remove_events_confirm_title),
            text = stringResource(R.string.remove_events_confirm_text, calendarName),
            confirmLabel = stringResource(R.string.action_remove),
            onConfirm = {
                confirming = false
                onRemoveEvents()
            },
            onDismiss = { confirming = false },
        )
    }
}
