package io.github.augustinavicius.kulendar.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateSelectorTest {

    private val appId = "io.github.augustinavicius.kulendar"

    private fun release(
        tag: String,
        prerelease: Boolean = false,
        publishedAt: String? = "2026-09-12T10:00:00Z",
        draft: Boolean = false,
        withManifest: Boolean = true,
        withApk: Boolean = true,
    ) = GitHubRelease(
        tagName = tag,
        draft = draft,
        prerelease = prerelease,
        htmlUrl = "https://github.com/augustinavicius/KULendar/releases/tag/$tag",
        publishedAt = publishedAt,
        assets = listOfNotNull(
            GitHubAsset("KULendar-$tag.apk", "https://github.com/download/$tag/KULendar-$tag.apk", 2_000_000).takeIf { withApk },
            GitHubAsset(MANIFEST_ASSET_NAME, "https://github.com/download/$tag/$MANIFEST_ASSET_NAME", 300).takeIf { withManifest },
        ),
    )

    private fun candidate(release: GitHubRelease, versionCode: Long, minSdk: Int = 26, applicationId: String = appId) =
        ReleaseCandidate(
            release,
            UpdateManifest(
                applicationId = applicationId,
                versionCode = versionCode,
                versionName = release.tagName.removePrefix("v"),
                channel = if (release.prerelease) "development" else "stable",
                minSdk = minSdk,
                apk = UpdateManifest.Apk(name = "KULendar-${release.tagName}.apk", size = 2_000_000, sha256 = "abc123"),
            ),
        )

    @Test
    fun `newest returns the most recently published release of each kind`() {
        val releases = listOf(
            release("v1.0.5", publishedAt = "2026-09-01T10:00:00Z"),
            release("v1.0.7-dev", prerelease = true, publishedAt = "2026-09-03T10:00:00Z"),
            release("v1.0.6", publishedAt = "2026-09-02T10:00:00Z"),
            release("v1.0.4-dev", prerelease = true, publishedAt = "2026-08-30T10:00:00Z"),
        )

        assertEquals("v1.0.6", UpdateSelector.newest(releases, prerelease = false)?.tagName)
        assertEquals("v1.0.7-dev", UpdateSelector.newest(releases, prerelease = true)?.tagName)
    }

    @Test
    fun `drafts and releases without an update manifest are ignored`() {
        val releases = listOf(
            release("v1.0.9", draft = true, publishedAt = null),
            release("v1.0.8", withManifest = false, publishedAt = "2026-09-05T10:00:00Z"),
            release("v1.0.3", publishedAt = "2026-08-01T10:00:00Z"),
        )

        assertEquals("v1.0.3", UpdateSelector.newest(releases, prerelease = false)?.tagName)
        assertNull(UpdateSelector.newest(releases, prerelease = true))
    }

    @Test
    fun `the highest newer version wins, whichever channel built it`() {
        val candidates = listOf(
            candidate(release("v1.0.50-dev", prerelease = true), versionCode = 150),
            candidate(release("v1.0.51"), versionCode = 151),
        )

        val update = UpdateSelector.choose(candidates, installedVersionCode = 140, applicationId = appId, sdkInt = 37)

        assertEquals(151L, update?.versionCode)
        assertEquals("https://github.com/download/v1.0.51/KULendar-v1.0.51.apk", update?.apkUrl)
        assertEquals("abc123", update?.sha256)
        assertEquals("stable", update?.channel)
    }

    @Test
    fun `nothing is offered when the installed build is as new or newer`() {
        val candidates = listOf(candidate(release("v1.0.51"), versionCode = 151))

        assertNull(UpdateSelector.choose(candidates, installedVersionCode = 151, applicationId = appId, sdkInt = 37))
        assertNull(UpdateSelector.choose(candidates, installedVersionCode = 160, applicationId = appId, sdkInt = 37))
    }

    @Test
    fun `builds of another app or for a newer Android version are skipped`() {
        val candidates = listOf(
            candidate(release("v1.0.60"), versionCode = 160, applicationId = "com.example.other"),
            candidate(release("v1.0.59"), versionCode = 159, minSdk = 99),
            candidate(release("v1.0.58"), versionCode = 158),
        )

        assertEquals(158L, UpdateSelector.choose(candidates, installedVersionCode = 100, applicationId = appId, sdkInt = 37)?.versionCode)
    }

    @Test
    fun `a release whose APK is missing falls back to the next best one`() {
        val candidates = listOf(
            candidate(release("v1.0.61", withApk = false), versionCode = 161),
            candidate(release("v1.0.60"), versionCode = 160),
        )

        assertEquals(160L, UpdateSelector.choose(candidates, installedVersionCode = 100, applicationId = appId, sdkInt = 37)?.versionCode)
    }
}
