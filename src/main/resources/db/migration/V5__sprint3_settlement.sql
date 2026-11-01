ALTER TABLE verdicts ADD COLUMN IF NOT EXISTS tier_minimum_score integer;
ALTER TABLE verdicts ADD COLUMN IF NOT EXISTS tier_payout_basis_points integer;
ALTER TABLE verdicts ADD COLUMN IF NOT EXISTS evidence_count integer NOT NULL DEFAULT 0;
ALTER TABLE verdicts ADD COLUMN IF NOT EXISTS explanation text;

ALTER TABLE ledger_postings ADD COLUMN IF NOT EXISTS verdict_id text;
ALTER TABLE ledger_postings ADD COLUMN IF NOT EXISTS outcome_id text;
