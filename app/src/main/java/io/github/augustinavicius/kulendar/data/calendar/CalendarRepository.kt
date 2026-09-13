package io.github.augustinavicius.kulendar.data.calendar

import android.Manifest
import android.accounts.Account
import android.content.ContentProviderOperation
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.provider.CalendarContract
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import androidx.core.content.ContextCompat
import androidx.core.database.getIntOrNull
import androidx.core.database.getLongOrNull
import androidx.core.database.getStringOrNull
import io.github.augustinavicius.kulendar.sync.EventMarker
import io.github.augustinavicius.kulendar.sync.EventSpec
import io.github.augustinavicius.kulendar.sync.ExistingEvent
import io.github.augustinavicius.kulendar.sync.SyncPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class DeviceCalendar(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val accountType: String,
    val color: Int,
    val accessLevel: Int,
    val visible: Boolean,
    val syncEvents: Boolean,
) {
    val isGoogle: Boolean get() = accountType == GOOGLE_ACCOUNT_TYPE

    companion object {
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
    }
}

/**
 * Reads and writes events through Android's calendar provider. For Google accounts, the system's
 * Google Calendar sync adapter uploads the changes to Google Calendar.
 */
class CalendarRepository(context: Context) {

    private val context = context.applicationContext
    private val resolver: ContentResolver get() = context.contentResolver

    fun hasPermission(): Boolean = PERMISSIONS.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    /** Calendars the user may add events to, Google calendars first. */
    suspend fun writableCalendars(): List<DeviceCalendar> = withContext(Dispatchers.IO) {
        resolver.query(
            Calendars.CONTENT_URI,
            CALENDAR_PROJECTION,
            "${Calendars.CALENDAR_ACCESS_LEVEL} >= ?",
            arrayOf(Calendars.CAL_ACCESS_CONTRIBUTOR.toString()),
            null,
        )?.use { cursor -> cursor.mapRows { it.toDeviceCalendar() } }
            .orEmpty()
            .sortedWith(compareBy({ !it.isGoogle }, { it.accountName.lowercase() }, { it.displayName.lowercase() }))
    }

    suspend fun calendar(id: Long): DeviceCalendar? = withContext(Dispatchers.IO) {
        resolver.query(ContentUris.withAppendedId(Calendars.CONTENT_URI, id), CALENDAR_PROJECTION, null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.toDeviceCalendar() else null }
    }

    /** Whether Android uploads this calendar's changes. Local calendars have nothing to upload. */
    fun isSyncEnabled(calendar: DeviceCalendar): Boolean {
        if (calendar.accountType == CalendarContract.ACCOUNT_TYPE_LOCAL) return true
        return runCatching {
            ContentResolver.getMasterSyncAutomatically() &&
                ContentResolver.getSyncAutomatically(Account(calendar.accountName, calendar.accountType), CalendarContract.AUTHORITY)
        }.getOrDefault(true)
    }

    /** All events KULendar created in the calendar, excluding ones already pending deletion. */
    suspend fun managedEvents(calendarId: Long): List<ExistingEvent> = withContext(Dispatchers.IO) {
        resolver.query(
            Events.CONTENT_URI,
            EVENT_PROJECTION,
            "${Events.CALENDAR_ID} = ? AND ${Events.DELETED} = 0 AND ${Events.DESCRIPTION} LIKE ?",
            arrayOf(calendarId.toString(), EventMarker.LIKE_PATTERN),
            null,
        )?.use { cursor -> cursor.mapRows { it.toExistingEvent() }.filterNotNull() }
            .orEmpty()
    }

    /** Applies [plan] in batches. Removals go first so duplicates disappear before anything is added. */
    suspend fun apply(calendarId: Long, plan: SyncPlan) = withContext(Dispatchers.IO) {
        val operations = buildList {
            plan.deletes.forEach { add(ContentProviderOperation.newDelete(eventUri(it.id)).build()) }
            plan.updates.forEach {
                add(ContentProviderOperation.newUpdate(eventUri(it.existing.id)).withValues(it.spec.toContentValues(null)).build())
            }
            plan.inserts.forEach {
                add(ContentProviderOperation.newInsert(Events.CONTENT_URI).withValues(it.toContentValues(calendarId)).build())
            }
        }
        operations.chunked(BATCH_SIZE).forEach { resolver.applyBatch(CalendarContract.AUTHORITY, ArrayList(it)) }
    }

    /** Removes every KULendar event from the calendar and returns how many there were. */
    suspend fun removeManagedEvents(calendarId: Long): Int {
        val events = managedEvents(calendarId)
        apply(calendarId, SyncPlan(inserts = emptyList(), updates = emptyList(), deletes = events, unchanged = 0))
        return events.size
    }

    private fun eventUri(id: Long) = ContentUris.withAppendedId(Events.CONTENT_URI, id)

    private fun EventSpec.toContentValues(calendarId: Long?) = ContentValues().apply {
        if (calendarId != null) put(Events.CALENDAR_ID, calendarId)
        put(Events.TITLE, title)
        put(Events.DESCRIPTION, description)
        put(Events.EVENT_LOCATION, location)
        put(Events.DTSTART, startMillis)
        put(Events.DTEND, endMillis)
        put(Events.EVENT_TIMEZONE, timeZone)
        put(Events.ALL_DAY, 0)
        put(Events.AVAILABILITY, Events.AVAILABILITY_BUSY)
        put(Events.STATUS, if (tentative) Events.STATUS_TENTATIVE else Events.STATUS_CONFIRMED)
    }

    private fun Cursor.toDeviceCalendar() = DeviceCalendar(
        id = getLong(0),
        displayName = getStringOrNull(1).orEmpty(),
        accountName = getStringOrNull(2).orEmpty(),
        accountType = getStringOrNull(3).orEmpty(),
        color = getIntOrNull(4) ?: 0,
        accessLevel = getIntOrNull(5) ?: 0,
        visible = getIntOrNull(6) != 0,
        syncEvents = getIntOrNull(7) != 0,
    )

    private fun Cursor.toExistingEvent(): ExistingEvent? {
        val description = getStringOrNull(3)
        val key = EventMarker.extractKey(description) ?: return null
        val start = getLong(5)
        return ExistingEvent(
            id = getLong(0),
            key = key,
            syncId = getStringOrNull(1),
            title = getStringOrNull(2),
            description = description,
            location = getStringOrNull(4),
            startMillis = start,
            endMillis = getLongOrNull(6) ?: start,
            tentative = getIntOrNull(7) == Events.STATUS_TENTATIVE,
        )
    }

    companion object {
        val PERMISSIONS = arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)

        private const val BATCH_SIZE = 100

        private val CALENDAR_PROJECTION = arrayOf(
            Calendars._ID,
            Calendars.CALENDAR_DISPLAY_NAME,
            Calendars.ACCOUNT_NAME,
            Calendars.ACCOUNT_TYPE,
            Calendars.CALENDAR_COLOR,
            Calendars.CALENDAR_ACCESS_LEVEL,
            Calendars.VISIBLE,
            Calendars.SYNC_EVENTS,
        )

        private val EVENT_PROJECTION = arrayOf(
            Events._ID,
            Events._SYNC_ID,
            Events.TITLE,
            Events.DESCRIPTION,
            Events.EVENT_LOCATION,
            Events.DTSTART,
            Events.DTEND,
            Events.STATUS,
        )
    }
}

private inline fun <T> Cursor.mapRows(transform: (Cursor) -> T): List<T> {
    val rows = ArrayList<T>(count.coerceAtLeast(0))
    while (moveToNext()) rows += transform(this)
    return rows
}
