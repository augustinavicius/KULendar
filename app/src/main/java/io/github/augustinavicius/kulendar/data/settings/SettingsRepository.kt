package io.github.augustinavicius.kulendar.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Serializable
enum class SyncTrigger { PERIODIC, MANUAL, SETTINGS_CHANGED, BOOT, APP_UPDATED }

@Serializable
enum class SyncStatus(
    /** Worth retrying soon without user involvement. */
    val isTransient: Boolean = false,
    /** Syncing is blocked until the user does something; worth a notification. */
    val needsAttention: Boolean = false,
) {
    SUCCESS,
    NOT_SIGNED_IN,
    NO_CALENDAR,
    INVALID_CREDENTIALS(needsAttention = true),
    CALENDAR_MISSING(needsAttention = true),
    NO_PERMISSION(needsAttention = true),
    PROTOCOL_ERROR(needsAttention = true),
    NETWORK_ERROR(isTransient = true),
    SERVER_ERROR(isTransient = true),
    FAILED(isTransient = true),
}

@Serializable
data class SyncRecord(
    val finishedAt: Long,
    val trigger: SyncTrigger,
    val status: SyncStatus,
    val fetched: Int = 0,
    val inserted: Int = 0,
    val updated: Int = 0,
    val deleted: Int = 0,
    val unchanged: Int = 0,
    /** False when the download looked incomplete, in which case no events were removed. */
    val complete: Boolean = true,
    val message: String? = null,
)

/** The calendar chosen as sync target. Account details guard against a reused calendar id. */
@Serializable
data class SelectedCalendar(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val accountType: String,
)

data class AppSettings(
    val calendar: SelectedCalendar? = null,
    val pastDays: Int = DEFAULT_PAST_DAYS,
    val futureDays: Int = DEFAULT_FUTURE_DAYS,
    val autoSync: Boolean = true,
    val intervalMinutes: Int = DEFAULT_INTERVAL_MINUTES,
    val showAllCalendars: Boolean = false,
    val history: List<SyncRecord> = emptyList(),
    val lastSuccess: SyncRecord? = null,
) {
    val lastRecord: SyncRecord? get() = history.firstOrNull()

    companion object {
        const val DEFAULT_PAST_DAYS = 30
        const val DEFAULT_FUTURE_DAYS = 120
        const val DEFAULT_INTERVAL_MINUTES = 60
        const val MIN_INTERVAL_MINUTES = 15
        const val MAX_RANGE_DAYS = 730
        const val HISTORY_SIZE = 20
    }
}

class SettingsRepository(context: Context) {

    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<AppSettings> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it.toSettings() }
        .distinctUntilChanged()

    suspend fun current(): AppSettings = settings.first()

    suspend fun setCalendar(calendar: SelectedCalendar?) {
        dataStore.edit { prefs ->
            if (calendar == null) prefs.remove(CALENDAR) else prefs[CALENDAR] = encode(SelectedCalendar.serializer(), calendar)
        }
    }

    suspend fun setPastDays(days: Int) {
        dataStore.edit { it[PAST_DAYS] = days.coerceIn(0, AppSettings.MAX_RANGE_DAYS) }
    }

    suspend fun setFutureDays(days: Int) {
        dataStore.edit { it[FUTURE_DAYS] = days.coerceIn(0, AppSettings.MAX_RANGE_DAYS) }
    }

    suspend fun setAutoSync(enabled: Boolean) {
        dataStore.edit { it[AUTO_SYNC] = enabled }
    }

    suspend fun setIntervalMinutes(minutes: Int) {
        dataStore.edit { it[INTERVAL_MINUTES] = minutes.coerceAtLeast(AppSettings.MIN_INTERVAL_MINUTES) }
    }

    suspend fun setShowAllCalendars(show: Boolean) {
        dataStore.edit { it[SHOW_ALL_CALENDARS] = show }
    }

    suspend fun addRecord(record: SyncRecord) {
        dataStore.edit { prefs ->
            val history = (listOf(record) + prefs.history()).take(AppSettings.HISTORY_SIZE)
            prefs[HISTORY] = encode(historySerializer, history)
            if (record.status == SyncStatus.SUCCESS) prefs[LAST_SUCCESS] = encode(SyncRecord.serializer(), record)
        }
    }

    private fun Preferences.toSettings() = AppSettings(
        calendar = decode(SelectedCalendar.serializer(), this[CALENDAR]),
        pastDays = this[PAST_DAYS] ?: AppSettings.DEFAULT_PAST_DAYS,
        futureDays = this[FUTURE_DAYS] ?: AppSettings.DEFAULT_FUTURE_DAYS,
        autoSync = this[AUTO_SYNC] ?: true,
        intervalMinutes = this[INTERVAL_MINUTES] ?: AppSettings.DEFAULT_INTERVAL_MINUTES,
        showAllCalendars = this[SHOW_ALL_CALENDARS] ?: false,
        history = history(),
        lastSuccess = decode(SyncRecord.serializer(), this[LAST_SUCCESS]),
    )

    private fun Preferences.history(): List<SyncRecord> = decode(historySerializer, this[HISTORY]).orEmpty()

    private fun <T> encode(serializer: KSerializer<T>, value: T): String = json.encodeToString(serializer, value)

    private fun <T> decode(serializer: KSerializer<T>, value: String?): T? =
        value?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }

    private companion object {
        val CALENDAR = stringPreferencesKey("calendar")
        val PAST_DAYS = intPreferencesKey("past_days")
        val FUTURE_DAYS = intPreferencesKey("future_days")
        val AUTO_SYNC = booleanPreferencesKey("auto_sync")
        val INTERVAL_MINUTES = intPreferencesKey("interval_minutes")
        val SHOW_ALL_CALENDARS = booleanPreferencesKey("show_all_calendars")
        val HISTORY = stringPreferencesKey("history")
        val LAST_SUCCESS = stringPreferencesKey("last_success")

        val json = Json { ignoreUnknownKeys = true }
        val historySerializer = ListSerializer(SyncRecord.serializer())
    }
}
