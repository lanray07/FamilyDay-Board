# CODEX MASTER BUILD PROMPT
# FamilyDay Board
## Premium Family Calendar, Chores & Household Organizer for Amazon Fire Tablets

### YOUR ROLE

Act as an expert team consisting of:

- Senior Android and Kotlin engineer
- Amazon Fire OS compatibility specialist
- Senior UX/UI designer
- Offline-first systems architect
- Backend and database engineer
- Child privacy and parental-control specialist
- Voice interaction engineer
- International localisation specialist
- Amazon Appstore SEO and conversion strategist
- Subscription and in-app purchasing engineer
- Accessibility and quality assurance engineer

Your mission is to **build, test, optimise, and prepare FamilyDay Board for commercial release on the Amazon Appstore**.

Deliver a real, functional, polished application—not a mockup, incomplete prototype, or demonstration-only interface.

Inspect the existing repository before making changes. Reuse suitable code if a project already exists. Otherwise, create a clean production-quality Android project.

Make sensible implementation decisions independently, document them, and proceed without unnecessary clarification.

Never claim a feature is complete unless it is implemented and verified.

---

# 1. APP IDENTITY AND POSITIONING

**Official app name:** FamilyDay Board

**SEO-optimised store title candidate:** FamilyDay Board: Family Planner

**Subtitle:** Family Calendar, Chores & Lists

**Tagline:** One shared space for everything your family needs.

**Primary platform:** Amazon Fire tablets

**Secondary platform:** Compatible Android tablets

**Package name candidate:** `com.familyday.board`

**Category:** Productivity

**Core positioning:**

FamilyDay Board transforms a shared Amazon Fire tablet into a private household organisation centre.

Families can manage schedules, shopping lists, reminders, chores, routines, and short household messages without juggling multiple applications.

The app should be especially useful when a Fire tablet is placed in a kitchen, living room, or shared family area.

### Primary users

- Parents coordinating family schedules
- Families with school-age children
- Couples managing shared responsibilities
- Flatmates sharing household tasks
- Carers coordinating household routines
- Multigenerational households
- Families who prefer a shared tablet over multiple phone apps

### Competitive differentiation

Prioritise:

1. Amazon Fire tablet-first design.
2. Fully functional offline household planning.
3. A premium kitchen dashboard.
4. Child-friendly profiles without child email accounts.
5. Simple voice-assisted actions.
6. Household calendar, lists, chores, and messages in one interface.
7. Private, parent-controlled household data.
8. Easy setup for non-technical users.
9. Multilingual support.
10. A clean, advertisement-free experience.

Do not imitate competitors' copyrighted assets or interfaces.

---

# 2. TECHNICAL ARCHITECTURE

Build a native Android application using:

- Kotlin
- Jetpack Compose
- Material 3 with a custom FamilyDay design system
- MVVM and clean architecture principles
- Room for local persistence
- Kotlin Coroutines and StateFlow
- WorkManager for supported background synchronisation
- DataStore for preferences
- Android Keystore-backed security where appropriate
- Hilt or Koin for dependency injection
- Gradle Kotlin DSL
- JUnit and appropriate Android UI testing tools

Use a modular, maintainable architecture.

Suggested modules:

- app
- core-ui
- core-database
- core-security
- core-sync
- core-localization
- core-billing
- feature-onboarding
- feature-dashboard
- feature-calendar
- feature-messages
- feature-lists
- feature-chores
- feature-family
- feature-childmode
- feature-voice
- feature-settings

Avoid unnecessary architectural complexity.

### Amazon Fire OS requirements

- Do not require Google Play Services.
- Do not use Google Play Billing.
- Do not assume Firebase Cloud Messaging is available.
- Use Amazon Appstore-compatible billing.
- Verify Fire OS and Android API compatibility.
- Test on supported 7-, 8-, and 10-inch tablet configurations.
- Support portrait and landscape.
- Account for limited memory and lower-performance devices.
- Minimise background battery consumption.
- Handle offline operation and network interruptions gracefully.

Use official Amazon documentation to verify current SDKs, APIs, purchasing requirements, and submission specifications.

Where current documentation cannot be accessed, clearly identify the requirement as unverified rather than inventing compatibility claims.

---

# 3. PREMIUM VISUAL DESIGN

Create a beautiful, welcoming, highly usable household application.

### Brand palette

- Primary: Warm teal `#277C78`
- Background: Soft cream `#F8F7F2`
- Accent: Muted orange `#E9A66B`
- Text: Deep charcoal `#263238`
- Success: Soft green `#4F916A`

Support a properly designed dark theme.

### Design principles

- Large, readable typography
- Spacious layouts
- Rounded cards
- Clear iconography
- Gentle shadows
- Restrained animation
- Accessible contrast
- Generous touch targets
- Consistent colour-coded family identities
- Premium empty states
- Clear offline and synchronisation indicators

Avoid a generic corporate dashboard appearance.

The interface should feel warm, calm, and relatable to real families.

### Tablet layouts

On larger screens, use a navigation rail or sidebar and multi-column dashboard.

On smaller screens, use responsive cards and appropriate bottom navigation.

Support font scaling, screen readers, reduced motion, keyboard navigation where available, and right-to-left layouts.

---

# 4. FAMILY ONBOARDING

Build a welcoming onboarding flow.

### Step 1: Welcome

Explain the value of FamilyDay Board using three simple screens:

- Plan together
- Share household tasks
- Stay organised, even offline

### Step 2: Create household

Collect:

- Household name
- Optional household icon
- Preferred language
- Time zone
- Week-start preference

### Step 3: Add members

Allow up to six members with Premium.

Each member has:

- First name or nickname
- Avatar
- Colour identity
- Role
- Optional notification preferences

Roles:

- Household Owner
- Parent/Administrator
- Carer
- Adult Member
- Child

Children must not need email addresses.

### Step 4: Set parental controls

Allow authorised adults to configure:

- Parent PIN
- Child calendar visibility
- Child message visibility
- Chore permissions
- Reward permissions
- Purchase restrictions

### Step 5: Choose household mode

Offer:

**Single Tablet Mode:** Fully usable offline, without requiring cloud registration.

**Connected Household Mode:** Secure synchronisation between authorised devices using a configured backend.

Explain the difference clearly.

---

# 5. TODAY DASHBOARD

Make the Today screen the centre of the application.

Display:

- Current date
- Personalised household greeting
- Today's events
- Upcoming reminders
- Outstanding chores
- Pinned messages
- Shopping-list summary
- One important household priority

Include quick actions:

- Add Event
- Add Message
- Add Shopping Item
- Add Chore

Allow authorised members to mark tasks complete directly.

### Premium Kitchen Board Mode

Build a full-screen, glanceable dashboard designed for a shared Fire tablet.

Features:

- Large calendar summary
- Today's family schedule
- Household reminders
- Chore progress
- Shopping essentials
- High-priority notices
- Optional always-on display behaviour
- Privacy-aware content visibility

Make Kitchen Board Mode usable from several feet away.

Provide an easy way to return to normal navigation.

---

# 6. SHARED FAMILY CALENDAR

Implement:

- Day view
- Week view
- Month view
- Agenda view
- Recurring events
- All-day events
- Birthdays
- Anniversaries
- School activities
- Medical appointments
- Family outings
- Household appointments

Allow events to be assigned to one or more household members.

Use member colours consistently.

Include:

- Event reminders
- Editing and deletion
- Recurrence exceptions
- Conflict detection
- Time-zone handling
- Daylight-saving support
- Optional location notes
- Event categories

Warn users when assigned events overlap.

Do not prevent legitimate overlapping events.

### External calendars

Design optional external calendar integration behind explicit adult consent.

Use only supported APIs and integrations.

Never upload external calendar data without authorisation.

Clearly distinguish locally created events from externally imported events.

---

# 7. FAMILY MESSAGE BOARD

Create a private household noticeboard rather than a full chat system.

Message categories:

- General Note
- Reminder
- Request
- Running Late
- Don't Forget
- Celebration
- Important Notice

Features:

- Create and edit messages
- Pin important messages
- Set expiry dates
- Mark requests complete
- Display author and timestamp
- Filter messages by category
- Archive expired reminders
- Control child visibility
- Optional acknowledgement

Keep message creation fast and simple.

No public profiles, strangers, social feeds, or open messaging.

---

# 8. SHARED SHOPPING LISTS

Create a dedicated list management experience.

Support:

- Grocery shopping
- Household supplies
- Packing
- Meal planning
- Gift ideas
- School supplies
- Custom lists

Allow:

- Adding items
- Editing items
- Marking complete
- Removing items
- Reordering
- Grouping by category
- Assigning members
- Duplicating lists
- Sharing list text

### Shopping Mode

Provide:

- Large checkboxes
- Clear item categories
- Shopping progress
- Fast item entry
- Voice item entry
- Offline access
- Easy one-handed interaction

Group groceries into sections such as produce, dairy, bakery, frozen foods, and household supplies.

Allow custom categories.

---

# 9. CHORES AND FAMILY ROUTINES

Build a family-friendly chore management system.

Examples:

- Feed pets
- Take out recycling
- Wash dishes
- Tidy bedroom
- Prepare school bags
- Water plants
- Set the table

Features:

- One-time chores
- Daily routines
- Weekly routines
- Monthly routines
- Custom recurrence
- Rotating assignments
- Completion tracking
- Weekly progress view
- Optional parent approval
- Optional rewards

### Child-friendly rewards

Parents may enable:

- Stars
- Encouraging messages
- Personal milestones
- Custom family rewards

Never use public leaderboards, shame, manipulative streak mechanics, or purchase-linked rewards.

Reward features must be optional.

---

# 10. CHILD MODE AND SECURITY

Build a separate, simplified child experience.

Show only authorised:

- Assigned chores
- Personal reminders
- Approved family messages
- Relevant calendar events
- Optional rewards

Use large icons, short labels, and age-appropriate language.

### Parental security

Require adult authentication for:

- Household administration
- Member removal
- Permission changes
- Purchases
- Subscription management
- Household deletion
- Sensitive data exports

Never rely solely on hiding controls in the UI.

Enforce permissions in the local application and backend where applicable.

Protect parent PINs using an appropriate salted, slow password-hashing approach and secure storage practices.

Rate-limit authentication attempts.

Reauthenticate before sensitive operations.

Avoid collecting unnecessary child data.

---

# 11. VOICE INTERACTION

Implement optional voice-assisted household management.

Example commands:

"Add milk to the shopping list."

"Remind me about football practice tomorrow."

"What chores do I have today?"

"Add a dentist appointment on Friday."

"Read today's reminders."

### Implementation

- Use Fire OS-compatible speech recognition.
- Check capability availability at runtime.
- Provide a manual input fallback.
- Request microphone permission only when needed.
- Never continuously record.
- Show recognised text before important changes are saved.
- Support corrections.
- Use text-to-speech where available.
- Respect child permissions.
- Avoid retaining raw recordings by default.

Do not claim offline voice recognition unless the actual engine supports it on tested devices.

Keep the app fully usable without voice features.

---

# 12. OFFLINE-FIRST DATA AND SYNC

This is a critical requirement.

All core household actions must work locally.

Use Room as the source of truth for displayed household data.

Support offline:

- Calendar creation and editing
- Messages
- Lists
- Shopping updates
- Chore completion
- Routines
- Local reminders

### Synchronisation engine

Implement:

- Durable mutation queue
- Unique operation IDs
- Entity versioning
- Idempotent server operations
- Retry with exponential backoff
- Conflict detection
- Deletion tombstones
- Clear sync status
- Recoverable error handling

Use appropriate merge strategies.

For incompatible simultaneous changes, offer a simple conflict-resolution interface.

Do not silently overwrite important household data.

### Connected household backend

Use a secure platform-neutral backend architecture.

Supabase is an acceptable option.

If using Supabase:

- Configure authentication.
- Implement row-level security.
- Restrict records to authorised households.
- Enforce role permissions.
- Protect child data.
- Test cross-household isolation.
- Never embed service-role secrets in the client.

Support secure device invitation and revocation.

Distinguish offline single-device functionality from internet-dependent multi-device synchronisation.

---

# 13. REMINDERS AND NOTIFICATIONS

Support:

- Calendar reminders
- Chore reminders
- Important household notices
- Daily summaries
- Recurring reminders

Use Fire OS-compatible notification APIs.

Respect:

- Quiet hours
- Child permissions
- Notification permissions
- Device battery restrictions
- Time-zone changes
- Reboot behaviour

Do not rely on unsupported Google push services.

If remote notifications are implemented, use a verified Amazon-compatible delivery mechanism.

---

# 14. PREMIUM SUBSCRIPTIONS

Implement a freemium business model.

### FamilyDay Free

- One household
- Up to three members
- Basic message board
- One shopping list
- Basic chores
- Limited calendar features
- Offline single-device use

### FamilyDay Premium

- Up to six members
- Unlimited lists
- Full calendar views
- Advanced recurring routines
- Parent chore approval
- Extended household customisation
- Multi-device synchronisation
- Backup and restore
- Data export
- Enhanced household history
- Premium themes

### Pricing

Use configurable launch-price candidates:

- Monthly: £3.99
- Annual: £29.99
- Free trial: 7 days, where supported

Do not hardcode regional subscription prices into the UI.

Display actual configured Amazon marketplace prices.

### Billing implementation

Use the current supported Amazon Appstore In-App Purchasing SDK and subscription APIs.

Implement:

- Product loading
- Purchase flow
- Purchase verification
- Subscription entitlement management
- Restore purchases
- Cancellation guidance
- Grace-period handling where supported
- Expired subscription handling
- Offline entitlement caching
- Secure backend verification where required

Never unlock Premium based solely on an unverified client callback.

Require a parental gate before any child-accessible purchase entry point.

Verify trial eligibility and support before advertising a free trial.

Do not display third-party advertisements.

### Fair downgrade behaviour

When Premium expires:

- Preserve existing household data.
- Do not silently delete members or lists.
- Restrict premium editing or creation features as appropriate.
- Explain the limitation clearly.
- Keep core free functionality usable.
- Allow access to appropriate data export and deletion controls.

Never place essential privacy controls behind a paywall.

---

# 15. INTERNATIONAL LOCALISATION

Prepare FamilyDay Board for all Amazon Appstore marketplaces where the app is eligible for distribution.

Do not assume all countries support the same languages, subscriptions, or features.

### Priority locales

- English (United Kingdom)
- English (United States)
- German
- French
- Spanish
- Italian
- Portuguese (Brazil)
- Dutch
- Japanese

Expand based on verified Amazon marketplace support and commercial priority.

### Requirements

Externalise all user-facing strings.

Support:

- Locale-specific dates
- Time formatting
- Regional number formatting
- Week-start preferences
- Plurals
- Text expansion
- Right-to-left layouts
- Accessibility translations
- Localised error messages
- Localised subscription descriptions

### Automatic translation pipeline

Create a translation workflow that:

1. Extracts source strings.
2. Detects missing translations.
3. Generates draft translations through a configurable translation service.
4. Preserves placeholders and variables.
5. Validates formatting.
6. Flags sensitive child-facing translations.
7. Supports human review.
8. Generates Android string resources.
9. Tests layout overflow.
10. Exports localised store metadata.

Store translation API keys securely in development or CI environments.

Never embed secrets in the distributed APK or AAB.

Do not automatically transmit private family messages to translation providers without explicit consent.

---

# 16. AMAZON APPSTORE SEO

Generate complete, keyword-optimised Amazon Appstore metadata.

### Primary keywords

- Family organizer
- Family planner
- Family calendar
- Shared calendar
- Chore chart
- Family chores
- Shopping list
- Household planner
- Family reminders
- Family message board

### Secondary keywords

- Kids chores
- Home organization
- Shared shopping list
- Household checklist
- Family schedule
- Daily routines
- Family dashboard
- Shared to-do list
- Kitchen family planner

Use natural keyword placement.

Avoid keyword stuffing and unsupported claims.

### SEO title

FamilyDay Board: Family Planner

Verify the current Amazon title character limits before finalising.

### Short description

"Organise family calendars, chores, shopping lists and reminders in one private, shared space. Made for Amazon Fire tablets."

### Full description

Write a persuasive, human-friendly description covering:

- The core problem
- How FamilyDay Board solves it
- Shared family calendar
- Household chores
- Shopping lists
- Private messages
- Child mode
- Offline access
- Premium benefits
- Fire tablet compatibility

Use clear headings and concise paragraphs.

Include natural-language answers to questions such as:

"How do I organise my family calendar?"

"Can children use a shared family planner?"

"Can I manage chores on an Amazon Fire tablet?"

"Does a family shopping list work offline?"

Optimise for conventional search and natural-language discovery.

Do not advertise unimplemented functionality.

---

# 17. PREMIUM APPSTORE SCREENSHOTS

Create a professional screenshot package using genuine application screens.

Screenshots must be:

- Premium
- Relatable
- Easy to understand
- Keyword-rich
- Visually consistent
- Localised
- Accurate to the finished app

Use fictional household data.

### Screenshot 1 — Main Dashboard

**Headline:** "Your Family, Organised"

**Subheadline:** "One place for calendars, chores and reminders"

Show a polished Today dashboard.

### Screenshot 2 — Family Calendar

**Headline:** "One Shared Family Calendar"

**Subheadline:** "Keep everyone's plans together"

Show colour-coded weekly events.

### Screenshot 3 — Chore Management

**Headline:** "Make Family Chores Easier"

**Subheadline:** "Build simple routines for everyone"

Show parent and child chore views.

### Screenshot 4 — Shopping Lists

**Headline