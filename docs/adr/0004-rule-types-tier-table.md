# ADR-0004 — Rule types and tier table

## Status
Accepted.

## Decision
Outcome rules are a closed `RuleSpec` sealed hierarchy: `Threshold`, `WindowedRate`, and `Deadline`. Tier tables convert scores into payout basis points and clawback below the floor.
