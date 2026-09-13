package io.github.augustinavicius.kulendar.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Where updates come from. Stable follows releases of the master branch; development follows pre-releases
 * of the development branch and also takes a stable release whenever that is newer.
 */
enum class UpdateChannel(val key: String) {
    STABLE("stable"),
    DEVELOPMENT("development");

    companion object {
        fun fromKey(key: String?): UpdateChannel? = entries.firstOrNull { it.key == key }
    }
}

/** Name of the release asset that describes the APK of a release. */
const val MANIFEST_ASSET_NAME = "kulendar-update.json"

internal val UpdateJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String,
    val name: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("html_url") val htmlUrl: String? = null,
    /** ISO-8601 in UTC, so it sorts chronologically as text. Null for drafts. */
    @SerialName("published_at") val publishedAt: String? = null,
    val assets: List<GitHubAsset> = emptyList(),
) {
    val manifestAsset: GitHubAsset? get() = assets.firstOrNull { it.name == MANIFEST_ASSET_NAME }
}

@Serializable
data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val downloadUrl: String,
    val size: Long = 0,
)

/** Contents of [MANIFEST_ASSET_NAME], written by `.github/scripts/prepare-release.sh`. */
@Serializable
data class UpdateManifest(
    val schema: Int = 1,
    val applicationId: String,
    val versionCode: Long,
    val versionName: String,
    val channel: String,
    val commit: String? = null,
    val minSdk: Int = 1,
    val apk: Apk,
) {
    @Serializable
    data class Apk(val name: String, val size: Long, val sha256: String)
}

/** A release newer than the installed app that can be installed on this device. */
data class AvailableUpdate(
    val versionCode: Long,
    val versionName: String,
    val channel: String,
    val releaseUrl: String?,
    val apkUrl: String,
    val apkSize: Long,
    val sha256: String,
)

enum class UpdateErrorKind { NETWORK, RATE_LIMITED, SERVER, PROTOCOL, VERIFICATION, SIGNATURE_MISMATCH, INSTALL_NOT_ALLOWED, INSTALL }

class UpdateException(val kind: UpdateErrorKind, message: String, cause: Throwable? = null) : Exception(message, cause)
