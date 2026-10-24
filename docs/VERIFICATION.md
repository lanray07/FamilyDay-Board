# Verification record

Final verified source: `ac6e0340816fae39fe58fb66f04898cc3cec1e10`, 24 October 2026. The subsequent evidence/documentation commit changes no application code.

[Successful GitHub Actions run and downloadable artifacts](https://github.com/lanray07/FamilyDay-Board/actions/runs/40096767094).

| Check | Measured result |
| --- | --- |
| Debug APK assembly | Passed locally and in CI |
| Minified unsigned release assembly | Passed locally and in CI |
| Android lint | Passed; dependency/update and other non-blocking warnings remain |
| JVM tests | 11 passed: recurrence, DST, leap days, month boundaries, overlaps, multi-day events, rotation and PIN hashing |
| Backend tests | 9 passed: isolation, HTTP authentication/conflict responses, idempotency, operation reuse, invitations, revocation, schema and deletion |
| Translation tooling | 3 passed: placeholders, missing keys and newline preservation |
| API 28 device tests | 10 passed |
| API 30 device tests | 10 passed |
| API 35 device tests | 10 passed |

Total: 53 passing automated test executions across JVM, Python and the three device configurations. Device tests cover child read/write restrictions, chore approval boundaries, parent lockout, stale edits/tombstones, atomic restore, read-only kitchen sessions, private list filtering, adult note creation, all-day/expiry editing, Today navigation and genuine screen capture. They use fictional data and reset only this development application's household.

- JDK 17, Android SDK 35 and Gradle 8.13 are available on the build host.
- A Fire HD 10 / KFTRWI / API 28 was detected, then disconnected before instrumentation ran. The user chose to continue without it. No physical Fire compatibility claim is made.
- The local Android emulator cannot boot on this host; hardware acceleration is unavailable and the software fallback exits. Instrumentation and screenshot tests therefore run in GitHub CI rather than locally.
- CI uses an explicit Pixel C hardware profile with requested logical sizes/densities: API 28 at 1024×600/160 dpi, API 30 at 1280×800/213 dpi and API 35 at 1920×1200/240 dpi. Tests wait for actual orientation changes before capturing. These are Android tablet configurations, not Fire OS emulation.
- Measured API 30 captures are 1280×800 landscape and 800×1280 portrait. API 35 captures are 1920×1200 and 1200×1920. API 28's shell capture records the full 2560×1800 / 1800×2560 physical framebuffer with the requested logical viewport letterboxed inside it; those images are test evidence, not store assets.
- Seven unedited API 35 screenshots are saved in `store/screenshots/en-GB`, with a provenance/caption manifest. Landscape dashboard, calendar, chores, lists and kitchen screens and portrait dashboard/child screens were captured. The dashboard, weekly calendar, kitchen and child layouts were visually inspected. Final store graphics and localisation remain pending.

Outstanding manual checks: physical Fire installation, process-death/offline persistence, reminder delivery through sleep/reboot/DST changes, speech engine availability, TalkBack/large fonts/keyboard/RTL, memory and battery use, real HTTPS multi-device sync and Amazon purchase testing. A built APK alone does not verify these behaviours.
