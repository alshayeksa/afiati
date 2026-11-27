-- Offline-first identity and synchronization support.
alter table public.profiles
  add column if not exists auth_user_id uuid references auth.users(id) on delete set null,
  add column if not exists phone text,
  add column if not exists email text,
  add column if not exists phone_verified boolean not null default false;

create unique index if not exists profiles_auth_user_id_unique
  on public.profiles(auth_user_id)
  where auth_user_id is not null;

alter table public.medications
  add column if not exists deleted_at timestamptz;

alter table public.medication_logs
  add column if not exists updated_at timestamptz not null default now();

create index if not exists idx_profiles_auth_user on public.profiles(auth_user_id);
create index if not exists idx_medications_sync on public.medications(patient_id, updated_at);
create index if not exists idx_medication_logs_sync on public.medication_logs(patient_id, updated_at);

create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = public
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

drop trigger if exists profiles_set_updated_at on public.profiles;
create trigger profiles_set_updated_at before update on public.profiles
for each row execute function public.set_updated_at();

drop trigger if exists medications_set_updated_at on public.medications;
create trigger medications_set_updated_at before update on public.medications
for each row execute function public.set_updated_at();

drop trigger if exists medication_logs_set_updated_at on public.medication_logs;
create trigger medication_logs_set_updated_at before update on public.medication_logs
for each row execute function public.set_updated_at();

create or replace function public.current_patient_id()
returns uuid
language sql
stable
security definer
set search_path = public
as $$
  select id from public.profiles where auth_user_id = (select auth.uid()) limit 1;
$$;

revoke all on function public.current_patient_id() from public;
grant execute on function public.current_patient_id() to authenticated;

drop policy if exists "dev_profiles" on public.profiles;
drop policy if exists "dev_medications" on public.medications;
drop policy if exists "dev_medication_logs" on public.medication_logs;
drop policy if exists "dev_invitations" on public.invitations;
drop policy if exists "dev_family_links" on public.family_links;
drop policy if exists "dev_push_tokens" on public.push_tokens;
drop policy if exists "dev_notifications" on public.notifications_log;

drop policy if exists "profiles_insert_own" on public.profiles;
drop policy if exists "profiles_select_related" on public.profiles;
drop policy if exists "profiles_update_own" on public.profiles;
create policy "profiles_insert_own" on public.profiles
  for insert to authenticated
  with check (auth_user_id = (select auth.uid()));
create policy "profiles_select_related" on public.profiles
  for select to authenticated
  using (
    auth_user_id = (select auth.uid())
    or id in (
      select patient_id from public.family_links
      where caregiver_id = public.current_patient_id() and status = 'active'
      union
      select caregiver_id from public.family_links
      where patient_id = public.current_patient_id() and status in ('pending', 'active')
    )
  );
create policy "profiles_update_own" on public.profiles
  for update to authenticated
  using (auth_user_id = (select auth.uid()))
  with check (auth_user_id = (select auth.uid()));

drop policy if exists "medications_own_all" on public.medications;
drop policy if exists "medications_family_read" on public.medications;
create policy "medications_own_all" on public.medications
  for all to authenticated
  using (patient_id = public.current_patient_id())
  with check (patient_id = public.current_patient_id());
create policy "medications_family_read" on public.medications
  for select to authenticated
  using (
    exists (
      select 1 from public.family_links link
      where link.patient_id = medications.patient_id
        and link.caregiver_id = public.current_patient_id()
        and link.status = 'active'
        and link.privacy_level = 'full'
    )
  );

drop policy if exists "logs_own_all" on public.medication_logs;
drop policy if exists "logs_family_read" on public.medication_logs;
create policy "logs_own_all" on public.medication_logs
  for all to authenticated
  using (patient_id = public.current_patient_id())
  with check (patient_id = public.current_patient_id());
create policy "logs_family_read" on public.medication_logs
  for select to authenticated
  using (
    exists (
      select 1 from public.family_links link
      where link.patient_id = medication_logs.patient_id
        and link.caregiver_id = public.current_patient_id()
        and link.status = 'active'
        and link.privacy_level in ('full', 'status_only')
    )
  );

drop policy if exists "invitations_own" on public.invitations;
create policy "invitations_own" on public.invitations
  for all to authenticated
  using (patient_id = public.current_patient_id())
  with check (patient_id = public.current_patient_id());

drop policy if exists "family_links_related_select" on public.family_links;
drop policy if exists "family_links_related_insert" on public.family_links;
drop policy if exists "family_links_patient_update" on public.family_links;
create policy "family_links_related_select" on public.family_links
  for select to authenticated
  using (patient_id = public.current_patient_id() or caregiver_id = public.current_patient_id());
create policy "family_links_related_insert" on public.family_links
  for insert to authenticated
  with check (patient_id = public.current_patient_id() or caregiver_id = public.current_patient_id());
create policy "family_links_patient_update" on public.family_links
  for update to authenticated
  using (patient_id = public.current_patient_id())
  with check (patient_id = public.current_patient_id());

drop policy if exists "push_tokens_own" on public.push_tokens;
create policy "push_tokens_own" on public.push_tokens
  for all to authenticated
  using (user_id = public.current_patient_id())
  with check (user_id = public.current_patient_id());

drop policy if exists "notifications_own" on public.notifications_log;
create policy "notifications_own" on public.notifications_log
  for all to authenticated
  using (user_id = public.current_patient_id())
  with check (user_id = public.current_patient_id());

