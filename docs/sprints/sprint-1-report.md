# Sprint 1 report — Domain core (rework batch)

## Summary

The Sprint 1 rework issues (#35, #36, #37, #38, #39, #40) are opened as draft PRs off `main`. This report does not claim acceptance, and no `sprint-1` tag has been created. Tracking issue #2 remains open and is closed only by the architect.

## Rework PRs

- Domain purity: `refactor/38-domain-purity` (PR created for #38)
- Evaluator semantics: `feat/35-evaluator-semantics` (PR created for #35)
- Golden verdict cases: `feat/36-golden-jackson-2` (PR created for #36)
- Ledger named accounts and settlement: `feat/39-ledger-settlement` (PR created for #39)
- JDBC append-only repositories and Testcontainers proof: `feat/37-jdbc-append-only` (PR created for #37)
- Closure docs and notes: `feat/40-sprint-closure` (PR created for #40)

## Verification command

`./gradlew check` on each PR plus CI `build`, `codeql`, and blocking `dependency-review`.

## Acceptance gate

The architect runs the Sprint 1 gate, including a security pass on CodeQL and Dependabot alerts, a blocking dependency-review check, credential grep, and review of each Copilot security finding.
