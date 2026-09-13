package io.github.augustinavicius.kulendar.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.File
import java.security.MessageDigest

/** Verifies downloaded updates and hands them to Android's package installer. */
class ApkInstaller(context: Context) {

    private val context = context.applicationContext

    val canInstallPackages: Boolean
        get() = context.packageManager.canRequestPackageInstalls()

    /** Checks that [apk] is [expectedVersionCode] of this app, newer than the installed copy, and signed with the same key. */
    fun verify(apk: File, expectedVersionCode: Long) {
        val archive = packageInfo { flags -> context.packageManager.getPackageArchiveInfo(apk.path, flags) }
            ?: throw UpdateException(UpdateErrorKind.VERIFICATION, "The download is not a valid app package")
        val installed = packageInfo { flags -> context.packageManager.getPackageInfo(context.packageName, flags) }
            ?: throw UpdateException(UpdateErrorKind.VERIFICATION, "Could not read the installed app")
        val archiveVersion = PackageInfoCompat.getLongVersionCode(archive)
        when {
            archive.packageName != context.packageName ->
                throw UpdateException(UpdateErrorKind.VERIFICATION, "The download is a different app")
            archiveVersion != expectedVersionCode ->
                throw UpdateException(UpdateErrorKind.VERIFICATION, "The download has an unexpected version")
            archiveVersion <= PackageInfoCompat.getLongVersionCode(installed) ->
                throw UpdateException(UpdateErrorKind.VERIFICATION, "The download is not newer than the installed app")
        }
        val archiveSigners = signerDigests(archive)
        if (archiveSigners.isEmpty() || archiveSigners != signerDigests(installed)) {
            throw UpdateException(UpdateErrorKind.SIGNATURE_MISMATCH, "The update is signed with a different key than the installed app")
        }
    }

    /** Starts installing [apk]. The outcome is delivered to [UpdateInstallReceiver]. */
    fun install(apk: File, versionName: String) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // After KULendar has installed one update itself, Android lets later updates complete without a prompt.
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                session.openWrite("base.apk", 0, apk.length()).use { output ->
                    apk.inputStream().use { it.copyTo(output) }
                    session.fsync(output)
                }
                val statusIntent = Intent(context, UpdateInstallReceiver::class.java)
                    .setAction(UpdateInstallReceiver.ACTION_INSTALL_STATUS)
                    .putExtra(UpdateInstallReceiver.EXTRA_VERSION_NAME, versionName)
                // Mutable so that the installer can add the status extras.
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
                session.commit(PendingIntent.getBroadcast(context, sessionId, statusIntent, flags).intentSender)
            }
        } catch (e: Exception) {
            installer.abandonSession(sessionId)
            throw UpdateException(UpdateErrorKind.INSTALL, e.message ?: "Could not start the installation", e)
        }
    }

    private fun packageInfo(read: (Int) -> PackageInfo?): PackageInfo? = runCatching {
        @Suppress("DEPRECATION")
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        read(flags)
    }.getOrNull()

    private fun signerDigests(info: PackageInfo): Set<String> {
        val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.apkContentsSigners
        } else {
            @Suppress("DEPRECATION")
            info.signatures
        }
        return signatures.orEmpty().mapTo(mutableSetOf()) { signature ->
            MessageDigest.getInstance("SHA-256").digest(signature.toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }
}
