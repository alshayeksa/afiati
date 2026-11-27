# Project Log

## 2026-11-27 - Help button (زر المساعدة)

### Change
- Worker (`worker/src/help.ts`):
  - `POST /v1/help` creates a request and pushes an urgent alert to every approved relative, whatever privacy level they were given. The alert includes the elder's phone number so they can call. Pressing again while a request is open alerts the family again instead of creating a second request. Limit: 10 requests per hour.
  - `POST /v1/help/:id/acknowledge` is "I'm coming". The first relative to answer is kept. The elder gets "<name> قادم إليك" and the other relatives are told who responded. Strangers get 404.
  - `POST /v1/help/:id/resolve` is "I'm fine" (elder only). It is recorded as cancelled if nobody had answered yet, otherwise resolved. The family is told.
  - `GET /v1/help/active` returns the elder's own active request and active requests of people the user follows (12 hours).
  - Cron trigger every minute (`wrangler.jsonc`): unanswered requests are pushed again every 2 minutes, up to 5 alerts in total, for one hour. Repeat alerts are not stored again in the notifications list.
- Android:
  - A red "أحتاج مساعدة" card on Today shows the current state ("sent", "<name> قادم إليك").
  - The help screen counts down 5 seconds with "Send now" and "Cancel, I'm fine", then shows a waiting screen and finally "<name> قادم إليك" with an "I'm fine now" button.
  - The "Call ambulance 997" button and the emergency contact button are always visible and work without internet. They open the dialer and never call by themselves.
  - New "رقم للطوارئ" setting, kept on the phone only.
  - Relatives see a pulsing red card at the top of every tab, with "Call" and "I'm coming".
  - Help alerts use a new "طلبات المساعدة العاجلة" channel with the alarm sound and a long vibration. The notification has "I'm coming" (answers without opening the app) and "Call" buttons. Alerts for one request replace each other instead of piling up.
- Fixed: the join-code sheet now opens the number keyboard by itself.

### Verification
- Local end-to-end with the mock Google server: 32/32 passed (urgent push to all relatives including alerts-only, phone number only for relatives, permissions, second press, first responder kept, notifications to the elder and other relatives, resolve and cancel, cron re-alert and its 2-minute spacing, no duplicate stored alerts).
- Production sync 20/20 and family 26/26 after deploying.
- Samsung SM-A566B, elder side: help button, 5-second countdown, "sent" screen, a relative answered by API, the phone showed "Khalid قادم إليك" and updated the screen, and "I'm fine now" notified the relative.
- Samsung, relative side: the phone followed a test elder. Her help request arrived as an urgent notification with "I'm coming" and "Call". Tapping "I'm coming" on the notification answered her request, and she received "Abu Khalid قادم إليك". The in-app red card appeared at the top of the tab.

### Not done
- Sharing the elder's location with the alert. The server accepts latitude and longitude, but the app does not ask for location permission yet.

## 2026-11-27 - Family push notifications (Firebase Cloud Messaging)

### Change
- Firebase project `afiati-89b21`. `native/android/app/google-services.json` and the service account key in `push-notification/` are kept out of git. The key is stored as the Worker secret `FCM_SERVICE_ACCOUNT`.
- Worker (`worker/src/fcm.ts`): sends FCM HTTP v1 data messages at high priority. It signs the OAuth assertion with the service account key (RS256), caches the access token, and clears tokens that Google reports as gone. `PUT /v1/me/push-token` accepts FCM tokens as well as Expo tokens. Expo tokens still go to Expo Push.
- Android: `PushService` shows family alerts on a new "تنبيهات العائلة" channel, and family reminders on the medication channel. The token is registered during sync and re-sent when Firebase rotates it. Tapping an "invite request" alert opens Family and shows the approval sheet; other alerts open the notifications sheet.

### Verification
- Local end-to-end against a mock Google server: 14/14 passed (assertion signature and scope, high priority, string data, channel, token reuse, stale token cleared).
- Production sync 20/20 and family 26/26 after deploying.
- Real Google OAuth accepted the service account key.
- Samsung SM-A566B (Android 17): with the app in the background, a join request and a family reminder both arrived as phone notifications. Tapping the join request opened the approval sheet. Test data was then removed from the phone and the server.

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
- Push notifications: family alerts did not pop up on the phone. *(Done: see the Firebase entry above.)*
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

## 2026-11-27 - Scheduled family alerts: missed doses and low stock

### Change
- Worker (`worker/src/alerts.ts`, migration `0002_scheduled_alerts.sql`, same cron every minute as help):
  - Missed doses: a dose still not taken one hour after its time (and at most 3 hours old) is reported once to every approved relative, in a single message per elder ("فاتت <name> جرعتان"). Medicine names are shown only to full-privacy relatives. If the elder's phone has not reached the server since the dose, the message says the dose may have been taken but not synced. The server never changes dose status; `medication_logs.family_notified_at` records that the dose was reported.
  - Low stock: a medicine at or below its alert level is reported once to full-privacy relatives, only between 09:00 and 21:59 in the elder's time zone. It is reported again only after a refill above the level. Tracked in `medications.stock_alerted_at`.
  - The phone sends its IANA zone in `X-Timezone`. It is stored in `devices.timezone` (invalid values are ignored; default Asia/Riyadh).
  - Bookkeeping columns are hidden from sync pull. The cron runs each job separately, so one failure does not stop the others.
- Android:
  - Each sync creates today's and tomorrow's doses first, so the server knows about doses before they are due even if the app is not opened.
  - "أخذتها" from the notification now syncs immediately (8 seconds at most, then a background job), so the family does not get a false missed-dose alert.
  - Local low-stock notification for the elder after taking a dose, once per crossing, cleared by a refill. Works offline.
  - Tapping a missed-dose or low-stock alert opens that relative's day with the remind button.

### Verification
- Local end-to-end with the mock Google server: 30/30 passed (grouping, privacy, once only, late take, offline remark, no family, stopped medicine, stock once and again after refill, night hold and morning send, invalid zone). Help regression 32/32.
- Production sync 20/20 and family 26/26 after deploying. The Samsung reports Asia/Riyadh to the server.
- Not yet seen on a real phone as a relative: the owner's real profile is on the Samsung, so no test family was linked to it.

## 2026-11-27 - Heart rate with the camera (قياس النبض)

### Change
- Android only, fully on the phone: no image is stored or sent, and readings stay in local SQLite (`heart_rate_local`, database version 2 with an upgrade step).
- PPG: the back camera (CameraX 1.7.0, 320x240 YUV) with the torch on. Each frame is reduced to the mean luma and chroma of the central half.
- Finger detection by strong red chroma. Measurement starts only after the finger is on, ignores the first 3 seconds while exposure settles, and restarts if the finger is lifted for more than 0.7 seconds.
- `pulse/PulseMath.kt`: resample to 30 Hz, two-way Butterworth band-pass 0.7–3.5 Hz, autocorrelation and sub-frame peak timing. A number is shown only when the two methods agree within 10%, autocorrelation clarity is at least 0.45, and 70% of beat intervals are steady. Otherwise the app asks to measure again and never guesses.
- 30 seconds of measuring. A provisional number appears after 10 seconds only if it passes the same checks.
- The torch turns off when measuring finishes, is stopped, the app goes to the background, the phone gets hot (thermal status severe), or after 90 seconds without a complete reading. The screen stays on while measuring.
- UI: a «قياس النبض» card on Today with the last reading, plus an intro with drawing and tips, a live progress ring and wave, the result with save and «measure again», and the last 5 readings. A gentle note appears outside 50–110 at rest, and «قراءة تقديرية وليست تشخيصًا طبيًا» is always shown.

### Verification
- Unit tests: 7 rates from 48 to 160 bpm with noise, lighting drift, uneven frame rate and a dicrotic wave, all within 2 bpm. Twenty pure-noise signals and a flat signal gave no number.
- Samsung SM-A566B: camera permission, torch on, live wave with clear beats and dicrotic notch, result 70 bpm saved. The camera was closed afterwards. The upgrade from database version 1 kept the owner's data.

### Not done
- Health Connect (Android) and HealthKit (iOS) export.
- Accuracy against a reference device on elderly users.

## 2026-11-27 - Help request: location on Google Maps, reason, time, grammatical gender

### Change
- Worker (migration `0003_help_details.sql`, `worker/src/arabic.ts`, `worker/src/help.ts`):
  - `profiles.gender` (male/female/NULL), set at registration and by `PUT /v1/me`. Older builds that do not send it keep the stored value.
  - Help messages follow the elder's gender (يحتاج/تحتاج، ضغط/ضغطت، اتصل به/بها) and the responder's gender (قادم/قادمة، في طريقه/طريقها). Unknown gender uses the masculine form.
  - The alert body: the chosen reason, then the press time in the elder's zone («ضغطت زر المساعدة الساعة 2:15 م»), then «موقعها مرفق، اضغط «الخريطة»» when located.
  - Location: `latitude`, `longitude`, `accuracy` with the request, or later through `POST /v1/help/:id/location`. If nobody has answered, the family is alerted again with the map (not stored twice). If answered, a quiet `help_location` message is sent.
  - The push carries `map_url` (`https://www.google.com/maps/search/?api=1&query=lat,lng`). Coordinates go only to relatives; the elder gets `location_shared` only.
- Android:
  - «أنا: رجل / امرأة» in onboarding (required) and in «ملفي». Local database version 3.
  - Help screen:
    - Four quick reasons during the countdown (سقطت، أشعر بدوخة، ألم في الصدر، ضيق في التنفس). Tapping one sends immediately with it.
    - Location: the last known location (up to 10 minutes old) is sent with the request, and a fresh fix follows when found. Sending is never delayed.
    - The sent screen shows «وصل موقعك لعائلتك», a button to allow location, or a button to turn location on.
  - Location permission row in Settings. Location is read only while the help screen is open, never in the background.
  - Relatives: a «الخريطة» button on the urgent notification (next to «أنا قادم» and «اتصال»), and «موقعها على خرائط Google · بدقة 12 م تقريبًا» in the red card.
  - Onboarding text corrected: the phone number and location reach the family only when help is requested.

### Verification
- Local end-to-end: new help details 27/27 (gender both ways, time in Riyadh, reason, map link, late location re-alert and quiet update, permissions, invalid coordinates, older-build profile update). Help regression 32/32 and alerts 30/30.
- Production after deploy: sync 20/20, family 26/26. Test accounts removed; only the owner's account remains.
- Not yet installed on the Samsung: it disconnected from the computer before installation.
