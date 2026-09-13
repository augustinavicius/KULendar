# KULendar
Klaipėdos Universiteto kalendoriaus sinchronizavimo sistema skirta Android įrenginiams.

KULendar keeps your Klaipėda University timetable from [tvarkarasciai.ku.lt](https://tvarkarasciai.ku.lt) in a
Google Calendar of your choice. It runs in the background, survives reboots and Doze, and never creates duplicates.

## Features

- Signs in with your tvarkarasciai.ku.lt username and password.
- Syncs into any Google calendar you can edit. Creating a separate calendar, e.g. "University", at calendar.google.com keeps lectures apart.
- Configurable range: how far back (none to 2 years) and how far ahead (up to 2 years) to sync.
- Automatic background sync every 15 minutes to once a day, plus "Sync now".
- Lecture title, time (Europe/Vilnius), room and address, lecturer, and the Teams link for online lectures.
- Rescheduled lectures are moved, cancelled lectures removed, and your own events never touched.
- A checklist that fixes everything Android uses to stop background work: battery optimization, background restriction and "pause app activity if unused".
- A notification when syncing needs your attention, e.g. after a password change.
- Updates itself from GitHub releases, on the Stable or Development channel.
- English and Lithuanian UI.

## Setting up the phone

1. Make sure your Google account is added to the phone and **Calendar sync is on** for it
   (Settings → Passwords & accounts → your Google account → Account sync).
2. Download the APK of the [latest release](https://github.com/augustinavicius/KULendar/releases/latest) and install it.
3. Sign in with your university account.
4. Allow calendar access and choose the Google calendar to sync into.
5. Pick the sync range and frequency.
6. Under **Uninterrupted sync**, press every button that is shown. Allowing the app to ignore battery
   optimization is what lets it sync on time while the phone sleeps.
7. Under **App updates**, allow KULendar to install updates.

Xiaomi, Huawei, Samsung and some other phones have extra battery managers; the app links to the
[dontkillmyapp.com](https://dontkillmyapp.com) instructions for your phone.

## Updates and release channels

| Channel | Gets |
| --- | --- |
| **Stable** | Releases built from the `master` branch (tags like `v1.0.42`). |
| **Development** | Pre-releases built from the `development` branch (tags like `v1.0.43-dev`), plus stable releases whenever they are newer. These builds may be unfinished. |

Switch channels under **App updates**; a new install starts on the channel of the release you installed and stays
there until you switch. The app checks every 6 hours and when it is opened. With **Install updates
automatically** on, new versions are downloaded and installed while you are not using the app; otherwise you get a
notification. Android may ask you to confirm an installation, at least the first time.

Before installing, the app verifies that the download matches the size and SHA-256 published with the release, that
it is KULendar, that it is newer than the installed version, and that it is signed with the same key. Updates never go
backwards: after switching from Development to Stable you stay on your build until a newer stable release is out.

Only builds from GitHub releases can update each other. A build you made yourself is signed with a different key, so
the app asks you to uninstall it and install the latest release once.

## How it works

```
tvarkarasciai.ku.lt ──(mobile API)──▶ KULendar ──▶ Android calendar provider ──(Google sync adapter)──▶ Google Calendar
```

- **University API.** KULendar uses the site's mobile API (`/api/mobile/auth/login`, `/auth/refresh`,
  `/api/mobile/reservations`). Access tokens last one hour and every refresh rotates the refresh token, so token
  handling is serialized and new tokens are stored before use. If the refresh token is rejected, the app logs in
  again with the stored password, so background syncing never waits for you.
- **Google Calendar.** Events are written through Android's calendar provider into the calendar you picked. The
  phone's Google account uploads them to Google Calendar, so no Google Cloud project or extra sign-in is needed.
- **No duplicates.** Every event carries `kulendar-id:<reservation id>` at the end of its description. This survives
  the round trip through Google, a reinstall, or a new phone. Each sync reconciles the whole calendar:
  - events are matched by reservation id regardless of time, so a moved lecture is updated in place;
  - any extra copy of a reservation is deleted;
  - an event is removed only if its lecture disappeared **and** it lies inside the sync range. Anything
    outside the range is kept as history;
  - if the download looks incomplete (fewer lectures than the server announced), nothing is removed.
- **Background sync.** WorkManager runs the sync periodically whenever there is a network connection, retries
  failures with backoff, and keeps the schedule across reboots and app updates. With the battery optimization
  exemption, the job also runs while the phone is in Doze.
- **Updates.** The app reads the public GitHub Releases API. Each release carries the APK and `kulendar-update.json`
  with its version, minimum Android version, size and SHA-256. Installation goes through Android's package installer.
- **TLS.** tvarkarasciai.ku.lt does not send its intermediate certificate, which Android would otherwise reject.
  The GÉANT intermediates issued by HARICA are bundled as extra trust anchors for this one host
  (`app/src/main/res/xml/network_security_config.xml`).

## Privacy and security

- The password and tokens are encrypted with an AES-256-GCM key that is generated inside the Android Keystore and
  never leaves the device.
- App data is excluded from cloud backups and device transfers.
- The app talks only to tvarkarasciai.ku.lt and, for updates, to GitHub. Other students listed in reservations are
  never copied to your calendar.

## Releases

[`.github/workflows/release.yml`](.github/workflows/release.yml) builds, tests and publishes on every push:

- to `master`: a release `v<baseVersion>.<run>`, marked as the latest release;
- to `development`: a pre-release `v<baseVersion>.<run>-dev`.

Pushing a new branch that points at an already pushed commit starts no run, because GitHub reports it only as a
branch creation. Push a new commit, or start the workflow by hand under **Actions → Release → Run workflow**.

`<run>` is the workflow's run number, shared by both branches, and the version code is `100 + <run>`, so every new
build can update every older one. `kulendar.baseVersion` in `gradle.properties` sets the first part of the version.
Each release contains the signed APK, `kulendar-update.json` and notes listing the commits since the previous release
of the same channel.

### Signing key (one-time setup)

All releases must be signed with the same key, otherwise installed copies cannot update. The workflow refuses to
publish without it. It reads the key from four repository secrets: `KULENDAR_KEYSTORE_BASE64`,
`KULENDAR_KEYSTORE_PASSWORD`, `KULENDAR_KEY_ALIAS` and `KULENDAR_KEY_PASSWORD`.

1. Create a key, unless you already have one:

   ```sh
   keytool -genkeypair -keystore ~/.android/kulendar-release.jks -storetype PKCS12 \
     -alias kulendar -keyalg RSA -keysize 4096 -validity 10950 -dname "CN=KULendar"
   ```

2. Describe it in a properties file, e.g. `~/.android/kulendar-release.properties`:

   ```properties
   storeFile=/home/you/.android/kulendar-release.jks
   storePassword=...
   keyAlias=kulendar
   keyPassword=...
   ```

3. Store it as repository secrets (needs the [GitHub CLI](https://cli.github.com), logged in with `gh auth login`):

   ```sh
   bash .github/scripts/set-release-secrets.sh ~/.android/kulendar-release.properties
   ```

**Back up the keystore and its passwords.** If they are lost, installed copies can no longer be updated and
everyone has to reinstall the app.

## Building

Requirements: JDK 17 or newer and the Android SDK with platform 37 (Android Studio installs both).

```sh
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleRelease     # app/build/outputs/apk/release/app-release.apk
```

Local builds are version `1.0.0-local`. Release builds are signed with the key from `keystore.properties` next to
`settings.gradle.kts` (same format as above, ignored by git) or from the `KULENDAR_*` environment variables used in CI;
without either they are signed with your debug key.

Install the APK with `adb install app/build/outputs/apk/release/app-release.apk`, or copy it to the phone.

## Project layout

| Package | Contents |
| --- | --- |
| `data.ku` | University API client, response parsing, token management |
| `data.calendar` | Calendar provider access |
| `data.security` | Keystore encryption and credential storage |
| `data.settings` | Settings and sync history (DataStore) |
| `sync` | Event mapping, reconciliation planner, sync engine, WorkManager worker and scheduling |
| `update` | GitHub release lookup, verified downloads, installation and update checks |
| `net` | Shared OkHttp helpers |
| `system` | Battery optimization, other background restrictions, app visibility |
| `ui` | Jetpack Compose screens |

## Troubleshooting

- **Events are on the phone but not in Google Calendar on the web.** Calendar sync is off for the Google
  account. The app warns about this under Google Calendar.
- **"The timetable website returned unexpected data".** The university changed its API. Nothing is removed from
  your calendar in that case; the app needs an update.
- **Sync is late.** Open the app and fix everything marked under **Uninterrupted sync**.
- **"This copy of KULendar wasn't installed from a GitHub release".** The installed build was signed with a different
  key. Uninstall it and install the latest release once; it updates itself from then on.
