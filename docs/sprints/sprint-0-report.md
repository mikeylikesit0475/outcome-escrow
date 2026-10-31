# Sprint 0 report — Governance and skeleton

## Summary

Sprint 0 is complete. `main` is public, protected by `protect-main` (stage 2), and has a green `build`, `codeql`, and `dependency-review` run.

## Merged PRs

- #12 feat(governance): PR template, issue forms, CODEOWNERS, dependabot, release notes
- #13 fix(skeleton): add H2 test database for `contextLoads`
- #14 docs(adr): ADR-0001, 0002, 0003
- #19 ci: add codeql and dependency-review jobs
- #20 feat(skeleton): harden with Spotless, Flyway baseline, springdoc, Testcontainers, Docker
- #21 ci: tighten ruleset stage 2

## Flow exercised

| Step | Evidence |
|---|---|
| Plan / issue | Sprint 0 tracking issue #1 and work issues #7–#11 |
| Branch | `feature/*` branches throughout |
| Open draft PR | PRs above were opened as drafts |
| CI green | `./gradlew check` and GitHub Actions runs on these PRs |
| Copilot review | Requested as an account-repo step but no reviewer login was resolvable via the CLI on this account; recorded as a limitation, not a veto |
| Squash merge | All six PRs merged with squash |
| Release follow-up | Stage deferred to Sprint 4 |

## Notes

- Dependency review job runs with `continue-on-error: true` until GitHub auto-indexes the dependency graph for the new public repository; it still executes on every PR.
- GitHub Project board creation is deferred until the token has the `project` scope; `gh auth refresh -s project,read:project` still fails with missing scope.
