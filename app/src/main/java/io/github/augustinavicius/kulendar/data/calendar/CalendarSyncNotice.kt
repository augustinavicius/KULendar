package io.github.augustinavicius.kulendar.data.calendar

/** Why Android is known not to upload a calendar's changes. */
enum class SyncOffReason { AUTO_SYNC_OFF, ACCOUNT_SYNC_OFF }

/** What to tell the user about whether KULendar's events reach the calendar's online account. */
sealed interface CalendarSyncNotice {
    data class SyncOff(val reason: SyncOffReason) : CalendarSyncNotice
    data class PendingUploads(val count: Int) : CalendarSyncNotice
}

/**
 * Picks the notice for [calendar]. Only facts Android actually reports are shown: a sync setting known to be off,
 * or events the account has not uploaded yet. Local calendars have nothing to upload.
 */
fun calendarSyncNotice(calendar: DeviceCalendar, syncOff: SyncOffReason?, pendingUploads: Int): CalendarSyncNotice? = when {
    calendar.isLocal -> null
    syncOff != null -> CalendarSyncNotice.SyncOff(syncOff)
    pendingUploads > 0 -> CalendarSyncNotice.PendingUploads(pendingUploads)
    else -> null
}
