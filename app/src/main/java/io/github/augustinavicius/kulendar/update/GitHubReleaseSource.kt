package io.github.augustinavicius.kulendar.update

import io.github.augustinavicius.kulendar.net.await
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** Reads KULendar releases from GitHub. The repository is public, so no token is needed. */
class GitHubReleaseSource(
    private val httpClient: OkHttpClient,
    private val repository: String,
    private val apiBaseUrl: HttpUrl = DEFAULT_API_URL,
) {
    // Downloads may take a while on slow connections, so only stalls are treated as failures.
    private val downloadClient by lazy {
        httpClient.newBuilder().callTimeout(0, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()
    }

    /** The newest stable release, or null while there is none. */
    suspend fun latestStable(): GitHubRelease? {
        val response = get(apiUrl("releases/latest"), GITHUB_JSON)
        return when (response.code) {
            200 -> decode(GitHubRelease.serializer(), response.body, "release")
            404 -> null
            else -> throw response.failure()
        }
    }

    /** Recent releases of both channels. */
    suspend fun recentReleases(): List<GitHubRelease> {
        val response = get(apiUrl("releases").newBuilder().addQueryParameter("per_page", "30").build(), GITHUB_JSON)
        if (response.code != 200) throw response.failure()
        return decode(ListSerializer(GitHubRelease.serializer()), response.body, "release list")
    }

    suspend fun manifest(asset: GitHubAsset): UpdateManifest {
        val response = get(asset.downloadUrl.toHttpUrlOrNull() ?: throw invalidUrl(), OCTET_STREAM)
        if (response.code != 200) throw response.failure()
        return decode(UpdateManifest.serializer(), response.body, "update manifest")
    }

    /**
     * Downloads [url] to [target] and verifies [expectedSize] and [expectedSha256]. [onProgress] receives
     * values from 0 to 1. Nothing is left at [target] unless the file checks out.
     */
    suspend fun download(
        url: String,
        target: File,
        expectedSize: Long,
        expectedSha256: String,
        onProgress: (Float) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url.toHttpUrlOrNull() ?: throw invalidUrl())
            .header("Accept", OCTET_STREAM)
            .build()
        target.parentFile?.mkdirs()
        val partial = File(target.path + ".part")
        try {
            downloadClient.newCall(request).await().use { response ->
                if (response.code != 200) throw TextResponse(response.code, "", response.header(RATE_LIMIT_REMAINING)).failure()
                val digest = MessageDigest.getInstance("SHA-256")
                var received = 0L
                response.body.byteStream().use { input ->
                    partial.outputStream().use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            received += read
                            if (received > expectedSize) throw mismatch("The download is larger than the release says")
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            onProgress(received.toFloat() / expectedSize)
                        }
                    }
                }
                if (received != expectedSize) throw mismatch("The download is incomplete")
                if (!digest.digest().toHex().equals(expectedSha256, ignoreCase = true)) throw mismatch("The download is corrupted")
                if (!partial.renameTo(target)) throw UpdateException(UpdateErrorKind.VERIFICATION, "Could not store the download")
            }
        } catch (e: IOException) {
            throw UpdateException(UpdateErrorKind.NETWORK, e.message ?: "Network error", e)
        } finally {
            partial.delete()
        }
    }

    private fun apiUrl(path: String): HttpUrl =
        apiBaseUrl.newBuilder().addPathSegments("repos/$repository/$path").build()

    private suspend fun get(url: HttpUrl, accept: String): TextResponse = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", accept)
            .header("X-GitHub-Api-Version", API_VERSION)
            .build()
        try {
            httpClient.newCall(request).await().use { TextResponse(it.code, it.body.string(), it.header(RATE_LIMIT_REMAINING)) }
        } catch (e: IOException) {
            throw UpdateException(UpdateErrorKind.NETWORK, e.message ?: "Network error", e)
        }
    }

    private class TextResponse(val code: Int, val body: String, val rateLimitRemaining: String?) {
        fun failure(): UpdateException = when {
            code == 429 || (code == 403 && rateLimitRemaining == "0") ->
                UpdateException(UpdateErrorKind.RATE_LIMITED, "GitHub rate limit reached")
            code >= 500 -> UpdateException(UpdateErrorKind.SERVER, "GitHub returned HTTP $code")
            else -> UpdateException(UpdateErrorKind.PROTOCOL, "Unexpected HTTP $code from GitHub")
        }
    }

    private fun <T> decode(serializer: KSerializer<T>, body: String, what: String): T = try {
        UpdateJson.decodeFromString(serializer, body)
    } catch (e: IllegalArgumentException) {
        throw UpdateException(UpdateErrorKind.PROTOCOL, "Unexpected $what format", e)
    }

    private fun invalidUrl() = UpdateException(UpdateErrorKind.PROTOCOL, "Invalid download URL in release")

    private fun mismatch(message: String) = UpdateException(UpdateErrorKind.VERIFICATION, message)

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        val DEFAULT_API_URL: HttpUrl = "https://api.github.com/".toHttpUrl()
        private const val GITHUB_JSON = "application/vnd.github+json"
        private const val OCTET_STREAM = "application/octet-stream"
        private const val API_VERSION = "2022-11-28"
        private const val RATE_LIMIT_REMAINING = "x-ratelimit-remaining"
        private const val BUFFER_SIZE = 64 * 1024
    }
}
