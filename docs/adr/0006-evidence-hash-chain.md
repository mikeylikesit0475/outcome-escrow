# ADR-0006 — Evidence hash chain and append-only verification

## Status
Accepted.

## Context
Evidence records are the auditable basis for verdicts. Once evidence is pulled or seeded, its content and ordering must be tamper-evident without relying on mutable application state.

## Decision
Every `EvidenceRecord` participates in a per-contract hash chain. On append, the repository locks the contract chain head inside a transaction, records the new record's previous-hash link, and inserts append-only. A verifier replays the ordered evidence by `captured_at, id`, recomputes source adapter hashes where canonical, and checks each previous-hash link.

Manual or alternate source hashes are treated as chain links only; only canonical hexadecimal hashes participate in adapter/payload recomputation.

## Consequences
Append-only DB triggers and ordering constraints protect evidence at rest. Verification is deterministic and reproducible from stored evidence.
