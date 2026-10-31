# ADR-0005 — Ledger model and idempotency key

## Status
Accepted.

## Decision
Double-entry postings are append-only and keyed by an idempotent `idempotencyKey`; posting the same key twice is a no-op.
