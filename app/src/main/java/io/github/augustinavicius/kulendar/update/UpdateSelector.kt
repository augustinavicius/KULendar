package io.github.augustinavicius.kulendar.update

/** A published release together with its manifest. */
data class ReleaseCandidate(val release: GitHubRelease, val manifest: UpdateManifest)

/** Decides which release, if any, the installed app should update to. */
object UpdateSelector {

    /** The newest published release of one kind that carries an update manifest. */
    fun newest(releases: List<GitHubRelease>, prerelease: Boolean): GitHubRelease? =
        releases
            .filter { !it.draft && it.prerelease == prerelease && it.publishedAt != null && it.manifestAsset != null }
            .maxByOrNull { it.publishedAt.orEmpty() }

    /**
     * The best update among [candidates]: the highest version that is newer than [installedVersionCode],
     * built for this app, installable on [sdkInt], and whose APK is attached to the release.
     * Updates never go backwards, so switching from development to stable waits for a newer stable release.
     */
    fun choose(
        candidates: List<ReleaseCandidate>,
        installedVersionCode: Long,
        applicationId: String,
        sdkInt: Int,
    ): AvailableUpdate? =
        candidates
            .filter { it.manifest.applicationId == applicationId && it.manifest.minSdk <= sdkInt }
            .filter { it.manifest.versionCode > installedVersionCode }
            .sortedByDescending { it.manifest.versionCode }
            .firstNotNullOfOrNull { candidate ->
                val apk = candidate.release.assets.firstOrNull { it.name == candidate.manifest.apk.name } ?: return@firstNotNullOfOrNull null
                AvailableUpdate(
                    versionCode = candidate.manifest.versionCode,
                    versionName = candidate.manifest.versionName,
                    channel = candidate.manifest.channel,
                    releaseUrl = candidate.release.htmlUrl,
                    apkUrl = apk.downloadUrl,
                    apkSize = candidate.manifest.apk.size,
                    sha256 = candidate.manifest.apk.sha256,
                )
            }
}
