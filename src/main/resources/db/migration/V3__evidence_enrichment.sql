ALTER TABLE evidence ADD COLUMN IF NOT EXISTS synthetic boolean NOT NULL DEFAULT false;
ALTER TABLE outcomes ADD COLUMN IF NOT EXISTS connector text;
ALTER TABLE outcomes ADD COLUMN IF NOT EXISTS metric text;
ALTER TABLE outcomes ADD COLUMN IF NOT EXISTS window_days integer;
ALTER TABLE outcomes ADD COLUMN IF NOT EXISTS params text;
