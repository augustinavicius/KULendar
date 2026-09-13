package io.github.augustinavicius.kulendar.sync

import io.github.augustinavicius.kulendar.data.ku.KuProtocolException
import io.github.augustinavicius.kulendar.data.ku.KuReservable
import io.github.augustinavicius.kulendar.data.ku.KuReservation
import io.github.augustinavicius.kulendar.data.ku.KuResource
import io.github.augustinavicius.kulendar.data.ku.KuUser
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventMapperTest {

    private val labels = EventLabels(
        lecturer = "Lecturer",
        responsible = "Responsible",
        room = "Room",
        onlineMeeting = "Online meeting",
        online = "Online",
        notApproved = "Not approved yet",
        syncedBy = "Synced by KULendar",
    )
    private val mapper = EventMapper(labels)

    private val room = KuReservable(
        type = "item",
        id = 104,
        name = "319 aud. (Bijūnų g. 17)",
        description = "Kompiuterinė auditorija",
        resource = KuResource(id = 16, name = "Fakultetas, Bijūnų g. 17", address = "Bijūnų g. 17, LT-91225 Klaipėda, Lietuva"),
    )

    private fun reservation(
        from: String = "2026-12-17 12:00:00",
        to: String = "2026-12-17 13:30:00",
        type: String = "lecture",
        approved: Boolean = true,
        isRemote: Boolean = false,
        remoteAddress: String? = null,
        reservable: KuReservable? = room,
        description: String? = null,
    ) = KuReservation(
        id = 100879,
        name = "Duomenų struktūros ir algoritmai",
        description = description,
        from = from,
        to = to,
        duration = 90,
        type = type,
        approved = approved,
        isRemote = isRemote,
        remoteAddress = remoteAddress,
        responsible = KuUser(id = 1, name = "Jonas Jonaitis", email = "jonas.jonaitis@ku.lt"),
        reservable = reservable,
    )

    private fun millis(instant: String) = Instant.parse(instant).toEpochMilli()

    @Test
    fun `winter lecture times are Vilnius wall clock time, UTC+2`() {
        val event = mapper.map(reservation())

        assertEquals(millis("2026-12-17T10:00:00Z"), event.startMillis)
        assertEquals(millis("2026-12-17T11:30:00Z"), event.endMillis)
        assertEquals("Europe/Vilnius", event.timeZone)
    }

    @Test
    fun `summer lecture times use daylight saving time, UTC+3`() {
        val event = mapper.map(reservation(from = "2026-09-17 08:20:00", to = "2026-09-17 09:50:00"))

        assertEquals(millis("2026-09-17T05:20:00Z"), event.startMillis)
        assertEquals(millis("2026-09-17T06:50:00Z"), event.endMillis)
    }

    @Test
    fun `ISO timestamps with an offset are converted by instant`() {
        val event = mapper.map(reservation(from = "2026-12-17T10:00:00.000000Z", to = "2026-12-17T11:30:00+00:00"))

        assertEquals(millis("2026-12-17T10:00:00Z"), event.startMillis)
        assertEquals(millis("2026-12-17T11:30:00Z"), event.endMillis)
    }

    @Test
    fun `duration is used when the end is not after the start`() {
        val event = mapper.map(reservation(to = "2026-12-17 12:00:00"))

        assertEquals(event.startMillis + 90 * 60_000, event.endMillis)
    }

    @Test(expected = KuProtocolException::class)
    fun `unparseable dates are a protocol error`() {
        mapper.map(reservation(from = "tomorrow"))
    }

    @Test
    fun `title key and marker identify the reservation`() {
        val event = mapper.map(reservation())

        assertEquals("Duomenų struktūros ir algoritmai", event.title)
        assertEquals("100879", event.key)
        assertEquals(event.key, EventMarker.extractKey(event.description))
    }

    @Test
    fun `room location includes the building address`() {
        val event = mapper.map(reservation())

        assertEquals("319 aud. (Bijūnų g. 17), Bijūnų g. 17, LT-91225 Klaipėda, Lietuva", event.location)
    }

    @Test
    fun `address already contained in the place name is not repeated`() {
        val building = KuReservable(type = "resource", id = 2, name = "Fakultetas, S. Nėries g. 5", address = "S. Nėries g. 5")

        assertEquals("Fakultetas, S. Nėries g. 5", mapper.map(reservation(reservable = building)).location)
    }

    @Test
    fun `remote lecture is online and links the meeting first in the description`() {
        val building = KuReservable(type = "resource", id = 2, name = "Fakultetas, S. Nėries g. 5", address = "S. Nėries g. 5")
        val link = "https://teams.microsoft.com/l/meetup-join/abc"

        val event = mapper.map(reservation(isRemote = true, remoteAddress = link, reservable = building))

        assertEquals("Online", event.location)
        assertTrue(event.description.startsWith("Online meeting: $link\n"))
    }

    @Test
    fun `description lists lecturer, room, notes and the marker`() {
        val event = mapper.map(reservation(description = "Bring a laptop"))

        assertEquals(
            "Lecturer: Jonas Jonaitis\n" +
                "Room: 319 aud. (Bijūnų g. 17) · Kompiuterinė auditorija\n" +
                "\n" +
                "Bring a laptop\n" +
                "\n" +
                "Synced by KULendar · kulendar-id:100879",
            event.description,
        )
    }

    @Test
    fun `reservations other than lectures name the responsible person`() {
        val event = mapper.map(reservation(type = "event"))

        assertTrue(event.description.startsWith("Responsible: Jonas Jonaitis\n"))
    }

    @Test
    fun `unapproved reservations are tentative`() {
        assertFalse(mapper.map(reservation()).tentative)

        val event = mapper.map(reservation(approved = false))

        assertTrue(event.tentative)
        assertTrue(event.description.contains("Not approved yet"))
    }
}
