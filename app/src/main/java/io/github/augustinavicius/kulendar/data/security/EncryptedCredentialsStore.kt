package io.github.augustinavicius.kulendar.data.security

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.augustinavicius.kulendar.data.ku.KuJson
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

private val Context.credentialsDataStore: DataStore<Preferences> by preferencesDataStore(name = "credentials")

/** Stores the account as a single Keystore-encrypted blob. */
class EncryptedCredentialsStore(
    context: Context,
    private val cipher: KeystoreCipher = KeystoreCipher(),
) : CredentialsStore {

    private val dataStore = context.applicationContext.credentialsDataStore

    private val blobs: Flow<String?> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[BLOB] }

    override val account: Flow<AccountInfo?> = blobs
        .map { blob -> decode(blob)?.let { AccountInfo(it.uid, it.displayName) } }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    override suspend fun load(): StoredCredentials? = withContext(Dispatchers.IO) { decode(blobs.first()) }

    override suspend fun save(credentials: StoredCredentials) {
        val blob = withContext(Dispatchers.IO) {
            val plaintext = KuJson.encodeToString(StoredCredentials.serializer(), credentials).encodeToByteArray()
            Base64.encodeToString(cipher.encrypt(plaintext), Base64.NO_WRAP)
        }
        dataStore.edit { it[BLOB] = blob }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(BLOB) }
    }

    // A blob that can no longer be decrypted, e.g. after the Keystore was reset, counts as signed out.
    private fun decode(blob: String?): StoredCredentials? = blob?.let {
        runCatching {
            val plaintext = cipher.decrypt(Base64.decode(it, Base64.NO_WRAP))
            KuJson.decodeFromString(StoredCredentials.serializer(), plaintext.decodeToString())
        }.getOrNull()
    }

    private companion object {
        val BLOB = stringPreferencesKey("blob")
    }
}
