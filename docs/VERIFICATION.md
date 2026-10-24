# Verification record

Local checks are recorded here as they finish. Automated tests are in source and GitHub Actions publishes their measured outcomes and artifacts.

- JDK 17, Android SDK 35 and Gradle 8.13 are available on the build host.
- Initial debug APK assembly and seven initial JVM tests passed before later feature additions.
- Backend: seven tests passed, including under Python `-O`, covering cross-household isolation, stale-version conflicts, idempotent retry, operation reuse rejection, invitation single use, revocation, deletion and private-field rejection.
- Translation tooling: three tests passed for placeholders, missing/unknown keys and newline preservation.
- A Fire HD 10 / KFTRWI / API 28 was detected, then disconnected before instrumentation ran. The user chose to continue without it. No physical Fire compatibility claim is made.
- The local Android emulator cannot boot on this host; hardware acceleration is unavailable and the software fallback exits. Instrumentation and screenshot tests therefore run in GitHub CI rather than locally.
- CI defines API 28 at 1024×600/160 dpi, API 30 at 1280×800/213 dpi and API 35 at 1920×1200/240 dpi, including portrait/landscape screenshot capture. These are representative Android tablet configurations, not Fire OS emulation.

Outstanding manual checks: physical Fire installation, process-death/offline persistence, reminder delivery through sleep/reboot/DST changes, speech engine availability, TalkBack/large fonts/keyboard/RTL, memory and battery use, real HTTPS multi-device sync and Amazon purchase testing. A built APK alone does not verify these behaviours.
