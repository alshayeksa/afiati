alter table public.medications
  add column if not exists repeat_type text not null default 'daily'
    check (repeat_type in ('daily', 'weekly', 'monthly')),
  add column if not exists repeat_days integer[] not null default '{}';
