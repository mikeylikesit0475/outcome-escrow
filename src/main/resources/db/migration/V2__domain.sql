CREATE TABLE contracts (
    id text PRIMARY KEY,
    name text NOT NULL,
    currency text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE outcomes (
    id text PRIMARY KEY,
    contract_id text NOT NULL REFERENCES contracts(id) ON DELETE CASCADE,
    name text NOT NULL,
    rule text NOT NULL,
    milestone_minor bigint NOT NULL,
    currency text NOT NULL
);

CREATE TABLE evidence (
    id text PRIMARY KEY,
    contract_id text NOT NULL REFERENCES contracts(id) ON DELETE CASCADE,
    outcome_id text REFERENCES outcomes(id) ON DELETE CASCADE,
    source text NOT NULL,
    source_ref text NOT NULL,
    captured_at timestamptz NOT NULL,
    payload text NOT NULL,
    hash text NOT NULL,
    prev_hash text
);

CREATE TABLE verdicts (
    id text PRIMARY KEY,
    outcome_id text NOT NULL REFERENCES outcomes(id) ON DELETE CASCADE,
    score integer NOT NULL,
    status text NOT NULL,
    rule_version text NOT NULL,
    evidence_head_hash text NOT NULL,
    evaluated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE ledger_accounts (
    id text PRIMARY KEY,
    currency text NOT NULL
);

CREATE TABLE ledger_postings (
    id text PRIMARY KEY,
    idempotency_key text NOT NULL UNIQUE,
    from_account_id text NOT NULL REFERENCES ledger_accounts(id),
    to_account_id text NOT NULL REFERENCES ledger_accounts(id),
    amount_minor bigint NOT NULL,
    currency text NOT NULL,
    posted_at timestamptz NOT NULL DEFAULT now()
);

CREATE OR REPLACE FUNCTION reject_evidence_mutation()
RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'evidence is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER evidence_no_update
BEFORE UPDATE ON evidence
FOR EACH ROW EXECUTE FUNCTION reject_evidence_mutation();

CREATE TRIGGER evidence_no_delete
BEFORE DELETE ON evidence
FOR EACH ROW EXECUTE FUNCTION reject_evidence_mutation();

CREATE OR REPLACE FUNCTION reject_posting_mutation()
RETURNS trigger AS $$
BEGIN
  RAISE EXCEPTION 'ledger_postings is append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ledger_postings_no_update
BEFORE UPDATE ON ledger_postings
FOR EACH ROW EXECUTE FUNCTION reject_posting_mutation();

CREATE TRIGGER ledger_postings_no_delete
BEFORE DELETE ON ledger_postings
FOR EACH ROW EXECUTE FUNCTION reject_posting_mutation();
