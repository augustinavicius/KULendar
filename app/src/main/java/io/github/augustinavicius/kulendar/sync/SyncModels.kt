package io.github.augustinavicius.kulendar.sync

import java.time.LocalDate
import java.time.ZoneId

/** The university publishes wall-clock times in Lithuanian local time. */
val UNIVERSITY_ZONE: ZoneId = ZoneId.of("Europe/Vilnius")

/** A calendar event as KULendar wants it to exist in the target calendar. */
data class EventSpec(
    val key: String,
    val title: String,
    val description: String,
    val location: String,
    val startMillis: Long,
    val endMillis: Long,
    val timeZone: String,
    val tentative: Boolean,
)

/** An event created by KULendar that is currently stored in the device calendar. */
data class ExistingEvent(
    val id: Long,
    val key: String,
    /** Id assigned by the calendar's sync adapter; null until the event has been uploaded. */
    val syncId: String?,
    val title: String?,
    val description: String?,
    val location: String?,
    val startMillis: Long,
    val endMillis: Long,
    val tentative: Boolean,
)

/** The half-open time interval `[startMillis, endMillisExclusive)` a sync is responsible for. */
data class SyncWindow(val startMillis: Long, val endMillisExclusive: Long) {
    operator fun contains(millis: Long): Boolean = millis >= startMillis && millis < endMillisExclusive
}

/** The days to sync, [firstDay] to [lastDay] inclusive, and the matching [window]. */
data class SyncRange(val firstDay: LocalDate, val lastDay: LocalDate, val window: SyncWindow) {
    companion object {
        fun around(today: LocalDate, pastDays: Int, futureDays: Int, zone: ZoneId = UNIVERSITY_ZONE): SyncRange {
            val firstDay = today.minusDays(pastDays.toLong())
            val lastDay = today.plusDays(futureDays.toLong())
            val window = SyncWindow(
                startMillis = firstDay.atStartOfDay(zone).toInstant().toEpochMilli(),
                endMillisExclusive = lastDay.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            )
            return SyncRange(firstDay, lastDay, window)
        }
    }
}

data class EventUpdate(val existing: ExistingEvent, val spec: EventSpec)

data class SyncPlan(
    val inserts: List<EventSpec>,
    val updates: List<EventUpdate>,
    val deletes: List<ExistingEvent>,
    val unchanged: Int,
)
