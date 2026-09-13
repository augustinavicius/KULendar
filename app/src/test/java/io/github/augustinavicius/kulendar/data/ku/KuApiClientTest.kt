package io.github.augustinavicius.kulendar.data.ku

import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KuApiClientTest {

    private val server = MockWebServer()
    private lateinit var client: KuApiClient

    private val firstDay = LocalDate.of(2026, 9, 1)
    private val lastDay = LocalDate.of(2026, 12, 31)

    @Before
    fun setUp() {
        server.start()
        client = KuApiClient(OkHttpClient(), server.url("/"))
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun respond(code: Int, body: String) {
        server.enqueue(MockResponse.Builder().code(code).body(body).build())
    }

    private fun reservation(id: Long) =
        """{"id":$id,"name":"Lecture $id","from":"2026-09-17 12:00:00","to":"2026-09-17 13:30:00"}"""

    private fun page(ids: List<Long>, current: Int, last: Int, total: Int) =
        """{"success":true,"data":{"items":[${ids.joinToString(",") { reservation(it) }}],""" +
            """"meta":{"current_page":$current,"last_page":$last,"per_page":15,"total":$total}}}"""

    @Test
    fun `login posts the credentials as JSON and parses the tokens`() = runTest {
        respond(200, """{"success":true,"data":{"access_token":"access-1","refresh_token":"refresh-1","token_type":"Bearer","expires_in":3600}}""")

        val tokens = client.login("vardenis.pavardenis", "secret")

        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/api/mobile/auth/login", request.url.encodedPath)
        assertEquals("application/json", request.headers["Accept"])
        assertEquals("""{"uid":"vardenis.pavardenis","password":"secret"}""", request.body?.utf8())
        assertEquals("access-1", tokens.accessToken)
        assertEquals("refresh-1", tokens.refreshToken)
    }

    @Test
    fun `rejected login is reported as invalid credentials with the server message`() = runTest {
        respond(401, """{"success":false,"message":"Invalid credentials."}""")

        val error = runCatching { client.login("vardenis.pavardenis", "wrong") }.exceptionOrNull()

        assertTrue(error is KuInvalidCredentialsException)
        assertEquals("Invalid credentials.", error?.message)
    }

    @Test
    fun `rejected refresh token is reported as unauthorized`() = runTest {
        respond(401, """{"success":false,"message":"Invalid or expired refresh token."}""")

        val error = runCatching { client.refresh("old") }.exceptionOrNull()

        assertTrue(error is KuUnauthorizedException)
        assertEquals("""{"refresh_token":"old"}""", server.takeRequest().body?.utf8())
    }

    @Test
    fun `downloads every page of the requested date range`() = runTest {
        respond(200, page((1L..15L).toList(), current = 1, last = 2, total = 17))
        respond(200, page(listOf(16L, 17L), current = 2, last = 2, total = 17))

        val result = client.allReservations("token", firstDay, lastDay)

        assertEquals((1L..17L).toList(), result.reservations.map { it.id })
        assertTrue(result.complete)
        val first = server.takeRequest()
        assertEquals("/api/mobile/reservations", first.url.encodedPath)
        assertEquals("Bearer token", first.headers["Authorization"])
        assertEquals("2026-09-01", first.url.queryParameter("date_from"))
        assertEquals("2026-12-31", first.url.queryParameter("date_to"))
        assertEquals("1", first.url.queryParameter("page"))
        assertEquals("2", server.takeRequest().url.queryParameter("page"))
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `bare list pages are followed until a short page`() = runTest {
        respond(200, """{"success":true,"data":[${(1L..15L).joinToString(",") { reservation(it) }}]}""")
        respond(200, """{"success":true,"data":[${reservation(16)}]}""")

        val result = client.allReservations("token", firstDay, lastDay)

        assertEquals(16, result.reservations.size)
        assertTrue(result.complete)
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `a reservation shifting between pages marks the download incomplete`() = runTest {
        respond(200, page((1L..15L).toList(), current = 1, last = 2, total = 17))
        respond(200, page(listOf(15L, 17L), current = 2, last = 2, total = 17))

        val result = client.allReservations("token", firstDay, lastDay)

        assertEquals(16, result.reservations.size)
        assertFalse(result.complete)
    }

    @Test
    fun `an empty range takes a single request`() = runTest {
        respond(200, page(emptyList(), current = 1, last = 1, total = 0))

        val result = client.allReservations("token", firstDay, lastDay)

        assertTrue(result.reservations.isEmpty())
        assertTrue(result.complete)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `expired access token is reported as unauthorized`() = runTest {
        respond(401, """{"message":"Baigėsi sesija. Prisijunkite iš naujo"}""")

        val error = runCatching { client.allReservations("expired", firstDay, lastDay) }.exceptionOrNull()

        assertTrue(error is KuUnauthorizedException)
    }

    @Test
    fun `server failures and throttling are retryable server errors`() = runTest {
        respond(503, "<html>Service unavailable</html>")
        respond(429, """{"message":"Too Many Attempts."}""")

        val unavailable = runCatching { client.allReservations("token", firstDay, lastDay) }.exceptionOrNull()
        val throttled = runCatching { client.allReservations("token", firstDay, lastDay) }.exceptionOrNull()

        assertEquals(503, (unavailable as KuServerException).code)
        assertEquals(429, (throttled as KuServerException).code)
        assertEquals("Too Many Attempts.", throttled.message)
    }

    @Test
    fun `unreachable server is a network error`() = runTest {
        server.close()

        val error = runCatching { client.login("vardenis.pavardenis", "secret") }.exceptionOrNull()

        assertTrue(error is KuNetworkException)
    }
}
