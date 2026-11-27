-- Help messages in the right grammatical gender, and the elder's location on Google Maps.
-- gender: chosen by the user in the app; NULL until chosen (messages then use the masculine form).
ALTER TABLE profiles ADD COLUMN gender TEXT CHECK (gender IN ('male', 'female'));
-- Location sent with a help request: accuracy in metres and when the phone measured it.
ALTER TABLE help_requests ADD COLUMN location_accuracy REAL;
ALTER TABLE help_requests ADD COLUMN location_at TEXT;
