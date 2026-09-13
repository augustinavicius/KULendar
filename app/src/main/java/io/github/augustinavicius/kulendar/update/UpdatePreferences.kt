package io.github.augustinavicius.kulendar.update

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable

private val Context.updateDataStore: DataStore<Preferences> by preferencesDataStore(name = "updates")

/** Outcome of the most recent update check. */
@Serializable
data class UpdateCheckRecord(
    val checkedAt: Long,
    val latestVersionCode: Long? = null,
    val latestVersionName: String? = null,
    val error: UpdateErrorKind? = null,
)

data class UpdateSettings(
    val channel: UpdateChannel,
    val autoInstall: Boolean,
    val lastCheck: UpdateCheckRecord?,
    /** The newest version the user was already notified about, so each version is announced once. */
    val notifiedVersionCode: Long?,
    /** The version whose installation was started, to confirm the update after the app restarts. */
    val installingVersionCode: Long?,
)

class UpdatePreferences(context: Context, private val defaultChannel: UpdateChannel) {

    private val dataStore = context.applicationContext.updateDataStore

    val settings: Flow<UpdateSettings> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            UpdateSettings(
                channel = UpdateChannel.fromKey(prefs[CHANNEL]) ?: defaultChannel,
                autoInstall = prefs[AUTO_INSTALL] ?: true,
                lastCheck = prefs[LAST_CHECK]?.let { runCatching { UpdateJson.decodeFromString(UpdateCheckRecord.serializer(), it) }.getOrNull() },
                notifiedVersionCode = prefs[NOTIFIED_VERSION_CODE],
                installingVersionCode = prefs[INSTALLING_VERSION_CODE],
            )
        }
        .distinctUntilChanged()

    suspend fun current(): UpdateSettings = settings.first()

    suspend fun setChannel(channel: UpdateChannel) {
        dataStore.edit { it[CHANNEL] = channel.key }
    }

    /**
     * Stores the channel of the installed build the first time the app runs, so that an update built for the
     * other channel, such as a newer stable release received on the development channel, does not change it.
     */
    suspend fun rememberChannel() {
        dataStore.edit { prefs -> if (prefs[CHANNEL] == null) prefs[CHANNEL] = defaultChannel.key }
    }

    suspend fun setAutoInstall(enabled: Boolean) {
        dataStore.edit { it[AUTO_INSTALL] = enabled }
    }

    suspend fun recordCheck(record: UpdateCheckRecord) {
        dataStore.edit { it[LAST_CHECK] = UpdateJson.encodeToString(UpdateCheckRecord.serializer(), record) }
    }

    suspend fun setNotifiedVersionCode(versionCode: Long) {
        dataStore.edit { it[NOTIFIED_VERSION_CODE] = versionCode }
    }

    suspend fun setInstallingVersionCode(versionCode: Long?) {
        dataStore.edit { prefs ->
            if (versionCode == null) prefs.remove(INSTALLING_VERSION_CODE) else prefs[INSTALLING_VERSION_CODE] = versionCode
        }
    }

    private companion object {
        val CHANNEL = stringPreferencesKey("channel")
        val AUTO_INSTALL = booleanPreferencesKey("auto_install")
        val LAST_CHECK = stringPreferencesKey("last_check")
        val NOTIFIED_VERSION_CODE = longPreferencesKey("notified_version_code")
        val INSTALLING_VERSION_CODE = longPreferencesKey("installing_version_code")
    }
}
