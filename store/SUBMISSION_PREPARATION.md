# Amazon draft preparation — 24 October 2026

The console account was already signed in. No FamilyDay Board listing existed. The new-app form has the following values entered, but has not been saved because Amazon requires a customer-support email and the account has no default support contact. Clicking Save confirmed a “Required field” validation error on the empty support-email field:

- Title: FamilyDay Board: Family Planner
- SKU: com.familyday.board
- Category: Productivity → Calendars (the console also lists Utilities → Calendars)
- Public support email: awaiting the publisher's value
- Optional phone and website: left blank

The user has been asked for the public support email and an existing privacy-policy URL. No placeholder address or unsupported privacy policy has been submitted. No final review submission or publication has occurred.

## Upload-ready assets

`submission_assets/` contains two icons rendered directly from the existing app icon geometry at 114×114 and 512×512, a 1024×500 original promotional composition, and seven genuine app screenshots. Screenshot dimensions remain 1920×1200 or 1200×1920. Lossless compression preserves every screenshot pixel; the manifest records source paths, dimensions, sizes and SHA-256 hashes. The promotional artwork contains the app name and brand icon, without screenshots, prices or trial claims.

`FamilyDay-Board-Amazon-submission-assets.zip` contains the same files, a plain-text reviewer guide, and listing copy. It is a handoff archive; Amazon's individual image fields require the individual PNG files.

The listing copy is plain text, the short description is 122 UTF-8 bytes, and the long description is 1,931 characters. Five feature bullets are prepared. Descriptive headings use sentence case. The current edition is described accurately: no purchases, subscriptions, advertising, trial offer or publisher-hosted sync service is advertised. The optional user-configured HTTPS sync requirement is disclosed.

## Verified specifications

[Amazon: Step 3, Add Appstore Details](https://developer.amazon.com/docs/app-submission/appstore-details.html) specifies 114×114 / 512×512 PNG tablet icons, 3–10 screenshots in supported resolutions including 1920×1200, optional 1024×500 promotional artwork, a 4,000-character long description, a 2,000-byte short-description field, and 3–5 feature bullets. The live creation form identifies a 200-character app-title limit. The icons and promotional artwork were visually reviewed after export; the screenshots' pixel identity was verified programmatically.

After the required email is supplied, save the app draft, inspect its actual upload/detail/privacy fields, upload the prepared files, fill factual metadata and reviewer instructions, and verify the saved previews. Leave binding declarations and final submission for a concrete publisher review where required.
