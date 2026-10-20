package io.github.augustinavicius.kulendar.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EventMarkerTest {

    @Test
    fun `extracts the key that was formatted into a description`() {
        val description = "Lecturer: Jonas Jonaitis\n\nSynced by KULendar · ${EventMarker.format("100879")}"

        assertEquals("100879", EventMarker.extractKey(description))
    }

    @Test
    fun `finds the key after the description was turned into HTML by Google Calendar`() {
        val description = "<p>Lecturer: Jonas Jonaitis<br><br>Synced by KULendar · kulendar-id:42</p>"

        assertEquals("42", EventMarker.extractKey(description))
    }

    @Test
    fun `events without a marker are not recognized`() {
        assertNull(EventMarker.extractKey("Dentist appointment"))
        assertNull(EventMarker.extractKey(""))
        assertNull(EventMarker.extractKey(null))
    }

    @Test
    fun `a stamped fingerprint can be read back and stripped`() {
        val description = "Lecturer: Jonas\n\nSynced by KULendar · kulendar-id:42"
        val fingerprint = EventMarker.fingerprint("Lecture", description, "319 aud.")

        val stamped = EventMarker.stamp(description, fingerprint)

        assertEquals(fingerprint, EventMarker.extractFingerprint(stamped))
        assertEquals("42", EventMarker.extractKey(stamped))
        assertEquals(description, EventMarker.withoutFingerprint(stamped))
        assertEquals(fingerprint.description, EventMarker.fingerprint(null, stamped, null).description)
    }

    @Test
    fun `stamping again replaces the fingerprint in place`() {
        val first = "<p>kulendar-id:42 · kulendar-sum:${EventMarker.fingerprint("a", "b", "c")}</p>"
        val second = EventMarker.fingerprint("x", "y", "z")

        val restamped = EventMarker.stamp(first, second)

        assertEquals("<p>kulendar-id:42 · kulendar-sum:$second</p>", restamped)
    }

    @Test
    fun `hashes ignore round trip whitespace`() {
        assertEquals(EventMarker.hash("a\nb"), EventMarker.hash(" a\r\nb \n"))
        assertEquals(EventMarker.hash(null), EventMarker.hash(""))
    }
}
