package io.github.augustinavicius.kulendar.data.ku

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal object KuResponseParser {

    fun parseTokens(body: String): KuTokens {
        val data = parseObject(body)["data"] as? JsonObject
            ?: throw KuProtocolException("Token response has no data")
        return decode("token response") { KuJson.decodeFromJsonElement(KuTokens.serializer(), data) }
    }

    /**
     * Parses one page of reservations. Parsing is strict about the envelope: a page whose items
     * cannot be located is an error rather than an empty page, because an empty page would make the
     * sync remove every event from the calendar.
     */
    fun parseReservationPage(body: String): KuReservationPage {
        val (items, meta) = when (val data = parseObject(body)["data"]) {
            // Earlier API versions returned the page as a bare list without pagination metadata.
            is JsonArray -> Pair(data, null)
            is JsonObject -> Pair(
                data["items"] as? JsonArray ?: throw KuProtocolException("Reservation page has no items"),
                data["meta"] as? JsonObject,
            )
            else -> throw KuProtocolException("Reservation response has no data")
        }
        val reservations = decode("reservation") {
            items.map { KuJson.decodeFromJsonElement(KuReservation.serializer(), it) }
        }
        val pageMeta = meta?.let { runCatching { KuJson.decodeFromJsonElement(KuPageMeta.serializer(), it) }.getOrNull() }
        return KuReservationPage(reservations, pageMeta)
    }

    /** The human-readable `message` of an API error response, if there is one. */
    fun parseMessage(body: String): String? = runCatching {
        ((KuJson.parseToJsonElement(body) as? JsonObject)?.get("message") as? JsonPrimitive)?.contentOrNull
    }.getOrNull()?.takeIf { it.isNotBlank() }

    private fun parseObject(body: String): JsonObject =
        runCatching { KuJson.parseToJsonElement(body) }.getOrNull() as? JsonObject
            ?: throw KuProtocolException("Response is not a JSON object")

    // kotlinx.serialization reports malformed input with subclasses of IllegalArgumentException.
    private inline fun <T> decode(what: String, block: () -> T): T = try {
        block()
    } catch (e: IllegalArgumentException) {
        throw KuProtocolException("Unexpected $what format", e)
    }
}
