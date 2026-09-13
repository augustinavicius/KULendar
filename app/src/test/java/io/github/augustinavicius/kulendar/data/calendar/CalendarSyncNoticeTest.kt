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
        assertNull(calendarSyncNotice(calendar("LOCAL"), SyncOffReason.AUTO_SYNC_OFF, pendingUploads = 5))
    }

    @Test
    fun `a sync setting known to be off is reported first`() {
        assertEquals(
            CalendarSyncNotice.SyncOff(SyncOffReason.ACCOUNT_SYNC_OFF),
            calendarSyncNotice(calendar("com.google"), SyncOffReason.ACCOUNT_SYNC_OFF, pendingUploads = 3),
        )
    }

    @Test
    fun `events waiting for upload are reported when sync is not known to be off`() {
        assertEquals(
            CalendarSyncNotice.PendingUploads(147),
            calendarSyncNotice(calendar("com.google"), syncOff = null, pendingUploads = 147),
        )
    }

    @Test
    fun `nothing is reported once everything is uploaded`() {
        assertNull(calendarSyncNotice(calendar("com.google"), syncOff = null, pendingUploads = 0))
    }
}
