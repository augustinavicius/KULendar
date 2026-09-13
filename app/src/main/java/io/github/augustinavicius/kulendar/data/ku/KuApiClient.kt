package io.github.augustinavicius.kulendar.data.ku

import io.github.augustinavicius.kulendar.net.await
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** Every reservation in a date range, and whether the download is known to be complete. */
data class KuReservationSet(val reservations: List<KuReservation>, val complete: Boolean)

/**
 * Client for the mobile API of tvarkarasciai.ku.lt.
 *
 * Logging in yields a one-hour bearer token and a rotating refresh token. Reservations come in fixed
 * pages of 15, newest first.
 */
class KuApiClient(
    private val httpClient: OkHttpClient,
    private val baseUrl: HttpUrl = DEFAULT_BASE_URL,
) {

    suspend fun login(uid: String, password: String): KuTokens {
        val body = buildJsonObject {
            put("uid", uid)
            put("password", password)
        }
        val response = execute(jsonPost("api/mobile/auth/login", body.toString()))
        return when (response.code) {
            in 200..299 -> KuResponseParser.parseTokens(response.body)
            401, 403, 422 -> throw KuInvalidCredentialsException(response.serverMessage ?: "Login rejected")
            else -> throw response.failure()
        }
    }

    suspend fun refresh(refreshToken: String): KuTokens {
        val body = buildJsonObject { put("refresh_token", refreshToken) }
        val response = execute(jsonPost("api/mobile/auth/refresh", body.toString()))
        return when (response.code) {
            in 200..299 -> KuResponseParser.parseTokens(response.body)
            400, 401, 403, 422 -> throw KuUnauthorizedException(response.serverMessage ?: "Refresh token rejected")
            else -> throw response.failure()
        }
    }

    /** One page of the reservations starting between [firstDay] and [lastDay], both inclusive. */
    suspend fun reservationsPage(
        accessToken: String,
        firstDay: LocalDate,
        lastDay: LocalDate,
        page: Int,
    ): KuReservationPage {
        val url = baseUrl.newBuilder()
            .addPathSegments("api/mobile/reservations")
            .addQueryParameter("date_from", firstDay.toString())
            .addQueryParameter("date_to", lastDay.toString())
            .addQueryParameter("page", page.toString())
            .build()
        val request = Request.Builder()
            .url(url)
            .header("Accept", JSON)
            .header("Authorization", "Bearer $accessToken")
            .build()
        val response = execute(request)
        return when (response.code) {
            in 200..299 -> KuResponseParser.parseReservationPage(response.body)
            401, 403 -> throw KuUnauthorizedException(response.serverMessage ?: "Access token rejected")
            else -> throw response.failure()
        }
    }

    /**
     * Downloads all reservations between [firstDay] and [lastDay], following pagination.
     *
     * The result is marked incomplete when fewer distinct reservations arrived than the server
     * announced, which happens if the timetable changes while the pages are being fetched.
     */
    suspend fun allReservations(accessToken: String, firstDay: LocalDate, lastDay: LocalDate): KuReservationSet {
        val byId = LinkedHashMap<Long, KuReservation>()
        var expectedTotal: Int? = null
        var page = 1
        while (true) {
            val result = reservationsPage(accessToken, firstDay, lastDay, page)
            result.items.forEach { byId[it.id] = it }
            val meta = result.meta
            if (meta?.total != null) expectedTotal = meta.total
            val lastPage = meta?.lastPage
            val finished = result.items.isEmpty() ||
                if (lastPage != null) page >= lastPage else result.items.size < (meta?.perPage ?: DEFAULT_PAGE_SIZE)
            if (finished) break
            if (++page > MAX_PAGES) throw KuProtocolException("Reservation list did not end after $MAX_PAGES pages")
        }
        val complete = expectedTotal?.let { byId.size >= it } ?: true
        return KuReservationSet(byId.values.toList(), complete)
    }

    private fun jsonPost(path: String, json: String): Request = Request.Builder()
        .url(baseUrl.newBuilder().addPathSegments(path).build())
        .header("Accept", JSON)
        .post(json.toRequestBody(JSON_MEDIA_TYPE))
        .build()

    private suspend fun execute(request: Request): HttpResult = withContext(Dispatchers.IO) {
        try {
            httpClient.newCall(request).await().use { HttpResult(it.code, it.body.string()) }
        } catch (e: IOException) {
            throw KuNetworkException(e)
        }
    }

    private class HttpResult(val code: Int, val body: String) {
        val serverMessage: String? get() = KuResponseParser.parseMessage(body)

        fun failure(): KuException = when (code) {
            429, in 500..599 -> KuServerException(code, serverMessage ?: "HTTP $code")
            else -> KuProtocolException("Unexpected HTTP $code" + (serverMessage?.let { ": $it" } ?: ""))
        }
    }

    companion object {
        val DEFAULT_BASE_URL: HttpUrl = "https://tvarkarasciai.ku.lt/".toHttpUrl()
        private const val JSON = "application/json"
        private val JSON_MEDIA_TYPE = JSON.toMediaType()
        private const val DEFAULT_PAGE_SIZE = 15
        private const val MAX_PAGES = 500
    }
}
