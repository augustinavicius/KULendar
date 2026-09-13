package io.github.augustinavicius.kulendar.data.ku

import io.github.augustinavicius.kulendar.data.security.AccountInfo
import io.github.augustinavicius.kulendar.data.security.CredentialsStore
import io.github.augustinavicius.kulendar.data.security.StoredCredentials
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Keeps a valid access token for the university API.
 *
 * Refresh tokens rotate: every refresh invalidates the previous access and refresh token. All token
 * work is therefore serialized and new tokens are persisted before use. When the refresh token is no
 * longer accepted, the stored password is used to log in again, so background syncing keeps working
 * without user interaction.
 */
class KuAuthManager(
    private val api: KuApiClient,
    private val store: CredentialsStore,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val mutex = Mutex()

    suspend fun isSignedIn(): Boolean = store.load() != null

    /** Checks the credentials with the university and stores them if they are accepted. */
    suspend fun signIn(uid: String, password: String): AccountInfo = mutex.withLock {
        val credentials = StoredCredentials(uid = uid, password = password).withTokens(api.login(uid, password))
        store.save(credentials)
        AccountInfo(credentials.uid, credentials.displayName)
    }

    suspend fun signOut() = mutex.withLock { store.clear() }

    /** Runs [block] with an access token, renewing the token and retrying once if the server rejects it. */
    suspend fun <T> withAccessToken(block: suspend (accessToken: String) -> T): T {
        val token = accessToken(rejected = null)
        return try {
            block(token)
        } catch (e: KuUnauthorizedException) {
            block(accessToken(rejected = token))
        }
    }

    private suspend fun accessToken(rejected: String?): String = mutex.withLock {
        val credentials = store.load() ?: throw KuNotSignedInException()
        val cached = credentials.accessToken
        if (cached != null && cached != rejected && clock() < credentials.accessTokenExpiresAt - EXPIRY_MARGIN_MS) {
            return@withLock cached
        }
        val refreshed = credentials.refreshToken?.let { refreshToken ->
            try {
                api.refresh(refreshToken)
            } catch (e: KuUnauthorizedException) {
                null
            }
        }
        val tokens = refreshed ?: api.login(credentials.uid, credentials.password)
        store.save(credentials.withTokens(tokens))
        tokens.accessToken
    }

    private fun StoredCredentials.withTokens(tokens: KuTokens) = copy(
        displayName = tokens.user?.name ?: displayName,
        accessToken = tokens.accessToken,
        accessTokenExpiresAt = clock() + tokens.expiresInSeconds * 1000,
        refreshToken = tokens.refreshToken,
    )

    private companion object {
        const val EXPIRY_MARGIN_MS = 60_000L
    }
}
