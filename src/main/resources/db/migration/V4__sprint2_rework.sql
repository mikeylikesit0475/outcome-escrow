ALTER TABLE evidence ADD COLUMN IF NOT EXISTS seq bigint;
UPDATE evidence SET seq = ranked.seq
  FROM (
    SELECT id, row_number() OVER (PARTITION BY contract_id ORDER BY captured_at, id) AS seq
    FROM evidence
  ) ranked
  WHERE ranked.id = evidence.id AND evidence.seq IS NULL;
ALTER TABLE evidence ALTER COLUMN seq SET NOT NULL;
CREATE UNIQUE INDEX IF NOT EXISTS evidence_contract_seq_uq ON evidence (contract_id, seq);

ALTER TABLE outcomes ADD COLUMN IF NOT EXISTS rule_json text;
ALTER TABLE outcomes ADD COLUMN IF NOT EXISTS tiers_json text;
-- rule_json/tiers_json remain nullable; the seed loader populates them
