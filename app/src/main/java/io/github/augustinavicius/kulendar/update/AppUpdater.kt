package io.github.augustinavicius.kulendar.update

import android.content.Context
import android.os.Build
import android.util.Log
import io.github.augustinavicius.kulendar.system.AppVisibility
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data class Available(val update: AvailableUpdate) : UpdateState
    data class Downloading(val update: AvailableUpdate, val progress: Float) : UpdateState
    data class Installing(val update: AvailableUpdate) : UpdateState
    data class Failed(val kind: UpdateErrorKind, val update: AvailableUpdate?) : UpdateState
}

/** Finds, downloads and installs new KULendar releases from the selected update channel. */
class AppUpdater(
    context: Context,
    private val source: GitHubReleaseSource,
    private val preferences: UpdatePreferences,
    private val installer: ApkInstaller,
    private val scope: CoroutineScope,
    private val installedVersionCode: Long,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val context = context.applicationContext
    private val mutex = Mutex()
    private val downloads = File(this.context.cacheDir, "updates")

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Looks for a release newer than the installed app and returns it, if any. */
    suspend fun check(): AvailableUpdate? = mutex.withLock {
        _state.value = UpdateState.Checking
        try {
            val candidates = candidates(preferences.current().channel)
            val newest = candidates.maxByOrNull { it.manifest.versionCode }?.manifest
            val update = UpdateSelector.choose(candidates, installedVersionCode, context.packageName, Build.VERSION.SDK_INT)
            preferences.recordCheck(UpdateCheckRecord(clock(), newest?.versionCode, newest?.versionName))
            _state.value = update?.let { UpdateState.Available(it) } ?: UpdateState.Idle
            update
        } catch (e: CancellationException) {
            _state.value = UpdateState.Idle
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Update check failed", e)
            val kind = (e as? UpdateException)?.kind ?: UpdateErrorKind.PROTOCOL
            preferences.recordCheck(UpdateCheckRecord(clock(), error = kind))
            _state.value = UpdateState.Failed(kind, null)
            null
        }
    }

    /** Downloads [update], verifies it and starts the installation. */
    suspend fun downloadAndInstall(update: AvailableUpdate): Unit = mutex.withLock {
        if (!installer.canInstallPackages) {
            _state.value = UpdateState.Failed(UpdateErrorKind.INSTALL_NOT_ALLOWED, update)
            return@withLock
        }
        val apk = File(downloads, "KULendar-${update.versionCode}.apk")
        try {
            if (!apk.isFile) {
                _state.value = UpdateState.Downloading(update, 0f)
                source.download(update.apkUrl, apk, update.apkSize, update.sha256) { progress ->
                    _state.value = UpdateState.Downloading(update, progress)
                }
            }
            _state.value = UpdateState.Installing(update)
            withContext(Dispatchers.IO) { installer.verify(apk, update.versionCode) }
            preferences.setInstallingVersionCode(update.versionCode)
            withContext(Dispatchers.IO) { installer.install(apk, update.versionName) }
        } catch (e: CancellationException) {
            _state.value = UpdateState.Available(update)
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Installing ${update.versionName} failed", e)
            apk.delete()
            preferences.setInstallingVersionCode(null)
            _state.value = UpdateState.Failed((e as? UpdateException)?.kind ?: UpdateErrorKind.INSTALL, update)
        }
    }

    /**
     * The periodic background check. Installs the update right away when the user allows it and is not using
     * the app; otherwise announces each new version once.
     */
    suspend fun runScheduledCheck() {
        val update = check() ?: return
        val settings = preferences.current()
        if (settings.autoInstall && installer.canInstallPackages && !AppVisibility.isVisible) {
            downloadAndInstall(update)
        } else if (settings.notifiedVersionCode != update.versionCode) {
            UpdateNotifications.showAvailable(context, update.versionName)
            preferences.setNotifiedVersionCode(update.versionCode)
        }
    }

    /** Called by [UpdateInstallReceiver] when the system installer did not install the update. */
    fun onInstallFailed(cancelledByUser: Boolean, message: String?) {
        Log.w(TAG, "Installation did not complete: $message")
        val update = when (val current = _state.value) {
            is UpdateState.Installing -> current.update
            is UpdateState.Downloading -> current.update
            is UpdateState.Available -> current.update
            is UpdateState.Failed -> current.update
            else -> null
        }
        _state.value = when {
            update == null -> UpdateState.Idle
            cancelledByUser -> UpdateState.Available(update)
            else -> UpdateState.Failed(UpdateErrorKind.INSTALL, update)
        }
        scope.launch { preferences.setInstallingVersionCode(null) }
    }

    /** Called once after the app was replaced by a new version. */
    suspend fun onAppUpdated(versionName: String) {
        withContext(Dispatchers.IO) { downloads.deleteRecursively() }
        val installing = preferences.current().installingVersionCode ?: return
        preferences.setInstallingVersionCode(null)
        if (installing == installedVersionCode) UpdateNotifications.showInstalled(context, versionName)
    }

    private suspend fun candidates(channel: UpdateChannel): List<ReleaseCandidate> {
        val releases = when (channel) {
            UpdateChannel.STABLE -> listOfNotNull(source.latestStable())
            UpdateChannel.DEVELOPMENT -> {
                val recent = source.recentReleases()
                listOfNotNull(
                    UpdateSelector.newest(recent, prerelease = true),
                    UpdateSelector.newest(recent, prerelease = false) ?: source.latestStable(),
                )
            }
        }
        return releases.mapNotNull { release -> release.manifestAsset?.let { ReleaseCandidate(release, source.manifest(it)) } }
    }

    private companion object {
        const val TAG = "AppUpdater"
    }
}
