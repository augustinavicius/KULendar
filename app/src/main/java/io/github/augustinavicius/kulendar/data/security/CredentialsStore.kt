package io.github.augustinavicius.kulendar.data.security

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
data class StoredCredentials(
    val uid: String,
    val password: String,
    val displayName: String? = null,
    val accessToken: String? = null,
    val accessTokenExpiresAt: Long = 0,
    val refreshToken: String? = null,
) {
    override fun toString() = "StoredCredentials(uid=$uid, displayName=$displayName, secrets=<redacted>)"
}

data class AccountInfo(val uid: String, val displayName: String?)

/** Persists the university account. Implementations must keep the contents confidential. */
interface CredentialsStore {
    val account: Flow<AccountInfo?>

    suspend fun load(): StoredCredentials?

    suspend fun save(credentials: StoredCredentials)

    suspend fun clear()
}
