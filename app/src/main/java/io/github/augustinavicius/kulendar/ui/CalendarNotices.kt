package io.github.augustinavicius.kulendar.ui

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
import io.github.augustinavicius.kulendar.data.calendar.SyncOffReason

/** Tells the user when KULendar's events do not, or have not yet, reached the calendar's online account. */
@Composable
fun CalendarSyncNoticeText(notice: CalendarSyncNotice, accountName: String, onOpenSyncSettings: () -> Unit) {
    Column {
        when (notice) {
            is CalendarSyncNotice.SyncOff -> NoticeRow(
                icon = Icons.Filled.Warning,
                color = MaterialTheme.colorScheme.error,
                text = when (notice.reason) {
                    SyncOffReason.AUTO_SYNC_OFF -> stringResource(R.string.calendar_auto_sync_off)
                    SyncOffReason.ACCOUNT_SYNC_OFF -> stringResource(R.string.calendar_sync_off, accountName)
                },
                modifier = Modifier.testTag("calendar_sync_off"),
            )
            is CalendarSyncNotice.PendingUploads -> NoticeRow(
                icon = Icons.Filled.Info,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                text = pluralStringResource(R.plurals.calendar_pending_uploads, notice.count, notice.count, accountName),
                modifier = Modifier.testTag("calendar_pending_uploads"),
            )
        }
        TextButton(onClick = onOpenSyncSettings) { Text(stringResource(R.string.action_open_sync_settings)) }
    }
}

@Composable
private fun NoticeRow(icon: ImageVector, color: Color, text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = color)
    }
}
