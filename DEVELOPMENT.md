# Developing Better TAMO

Better TAMO is a native Android client built with Kotlin, Jetpack Compose and Material 3.

## Built on tamo-dienynas-api

The app has no backend of its own. It is a client for TAMO's API, which is documented in **[sobakintech/tamo-dienynas-api](https://github.com/sobakintech/tamo-dienynas-api)**, a reverse-engineered reference for the TAMO IŠMANIEMS app. That reference covers authentication and roles, calendar, diary, grades, homework, messaging, files and push registration. See also [the premium notes](https://github.com/sobakintech/tamo-dienynas-api/blob/main/docs/premium.md).

If something here breaks after a TAMO update, the API reference is the first place to look.

## Building from source

Requirements: JDK 17+, Android SDK Platform 36 and Build Tools 36.0.0. Open the project in Android Studio, or set `sdk.dir` in `local.properties`.

```sh
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The app installs as `lt.bettertamo`, so it runs alongside the official app.

Every build gets its version from the build time in Europe/Vilnius. `versionName` is `yyyy.MM.dd.HHmm` (e.g. `2026.09.26.2200`), and `versionCode` is minutes since the Unix epoch, so each build is newer than the one before it.

## Tests

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

Every push to `main` publishes a release, except pushes that only change Markdown files. You can also run the **Release** workflow manually from the **Actions** tab. [`.github/workflows/release.yml`](.github/workflows/release.yml) runs the unit tests, then builds, signs and publishes the APK to GitHub Releases, tagged `v<versionName>` (e.g. `v2026.09.26.2200`).

The app checks GitHub's latest release when it opens and installs updates itself. Debug builds can't install release APKs because they're signed with a different key.

It needs these repository secrets:

| Secret | Value |
| --- | --- |
| `KEYSTORE_BASE64` | The release keystore, base64-encoded (`base64 -w0 release.jks`) |
| `KEYSTORE_PASSWORD` | Keystore password |
| `KEY_ALIAS` | Key alias |
| `KEY_PASSWORD` | Key password |

To sign a release build locally, set `BETTER_TAMO_KEYSTORE`, `BETTER_TAMO_KEYSTORE_PASSWORD`, `BETTER_TAMO_KEY_ALIAS` and `BETTER_TAMO_KEY_PASSWORD`, then run `./gradlew :app:assembleRelease`. Keep the keystore safe: Android only installs updates signed with the same key.
