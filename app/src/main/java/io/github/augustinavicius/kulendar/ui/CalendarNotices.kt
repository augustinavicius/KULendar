package io.github.augustinavicius.kulendar.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.augustinavicius.kulendar.R
import io.github.augustinavicius.kulendar.data.calendar.CalendarSyncNotice
import io.github.augustinavicius.kulendar.data.calendar.DeviceCalendar
import io.github.augustinavicius.kulendar.data.calendar.SyncOffReason

/** Tells the user when KULendar's events do not, or have not yet, reached the calendar's online account, and offers the fix. */
@Composable
fun CalendarSyncNoticeText(
    notice: CalendarSyncNotice,
    calendar: DeviceCalendar,
    onTurnOnSync: () -> Unit,
    onCheckAccount: () -> Unit,
    onOpenGoogleCalendar: () -> Unit,
    onOpenSyncSettings: () -> Unit,
) {
    val account = calendar.accountName
    Column {
        when (notice) {
            is CalendarSyncNotice.SyncOff -> {
                NoticeRow(
                    icon = Icons.Filled.Warning,
                    color = MaterialTheme.colorScheme.error,
                    text = when (notice.reason) {
                        SyncOffReason.AUTO_SYNC_OFF -> stringResource(R.string.calendar_auto_sync_off)
                        SyncOffReason.NOT_SYNCABLE -> stringResource(
                            if (calendar.isGoogle) R.string.calendar_not_shared else R.string.calendar_not_syncable,
                            account,
                        )
                        SyncOffReason.ACCOUNT_SYNC_OFF -> stringResource(R.string.calendar_sync_off, account)
                    },
                    modifier = Modifier.testTag("calendar_sync_off"),
                )
                when {
                    notice.reason != SyncOffReason.NOT_SYNCABLE -> NoticeButton(R.string.action_turn_on_sync, "turn_on_sync", onTurnOnSync)
                    calendar.isGoogle -> OpenGoogleCalendarButton(onOpenGoogleCalendar)
                    else -> NoticeButton(R.string.action_open_sync_settings, "open_sync_settings", onOpenSyncSettings)
                }
            }
            is CalendarSyncNotice.PendingUploads -> {
                val pending = pluralStringResource(R.plurals.calendar_pending_uploads, notice.count, notice.count, account)
                NoticeRow(
                    icon = Icons.Filled.Info,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    text = if (notice.accountHidden) "$pending ${stringResource(R.string.calendar_check_account_hint)}" else pending,
                    modifier = Modifier.testTag("calendar_pending_uploads"),
                )
                if (notice.accountHidden) {
                    NoticeButton(R.string.action_check_account, "check_account", onCheckAccount)
                } else {
                    NoticeButton(R.string.action_open_sync_settings, "open_sync_settings", onOpenSyncSettings)
                }
            }
        }
    }
}

/** Opens Google Calendar's settings, where "Share Google Calendar data with other apps" lives. */
@Composable
fun OpenGoogleCalendarButton(onClick: () -> Unit) {
    NoticeButton(R.string.action_open_google_calendar, "open_google_calendar", onClick)
}

@Composable
private fun NoticeButton(@StringRes label: Int, tag: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.testTag(tag)) { Text(stringResource(label)) }
}

@Composable
private fun NoticeRow(icon: ImageVector, color: Color, text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}
