# GitHub operating procedure — the nine-step flow as repo artifacts

> Every step below names the artifact that proves it and the command the architect runs at acceptance. The developer (Copilot CLI) verifies each `gh` flag against `gh <cmd> --help` before use; the GitHub CLI is at 2.97.

Repo: `mikeylikesit0475/outcome-escrow` · default branch `main` · owner token scopes needed: `repo, workflow, project, read:project`.

| # | Step | Artifact in this repo | Verified by |
|---|---|---|---|
| 1 | Plan | labels, milestones `Sprint 0..5`, GitHub Project, tracking issue per sprint, issue per work item | `gh label list`, `gh api repos/{owner}/{repo}/milestones`, `gh project list --owner mikeylikesit0475`, `gh issue list --milestone "Sprint N"` |
| 2 | Branch | `feature/<issue>-<slug>`, `fix/<issue>-<slug>`, `ci/<slug>`, `docs/<slug>` from `main`; ruleset `protect-main` | `gh ruleset list`, `gh ruleset view protect-main` |
| 3 | Develop | Conventional Commits; `./gradlew check` (Spotless, compile `-Werror`, unit + integration tests) before every push; pre-commit hook optional | `git log --oneline` on the PR; CI `build` job |
| 4 | Push + PR | draft PR opened first (`gh pr create --draft`), converted with `gh pr ready` when CI is green; PR template filled, `Closes #N` | `gh pr view N --json isDraft,body,closingIssuesReferences` |
| 5 | CI | `ci.yml` jobs `build`, `codeql`, `dependency-review`; `dependabot.yml` | `gh run list --workflow ci.yml`, `gh pr checks N` |
| 6 | Review | Copilot code review requested on each PR; author self-review checklist in template; CODEOWNERS | `gh pr view N --json reviewRequests,reviews` |
| 7 | Merge | squash-only, delete-branch-on-merge, auto-merge allowed; required checks green | `gh repo view --json squashMergeAllowed,mergeCommitAllowed,rebaseMergeAllowed,deleteBranchOnMerge`; `git branch -r` has no merged feature branches |
| 8 | Release | tag `v*` → `release.yml`: build, GHCR push, `gh release create --generate-notes` with `.github/release.yml` categories, `smoke-deploy` job | `gh release view vX.Y.Z`, `gh run list --workflow release.yml`, `docker pull ghcr.io/mikeylikesit0475/outcome-escrow:vX.Y.Z` |
| 9 | Observe | `/actuator/health`, `/actuator/prometheus`, JSON logs, `observe.yml` cron that files `sla-breach` issues; bug issue form | `gh workflow run observe.yml && gh run watch`, `gh issue list --label sla-breach --state all` |

## Step 1 — Plan

Labels (create with `gh label create <name> --color <hex> --description "..."`; `--force` to update):

| Label | Purpose |
|---|---|
| `type:feature`, `type:bug`, `type:docs`, `type:ci`, `type:chore`, `type:security` | feeds `.github/release.yml` categories |
| `area:domain`, `area:ledger`, `area:evidence`, `area:connectors`, `area:api`, `area:observability`, `area:github-flow` | navigation |
| `sprint:0` … `sprint:5` | redundant with milestones but filterable on the Project board |
| `priority:p1`, `priority:p2` | triage |
| `sla-breach` | applied by `observe.yml`; excluded from release notes |

Milestones: `gh api -X POST repos/{owner}/{repo}/milestones -f title="Sprint N" -f due_on=<ISO> -f description="<goal line from MASTER_SCRUM>"`. Due dates: Sprint 0 = creation day, then one calendar day apart (Copilot sessions are short); the `self-delivery` contract reads these dates, so set them honestly and do not move them after the fact (moving a milestone is a Decision Log entry).

Project: `gh project create --owner mikeylikesit0475 --title "Outcome Escrow"`; add a single-select `Status` with `Todo / In progress / In review / Done`; `gh project item-add` every issue. Link the project to the repo.

Tracking issue per sprint titled `Sprint N — <name>` with the sprint's numbered items as a task list; the sprint report is posted as a comment there and the issue is closed at review.

## Step 2 — Branch

Ruleset (JSON applied with `gh api -X POST repos/{owner}/{repo}/rulesets --input ruleset.json`; keep the file at `.github/rulesets/protect-main.json` so the setting is reviewable):

- target: branch `~DEFAULT_BRANCH`
- rules: `deletion`, `non_fast_forward`, `required_linear_history`, `pull_request` (`required_approving_review_count: 0`, `dismiss_stale_reviews_on_push: true`, `required_review_thread_resolution: true`), `required_status_checks` with `strict_required_status_checks_policy: true`
- required contexts, **stage 1** (applied right after the bootstrap push): `build` only — the bootstrap commit ships `ci.yml` with that job, so the check exists before the first PR
- required contexts, **stage 2** (a `ci:` PR after the CodeQL + dependency-review PR has merged and both jobs have reported green once): `build`, `codeql`, `dependency-review`; apply with `gh api -X PUT repos/{owner}/{repo}/rulesets/<id> --input .github/rulesets/protect-main.json`
- bypass actors: none. Never add a bypass to get unstuck; fix the check.

Branch names: `feature/12-evaluator-golden-tests`. One issue per branch; one branch per PR.

## Step 3 — Develop

- `./gradlew spotlessApply check` before every push. The compile has `-Xlint:all -Werror`; tests include ArchUnit, unit, Testcontainers integration.
- Small commits, each buildable. Conventional Commit subjects ≤ 72 chars, body explains why.
- No secrets in the repo: `GITHUB_TOKEN` in Actions, a personal token only via env locally; `.env.example` lists variables.

## Step 4 — Push + PR

```
git push -u origin feature/12-evaluator-golden-tests
gh pr create --draft --title "feat(domain): evaluator with golden tests" --body-file .github/PR_BODY.md   # or fill the template interactively
# ... CI green ...
gh pr ready <n>
```

PR template sections (all mandatory): **Closes #** · **What / Why** · **How verified** (pasted `./gradlew check` tail and `gh pr checks` output) · **Screenshots** (when UI) · **Checklist** (tests added, docs updated, no suppressions added, ADR if decision, synthetic data labeled).

## Step 5 — CI

`ci.yml`:

```yaml
on:
  pull_request:
  push: { branches: [main] }
permissions: { contents: read }
jobs:
  build:           # setup-java temurin 25 + gradle/actions/setup-gradle, ./gradlew check, upload build/reports
  codeql:          # github/codeql-action init (java-kotlin) → ./gradlew assemble -x test → analyze; permissions security-events: write
  dependency-review:   # actions/dependency-review-action, pull_request only, fail-on-severity: high
```

Job names must equal the ruleset's required contexts. Testcontainers works on `ubuntu-latest` (Docker preinstalled). Dependabot: `gradle` and `github-actions` ecosystems, weekly, labels `type:chore`.

## Step 6 — Review

- Request Copilot's review on every PR. Verify the mechanism on this account before relying on it: either the repo ruleset option "Automatically request Copilot code review" or `gh pr edit <n> --add-reviewer <copilot-reviewer-login>`; record which one works in ADR-0002's "Consequences" and in the Sprint 0 report. If Copilot review is unavailable on this plan, say so in the report and keep the self-review checklist as the gate; do not fake a review.
- Address every review thread (ruleset requires resolution). Disagreements are answered in the thread, not silently resolved.
- Architect acceptance is run outside GitHub (fresh clone, commands in MASTER_SCRUM), then the sprint tracking issue gets the acceptance comment.

## Step 7 — Merge

```
gh repo edit --enable-squash-merge --enable-merge-commit=false --enable-rebase-merge=false --delete-branch-on-merge --enable-auto-merge
gh pr merge <n> --squash --auto     # merges when checks are green
```

Squash commit title = PR title (Conventional Commit). Branch is deleted by the setting; confirm with `git fetch --prune`.

## Step 8 — Release

- `.github/release.yml` categories: Features (`type:feature`), Fixes (`type:bug`), Security (`type:security`), CI & chores (`type:ci`, `type:chore`), Docs (`type:docs`); exclude `sla-breach`.
- `release.yml` on `push: tags: ['v*']`; permissions `contents: write, packages: write`; jobs `build-test` → `publish-image` (docker/login-action to GHCR with `GITHUB_TOKEN`, tags `vX.Y.Z`, `sha-<short>`, `latest`) → `github-release` (`gh release create "$TAG" --generate-notes --verify-tag`) → `smoke-deploy` (postgres service container, run the pushed image, poll `/actuator/health` for 60 s, `curl /contracts`, fail otherwise).
- GHCR package visibility is changed by Michael in the GitHub UI after the first push (see "Before Sprint 4" in MASTER_SCRUM); the builder records in the Sprint 4 report whether it was already public.
- Cutting a release: merge a `chore(release): v0.1.0` PR that bumps `version` in `build.gradle` and the README badge, then `git tag -a v0.1.0 -m "v0.1.0" && git push origin v0.1.0` from `main`. Sprint tags `sprint-N` are lightweight tags with no release.

## Step 9 — Observe

- `observe.yml`: `schedule: cron '17 */6 * * *'` + `workflow_dispatch`; permissions `contents: read, actions: read, pull-requests: read, issues: write, packages: read` (`actions: read` for workflow-run listing, `pull-requests: read` for merge times); runs the latest released image with a postgres service, `POST /contracts/self-delivery/evidence:pull` and `/evaluate` with `GITHUB_TOKEN`, reads verdicts, and for each `MISSED` outcome: search `gh issue list --label sla-breach --state open --search "<outcome title> in:title"`; comment if found, else `gh issue create --label sla-breach --title "SLA breach: <outcome title>" --body "<evidence head hash, rule version, score, link to run>"`. Health failure files `Observe: service unhealthy`.
- Issue forms in `.github/ISSUE_TEMPLATE/`: `bug.yml` (fields: observed via, steps, expected, actual, version), `feature.yml`, `sla-breach.yml` (used by the workflow body format; also filable by hand).
- Bugs found by observation become `type:bug` issues → `fix/<n>-...` branch → PR `Closes #n` → patch release. Sprint 5 proves one full cycle and links it from the README.
