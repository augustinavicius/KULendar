package io.github.augustinavicius.kulendar.data.ku

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Lenient JSON settings, so fields added to the university API later do not break syncing. */
val KuJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
    isLenient = true
}

@Serializable
data class KuTokens(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("expires_in") val expiresInSeconds: Long = 3600,
    val user: KuUser? = null,
)

@Serializable
data class KuUser(
    val id: Long? = null,
    val name: String? = null,
    val email: String? = null,
)

/** One occurrence of a timetable entry. Recurring lectures are expanded into one reservation per date. */
@Serializable
data class KuReservation(
    val id: Long,
    val name: String,
    val description: String? = null,
    /** Local Europe/Vilnius wall-clock time, e.g. `2026-12-17 12:00:00`. */
    val from: String,
    val to: String,
    val duration: Int? = null,
    val type: String? = null,
    val approved: Boolean = true,
    @SerialName("is_remote") val isRemote: Boolean = false,
    @SerialName("remote_address") val remoteAddress: String? = null,
    val user: KuUser? = null,
    /** The lecturer for lectures, otherwise the person responsible for the reservation. */
    val responsible: KuUser? = null,
    val reservable: KuReservable? = null,
)

/** Where a reservation takes place: a room (`item`, with its building in [resource]) or a whole building (`resource`). */
@Serializable
data class KuReservable(
    val type: String? = null,
    val id: Long? = null,
    val name: String? = null,
    val description: String? = null,
    val address: String? = null,
    val resource: KuResource? = null,
)

@Serializable
data class KuResource(
    val id: Long? = null,
    val name: String? = null,
    val address: String? = null,
)

@Serializable
data class KuPageMeta(
    @SerialName("current_page") val currentPage: Int? = null,
    @SerialName("last_page") val lastPage: Int? = null,
    @SerialName("per_page") val perPage: Int? = null,
    val total: Int? = null,
)

data class KuReservationPage(
    val items: List<KuReservation>,
    val meta: KuPageMeta?,
)
