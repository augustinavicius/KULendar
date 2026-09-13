package io.github.augustinavicius.kulendar.system

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.IntentCompat
import androidx.core.content.PackageManagerCompat
import androidx.core.content.UnusedAppRestrictionsConstants
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class HibernationState { OFF, ON, UNAVAILABLE }

/** The system settings that decide whether background syncs run on time. */
data class BackgroundHealth(
    val ignoringBatteryOptimizations: Boolean = true,
    val backgroundRestricted: Boolean = false,
    val hibernation: HibernationState = HibernationState.UNAVAILABLE,
    val notificationsEnabled: Boolean = true,
    val manufacturer: String = Build.MANUFACTURER.orEmpty(),
) {
    /** Manufacturers known to kill background work beyond what stock Android does. */
    val hasAggressiveManufacturer: Boolean
        get() = manufacturer.lowercase() in AGGRESSIVE_MANUFACTURERS

    private companion object {
        val AGGRESSIVE_MANUFACTURERS = setOf(
            "xiaomi", "redmi", "poco", "huawei", "honor", "samsung", "oneplus", "oppo", "realme",
            "vivo", "meizu", "asus", "lenovo", "tecno", "infinix", "nothing", "motorola", "sony",
        )
    }
}

object BackgroundHealthChecker {

    suspend fun check(context: Context): BackgroundHealth = withContext(Dispatchers.IO) {
        val powerManager = context.getSystemService(PowerManager::class.java)
        val activityManager = context.getSystemService(ActivityManager::class.java)
        BackgroundHealth(
            ignoringBatteryOptimizations = powerManager.isIgnoringBatteryOptimizations(context.packageName),
            backgroundRestricted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && activityManager.isBackgroundRestricted,
            hibernation = hibernationState(context),
            notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled(),
        )
    }

    private fun hibernationState(context: Context): HibernationState = runCatching {
        when (PackageManagerCompat.getUnusedAppRestrictionsStatus(context).get()) {
            UnusedAppRestrictionsConstants.DISABLED -> HibernationState.OFF
            UnusedAppRestrictionsConstants.API_30_BACKPORT,
            UnusedAppRestrictionsConstants.API_30,
            UnusedAppRestrictionsConstants.API_31 -> HibernationState.ON
            else -> HibernationState.UNAVAILABLE
        }
    }.getOrDefault(HibernationState.UNAVAILABLE)

    /** Shows the system dialog that exempts the app from battery optimization. */
    @SuppressLint("BatteryLife") // Uninterrupted background syncing is this app's purpose.
    fun batteryOptimizationIntent(context: Context) =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, "package:${context.packageName}".toUri())

    fun appDetailsIntent(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())

    /** Must be launched for a result, as required by [IntentCompat.createManageUnusedAppRestrictionsIntent]. */
    fun hibernationIntent(context: Context): Intent =
        IntentCompat.createManageUnusedAppRestrictionsIntent(context, context.packageName)

    fun notificationSettingsIntent(context: Context) =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun syncSettingsIntent() = Intent(Settings.ACTION_SYNC_SETTINGS)

    fun dontKillMyAppIntent(manufacturer: String): Intent =
        Intent(Intent.ACTION_VIEW, "https://dontkillmyapp.com/${manufacturer.lowercase()}".toUri())
}
