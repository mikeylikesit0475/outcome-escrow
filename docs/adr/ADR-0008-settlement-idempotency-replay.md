# ADR-0008: Settlement idempotency and replay

## Status
Accepted.

## Context
Sprint 3 moves value by evaluating stored evidence and posting to the ledger. Reevaluation and repeated evaluate calls must not duplicate payouts or clawbacks.

## Decision
- Each ledger posting uses an idempotency key `sha256(outcomeId | evidenceHeadHash | ruleVersion)`.
- Posting outcome is determined by fitting the verdict to the stored evidence set and outcome milestone value.
- Replay loads the stored verdict and reevaluates the current evidence chain; identity is based on score, status, rule version, and evidence head hash, not timestamps.
- If the idempotency key already exists, no posting, webhook, or balance movement occurs.

## Consequences
- Ledger balances are deterministic functions of appended postings.
- Verdict history remains auditable even when replay differs from a stored verdict.
