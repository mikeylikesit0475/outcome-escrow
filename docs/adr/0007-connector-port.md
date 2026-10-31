# ADR-0007 — Connector port and adapter boundary

## Status
Accepted.

## Decision
Connectors implement the read-only `EvidenceSource` port (`id`, `synthetic`, `fetch(Outcome, Window, Instant)`) and may use private HTTP clients and adapters. They are registered by a `EvidenceSourceRegistry`. They never enter the decision path or depend on the domain clock except through an injected `Instant now`.

## Consequences
Connectors stay replaceable and testable against recorded responses; evidence hashing normalizes results into immutable `Evidence` records.
