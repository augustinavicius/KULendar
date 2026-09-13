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
}
