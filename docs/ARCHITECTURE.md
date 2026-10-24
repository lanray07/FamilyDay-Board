# Architecture

The initial application is one Gradle module with separate `domain`, `data`, `security`, `reminders`, `sync`, `billing` and `ui` packages. This keeps build cost low while preserving dependency seams for extracting modules later. Koin supplies the database, repositories, services and ViewModel. Compose observes lifecycle-aware StateFlows; Room is the displayed-data source of truth. DataStore holds appearance, reward and quiet-hours preferences.

## Data

Room contains a household, members, typed entries and an outbox. Entries represent events, chores, notes, lists and list items. Assignments reference stable member IDs; list items reference lists. Application transactions validate references, enforce adult/child access, update entity versions and record outbox operations. Deletes retain tombstones. Room's initial schema is checked into `app/schemas`; destructive migration fallback is deliberately absent.

Recurrence retains the original local wall clock time and time zone. Monthly dates clamp to the last day of shorter months; yearly leap-day events use February 28 in non-leap years. A custom interval applies to the chosen daily/weekly/monthly/yearly period. Occurrence exceptions do not shift the anchor. Rotating chores use that same stable occurrence index. Multi-day events are limited to 366 days. A chore completion is tracked separately for each local date.

## Access

Adults share a local parent PIN in this edition. Role labels describe household membership; all non-child roles currently have the same PIN-authorised permissions. Child profiles have no email, account credentials or purchase entry point. Child visibility is enforced in the repository before UI and voice receive entries, with a synchronous UI check on role changes. Children may complete only their own visible chores and cannot approve them.

PIN derivation uses PBKDF2-HMAC-SHA256, a random 32-byte salt and 210,000 iterations. PIN material and rate-limit state are encrypted with AES-GCM using an Android Keystore key. Five failed attempts begin a persistent, increasing cooldown. Adult sessions expire after five minutes and lock when the app goes into the background. Explicit file/speech result flows may retain the short session while the system activity is open; sensitive operations still check authentication on completion. Removing members, changing reward permissions, connecting devices, exporting, restoring and household deletion have fresh PIN gates.

Android backup and device transfer are excluded. Explicit portable backups contain readable JSON and exclude the PIN, device credentials and sessions. App database encryption is provided by the device's app sandbox and storage encryption; the Room database is not independently encrypted. The device owner can uninstall the app or access system settings: child mode is not an operating-system kiosk or device-management boundary.

## Connected mode

No endpoint is configured by default. An adult explicitly chooses a trusted HTTPS server and stores a per-device token in the encrypted vault. WorkManager synchronises approximately every 15 minutes when connectivity is available; adults can also request a foreground sync. No Google push service is used.

The outbox coalesces committed changes into an immutable transport snapshot, retaining the captured operation IDs until acknowledgement. A persisted transport packet is retried with the same UUID after a dropped response. The server checks its household-scoped idempotency ledger before checking the optimistic household version. New local changes occurring during transport remain queued.

This implementation intentionally uses conservative **whole-household conflicts**. It does not attempt field-level merging. If local edits and remote edits both exist, neither is silently discarded. The adult can export a backup, then choose the local or server version. Tombstones and entity versions travel in sync snapshots. Pulls check the local queue inside the same Room transaction before replacing data.

The server scopes every operation to the authenticated device's household, stores only token hashes and offers expiring single-use invitations, device revocation and server deletion. Tokens authorise adult devices, not individual child accounts. App role controls govern local child actions; the server trusts authorised adult devices. The operator must deploy TLS, encrypted storage, backups, request limits and monitoring. This is not end-to-end encryption.

## Platform services

Local notifications show a generic reminder rather than private family details. Quiet hours follow the household time zone. WorkManager handles persistence and rescheduling across process death/reboot, but is inexact. Speech uses the optional Android recognition intent and TTS engine; engine availability and online processing vary on Fire devices. The app neither continuously records nor stores raw audio. External calendar access is requested only following adult consent; users preview/select individual events before import.

The native Amazon SDK adapter accepts only verifier-issued entitlements; SDK callbacks alone do not unlock anything. It is not wired into the development UI. A live commercial entitlement service and downgrade policy must be completed and tested before enabling paid restrictions.
