package io.github.augustinavicius.kulendar.sync

/**
 * Tags events created by KULendar with the id of the reservation they mirror.
 *
 * The tag is kept in the event description because that field survives the round trip through
 * Google's servers: after a reinstall, a cleared calendar cache or on a new phone, events synced
 * earlier are still recognized, which is what keeps the calendar free of duplicates.
 */
object EventMarker {
    private const val PREFIX = "kulendar-id:"
    private val pattern = Regex("""kulendar-id:([A-Za-z0-9._-]+)""")

    /** SQL `LIKE` pattern that narrows down candidate rows; [extractKey] makes the final decision. */
    const val LIKE_PATTERN = "%$PREFIX%"

    fun format(key: String): String = PREFIX + key

    fun extractKey(description: String?): String? =
        description?.let { pattern.find(it)?.groupValues?.get(1) }
}
