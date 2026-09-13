package io.github.augustinavicius.kulendar.update

import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GitHubReleaseSourceTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var source: GitHubReleaseSource

    @Before
    fun setUp() {
        server.start()
        source = GitHubReleaseSource(OkHttpClient(), "augustinavicius/KULendar", server.url("/"))
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun respond(code: Int, body: String = "", headers: Map<String, String> = emptyMap()) {
        val builder = MockResponse.Builder().code(code).body(body)
        headers.forEach { (name, value) -> builder.addHeader(name, value) }
        server.enqueue(builder.build())
    }

    private fun respondBytes(bytes: ByteArray) {
        server.enqueue(MockResponse.Builder().code(200).body(Buffer().write(bytes)).build())
    }

    private fun releaseJson(tag: String, prerelease: Boolean) = """
        {"tag_name":"$tag","name":"KULendar $tag","draft":false,"prerelease":$prerelease,
         "html_url":"https://github.com/augustinavicius/KULendar/releases/tag/$tag","published_at":"2026-09-12T15:57:39Z",
         "author":{"login":"github-actions[bot]"},
         "assets":[{"name":"kulendar-update.json","browser_download_url":"https://github.com/augustinavicius/KULendar/releases/download/$tag/kulendar-update.json","size":312}]}
    """.trimIndent()

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test
    fun `latest stable release comes from the latest release endpoint`() = runTest {
        respond(200, releaseJson("v1.0.51", prerelease = false))

        val release = source.latestStable()

        val request = server.takeRequest()
        assertEquals("/repos/augustinavicius/KULendar/releases/latest", request.url.encodedPath)
        assertEquals("application/vnd.github+json", request.headers["Accept"])
        assertEquals("2022-11-28", request.headers["X-GitHub-Api-Version"])
        assertEquals("v1.0.51", release?.tagName)
        assertEquals(MANIFEST_ASSET_NAME, release?.manifestAsset?.name)
    }

    @Test
    fun `a repository without stable releases is not an error`() = runTest {
        respond(404, """{"message":"Not Found"}""")

        assertNull(source.latestStable())
    }

    @Test
    fun `recent releases include both channels`() = runTest {
        respond(200, "[${releaseJson("v1.0.52-dev", prerelease = true)},${releaseJson("v1.0.51", prerelease = false)}]")

        val releases = source.recentReleases()

        assertEquals(listOf(true, false), releases.map { it.prerelease })
        assertEquals("30", server.takeRequest().url.queryParameter("per_page"))
    }

    @Test
    fun `rate limiting is reported as such`() = runTest {
        respond(403, """{"message":"API rate limit exceeded"}""", mapOf("x-ratelimit-remaining" to "0"))
        respond(429)

        val forbidden = runCatching { source.recentReleases() }.exceptionOrNull()
        val tooManyRequests = runCatching { source.latestStable() }.exceptionOrNull()

        assertEquals(UpdateErrorKind.RATE_LIMITED, (forbidden as UpdateException).kind)
        assertEquals(UpdateErrorKind.RATE_LIMITED, (tooManyRequests as UpdateException).kind)
    }

    @Test
    fun `the manifest is read from the release asset`() = runTest {
        respond(
            200,
            """{"schema":1,"applicationId":"io.github.augustinavicius.kulendar","versionCode":151,"versionName":"1.0.51",""" +
                """"channel":"stable","commit":"abc","minSdk":26,"apk":{"name":"KULendar-1.0.51.apk","size":1921840,"sha256":"00ff"},"later":1}""",
        )

        val manifest = source.manifest(GitHubAsset(MANIFEST_ASSET_NAME, server.url("/download/kulendar-update.json").toString(), 312))

        assertEquals(151L, manifest.versionCode)
        assertEquals("KULendar-1.0.51.apk", manifest.apk.name)
        assertEquals("/download/kulendar-update.json", server.takeRequest().url.encodedPath)
    }

    @Test
    fun `a matching download is stored`() = runTest {
        val bytes = ByteArray(200_000) { (it % 251).toByte() }
        respondBytes(bytes)
        val target = File(folder.root, "updates/KULendar-151.apk")
        val progress = mutableListOf<Float>()

        source.download(server.url("/KULendar.apk").toString(), target, bytes.size.toLong(), sha256(bytes)) { progress += it }

        assertArrayEquals(bytes, target.readBytes())
        assertEquals(1f, progress.last(), 0f)
        assertFalse(File(target.path + ".part").exists())
    }

    @Test
    fun `corrupted and incomplete downloads are rejected and discarded`() = runTest {
        val bytes = ByteArray(10_000) { 7 }
        respondBytes(bytes)
        respondBytes(bytes)
        val target = File(folder.root, "KULendar-151.apk")
        val url = server.url("/KULendar.apk").toString()

        val corrupted = runCatching { source.download(url, target, bytes.size.toLong(), "00".repeat(32)) {} }.exceptionOrNull()
        val incomplete = runCatching { source.download(url, target, bytes.size + 1L, sha256(bytes)) {} }.exceptionOrNull()

        assertEquals(UpdateErrorKind.VERIFICATION, (corrupted as UpdateException).kind)
        assertEquals(UpdateErrorKind.VERIFICATION, (incomplete as UpdateException).kind)
        assertFalse(target.exists())
        assertFalse(File(target.path + ".part").exists())
    }
}
