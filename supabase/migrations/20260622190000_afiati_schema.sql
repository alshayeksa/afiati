create extension if not exists "pgcrypto";

create table if not exists public.profiles (
  id uuid primary key,
  full_name text not null,
  avatar_url text,
  created_at timestamptz default now(),
  updated_at timestamptz default now()
);

create table if not exists public.medications (
  id uuid primary key default gen_random_uuid(),
  patient_id uuid not null references public.profiles(id) on delete cascade,
  name text not null,
  dose_amount numeric not null,
  dose_unit text not null check (dose_unit in ('حبة', 'مل', 'قطرة', 'بخة', 'other')),
  frequency integer not null check (frequency between 1 and 4),
  times text[] not null,
  stock_count integer,
  stock_alert integer default 7,
  is_active boolean default true,
  notes text,
  created_at timestamptz default now(),
  updated_at timestamptz default now()
);

create table if not exists public.medication_logs (
  id uuid primary key default gen_random_uuid(),
  medication_id uuid not null references public.medications(id) on delete cascade,
  patient_id uuid not null references public.profiles(id) on delete cascade,
  scheduled_at timestamptz not null,
  taken_at timestamptz,
  status text not null default 'pending' check (status in ('pending', 'taken', 'missed', 'skipped')),
  created_at timestamptz default now(),
  unique (medication_id, scheduled_at)
);

create index if not exists idx_medication_logs_patient on public.medication_logs(patient_id, scheduled_at);
create index if not exists idx_medication_logs_medication on public.medication_logs(medication_id, scheduled_at);

create table if not exists public.invitations (
  id uuid primary key default gen_random_uuid(),
  patient_id uuid not null references public.profiles(id) on delete cascade,
  code text not null unique,
  expires_at timestamptz not null default (now() + interval '24 hours'),
  used boolean default false,
  created_at timestamptz default now()
);

create index if not exists idx_invitations_code on public.invitations(code);

create table if not exists public.family_links (
  id uuid primary key default gen_random_uuid(),
  patient_id uuid not null references public.profiles(id) on delete cascade,
  caregiver_id uuid not null references public.profiles(id) on delete cascade,
  privacy_level text not null default 'full' check (privacy_level in ('full', 'status_only', 'alerts_only')),
  status text not null default 'pending' check (status in ('pending', 'active', 'rejected', 'removed')),
  invited_at timestamptz default now(),
  approved_at timestamptz,
  unique (patient_id, caregiver_id)
);

create table if not exists public.push_tokens (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  token text not null unique,
  platform text,
  created_at timestamptz default now()
);

create table if not exists public.notifications_log (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles(id) on delete cascade,
  type text not null default 'reminder',
  title text not null,
  body text not null,
  data jsonb default '{}'::jsonb,
  read boolean default false,
  created_at timestamptz default now()
);

create or replace function public.decrement_stock(med_id uuid)
returns void
language sql
security definer
set search_path = public
as $$
  update medications
  set stock_count = greatest(stock_count - 1, 0),
      updated_at = now()
  where id = med_id and stock_count is not null;
$$;

insert into public.profiles (id, full_name)
values ('00000000-0000-0000-0000-000000000001', 'شايع')
on conflict (id) do update set full_name = excluded.full_name, updated_at = now();

alter table public.profiles enable row level security;
alter table public.medications enable row level security;
alter table public.medication_logs enable row level security;
alter table public.invitations enable row level security;
alter table public.family_links enable row level security;
alter table public.push_tokens enable row level security;
alter table public.notifications_log enable row level security;

drop policy if exists "dev_profiles" on public.profiles;
drop policy if exists "dev_medications" on public.medications;
drop policy if exists "dev_medication_logs" on public.medication_logs;
drop policy if exists "dev_invitations" on public.invitations;
drop policy if exists "dev_family_links" on public.family_links;
drop policy if exists "dev_push_tokens" on public.push_tokens;
drop policy if exists "dev_notifications" on public.notifications_log;

-- Phase 1 only: unauthenticated Expo screens use DEV_USER.
-- Remove these dev policies when real Supabase Auth is enabled.
create policy "dev_profiles" on public.profiles
  for all using (id = '00000000-0000-0000-0000-000000000001')
  with check (id = '00000000-0000-0000-0000-000000000001');

create policy "dev_medications" on public.medications
  for all using (patient_id = '00000000-0000-0000-0000-000000000001')
  with check (patient_id = '00000000-0000-0000-0000-000000000001');

create policy "dev_medication_logs" on public.medication_logs
  for all using (patient_id = '00000000-0000-0000-0000-000000000001')
  with check (patient_id = '00000000-0000-0000-0000-000000000001');

create policy "dev_invitations" on public.invitations
  for all using (patient_id = '00000000-0000-0000-0000-000000000001')
  with check (patient_id = '00000000-0000-0000-0000-000000000001');

create policy "dev_family_links" on public.family_links
  for all using (
    patient_id = '00000000-0000-0000-0000-000000000001'
    or caregiver_id = '00000000-0000-0000-0000-000000000001'
  )
  with check (
    patient_id = '00000000-0000-0000-0000-000000000001'
    or caregiver_id = '00000000-0000-0000-0000-000000000001'
  );

create policy "dev_push_tokens" on public.push_tokens
  for all using (user_id = '00000000-0000-0000-0000-000000000001')
  with check (user_id = '00000000-0000-0000-0000-000000000001');

create policy "dev_notifications" on public.notifications_log
  for all using (user_id = '00000000-0000-0000-0000-000000000001')
  with check (user_id = '00000000-0000-0000-0000-000000000001');
