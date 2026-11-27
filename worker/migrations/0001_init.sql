-- Afiati on Cloudflare D1 (SQLite).
-- Converted from supabase/migrations/*.sql:
--   uuid / timestamptz  -> TEXT (ISO-8601 UTC, same as the app's local SQLite)
--   text[] / int[] / jsonb -> TEXT holding JSON (*_json columns)
--   boolean             -> INTEGER 0/1
--   RLS policies        -> enforced in Worker code (worker/src)
--   auth.users          -> devices table (one secret per installed app)
--   push_tokens         -> devices.push_token (one token per device)
--
-- server_seq: per-table counter the Worker bumps on every write (MAX + 1, writes are
-- serialized in D1). Sync pulls use it as the cursor, so phone clocks never decide
-- what gets downloaded and no change can be skipped.

CREATE TABLE profiles (
  id TEXT PRIMARY KEY NOT NULL,             -- generated on the phone, never changes
  full_name TEXT NOT NULL,
  phone TEXT NOT NULL,
  email TEXT,
  phone_verified INTEGER NOT NULL DEFAULT 0 CHECK (phone_verified IN (0, 1)),
  avatar_url TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

CREATE TABLE devices (
  id TEXT PRIMARY KEY NOT NULL,
  profile_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  secret_hash TEXT NOT NULL,                -- SHA-256 of the device secret; the secret itself is never stored
  platform TEXT CHECK (platform IN ('android', 'ios', 'web')),
  push_token TEXT UNIQUE,                   -- Expo push token
  created_at TEXT NOT NULL,
  last_seen_at TEXT NOT NULL,
  revoked_at TEXT
);

CREATE INDEX idx_devices_profile ON devices(profile_id);

CREATE TABLE medications (
  id TEXT PRIMARY KEY NOT NULL,
  patient_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  name TEXT NOT NULL,
  dose_amount REAL NOT NULL,
  dose_unit TEXT NOT NULL CHECK (dose_unit IN ('حبة', 'مل', 'قطرة', 'بخة', 'other')),
  repeat_type TEXT NOT NULL DEFAULT 'daily' CHECK (repeat_type IN ('daily', 'weekly', 'monthly')),
  repeat_days_json TEXT NOT NULL DEFAULT '[]',
  frequency INTEGER NOT NULL CHECK (frequency BETWEEN 1 AND 4),
  times_json TEXT NOT NULL,
  stock_count INTEGER,
  stock_alert INTEGER NOT NULL DEFAULT 7,
  is_active INTEGER NOT NULL DEFAULT 1 CHECK (is_active IN (0, 1)),
  notes TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,                 -- client version, used for conflict resolution
  deleted_at TEXT,
  server_seq INTEGER NOT NULL
);

CREATE INDEX idx_medications_sync ON medications(patient_id, server_seq);
CREATE INDEX idx_medications_seq ON medications(server_seq);

CREATE TABLE medication_logs (
  id TEXT PRIMARY KEY NOT NULL,
  medication_id TEXT NOT NULL REFERENCES medications(id) ON DELETE CASCADE,
  patient_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  scheduled_at TEXT NOT NULL,
  taken_at TEXT,
  status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'taken', 'missed', 'skipped')),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  server_seq INTEGER NOT NULL,
  UNIQUE (medication_id, scheduled_at)
);

CREATE INDEX idx_logs_sync ON medication_logs(patient_id, server_seq);
CREATE INDEX idx_logs_seq ON medication_logs(server_seq);
CREATE INDEX idx_logs_schedule ON medication_logs(patient_id, scheduled_at);
CREATE INDEX idx_logs_pending ON medication_logs(status, scheduled_at);

CREATE TABLE invitations (
  id TEXT PRIMARY KEY NOT NULL,
  patient_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  code TEXT NOT NULL UNIQUE,                -- 6 digits; several relatives may join with it until it expires
  expires_at TEXT NOT NULL,
  revoked_at TEXT,                          -- set when the patient generates a new code
  created_at TEXT NOT NULL
);

CREATE INDEX idx_invitations_patient ON invitations(patient_id);
CREATE INDEX idx_invitations_expiry ON invitations(expires_at);

-- Fixed-window counters (wrong invite codes, family reminders). key = "<purpose>:<subject>".
CREATE TABLE rate_limits (
  key TEXT PRIMARY KEY NOT NULL,
  window_start INTEGER NOT NULL,            -- unix seconds
  count INTEGER NOT NULL
);

CREATE TABLE family_links (
  id TEXT PRIMARY KEY NOT NULL,
  patient_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  caregiver_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  privacy_level TEXT NOT NULL DEFAULT 'full' CHECK (privacy_level IN ('full', 'status_only', 'alerts_only')),
  status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'active', 'rejected', 'removed')),
  invited_at TEXT NOT NULL,
  approved_at TEXT,
  updated_at TEXT NOT NULL,
  UNIQUE (patient_id, caregiver_id),
  CHECK (patient_id <> caregiver_id)
);

CREATE INDEX idx_family_links_caregiver ON family_links(caregiver_id, status);

CREATE TABLE notifications_log (
  id TEXT PRIMARY KEY NOT NULL,
  user_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  type TEXT NOT NULL DEFAULT 'reminder',
  title TEXT NOT NULL,
  body TEXT NOT NULL,
  data_json TEXT NOT NULL DEFAULT '{}',
  read INTEGER NOT NULL DEFAULT 0 CHECK (read IN (0, 1)),
  created_at TEXT NOT NULL
);

CREATE INDEX idx_notifications_user ON notifications_log(user_id, created_at);

-- Help button: the elder asks for help, family members are pushed, first one to answer acknowledges.
CREATE TABLE help_requests (
  id TEXT PRIMARY KEY NOT NULL,
  patient_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  status TEXT NOT NULL DEFAULT 'open' CHECK (status IN ('open', 'acknowledged', 'resolved', 'cancelled')),
  message TEXT,
  latitude REAL,                            -- only when the elder allowed sharing location
  longitude REAL,
  acknowledged_by TEXT REFERENCES profiles(id) ON DELETE SET NULL,
  acknowledged_at TEXT,
  resolved_at TEXT,
  notify_count INTEGER NOT NULL DEFAULT 1,  -- how many times family was pushed (escalation)
  last_notified_at TEXT NOT NULL,
  created_at TEXT NOT NULL
);

CREATE INDEX idx_help_patient ON help_requests(patient_id, created_at);
CREATE INDEX idx_help_open ON help_requests(status, last_notified_at);
