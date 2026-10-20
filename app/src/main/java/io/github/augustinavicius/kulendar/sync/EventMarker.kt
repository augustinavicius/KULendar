package io.github.augustinavicius.kulendar.sync

import java.security.MessageDigest

/**
 * Tags events created by KULendar with the id of the reservation they mirror.
 *
 * The tag is kept in the event description because that field survives the round trip through
 * Google's servers: after a reinstall, a cleared calendar cache or on a new phone, events synced
 * earlier are still recognized, which is what keeps the calendar free of duplicates.
 *
 * Next to the tag, a [Fingerprint] records hashes of the title, description and location KULendar
 * wrote. A field that no longer matches its hash was edited by the user and is left alone by later syncs.
 */
object EventMarker {
    private const val PREFIX = "kulendar-id:"
    private const val FINGERPRINT_PREFIX = "kulendar-sum:"
    private val pattern = Regex("""kulendar-id:([A-Za-z0-9._-]+)""")
    private val fingerprintPattern = Regex("""kulendar-sum:([0-9a-f]{6})\.([0-9a-f]{6})\.([0-9a-f]{6})""")
    private val fingerprintToken = Regex("""(?: · )?kulendar-sum:[0-9a-f.]*""")

    /** SQL `LIKE` pattern that narrows down candidate rows; [extractKey] makes the final decision. */
    const val LIKE_PATTERN = "%$PREFIX%"

    /** Hashes of the field values KULendar wants an event to have. */
    data class Fingerprint(val title: String, val description: String, val location: String) {
        override fun toString() = "$title.$description.$location"
    }

    fun format(key: String): String = PREFIX + key

    fun extractKey(description: String?): String? =
        description?.let { pattern.find(it)?.groupValues?.get(1) }

    fun fingerprint(title: String?, description: String?, location: String?) =
        Fingerprint(hash(title), hash(withoutFingerprint(description)), hash(location))

    fun extractFingerprint(description: String?): Fingerprint? =
        description?.let { fingerprintPattern.find(it) }?.destructured?.let { (title, text, location) ->
            Fingerprint(title, text, location)
        }

    /** Writes [fingerprint] into [description], replacing the one already there or appending it. */
    fun stamp(description: String, fingerprint: Fingerprint): String {
        val token = FINGERPRINT_PREFIX + fingerprint
        val existing = fingerprintPattern.find(description) ?: return "$description · $token"
        return description.replaceRange(existing.range, token)
    }

    fun withoutFingerprint(description: String?): String? = description?.replace(fingerprintToken, "")

    /** Short, stable hash of a field, insensitive to the line ending and whitespace changes of a round trip. */
    fun hash(value: String?): String {
        val text = value.orEmpty().replace("\r\n", "\n").trim()
        return MessageDigest.getInstance("SHA-256").digest(text.toByteArray())
            .take(HASH_BYTES).joinToString("") { "%02x".format(it) }
    }

    private const val HASH_BYTES = 3
}
