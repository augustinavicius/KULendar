package io.github.augustinavicius.kulendar.sync

import io.github.augustinavicius.kulendar.data.ku.KuProtocolException
import io.github.augustinavicius.kulendar.data.ku.KuReservation
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Localized texts used in event descriptions and locations. */
data class EventLabels(
    val lecturer: String,
    val responsible: String,
    val room: String,
    val onlineMeeting: String,
    val online: String,
    val notApproved: String,
    val syncedBy: String,
)

/** Turns university reservations into calendar events. */
class EventMapper(private val labels: EventLabels, private val zone: ZoneId = UNIVERSITY_ZONE) {

    fun map(reservation: KuReservation): EventSpec {
        val start = parseDateTime(reservation.from)
        val end = parseDateTime(reservation.to).takeIf { it.isAfter(start) }
            ?: start.plusMinutes((reservation.duration ?: DEFAULT_DURATION_MINUTES).coerceAtLeast(1).toLong())
        val key = reservation.id.toString()
        return EventSpec(
            key = key,
            title = reservation.name.trim(),
            description = description(reservation, key),
            location = location(reservation),
            startMillis = start.toInstant().toEpochMilli(),
            endMillis = end.toInstant().toEpochMilli(),
            timeZone = zone.id,
            tentative = !reservation.approved,
        )
    }

    /** Accepts the API's local `2026-12-17 12:00:00` format as well as ISO-8601 with or without an offset. */
    internal fun parseDateTime(value: String): ZonedDateTime {
        val text = value.trim().replace(' ', 'T')
        return runCatching { LocalDateTime.parse(text).atZone(zone) }
            .recoverCatching { OffsetDateTime.parse(text).atZoneSameInstant(zone) }
            .getOrElse { throw KuProtocolException("Unrecognized date \"$value\"", it) }
    }

    internal fun location(reservation: KuReservation): String {
        val place = reservation.reservable
        val isRoom = place?.resource != null
        if (reservation.isRemote && !isRoom) return labels.online
        if (place == null) return ""
        val name = place.name?.trim().orEmpty()
        val address = (place.resource?.address ?: place.address)?.trim().orEmpty()
        return when {
            address.isEmpty() || name.contains(address, ignoreCase = true) -> name
            name.isEmpty() -> address
            else -> "$name, $address"
        }
    }

    internal fun description(reservation: KuReservation, key: String): String = buildString {
        reservation.remoteAddress?.trim()?.takeIf { reservation.isRemote && it.isNotEmpty() }?.let {
            appendLine("${labels.onlineMeeting}: $it")
        }
        reservation.responsible?.name?.trim()?.takeIf { it.isNotEmpty() }?.let {
            val label = if (reservation.type == TYPE_LECTURE) labels.lecturer else labels.responsible
            appendLine("$label: $it")
        }
        reservation.reservable?.takeIf { it.resource != null }?.let { room ->
            val details = listOfNotNull(room.name, room.description).map { it.trim() }.filter { it.isNotEmpty() }
            if (details.isNotEmpty()) appendLine("${labels.room}: ${details.joinToString(" · ")}")
        }
        if (!reservation.approved) appendLine(labels.notApproved)
        reservation.description?.trim()?.takeIf { it.isNotEmpty() }?.let {
            if (isNotEmpty()) appendLine()
            appendLine(it)
        }
        if (isNotEmpty()) appendLine()
        append("${labels.syncedBy} · ${EventMarker.format(key)}")
    }

    private companion object {
        const val TYPE_LECTURE = "lecture"
        const val DEFAULT_DURATION_MINUTES = 90
    }
}
