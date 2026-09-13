package io.github.augustinavicius.kulendar.sync

import android.content.Context
import io.github.augustinavicius.kulendar.R
import io.github.augustinavicius.kulendar.data.calendar.CalendarRepository
import io.github.augustinavicius.kulendar.data.ku.KuApiClient
import io.github.augustinavicius.kulendar.data.ku.KuAuthManager
import io.github.augustinavicius.kulendar.data.ku.KuInvalidCredentialsException
import io.github.augustinavicius.kulendar.data.ku.KuNetworkException
import io.github.augustinavicius.kulendar.data.ku.KuNotSignedInException
import io.github.augustinavicius.kulendar.data.ku.KuProtocolException
import io.github.augustinavicius.kulendar.data.ku.KuServerException
import io.github.augustinavicius.kulendar.data.ku.KuUnauthorizedException
import io.github.augustinavicius.kulendar.data.settings.SettingsRepository
import io.github.augustinavicius.kulendar.data.settings.SyncRecord
import io.github.augustinavicius.kulendar.data.settings.SyncStatus
import io.github.augustinavicius.kulendar.data.settings.SyncTrigger
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Mirrors the university timetable into the selected calendar. Syncs never overlap. */
class SyncEngine(
    private val context: Context,
    private val settings: SettingsRepository,
    private val auth: KuAuthManager,
    private val api: KuApiClient,
    private val calendars: CalendarRepository,
    private val clock: Clock = Clock.systemUTC(),
) {
    private val mutex = Mutex()

    suspend fun sync(trigger: SyncTrigger): SyncRecord = mutex.withLock {
        val record = try {
            runSync(trigger)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncRecord(clock.millis(), trigger, e.toSyncStatus(), message = e.message?.take(MAX_MESSAGE_LENGTH))
        }
        settings.addRecord(record)
        record
    }

    private suspend fun runSync(trigger: SyncTrigger): SyncRecord {
        fun stopped(status: SyncStatus) = SyncRecord(clock.millis(), trigger, status)

        if (!auth.isSignedIn()) return stopped(SyncStatus.NOT_SIGNED_IN)
        val config = settings.current()
        val selected = config.calendar ?: return stopped(SyncStatus.NO_CALENDAR)
        if (!calendars.hasPermission()) return stopped(SyncStatus.NO_PERMISSION)
        val calendar = calendars.calendar(selected.id)
            ?.takeIf { it.accountName == selected.accountName && it.accountType == selected.accountType }
            ?: return stopped(SyncStatus.CALENDAR_MISSING)

        val range = SyncRange.around(LocalDate.now(clock.withZone(UNIVERSITY_ZONE)), config.pastDays, config.futureDays)
        val download = auth.withAccessToken { token -> api.allReservations(token, range.firstDay, range.lastDay) }
        val mapper = EventMapper(eventLabels(context))
        val desired = download.reservations.map(mapper::map).filter { it.startMillis in range.window }
        val plan = SyncPlanner.plan(
            desired = desired,
            existing = calendars.managedEvents(calendar.id),
            window = range.window,
            allowRemovals = download.complete,
        )
        calendars.apply(calendar.id, plan)
        return SyncRecord(
            finishedAt = clock.millis(),
            trigger = trigger,
            status = SyncStatus.SUCCESS,
            fetched = desired.size,
            inserted = plan.inserts.size,
            updated = plan.updates.size,
            deleted = plan.deletes.size,
            unchanged = plan.unchanged,
            complete = download.complete,
        )
    }

    private fun Exception.toSyncStatus(): SyncStatus = when (this) {
        is KuNotSignedInException -> SyncStatus.NOT_SIGNED_IN
        is KuInvalidCredentialsException -> SyncStatus.INVALID_CREDENTIALS
        is KuNetworkException -> SyncStatus.NETWORK_ERROR
        is KuServerException, is KuUnauthorizedException -> SyncStatus.SERVER_ERROR
        is KuProtocolException -> SyncStatus.PROTOCOL_ERROR
        is SecurityException -> SyncStatus.NO_PERMISSION
        else -> SyncStatus.FAILED
    }

    private companion object {
        const val MAX_MESSAGE_LENGTH = 300
    }
}

internal fun eventLabels(context: Context) = EventLabels(
    lecturer = context.getString(R.string.event_lecturer),
    responsible = context.getString(R.string.event_responsible),
    room = context.getString(R.string.event_room),
    onlineMeeting = context.getString(R.string.event_online_meeting),
    online = context.getString(R.string.event_online),
    notApproved = context.getString(R.string.event_not_approved),
    syncedBy = context.getString(R.string.event_synced_by),
)
