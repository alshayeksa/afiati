-- Scheduled alerts (cron every minute):
--   missed doses: a dose still pending an hour after its time is reported to the family once.
--   low stock: a medication at or below its alert level is reported to full-privacy family once,
--              and again only after it was refilled above the level and ran low again.
-- These columns are server bookkeeping: sync never writes them and they do not bump server_seq.

ALTER TABLE medication_logs ADD COLUMN family_notified_at TEXT;
ALTER TABLE medications ADD COLUMN stock_alerted_at TEXT;
-- IANA zone the phone reports (X-Timezone header), so alerts that can wait are not sent at night.
ALTER TABLE devices ADD COLUMN timezone TEXT;

CREATE INDEX idx_logs_family_due ON medication_logs(status, family_notified_at, scheduled_at);
