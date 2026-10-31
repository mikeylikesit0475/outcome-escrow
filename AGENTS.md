# AGENTS.md — rules for the implementing agent (GitHub Copilot CLI)

You are the **developer** on this repository. The plan is [MASTER_SCRUM.md](MASTER_SCRUM.md); the design is [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md); the GitHub procedure is [docs/GITHUB_FLOW.md](docs/GITHUB_FLOW.md). Read all three before the first command of every session. Work one sprint per session, in order.

## Non-negotiables

1. **Every change lands through the flow.** Issue → branch → commits → draft PR → CI green → Copilot review requested → `gh pr ready` → squash merge → branch deleted. The only direct push to `main` is the Sprint 0 bootstrap commit. If a ruleset blocks you, you are doing it right; open a PR.
2. **Evidence over claims.** In every PR body and sprint report paste the actual tail of `./gradlew check` and the output of `gh pr checks` / `gh run list`. Never write "tests pass" without the output. If something fails or was skipped, say so in those words.
3. **No suppression.** Do not add `@SuppressWarnings`, `@Disabled`, `continue-on-error`, lint ignores, CodeQL dismissals or dependency-alert dismissals without a justification comment next to it and a line in the MASTER_SCRUM Decision Log. Fix the cause instead.
4. **Deterministic core.** Nothing in `io.outcomeescrow.domain` may touch the clock, randomness, Spring, or I/O. Money is `long` minor units. Evidence order never changes a verdict.
5. **Synthetic data is labeled.** Every fixture file, seed and report that uses generated data carries `synthetic: true` and says so in prose where a human reads it.
6. **Scope is the Scope Contract.** Anything not listed there goes to the icebox via a Decision Log entry, not into the code.
7. **Verify tool flags before use.** Spring Boot 4.1 starter names, Gradle 9.8 DSL, and `gh` 2.97 flags differ from older training data. Generate the skeleton from start.spring.io, read `gh <cmd> --help`, and check `./gradlew --version` first. If a command or action version does not exist, find the current one and record it; do not guess.
8. **Secrets.** Never commit tokens. `GITHUB_TOKEN` in Actions; `.env.example` documents local variables.

## Session protocol

- Start: `git status` clean on `main`, `git pull`, read the sprint section, open or update the sprint tracking issue, move its Project items to *In progress*.
- During: one issue per branch per PR. Keep PRs under ~400 changed lines where possible; split otherwise.
- End: run the sprint's **Demo** line and **Acceptance** commands yourself, paste outputs into the sprint tracking issue as the sprint report, add the interview story to MASTER_SCRUM §Interview Stories (via a `docs:` PR), close the milestone, push the tag named in the sprint. Stop. Do not start the next sprint.

## Sprint report format (comment on the tracking issue)

```
## Sprint N report
**Done:** #12, #13, #14 (links)
**Not done / deviated:** ... (or "none")
**Demo output:** <fenced block, verbatim>
**Acceptance commands:** <fenced blocks, verbatim>
**Suppressions added:** none | list with reasons
**Decisions needing the architect:** ...
```
