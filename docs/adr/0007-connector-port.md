# ADR-0007 — Connector port and adapter boundary

## Status
Accepted.

## Decision
Connectors implement the read-only `EvidenceSource` port (`id`, `synthetic`, `fetch(Outcome, Window, Instant)`) and may use private HTTP clients and adapters. They are registered by an `EvidenceSourceRegistry`. They never enter the decision path or depend on the domain clock except through an injected `Instant now`.

Connectors return metric values as immutable `Evidence` records within the caller's window. They cannot set authoritative hashes; the evidence repository computes the canonical payload hash and chain position. Tests use recorded responses and must cover each implemented metric, including empty results and warnings instead of silent nulls.

## Consequences
Connectors stay replaceable and testable against recorded responses; evidence hashing normalizes results into immutable `Evidence` records.
