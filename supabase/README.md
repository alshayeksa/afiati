# Afiati Supabase Setup

The schema is in `supabase/migrations/20260622190000_afiati_schema.sql`.

Apply it with either:

```powershell
psql "host=db.lvwgmihyugxhoxtjptfa.supabase.co port=5432 dbname=postgres user=postgres sslmode=require" -f "supabase\migrations\20260622190000_afiati_schema.sql"
```

or:

```powershell
npx supabase link --project-ref lvwgmihyugxhoxtjptfa
npx supabase db push
npx supabase functions deploy generate-invite-code
npx supabase functions deploy join-family
npx supabase functions deploy approve-family-link
npx supabase functions deploy send-due-reminders
npx supabase functions deploy check-missed-doses
npx supabase functions deploy stock-alert-check
npx supabase functions deploy send-family-reminder
```

The offline-first migration creates a stable local `profiles.id` for every patient and links it to
Supabase Auth through `profiles.auth_user_id`. Anonymous Auth is used for silent synchronization;
enable Anonymous Sign-Ins in Authentication settings before testing synchronization.
