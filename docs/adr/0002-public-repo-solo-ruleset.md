# ADR-0002 — Public repository and solo-repo ruleset from Sprint 0

## Status
Accepted.

## Context
The GitHub flow itself is the deliverable, and rulesets on GitHub Free apply to public repositories only. There is one human and one bot.

## Decision
Public from the first commit. Ruleset `protect-main` requires PR + required checks + linear history, with zero required approvals. Copilot review is requested on every PR; architect acceptance happens outside GitHub.

## Consequences
Work in progress is visible by design. The README states that the repository is a worked example of the flow.
