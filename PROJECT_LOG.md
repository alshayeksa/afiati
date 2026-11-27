# Project Log

## 2026-11-27 - Native Android app (Kotlin + Jetpack Compose)

### Why
The owner asked for real native apps (Kotlin for Android, Swift/SwiftUI for iOS) with the look of the Mawja app (`D:\projects\mawja`), and a redesign that is easier for elderly users. Android comes first because it can be built and tested on this Windows machine.

### Change
- New project in `native/android` (package `com.alshayeksa.afiati`, minSdk 26, targetSdk 36). The Expo app is untouched and keeps working until the native apps replace it.
- Design taken from Mawja: near-black background with a soft top glow, glass cards, glowing primary button, animated wave ribbon, floating pill tab bar with a round add button, Tajawal font, six colour themes plus dark, light and automatic modes. The default theme is ocean blue to match the Afiati logo. Text is larger than Mawja's, with an extra "large text" switch.
- Screens: welcome and profile setup; Today (greeting, Gregorian and Hijri date, save/sync status, adherence ring, a focus card with one big "I took it" button for the due dose, doses grouped by morning, noon, evening and night, low-stock cards); My medications (swipe or long-press to add stock or stop a medication, details sheet); History (7-day and 30-day adherence, full-day streak, 30-day strip, per-day doses); Family (followers and following, pending request card, invite sheet with 6-digit code, QR and share, join sheet, approval sheet with privacy levels, relative's day screen with "send a gentle reminder"); notifications sheet; settings sheet (notification and exact-alarm status, theme, large text, sync status, about); profile sheet.
- Add medication: six short steps with large controls. Doses can be halves. A new large-button time picker replaces the clock dial. At most 4 times a day, matching the server limit (the Expo wizard allowed 5, which the server rejects).
- Taking a dose shows "Undo" for 5 seconds before it is saved. A dose more than an hour early asks for confirmation.
- Reminders: one exact alarm per medication time (`AlarmManager`), rescheduled after boot, app update and time changes. The notification has "I took it" (records the dose directly) and "Remind me in 10 minutes".
- Data: the same SQLite tables, outbox and conflict rules as the Expo app. Dose ids use the same `sha256(medication_id|scheduled_at)` UUID. Logs are created for today and the 6 previous days (from the day a medication was added), so missed days show as missed. Doses scheduled before a medication was added are not created.
- Sync: the same order as the Expo engine (register once, push profile, push outbox in batches of 25, pull by server cursor). Background sync through `JobScheduler`: every 15 minutes, and soon after any change once the network is available.
- Unexpected errors in background work show a short message instead of closing the app.
- CI: `.github/workflows/android.yml` runs unit tests and builds a debug APK when `native/android/**` changes.

### Server
- Deployed the Worker to `https://afiati-api.alshayeksa.workers.dev` with a new D1 database `afiati` (`424e4dd1-5315-4db6-917b-a2785aadfa4f`). The end-to-end sync and family tests passed against production, and the test data was then deleted.

### Verification
- Unit tests (JVM): 9/9 passed (dose id matches the TypeScript algorithm, ISO format, local schedule times, weekday numbering, next occurrence, conflict rules).
- Emulator (Android 11, 1080x1920): onboarding, adding a medication, taking a dose with undo, all four tabs, invite code from the server, a test relative joining by API, approval in the app, the relative reading the day and sending a reminder, notifications and settings sheets.
- Production D1 received the profile, device, medication and dose logs from the emulator.

### Known gaps
- Push notifications: family alerts are stored and shown in the notifications sheet, but do not pop up on the phone. The Worker sends Expo push tokens only; the native app needs Firebase Cloud Messaging. This needs a Firebase project for Afiati and is planned with the help button.
- iOS (Swift/SwiftUI) is not started yet.

## 2026-11-27 - Move backend to Cloudflare (step 3: family + notifications)

### Change
- Worker endpoints replace the Supabase Edge Functions `generate-invite-code`, `join-family`, `approve-family-link`, `send-family-reminder` and the RLS read rules:
  `GET /v1/family`, `POST /v1/family/invitations`, `POST /v1/family/join`, `POST /v1/family/respond`,
  `GET /v1/family/patients/:id/today`, `POST /v1/family/patients/:id/remind`, `GET /v1/notifications`, `POST /v1/notifications/read-all`.
- Privacy levels are enforced in the Worker: `full` returns medications and dose logs, `status_only` returns dose logs only, `alerts_only` returns neither.
- Fixed the old RLS hole: only the patient can approve a pending request. A caregiver can no longer insert an active link for themselves.
- One invite code is still valid for several relatives for 24 hours, and each join still needs the patient's approval. A new code revokes the old one. Each device may try at most 10 codes per hour.
- Family reminders are limited to 1 per caregiver per patient every 10 minutes.
- Responses expose only the other person's name, never their phone number or email.
- Every notification is stored in `notifications_log` and pushed through Expo Push to the user's devices.
- App: added `lib/family.ts`. `familyStore`, `invite`, `join`, `privacy-approval`, `patient/[id]` and `notifications` now use the Worker. Removed `lib/supabase.ts`, `@supabase/supabase-js` and `react-native-url-polyfill`.

### Known gap
The app has never registered an Expo push token: no `getExpoPushTokenAsync` call existed, even with Supabase. Notifications are stored and appear in the notifications screen, but they do not pop up on the phone yet. Token registration needs an EAS project id and FCM credentials, and is planned together with the help button.

### Verification
- App TypeScript: passed. App tests: 8/8 passed. Android Metro bundle export: passed.
- Worker TypeScript: passed. Worker end-to-end against local D1: family 26/26, sync 20/20.

## 2026-11-27 - Move backend from Supabase to Cloudflare (steps 1-2: database + sync)

### Why
The free Supabase project pauses after a period of inactivity, and keep-alive pings did not work reliably. Cloudflare Workers + D1 are free at this scale, never pause, and include cron triggers for scheduled tasks.

### Change
- New `worker/` folder (Cloudflare Worker + D1). The schema is in `worker/migrations/0001_init.sql`, converted from `supabase/migrations/*.sql`, plus `devices` (replaces Supabase Auth) and `help_requests` (help button).
- Identity: on first sync the phone registers with `POST /v1/register` and receives a device token, stored in `sync_state.device_token`. `patients_local.auth_user_id` now holds the Worker device id. Registering an existing profile id is refused, so a profile cannot be taken over.
- Sync: `POST /v1/sync/push` (max 25 operations per request) and `GET /v1/sync/pull` with a server-side `server_seq` cursor per table, so phone clocks no longer affect sync. The conflict rules are unchanged: a newer medication wins, and a `taken` dose never reverts.
- Dose log ids are now derived from `sha256(medication_id|scheduled_at)` (`lib/sync/ids.ts`), so the same dose has the same id on every device.
- Outbox: operations the server rejects 5 times stop being sent, so they cannot block newer operations.
- The app reads the Worker URL from `EXPO_PUBLIC_API_URL`. Without it, sync reports `not_configured` and the app keeps working locally.

### Still on Supabase (next step)
Family screens (invite, join, approval, patient view, notifications) still call Supabase and will show a connection error until they are moved to the Worker. *(Done in step 3.)*

### Verification
- App TypeScript: passed. App tests: 8/8 passed.
- Worker TypeScript: passed.
- Worker end-to-end against local D1: 20/20 passed (UTF-8 Arabic, conflict rules, patient isolation, rejection of invalid rows, limits, paging order).

## 2026-07-24 - Elderly-friendly step 1 medication name screen

### Change
Rebuilt step 1 of `app/add-medication.tsx` per the elderly UX spec:

- Placeholder is now `اكتب اسم الدواء من العلبة`.
- Permanent quick suggestions (5 chips): دواء السكري، دواء الضغط، مسكن، دواء القلب، دواء المعدة.
- `عرض المزيد` button reveals 6 additional categories (فيتامينات، الحساسية، الكوليسترول، العين، الربو، المفاصل); tapping a category shows its sub-options under `اختر النوع` (max 6 per level) with a back button.
- Tapping any suggestion fills the name input and highlights the selected chip; the input stays editable and manual text is never overwritten.
- Empty name on `التالي` shows `اكتب اسم الدواء أولًا`.
- Design rules applied: no level shows more than 6 items, fonts >= 18, chips >= 56px, progressive disclosure (categories and sub-options hidden until requested).

### Verification
- TypeScript: passed.

## 2026-07-24 - Wheel time picker for medication schedule

### Context
In `app/add-medication.tsx`, step 4 (`متى تأخذ الدواء؟`) used a plain `TextInput` for each dose time, which was error-prone and unprofessional.

### Change
- Added `components/TimeWheelPicker.tsx`: a modal wheel picker (hours 1-12, minutes 00-59, ص/م toggle) built with `FlatList` snap scrolling. Pure React Native, no new native dependency, so no rebuild is required.
- Step 4 now shows each dose as a tappable row (`الجرعة الأولى`...) with the formatted time and a clock icon; tapping opens the picker.
- Times are still stored as `HH:mm` 24-hour strings, so repositories, notifications, and sync are unchanged.
- The confirmation step now displays times via `formatTime` (Arabic 12-hour format) like the rest of the app.

### Verification
- TypeScript: passed.
- Tests: 5/5 passed.

## 2026-06-23 - RTL alignment inside Add Medication card

### Context
In `app/add-medication.tsx`, the first card in the add-medication flow contains the title `ما اسم الدواء؟`.
Although the text itself is Arabic and uses RTL text styles, it appeared on the left side of the card.

### Goal
Keep the title visually aligned to the right side of the card without forcing the title to stretch across the full card width and without adding `flex: 1`.

### Fix
The card container for the medication-name step uses `direction: 'rtl'`.
Inside an RTL container in React Native, `flex-start` represents the visual start of the RTL row, which is the right side.

So the fix was:

```ts
nameCard: {
  direction: 'rtl',
  alignItems: 'flex-start',
  borderWidth: 1.5,
  borderColor: Colors.line,
  padding: 16,
  gap: 12,
}
```

The suggestions row also uses:

```ts
alignSelf: 'flex-start'
```

### Why
Using `alignItems: 'flex-end'` in an RTL container did not move the title to the right as expected. In this layout, `flex-start` is the correct value because it follows the RTL writing direction. This keeps compact text like `ما اسم الدواء؟` naturally sized while placing it on the right.

### Rule of Thumb
For manually controlled RTL layouts in this project:

- Use `direction: 'rtl'` on the relevant container.
- Use `flex-start` when you want children to begin from the right in that RTL container.
- Avoid `flex: 1` or forced full-width text unless the element truly needs to fill the row.

## 2026-06-23 - Removed medication image scanning

### Context
The medication image scanning experiment was removed because it made the add-medication flow too slow.

### Goal
Keep the add-medication screen fast and focused on manual entry plus quick suggestion chips.

### Fix
Removed the camera/photo buttons, image-reading logic, related permissions, and unused image/OCR dependencies.

- `تصوير الدواء`
- `من الصور`


### Why
Manual entry is currently faster and more reliable for the target flow.

## 2026-07-15 - Offline-first SQLite transition

### Implemented

- Upgraded the project and native Android build to Expo SDK 56.
- Added `expo-sqlite`, `expo-network`, `expo-crypto`, `expo-background-task`, and `expo-task-manager`.
- Added a versioned `afiati.db` database with WAL and foreign keys.
- Added a stable, locally generated UUID for each patient. The patient UUID does not change after cloud sync.
- Added first-run local profile setup: name and phone are required; email is optional.
- Added Repository-based local reads and writes for medications and medication logs.
- Added local daily-log generation, stock decrement, soft deletion, and duplicate-medication warning.
- Added local daily medication reminders.
- Added an Outbox whose operations survive app restarts and are retried after connectivity returns.
- Added sync triggers on app start, foreground resume, network recovery, and best-effort background execution.
- Added conflict rules: newer medication versions win, and a `taken` dose never returns to `pending`.
- Restricted family, invitations, remote notifications, and patient-following screens to online operation.
- Added a real online family patient view and a family reminder Edge Function source.
- Applied `20260715090000_offline_first.sql` to the remote Supabase database, including the new identity columns and RLS policies.

### Verification

- TypeScript: passed.
- Sync conflict/offline tests: 5/5 passed.
- Expo Doctor: 21/21 passed.
- Android SDK 56 Debug build: passed for `arm64-v8a`.
- Samsung device: installed and opened without runtime or SQLite errors.
- Samsung offline test: created a local patient, added a medication and daily dose, force-stopped the app, reopened it offline, and confirmed the data persisted.
- Wi-Fi and mobile data were restored after the test.

### Cloud activation remaining

- Supabase Anonymous Sign-Ins are currently disabled. They must be enabled for silent cloud identity creation and Outbox upload.
- Edge Function deployment requires `SUPABASE_ACCESS_TOKEN`; the database service-role key cannot deploy functions.
- Legacy records owned by the old fixed `DEV_USER` were left untouched. Reassign them only after the real patient identity is verified; automatic claiming by an unverified name, email, or phone would be insecure.
- Until those two cloud settings are completed, personal features continue to work locally, while sync and family activation report a controlled error and retain pending Outbox operations.
