# Better TAMO

A faster, cleaner, native Android client for the [TAMO](https://www.tamo.lt) school diary, built with Kotlin, Jetpack Compose and Material 3.

Better TAMO covers the school-diary parts of the official app: timetable, homework, grades, messages and events. It adds offline access, your own lesson names, custom events and a consistent Material design.

> [!WARNING]
> Better TAMO is an **unofficial** app. It is not affiliated with, endorsed by, or supported by TAMO or its operator.
>
> It talks to TAMO's private API, which is documented in [tamo-dienynas-api](https://github.com/sobakintech/tamo-dienynas-api). TAMO's [Terms of Use](https://content.tamo.lt/taisykles/naudojimo.html) prohibit unofficial programmatic access. **Use it at your own risk.** Your account could be restricted or lose access.

## Download

Get the latest APK from [**Releases**](https://github.com/sobakintech/better-tamo/releases/latest) and install it on Android 8.0 or newer. Better TAMO installs as `lt.bettertamo`, so it runs alongside the official app.

You'll probably need an active **TAMO IŠMANIEMS** subscription. The official app gates diary, grade, homework and calendar access behind it, and Better TAMO has only been tested with premium enabled. For details, see [the premium notes](https://github.com/sobakintech/tamo-dienynas-api/blob/main/docs/premium.md).

## Features

### Tvarkaraštis (timetable)
- A weekly timetable with real dates and times, a seven-day strip and a month calendar that expands from the header.
- Lesson cards show the lesson type (tests are highlighted), that lesson's own grade and attendance mark, cumulative (kaupiamieji) grades, praise/remark icons, and homework chips (filled = due today, outlined = assigned today).
- Free periods appear as gaps you can tap to add your own lesson there.
- Tap a lesson for a detail sheet with its topic, marks, remarks and homework. From there, open the full subject view.
- Weekends are red, and pull-to-refresh reloads the week.

### Namų darbai (homework)
- Upcoming and past assignments grouped by day, with relative-day labels (Šiandien, Rytoj, Po 3 d.…).
- Tap a card to expand the full assignment text and teacher details.
- Filter by status, with counts. Tick off tasks you've done (the checkmarks stay on your device).

### Įvykiai (events)
- The latest grades, homework, praise and remarks in one feed, with filters.
- Browse the full monthly praise/remark history.

### Pranešimai (messages)
- A native TAMO messages client with received, starred, sent, group and deleted folders, plus search and paging.
- Star messages, mark them unread, read HTML bodies and download attachments.
- Unread badge on the tab.
- Writing and replying open TAMO's own composer inside the app.

### Pažymiai (grades)
- **Dienynas**: grades, cumulative grades and attendance by month.
- **Dalykai**: your overall average, then per-subject averages for the selected period, recent marks and missed lessons.
- **Subject pages** gather everything about one subject: period average and final grade, upcoming tests, unused cumulative grades, every grade this school year, the subject's weekly slots, full lesson history with topics and homework, remarks and attendance.

### Daugiau (more)
Open it with the profile button on any tab.
- Upcoming tests and holidays, lesson history and a weekly schedule.
- TAMO's extra pages, such as Analitika, shown in-app.
- **Mano pamokos**: rename recurring lessons (by weekday and slot, or by subject and teacher) and add your own weekly events.
- Role switching for accounts with several roles (e.g. parents with multiple children).
- Notifications, light/dark/system theme, and sign-out.

### Everywhere
- **Works offline.** The last loaded data is cached encrypted on the device, so the app opens instantly and stays readable without a connection.
- **Notifications (optional).** Better TAMO registers with TAMO's own push service, so new grades, homework and messages arrive the same way they do in the official app. Without Google Play services, it checks for new items every 15 minutes instead.
- Tablet- and landscape-friendly, with a navigation rail on wide screens.

## Privacy

- Your password is never stored. After sign-in, only the session is kept, encrypted with the Android Keystore.
- Cached school data is also Keystore-encrypted and excluded from backups and device transfers.
- There are no analytics or trackers. The app only talks to TAMO, plus Google's Firebase Cloud Messaging if you turn on notifications.
- Homework checkmarks, lesson renames and custom events stay on your device.
- The app only changes things in your TAMO account when you do something that changes them in the official app too: opening a message marks it as read, starring or marking a message unread, sending through TAMO's composer, and registering or removing this device for notifications.
- Signing out deletes the session, the cache, the push registration and in-app web cookies.

## Built on tamo-dienynas-api

The app has no backend of its own. It is a client for TAMO's API, which is documented in **[sobakintech/tamo-dienynas-api](https://github.com/sobakintech/tamo-dienynas-api)**, a reverse-engineered reference for the TAMO IŠMANIEMS app. That reference covers authentication and roles, calendar, diary, grades, homework, messaging, files and push registration.

If something here breaks after a TAMO update, the API reference is the first place to look.

## Building from source

Requirements: JDK 17+, Android SDK Platform 36 and Build Tools 36.0.0. Open the project in Android Studio, or set `sdk.dir` in `local.properties`.

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Every build gets its version from the build time in Europe/Vilnius. `versionName` is `yyyy.MM.dd.HHmm` (e.g. `2026.09.26.2200`), and `versionCode` is minutes since the Unix epoch, so each build is newer than the one before it.

### Tests

```sh
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Unit tests use fictional fixtures only. Instrumented UI tests (`app/src/androidTest`) run in an isolated Compose activity with synthetic data and never touch a live account:

```sh
./gradlew :app:assembleDebugAndroidTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -r lt.bettertamo.test/androidx.test.runner.AndroidJUnitRunner
```

Prefer `am instrument` over `connectedDebugAndroidTest` on a device you actually use, because the latter uninstalls the app and wipes its data.

## Releases

To publish a release, run the **Release** workflow from the **Actions** tab. [`.github/workflows/release.yml`](.github/workflows/release.yml) runs the unit tests, then builds, signs and publishes the APK to GitHub Releases, tagged `v<versionName>` (e.g. `v2026.09.26.2200`).

It needs these repository secrets:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | The release keystore, base64-encoded (`base64 -w0 release.jks`) |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

To sign a release build locally, set `BETTER_TAMO_KEYSTORE`, `BETTER_TAMO_KEYSTORE_PASSWORD`, `BETTER_TAMO_KEY_ALIAS` and `BETTER_TAMO_KEY_PASSWORD`, then run `./gradlew :app:assembleRelease`. Keep the keystore safe: Android only installs updates signed with the same key.

## Disclaimer

TAMO is a trademark of its respective owner. This project is an independent, unofficial client provided as-is, without warranty. It isn't meant to bypass subscriptions or access controls. You need your own TAMO account and whatever access your school and subscription provide.
