# Release status

This is an initial development implementation, not a claim that the entire master prompt is commercially complete. The supplied prompt is preserved verbatim and ends partway through Screenshot 4 in section 17.

| Area | Implementation | Remaining release work |
| --- | --- | --- |
| Native Android | Kotlin, Compose Material 3, MVVM, Room, Koin, DataStore, WorkManager; 11 checks passed on a physical KFTRWI / API 28 | Other physical Fire models/OS versions, extended battery/reminder testing, profiling and accessibility QA |
| Onboarding | Three introduction panels, household, owner, PIN, zone and week start | In-app language selection after reviewed translations; profile avatar choices |
| Today/kitchen | Responsive cards, quick actions, privacy filtering and optional awake display | Across-room readability and lower-memory device testing |
| Calendar | Four views, assignments, recurrence/custom intervals, exceptions, all-day/multi-day plans, overlap warnings | Dedicated birthday/anniversary templates; complex recurrence rules; per-occurrence editing beyond skipping |
| Messages | Categories, pin, expiry, archive, author, completion, child visibility | Optional individual acknowledgements |
| Lists | Multiple lists, categories, edit, completion, reorder, duplication, sharing, shopping mode | Meal-plan-specific structure and advanced one-handed QA |
| Chores | Recurrence, rotation, completion dates, approval, optional stars, weekly progress | Custom reward redemption and milestone management |
| Family/child | Six profiles, adult role labels, simplified child space, service permissions | Independent adult identities, editable member roles and detailed per-child permission settings |
| Voice | Runtime optional recognition, manual fallback, draft review, TTS; typed fallback verified on a Fire HD 10 without a recognizer | Actual TTS audio, other Fire speech engines and multilingual natural-language date parsing; no offline voice claim |
| Sync | Opt-in HTTPS client, durable queue, immutable retries, backend, conflicts, invitations, revocation and deletion | Deploy and test HTTPS end-to-end; field-level merges and account recovery |
| Notifications | Private local reminders, quiet hours and permission request | Fire battery/reboot/time-zone device QA; remote push and daily summaries |
| Premium | Native Amazon SDK 3.0.9 adapter with verifier boundary | SKUs, app public key, RVS backend, purchase/restore UX, verified caching, cancellation/grace/expiry, free/premium limits and downgrade tests; disabled meanwhile |
| Localisation | Externalised strings/plurals, locale dates, draft/validation/review workflow | Human-reviewed translations in all priority languages, store translation and layout QA |
| Store | Accurate draft English metadata, screenshot capture tests | Review current console limits, final branding/screenshots, privacy policy, content rating, signing, Live App Testing and submission |

The current portable restore limit is 5 MB of JSON; sync responses are limited to 5.5 MB. Large-history archival and multi-part export have not been implemented. Preserve local data and check backup size before relying on a portable restore. Conflict resolution applies to a whole household snapshot, rather than a field-level merge.

All six local member slots and multiple lists are currently usable without payment. This is deliberate development behaviour and must not be sold as a finished subscription product. No premium expiry can delete data because this build has no commercial entitlement enforcement. Essential privacy/export/deletion controls are available to authenticated adults.

## Official references checked on 24 October 2026

- [Fire tablet identification and API levels](https://developer.amazon.com/docs/device-specs/ft-identify-tablet-devices.html): the connected KFTRWI model corresponds to the 2021 Fire HD 10 / API 28. This identification is not a compatibility test.
- [Amazon Appstore SDK integration](https://developer.amazon.com/docs/appstore-sdk/integrate-appstore-sdk.html): native Maven artifact and per-app public-key setup.
- [Amazon SDK release notes](https://developer.amazon.com/docs/appstore-sdk/release-notes.html): version 3.0.9.
- [IAP implementation](https://developer.amazon.com/docs/in-app-purchasing/iap-implement-iap.html): marketplace product data, purchase listener and receipt flow.
- [Submission and architecture guidance](https://developer.amazon.com/docs/app-submission/understanding-submission.html): device/ABI and binary considerations.

The exact current title, description and tablet screenshot limits were not established from an accessible authoritative submission form during this build. Store metadata retains explicit unverified-limit flags. Verify marketplace eligibility, pricing, trial availability and current submission requirements in the developer console before publication. No Amazon account configuration or store submission was performed.
