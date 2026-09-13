package io.github.augustinavicius.kulendar

import android.content.Context
import android.os.Build
import io.github.augustinavicius.kulendar.data.calendar.CalendarRepository
import io.github.augustinavicius.kulendar.data.ku.KuApiClient
import io.github.augustinavicius.kulendar.data.ku.KuAuthManager
import io.github.augustinavicius.kulendar.data.security.CredentialsStore
import io.github.augustinavicius.kulendar.data.security.EncryptedCredentialsStore
import io.github.augustinavicius.kulendar.data.settings.SettingsRepository
import io.github.augustinavicius.kulendar.sync.SyncEngine
import io.github.augustinavicius.kulendar.update.ApkInstaller
import io.github.augustinavicius.kulendar.update.AppUpdater
import io.github.augustinavicius.kulendar.update.GitHubReleaseSource
import io.github.augustinavicius.kulendar.update.UpdateChannel
import io.github.augustinavicius.kulendar.update.UpdatePreferences
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient

/** Creates and holds the app's long-lived objects. */
class AppContainer(context: Context) {

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(context)
    val credentials: CredentialsStore = EncryptedCredentialsStore(context)
    val calendars = CalendarRepository(context)

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
        }
        .build()

    val api = KuApiClient(httpClient)
    val auth = KuAuthManager(api, credentials)
    val syncEngine = SyncEngine(context.applicationContext, settings, auth, api, calendars)

    // A development build follows the development channel until the user picks another one.
    val updatePreferences = UpdatePreferences(context, UpdateChannel.fromKey(BuildConfig.UPDATE_CHANNEL) ?: UpdateChannel.STABLE)
    val apkInstaller = ApkInstaller(context)
    val updater = AppUpdater(
        context = context,
        source = GitHubReleaseSource(httpClient, BuildConfig.UPDATE_REPOSITORY),
        preferences = updatePreferences,
        installer = apkInstaller,
        scope = applicationScope,
        installedVersionCode = BuildConfig.VERSION_CODE.toLong(),
    )

    private companion object {
        val USER_AGENT = "KULendar/${BuildConfig.VERSION_NAME} (Android ${Build.VERSION.RELEASE})"
    }
}
