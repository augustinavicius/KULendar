package io.github.augustinavicius.kulendar.data.calendar

/** Why Android is known not to upload a calendar's changes. */
enum class SyncOffReason {
    /** Auto-sync is off for the whole phone. */
    AUTO_SYNC_OFF,

    /**
     * The account isn't synced with the phone's calendar at all. For Google accounts, this is what turning off
     * "Share Google Calendar data with other apps" in the Google Calendar app does.
     */
    NOT_SYNCABLE,

    /** Calendar sync is turned off for the account. */
    ACCOUNT_SYNC_OFF,
}

/** What to tell the user about whether KULendar's events reach the calendar's online account. */
sealed interface CalendarSyncNotice {
    data class SyncOff(val reason: SyncOffReason) : CalendarSyncNotice

    /** [accountHidden]: Android hides the account's sync settings from KULendar, so it cannot tell why yet. */
    data class PendingUploads(val count: Int, val accountHidden: Boolean) : CalendarSyncNotice
}

/**
 * Works out why uploads are off from the settings Android reports. Android reports an account's own settings as off
 * to apps that cannot see the account, so those only count when [accountVisible].
 *
 * @param syncable what [android.content.ContentResolver.getIsSyncable] returns: positive when the account syncs its
 * calendars, 0 when it does not, negative when that is not decided yet.
 */
fun detectSyncOff(autoSync: Boolean, accountVisible: Boolean, syncable: Int, accountSync: Boolean): SyncOffReason? = when {
    !autoSync -> SyncOffReason.AUTO_SYNC_OFF
    !accountVisible -> null
    syncable == 0 -> SyncOffReason.NOT_SYNCABLE
    !accountSync -> SyncOffReason.ACCOUNT_SYNC_OFF
    else -> null
}

/**
 * Picks the notice for [calendar]. Only facts Android actually reports are shown: a sync setting known to be off,
 * or events the account has not uploaded yet. Local calendars have nothing to upload.
 */
fun calendarSyncNotice(
    calendar: DeviceCalendar,
    syncOff: SyncOffReason?,
    pendingUploads: Int,
    accountVisible: Boolean,
): CalendarSyncNotice? = when {
    calendar.isLocal -> null
    syncOff != null -> CalendarSyncNotice.SyncOff(syncOff)
    pendingUploads > 0 -> CalendarSyncNotice.PendingUploads(pendingUploads, accountHidden = !accountVisible)
    else -> null
}
