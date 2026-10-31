# ADR-0003 — Deterministic core, no LLM in the decision path

## Status
Accepted.

## Context
The product promise is "real data, not arguments". Verdicts must be reproducible from stored evidence.

## Decision
The `domain` package is pure Java with an injected clock. The evaluator is golden-tested. Any narration is strictly post-verdict and lives in the icebox.

## Consequences
ArchUnit enforces dependency rules. Any nondeterminism in the decision path is a bug.
