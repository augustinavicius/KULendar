package io.github.augustinavicius.kulendar.data.calendar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarSyncNoticeTest {

    private fun calendar(accountType: String) = DeviceCalendar(
        id = 1,
        displayName = "University",
        accountName = "vardenis@gmail.com",
        accountType = accountType,
        color = 0,
        accessLevel = 700,
        visible = true,
        syncEvents = true,
    )

    @Test
    fun `local calendars have nothing to upload`() {
        assertNull(calendarSyncNotice(calendar("LOCAL"), SyncOffReason.AUTO_SYNC_OFF, pendingUploads = 5, accountVisible = false))
    }

    @Test
    fun `a sync setting known to be off is reported first`() {
        assertEquals(
            CalendarSyncNotice.SyncOff(SyncOffReason.NOT_SYNCABLE),
            calendarSyncNotice(calendar("com.google"), SyncOffReason.NOT_SYNCABLE, pendingUploads = 3, accountVisible = true),
        )
    }

    @Test
    fun `events waiting for upload are reported with whether KULendar can check the account`() {
        assertEquals(
            CalendarSyncNotice.PendingUploads(147, accountHidden = true),
            calendarSyncNotice(calendar("com.google"), syncOff = null, pendingUploads = 147, accountVisible = false),
        )
        assertEquals(
            CalendarSyncNotice.PendingUploads(2, accountHidden = false),
            calendarSyncNotice(calendar("com.google"), syncOff = null, pendingUploads = 2, accountVisible = true),
        )
    }

    @Test
    fun `nothing is reported once everything is uploaded`() {
        assertNull(calendarSyncNotice(calendar("com.google"), syncOff = null, pendingUploads = 0, accountVisible = false))
    }

    @Test
    fun `auto-sync being off is known even for hidden accounts`() {
        assertEquals(
            SyncOffReason.AUTO_SYNC_OFF,
            detectSyncOff(autoSync = false, accountVisible = false, syncable = 0, accountSync = false),
        )
    }

    @Test
    fun `settings of accounts KULendar cannot see are ignored because Android reports them as off`() {
        assertNull(detectSyncOff(autoSync = true, accountVisible = false, syncable = 0, accountSync = false))
    }

    @Test
    fun `an account that is not synced with the phone at all is reported before its sync switch`() {
        assertEquals(
            SyncOffReason.NOT_SYNCABLE,
            detectSyncOff(autoSync = true, accountVisible = true, syncable = 0, accountSync = false),
        )
    }

    @Test
    fun `calendar sync turned off for a visible account is reported`() {
        assertEquals(
            SyncOffReason.ACCOUNT_SYNC_OFF,
            detectSyncOff(autoSync = true, accountVisible = true, syncable = 1, accountSync = false),
        )
        assertEquals(
            SyncOffReason.ACCOUNT_SYNC_OFF,
            detectSyncOff(autoSync = true, accountVisible = true, syncable = -1, accountSync = false),
        )
    }

    @Test
    fun `nothing is off when every setting is on`() {
        assertNull(detectSyncOff(autoSync = true, accountVisible = true, syncable = 1, accountSync = true))
    }
}
