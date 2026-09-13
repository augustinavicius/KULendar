package io.github.augustinavicius.kulendar.ui

import android.accounts.AccountManager
import android.app.Notification
import android.content.Context
import android.content.Intent
import io.github.augustinavicius.kulendar.data.calendar.DeviceCalendar

private const val GOOGLE_CALENDAR_PACKAGE = "com.google.android.calendar"

/**
 * Android's account chooser, limited to the calendar's account. Picking the account lets KULendar see it, and so
 * read its real sync settings, from then on.
 */
fun accountChooserIntent(calendar: DeviceCalendar, description: String): Intent = AccountManager.newChooseAccountIntent(
    calendar.account,
    listOf(calendar.account),
    arrayOf(calendar.accountType),
    description,
    null,
    null,
    null,
)

/**
 * Google Calendar's settings screen, which lists "Share Google Calendar data with other apps" under General. This is
 * the app's public entry to its settings, the one Android's notification settings open.
 */
fun googleCalendarSettingsIntent(): Intent = Intent(Intent.ACTION_MAIN)
    .addCategory(Notification.INTENT_CATEGORY_NOTIFICATION_PREFERENCES)
    .setClassName(GOOGLE_CALENDAR_PACKAGE, "com.google.android.calendar.timely.settings.CalendarPublicPreferenceActivity")

fun googleCalendarLaunchIntent(context: Context): Intent? =
    context.packageManager.getLaunchIntentForPackage(GOOGLE_CALENDAR_PACKAGE)
