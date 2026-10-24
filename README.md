# FamilyDay Board

A native Kotlin / Jetpack Compose household organiser for Amazon Fire tablets and Android tablets. The app uses Room locally and has no Google Play Services dependency.

## Run the app

Open the repository in Android Studio with JDK 17 and Android SDK 35 installed. Alternatively, set `ANDROID_HOME` and run:

```sh
./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```

On Windows use `gradlew.bat`. Minimum Android API: 26. Eleven automated checks passed on a physical Fire HD 10 / KFTRWI / API 28. Fire OS 8 / API 30 is also an intended target; other physical models and extended device behaviour still require verification. Fire OS 5 tablets are not supported.

Start with an empty household, choose a time zone and parent PIN, then add household members in Family. No email or cloud account is required. The parent PIN has no remote recovery: keep it safe and use explicit backups.

## Available in this build

- Offline calendar with day, week, month and 30-day agenda views; all-day and recurring events, custom recurrence intervals, assignments, overlap warnings and occurrence skips.
- Household notes with categories, pins, expiry, completion/archive, author and child visibility.
- Multiple lists with categories, checkboxes, editing, ordering, duplication, text sharing and shopping mode.
- Recurring chores, rotating assignments, per-date completion, parent approval, optional stars and weekly progress.
- Six local household profiles, PIN-gated adult administration, child spaces and privacy filtering in the repository.
- Responsive navigation, light/dark themes and a large kitchen board with optional screen wake lock.
- Read-only kitchen presentation can remain open without granting adult privileges; a parent PIN is required to leave it.
- Optional device speech input with editable drafts, text-to-speech and manual fallbacks.
- WorkManager reminders with quiet hours and private notification text. Delivery is approximate and affected by device battery restrictions.
- Adult-controlled device calendar import, JSON backup/restore and local deletion.
- Optional HTTPS sync, durable outbox, idempotent writes, version conflicts, single-use invitations and device revocation. Requires deploying the included server.

This is a **development edition, not an Amazon Appstore release**. Purchases and paid feature restrictions are disabled. A native Amazon SDK adapter is included, but live billing requires marketplace configuration, a public authentication key, server receipt verification and App Tester / Live App Testing. Do not advertise a trial or accept payments with this build.

## Verification

```sh
./gradlew assembleDebug assembleRelease lintDebug testDebugUnitTest assembleDebugAndroidTest
python -m unittest discover -s backend -v
python -m unittest discover -s tools -v
./gradlew connectedDebugAndroidTest
```

The final command needs a connected Android device or working emulator. Instrumentation tests use fictional data and reset this development app's household, so use a test installation. They do not touch other applications. GitHub Actions runs API 28, 30 and 35 tablet configurations and uploads reports, screenshots and APKs. See [verification notes](docs/VERIFICATION.md) for measured results and remaining device work.

Release APKs are intentionally unsigned. Configure private signing credentials outside the repository before distribution.

## Configuration and release

- [Architecture and privacy boundaries](docs/ARCHITECTURE.md)
- [Deploying connected households](backend/README.md)
- [Feature and release status](docs/RELEASE_STATUS.md)
- [Translation workflow](localization/README.md)
- [Draft store metadata](store/en-GB.json)
- [Genuine app screenshots and draft caption copy](store/screenshots/manifest.json)

See [the master build prompt](docs/MASTER_BUILD_PROMPT.md) for the supplied requirements.

The supplied prompt ends partway through section 17, Screenshot 4, at `**Headline`. It is preserved as received; the remaining specification will need to be supplied before that section can be completed.
