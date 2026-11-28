-- Usage statistics on the /forceupdate page, and medical partners (ambulance, hospitals) for help requests.

-- The country of each device, from Cloudflare (request.cf.country). No IP address is stored.
ALTER TABLE devices ADD COLUMN country TEXT;

-- One row per device per day it used the app (UTC day), for the daily-users chart.
CREATE TABLE device_days (
  day TEXT NOT NULL,                        -- YYYY-MM-DD
  device_id TEXT NOT NULL REFERENCES devices(id) ON DELETE CASCADE,
  PRIMARY KEY (day, device_id)
);

-- The elder agreed that a medical partner may receive their help requests (name, phone, location).
-- Off by default: nothing goes to any partner until the elder turns it on in the app.
ALTER TABLE profiles ADD COLUMN partner_consent INTEGER NOT NULL DEFAULT 0 CHECK (partner_consent IN (0, 1));

-- A medical partner, added from the control page. It receives help requests on its webhook
-- (signed with webhook_secret) and answers through /v1/partner/* with its API key.
CREATE TABLE partners (
  id TEXT PRIMARY KEY NOT NULL,
  name TEXT NOT NULL,
  key_hash TEXT NOT NULL UNIQUE,            -- SHA-256 of the API key; the key is shown once
  webhook_url TEXT,
  webhook_secret TEXT NOT NULL,
  countries_json TEXT NOT NULL DEFAULT '[]', -- ISO country codes it serves; empty = every country
  active INTEGER NOT NULL DEFAULT 1 CHECK (active IN (0, 1)),
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);

-- A help request sent to a partner, and the partner's answer.
CREATE TABLE help_dispatches (
  help_id TEXT NOT NULL REFERENCES help_requests(id) ON DELETE CASCADE,
  partner_id TEXT NOT NULL REFERENCES partners(id) ON DELETE CASCADE,
  status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'sent', 'failed', 'acknowledged', 'arrived', 'closed')),
  attempts INTEGER NOT NULL DEFAULT 0,
  last_error TEXT,
  note TEXT,
  eta_minutes INTEGER,
  sent_at TEXT,
  acknowledged_at TEXT,
  updated_at TEXT NOT NULL,
  PRIMARY KEY (help_id, partner_id)
);

CREATE INDEX idx_dispatch_retry ON help_dispatches(status, updated_at);
CREATE INDEX idx_device_days_device ON device_days(device_id);
