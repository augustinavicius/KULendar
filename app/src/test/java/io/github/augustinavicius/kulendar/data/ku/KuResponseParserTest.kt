package io.github.augustinavicius.kulendar.data.ku

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KuResponseParserTest {

    private val reservationJson = """
        {
          "id": 100879,
          "name": "Duomenų struktūros ir algoritmai",
          "description": null,
          "from": "2026-12-17 12:00:00",
          "to": "2026-12-17 13:30:00",
          "duration": 90,
          "type": "lecture",
          "approved": true,
          "is_remote": false,
          "remote_address": null,
          "user": {"id": 1, "name": "Ona Onaitė", "email": "ona.onaite@ku.lt"},
          "responsible": {"id": 2, "name": "Jonas Jonaitis", "email": "jonas.jonaitis@ku.lt"},
          "reservable": {
            "type": "item", "id": 104, "name": "319 aud.", "description": "Kompiuterinė auditorija", "contact_info": null,
            "resource": {"id": 16, "name": "Fakultetas", "address": "Bijūnų g. 17, Klaipėda"}
          },
          "annexes": [],
          "invitations": [{"id": 3, "name": "Student", "email": "student@edu.ku.lt"}],
          "repeater": {"id": 4305, "type": "weekly", "interval": 1, "weekdays": ["thursday"], "until": "2026-12-17"}
        }
    """.trimIndent()

    @Test
    fun `parses the paginated response`() {
        val page = KuResponseParser.parseReservationPage(
            """{"success":true,"data":{"items":[$reservationJson],"meta":{"current_page":1,"last_page":3,"per_page":15,"total":40}}}""",
        )

        assertEquals(1, page.items.size)
        assertEquals(KuPageMeta(currentPage = 1, lastPage = 3, perPage = 15, total = 40), page.meta)
        with(page.items.single()) {
            assertEquals(100879L, id)
            assertEquals("2026-12-17 12:00:00", from)
            assertEquals("Jonas Jonaitis", responsible?.name)
            assertEquals("Bijūnų g. 17, Klaipėda", reservable?.resource?.address)
        }
    }

    @Test
    fun `parses the older bare list response`() {
        val page = KuResponseParser.parseReservationPage("""{"success":true,"data":[$reservationJson]}""")

        assertEquals(1, page.items.size)
        assertNull(page.meta)
    }

    @Test
    fun `nulls fall back to defaults`() {
        val item = """{"id":1,"name":"X","from":"2026-12-17 12:00:00","to":"2026-12-17 13:30:00","approved":null,"is_remote":null}"""

        val reservation = KuResponseParser.parseReservationPage("""{"data":{"items":[$item]}}""").items.single()

        assertTrue(reservation.approved)
        assertEquals(false, reservation.isRemote)
    }

    @Test(expected = KuProtocolException::class)
    fun `a page whose items cannot be found is an error, not an empty page`() {
        KuResponseParser.parseReservationPage("""{"success":true,"data":{"reservations":[]}}""")
    }

    @Test(expected = KuProtocolException::class)
    fun `reservations without required fields are an error`() {
        KuResponseParser.parseReservationPage("""{"data":{"items":[{"id":1,"name":"X"}]}}""")
    }

    @Test(expected = KuProtocolException::class)
    fun `non JSON responses are an error`() {
        KuResponseParser.parseReservationPage("<html>Maintenance</html>")
    }

    @Test
    fun `parses login tokens`() {
        val tokens = KuResponseParser.parseTokens(
            """{"success":true,"data":{"access_token":"a","refresh_token":"r","token_type":"Bearer","expires_in":3600,"user":{"id":7,"name":"Vardenis Pavardenis","email":"v@edu.ku.lt"}}}""",
        )

        assertEquals("a", tokens.accessToken)
        assertEquals("r", tokens.refreshToken)
        assertEquals(3600L, tokens.expiresInSeconds)
        assertEquals("Vardenis Pavardenis", tokens.user?.name)
    }

    @Test
    fun `extracts error messages`() {
        assertEquals("Invalid credentials.", KuResponseParser.parseMessage("""{"success":false,"message":"Invalid credentials."}"""))
        assertNull(KuResponseParser.parseMessage("not json"))
    }
}
