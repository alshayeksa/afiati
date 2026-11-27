-- Owner controls from the /forceupdate page: forced update per platform, and the service switch.
-- One JSON row (key 'control'), read by every request (cached for a few seconds).
CREATE TABLE app_settings (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  updated_by TEXT
);
