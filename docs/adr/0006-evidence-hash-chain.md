# ADR-0006 — Evidence hash chain and append-only verification

## Status
Accepted.

## Context
Evidence records are the auditable basis for verdicts. Once evidence is pulled or seeded, its content and ordering must be tamper-evident without relying on mutable application state. Adapters receive data that has crossed network boundaries, so their supplied hashes cannot be trusted as proof of stored content.

## Decision
Every `EvidenceRecord` participates in a per-contract hash chain. On append, the repository locks the contract row, allocates the next `seq` value, computes `sha256(payload)` as the record's hash, and links the previous hash in that order. Adapters may supply a hash, but the repository ignores it and recomputes the canonical payload hash.

Verification replays stored evidence ordered by `seq`, recomputes every payload hash, and checks both the hash value and the `prev_hash` linkage. Evidence is append-only at the database level through triggers. Records are never ordered by physically non-deterministic timestamp columns for chain membership.

## Consequences
Append-only DB triggers, the `seq` constraint, and contract-row locking protect evidence at rest. Verification is deterministic and reproducible from stored evidence and cannot be bypassed by a stale or arbitrary adapter hash.
