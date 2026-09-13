package io.github.augustinavicius.kulendar.data.ku

import io.github.augustinavicius.kulendar.data.security.AccountInfo
import io.github.augustinavicius.kulendar.data.security.CredentialsStore
import io.github.augustinavicius.kulendar.data.security.StoredCredentials
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class KuAuthManagerTest {

    private val server = MockWebServer()
    private val store = FakeCredentialsStore()
    private val now = 1_000_000L
    private lateinit var auth: KuAuthManager

    private val signedIn = StoredCredentials(
        uid = "vardenis.pavardenis",
        password = "secret",
        accessToken = "access-0",
        accessTokenExpiresAt = now + 30 * 60_000,
        refreshToken = "refresh-0",
    )
    private val expired = signedIn.copy(accessTokenExpiresAt = now - 1)

    @Before
    fun setUp() {
        server.start()
        auth = KuAuthManager(KuApiClient(OkHttpClient(), server.url("/")), store) { now }
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun respond(code: Int, body: String) {
        server.enqueue(MockResponse.Builder().code(code).body(body).build())
    }

    private fun tokens(n: Int) =
        """{"success":true,"data":{"access_token":"access-$n","refresh_token":"refresh-$n","token_type":"Bearer",""" +
            """"expires_in":3600,"user":{"id":7,"name":"Vardenis Pavardenis","email":"v@edu.ku.lt"}}}"""

    @Test
    fun `sign in stores the credentials and tokens`() = runTest {
        respond(200, tokens(1))

        val account = auth.signIn("vardenis.pavardenis", "secret")

        assertEquals(AccountInfo("vardenis.pavardenis", "Vardenis Pavardenis"), account)
        val stored = store.load()!!
        assertEquals("secret", stored.password)
        assertEquals("access-1", stored.accessToken)
        assertEquals("refresh-1", stored.refreshToken)
        assertEquals(now + 3_600_000, stored.accessTokenExpiresAt)
    }

    @Test
    fun `failed sign in stores nothing`() = runTest {
        respond(401, """{"success":false,"message":"Invalid credentials."}""")

        val error = runCatching { auth.signIn("vardenis.pavardenis", "wrong") }.exceptionOrNull()

        assertTrue(error is KuInvalidCredentialsException)
        assertNull(store.load())
    }

    @Test
    fun `a valid access token is reused without contacting the server`() = runTest {
        store.save(signedIn)

        assertEquals("access-0", auth.withAccessToken { it })
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `an expiring access token is refreshed and the rotated tokens are stored`() = runTest {
        store.save(expired)
        respond(200, tokens(1))

        assertEquals("access-1", auth.withAccessToken { it })

        val request = server.takeRequest()
        assertEquals("/api/mobile/auth/refresh", request.url.encodedPath)
        assertEquals("""{"refresh_token":"refresh-0"}""", request.body?.utf8())
        assertEquals("refresh-1", store.load()?.refreshToken)
        assertEquals("secret", store.load()?.password)
    }

    @Test
    fun `a rejected refresh token falls back to logging in with the stored password`() = runTest {
        store.save(expired)
        respond(401, """{"success":false,"message":"Invalid or expired refresh token."}""")
        respond(200, tokens(2))

        assertEquals("access-2", auth.withAccessToken { it })

        assertEquals("/api/mobile/auth/refresh", server.takeRequest().url.encodedPath)
        val login = server.takeRequest()
        assertEquals("/api/mobile/auth/login", login.url.encodedPath)
        assertEquals("""{"uid":"vardenis.pavardenis","password":"secret"}""", login.body?.utf8())
        assertEquals("refresh-2", store.load()?.refreshToken)
    }

    @Test
    fun `a token rejected mid-sync is renewed once and the call retried`() = runTest {
        store.save(signedIn)
        respond(200, tokens(1))
        val usedTokens = mutableListOf<String>()

        val result = auth.withAccessToken { token ->
            usedTokens += token
            if (token == "access-0") throw KuUnauthorizedException("Session expired")
            "downloaded"
        }

        assertEquals("downloaded", result)
        assertEquals(listOf("access-0", "access-1"), usedTokens)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a changed university password surfaces as invalid credentials`() = runTest {
        store.save(expired.copy(refreshToken = null))
        respond(401, """{"success":false,"message":"Invalid credentials."}""")

        val error = runCatching { auth.withAccessToken { it } }.exceptionOrNull()

        assertTrue(error is KuInvalidCredentialsException)
    }

    @Test
    fun `without an account there is no token`() = runTest {
        val error = runCatching { auth.withAccessToken { it } }.exceptionOrNull()

        assertTrue(error is KuNotSignedInException)
        assertEquals(0, server.requestCount)
    }
}

private class FakeCredentialsStore : CredentialsStore {
    private val state = MutableStateFlow<StoredCredentials?>(null)

    override val account: Flow<AccountInfo?> = state.map { it?.let { credentials -> AccountInfo(credentials.uid, credentials.displayName) } }

    override suspend fun load(): StoredCredentials? = state.value

    override suspend fun save(credentials: StoredCredentials) {
        state.value = credentials
    }

    override suspend fun clear() {
        state.value = null
    }
}
