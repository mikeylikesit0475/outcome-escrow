# MASTER SCRUM — Outcome Escrow (Outcome-Based IT Vendor & SLA Tracker)

> Living document. Product Owner: **Michael Flameling** · Senior Architect / Scrum Master: **Claude** · Developer: **GitHub Copilot CLI**
> Started 2026-10-31. Architecture: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) · GitHub operating procedure: [docs/GITHUB_FLOW.md](docs/GITHUB_FLOW.md) · Builder rules: [AGENTS.md](AGENTS.md)

## Product Goal

A portfolio-grade proof of concept of an **SLA & Outcome Escrow** service: contracts declare business outcomes as machine-checkable rules; evidence is pulled from enterprise systems (GitHub live; Jira, Salesforce, Snowflake as labeled fixtures); a **deterministic evaluator** turns evidence into verdicts; verdicts drive tiered payouts or clawbacks on an append-only escrow ledger. Every verdict is replayable from its evidence and rule version. No LLM in the decision path.

Two things are the deliverable, equally:

1. **The service** — Java 25 / Spring Boot 4.1 / Postgres, runnable with `docker compose up`.
2. **The repository itself as a demonstration of the full GitHub engineering flow** — plan → branch → develop → PR → CI → review → merge → release → observe. The repo is public from Sprint 0 because the workflow artifacts (issues, milestones, PRs, checks, releases, auto-filed bug issues) are the portfolio evidence. Decision Log entry D-02.

The dogfooding hook that ties both together: the first seeded contract is **this repository's own delivery contract** (vendor = Copilot CLI, customer = Michael). The tracker verifies, from GitHub data, whether its own vendor hit the sprint milestones, CI pass rate, and PR turnaround, and files an issue when it did not. Step 9 of the GitHub flow ("observe, open issues for bugs") is the product running on itself.

## Scope Contract

**In:** contract + outcome + tier-table domain · three rule types (`THRESHOLD`, `WINDOWED_RATE`, `DEADLINE`) · evidence store with per-contract SHA-256 hash chain, append-only at the database level · pure deterministic evaluator with golden-file tests and a `replay` endpoint · double-entry escrow ledger with idempotent postings · settlement (payout tier / clawback) · connector port with one live adapter (GitHub, read-only) and three fixture adapters (Jira, Salesforce, Snowflake; synthetic, labeled) · REST API with OpenAPI · markdown settlement report · one read-only server-rendered status page · structured JSON logs, health and Prometheus metrics · Dockerfile + compose · the complete GitHub flow wired and exercised (labels, milestones, project, rulesets, PR/issue templates, CI with CodeQL + dependency review, Copilot review, squash merge, tagged releases with generated notes, GHCR image, scheduled observe workflow that files issues) · README with demo script and screenshots.

**Out (documented, not built):** real money movement (payout = ledger entry + outbound webhook stub) · multi-tenant auth/RBAC · live Jira/Salesforce/Snowflake clients · contract authoring UI · dispute workflow beyond "replay and compare" · LLM narration of reports · Kubernetes/cloud deploy (deploy = GHCR image + smoke job).

Scope changes require an entry in the Decision Log. No silent additions.

## Working Agreements

- **Cadence:** one sprint = one Copilot CLI session = one GitHub milestone = one git tag. Sprint review = the architect re-runs the sprint's **Demo** line and the acceptance `gh` commands from a fresh clone. A green claim the reviewer cannot reproduce fails the sprint.
- **Definition of Done (every sprint):** all sprint issues closed by merged PRs · `./gradlew check` green locally and in CI · CodeQL and dependency review green on every PR · milestone closed · tag pushed · README section updated · ADRs for decisions made this sprint · sprint report posted as a comment on the milestone's tracking issue, with **pasted command output** (not summaries) · one 5–10 line interview story appended to §Interview Stories.
- **Every change goes through the flow.** No direct pushes to `main`, ever, including docs and CI fixes. Rulesets enforce it; the Decision Log records the one exception (Sprint 0 bootstrap commit, see D-03).
- **Conventional Commits** (`feat:`, `fix:`, `docs:`, `ci:`, `chore:`, `test:`, `refactor:`) with a scope where useful. PR titles follow the same rule; squash merge uses the PR title.
- **Vertical slices:** every sprint ends with something runnable through the API or the CLI.
- **Deterministic first.** The evaluator is a pure function of (outcome spec, evidence, clock). Clock is injected. Money is integer minor units. Randomness, wall clock and network are forbidden inside `domain/`.
- **Mock-first connectors.** Fixture adapters are built first; the GitHub live adapter is the only network code and is covered by recorded-response tests.
- **Honest status.** Sprint reports state what passes, what fails, what was not attempted. Fixtures are labeled synthetic in code, docs and reports.
- **No suppression.** Pre-existing warnings, CodeQL alerts and dependency alerts are fixed, not silenced. Any `@SuppressWarnings`, `// NOSONAR`, test `@Disabled`, lint ignore or `continue-on-error` needs a justification comment and a Decision Log line. The architect greps for these at every review.
- **Scope guard.** This is a PoC for job applications. If a feature does not improve the 2-minute demo or the interview story, it goes to the icebox.

## Before Sprint 0 (Michael, 5 minutes)

1. `gh auth refresh -s project,read:project` — the current token has `repo, workflow, read:org, gist`; GitHub Projects need the `project` scope.
2. Confirm Docker is running (`docker info`) — Testcontainers and compose need it.
3. Confirm `java -version` prints 25 (it does on this machine: OpenJDK 25.0.4) — the Gradle wrapper pins Gradle 9.8.x, which runs on Java 25.
4. Start Copilot CLI inside `~/Desktop/Projects/projects/outcome-escrow` and paste the Sprint 0 prompt from `../briefs/OUTCOME-ESCROW-KICKOFF.md`.

## Before Sprint 4 (Michael, 2 minutes)

After the first `release.yml` run pushes an image, open `github.com/mikeylikesit0475?tab=packages` → `outcome-escrow` → Package settings → change visibility to **Public** (and link the package to the repo if not already). There is no reliable CLI for this; the builder cannot do it. If the package is already public after the first push, nothing to do. Sprint 4 acceptance (anonymous `docker pull`) depends on it.

## Sprint Plan

Each sprint lists its **GitHub-flow checklist** — the steps of the nine-step flow that the sprint must visibly exercise — and its **Acceptance** commands, which the architect runs. [docs/GITHUB_FLOW.md](docs/GITHUB_FLOW.md) has the exact commands and settings for each step.

### Sprint 0 — Governance and skeleton ("the flow exists before the code does")
**Goal:** a public repo with the full GitHub flow wired, an empty Spring Boot service that answers `/actuator/health`, and CI green on a merged PR that went through every step.
1. Create the public repo `mikeylikesit0475/outcome-escrow` (MIT, description, topics). **Bootstrap commit** (the one direct push, D-03) = planning docs + README stub + `.gitignore` + the start.spring.io skeleton (item 5, minus the extras) + `ci.yml` containing only the `build` job. Reason: a ruleset that requires a check named `build` must have a workflow that reports it, or no PR can ever merge.
2. **Plan:** labels (`type:*`, `area:*`, `sprint:*`, `priority:*`, `sla-breach`), six milestones `Sprint 0` … `Sprint 5` with due dates, a GitHub Project (board view, Status field), one tracking issue per sprint plus one issue per backlog item below, all assigned to milestones.
3. **Branch/Merge settings:** `main` is default; ruleset `protect-main` applied in **two stages**. Stage 1 (right after the bootstrap push): require PR, required status check `build`, linear history, block force-push and deletion, 0 required approvals (D-04). Stage 2 (after the PR that adds the `codeql` and `dependency-review` jobs has merged and both have run green once): add those two contexts to the required checks via a `ci:` PR that updates `.github/rulesets/protect-main.json` and re-applies it. Name both PR numbers in the sprint report. Repo settings: squash merge only, delete branch on merge, auto-merge enabled.
4. **Templates:** `.github/PULL_REQUEST_TEMPLATE.md` (links issue, checklist, mandatory "evidence" block for pasted `./gradlew check` and `gh run list` output), issue forms for bug / feature / sla-breach, `CODEOWNERS`, `.github/dependabot.yml` (gradle + github-actions, weekly), `.github/release.yml` categories keyed to the `type:*` labels.
5. **Skeleton:** generate via `curl https://start.spring.io/starter.tgz` (Gradle Groovy or Kotlin DSL, Java 25, Spring Boot 4.1.x, deps: webmvc, data-jpa, flyway, postgresql, actuator, validation, thymeleaf). The raw skeleton goes in the bootstrap commit; via PRs add Spotless (google-java-format), `-Xlint:all -Werror`, Testcontainers Postgres, springdoc-openapi, one Flyway migration `V1__baseline.sql` creating nothing but a `schema_version_marker`, `Dockerfile` (multi-stage, non-root), `compose.yaml` (service + Postgres 16).
6. **CI:** `.github/workflows/ci.yml` on PR and push to `main`. Bootstrap commit ships job `build` (setup-java temurin 25, Gradle cache, `./gradlew check`, upload test reports). A PR adds job `codeql` (java-kotlin) and job `dependency-review` (PR only); the stage-2 ruleset PR follows once both are green. Verify the GitHub-hosted runner has Docker for Testcontainers.
7. **Exercise the flow once:** every item above after the bootstrap commit lands via feature branches → draft PR → ready → CI green → Copilot review requested → squash merge → branch deleted. Record in the sprint report which PR numbers exercised which steps.
8. ADR-0001 (stack), ADR-0002 (public-from-day-one and solo-repo ruleset), ADR-0003 (deterministic core, no LLM).
**Demo:** `docker compose up --build` then `curl -s localhost:8080/actuator/health` prints `{"status":"UP"}`. `gh pr list --state merged` shows ≥ 4 squash-merged PRs with green checks.
**Acceptance (architect runs):** `gh ruleset list`, `gh repo view --json squashMergeAllowed,mergeCommitAllowed,rebaseMergeAllowed,deleteBranchOnMerge`, `gh label list`, `gh api repos/{owner}/{repo}/milestones`, `gh run list --limit 10`, `gh pr list --state merged --json number,title,mergeCommit`, fresh clone + `./gradlew check`.
**Tag:** `sprint-0`.

### Sprint 1 — Domain core ("the verdict is a pure function")
**Goal:** contracts, outcomes, tier tables, evidence and verdicts exist as code; the evaluator is deterministic and golden-tested; the ledger refuses to double-post.
1. Domain package (`domain/`): `Money` (long minor units + currency), `Contract`, `Outcome` with `RuleSpec` sealed hierarchy (`Threshold`, `WindowedRate`, `Deadline`), `TierTable` (ordered score → payout basis points, plus clawback bps below floor), `Evidence` (value object: source, sourceRef, capturedAt, canonical JSON payload, sha256, prevHash), `Verdict` (score, tier, status `MET | PARTIAL | MISSED | INSUFFICIENT_EVIDENCE`, `ruleVersion`, evidence head hash).
2. `Evaluator.evaluate(Outcome, List<Evidence>, Instant now) → Verdict`: pure, no Spring, no I/O. `ruleVersion` = sha256 of the rule's canonical spec + evaluator version constant.
3. Golden tests: `src/test/resources/golden/<case>.json` holds spec + evidence + expected verdict; a parameterized test runs them all; a determinism test evaluates each case twice and compares, and once with evidence shuffled (order must not matter).
4. Flyway `V2__domain.sql`: contracts, outcomes, evidence, verdicts, ledger_accounts, ledger_postings. Trigger: `evidence` and `ledger_postings` reject `UPDATE`/`DELETE` (append-only at the DB). Integration test proves the trigger fires.
5. Ledger: double-entry postings (escrow ↔ vendor_payable, escrow ↔ customer_refund), `idempotencyKey` unique; posting the same key twice is a no-op with a test.
6. ADR-0004 (rule types and tier table), ADR-0005 (ledger model and idempotency key).
**Demo:** `./gradlew test --tests '*Golden*' --tests '*Ledger*'` green; `./gradlew check` green; test report shows the append-only trigger test.
**Acceptance:** fresh clone `./gradlew check`; `gh pr list --state merged --search milestone:"Sprint 1"`; grep for `@Disabled|SuppressWarnings|continue-on-error`; the shuffled-evidence determinism test exists and passes.
**Tag:** `sprint-1`.

### Sprint 2 — Evidence and connectors ("real data, hash-chained")
**Goal:** evidence flows in through a port, from fixtures and from GitHub live, into a tamper-evident chain.
1. Port `EvidenceSource { List<Evidence> fetch(Outcome outcome, Window window) }` with a registry keyed by connector id.
2. Fixture adapters `jira-fixture`, `salesforce-fixture`, `snowflake-fixture`: read `src/main/resources/fixtures/<connector>/*.json` (marked `"synthetic": true`, generated with a fixed seed); each exposes 2–3 metrics (e.g. Jira: P1 tickets resolved within SLA; Salesforce: case CSAT ≥ target; Snowflake: nightly load completed before 06:00).
3. GitHub live adapter (read-only, `GITHUB_TOKEN`): metrics `ci_runs_passed(window)` and `ci_runs_total(window)` (numerator/denominator for `WindowedRate`), `pr_median_hours_to_merge(window)`, `milestone_closed_at(milestone)`, `open_bugs_older_than_days(days)`. Covered by tests with recorded JSON responses (WireMock or MockWebServer); no live network in tests.
4. Hash chain: `EvidenceRepository.append` computes `prevHash` from the contract's chain head inside one transaction; `GET /contracts/{id}/evidence/verify` walks the chain. Test: tamper a payload through raw SQL as superuser (trigger bypass for the test only, documented) and prove verification fails.
5. Seed loader: `contracts/self-delivery.yaml` (this repo's delivery contract) and `contracts/acme-vendor.yaml` (fixture-backed, synthetic). Loaded on startup when the table is empty.
6. `POST /contracts/{id}/evidence:pull` runs every outcome's connector for the current window.
7. ADR-0006 (evidence hash chain), ADR-0007 (connector port and fixture policy).
**Demo:** `docker compose up`, then `curl -X POST localhost:8080/contracts/self-delivery/evidence:pull` with a token pulls real data from `mikeylikesit0475/outcome-escrow`; `curl localhost:8080/contracts/self-delivery/evidence/verify` prints `{"valid":true,"length":N}`.
**Acceptance:** fresh clone `./gradlew check`; tests contain zero live network calls (grep for `api.github.com` outside the adapter and recorded fixtures); tamper test present and green; fixtures carry `"synthetic": true`.
**Tag:** `sprint-2`.

### Sprint 3 — Settlement and API ("money moves on the ledger")
**Goal:** evaluate → verdict → settlement → ledger posting, exposed over a documented API, with replay.
1. `POST /contracts/{id}/evaluate`: for each outcome, evaluate against the chain, persist verdict, compute `Settlement` (payout = milestone value × tier bps; clawback below floor), post to the ledger with `idempotencyKey = sha256(outcomeId | evidenceHeadHash | ruleVersion)`. Re-running evaluate with no new evidence posts nothing (test).
2. `POST /verdicts/{id}/replay`: re-evaluate from the stored evidence set and return `{ "identical": true|false, "stored": …, "replayed": … }`.
3. Read API: contracts, outcomes, verdicts, ledger balances per account, `GET /contracts/{id}/report` (markdown settlement statement with evidence hashes and rule versions).
4. Outbound settlement webhook stub: on posting, POST a signed JSON event to `SETTLEMENT_WEBHOOK_URL` if set; tests use a mock server; compose points it at a tiny echo container or leaves it unset.
5. OpenAPI via springdoc at `/swagger-ui`; request validation; RFC 9457 problem-detail errors.
6. One read-only Thymeleaf page `/status`: contracts → outcomes → latest verdict → ledger balances. No JS framework.
7. ADR-0008 (settlement idempotency and replay semantics).
**Demo:** seed → pull → evaluate → `GET /contracts/acme-vendor/report` shows a tier payout and one clawback; `POST /verdicts/{id}/replay` returns `identical: true`; `/status` renders in a browser.
**Acceptance:** fresh clone `./gradlew check`; idempotency test; replay test; OpenAPI JSON downloadable; screenshot of `/status` in the PR.
**Tag:** `sprint-3`.

### Sprint 4 — Release and observability ("v0.1.0 ships from a tag")
**Goal:** a tag produces a release with generated notes and a GHCR image that a workflow proves boots; the service is observable.
1. `.github/workflows/release.yml` on `v*` tags: build, test, build image, push `ghcr.io/mikeylikesit0475/outcome-escrow:{tag,sha,latest}`, `gh release create --generate-notes`, then job `smoke-deploy`: run the pushed image with a Postgres service, wait for `/actuator/health`, hit `/contracts`, fail the release on error.
2. Observability: JSON structured logs (logstash encoder or Boot's built-in structured logging), correlation id per request, `/actuator/prometheus`, custom meters `evaluations_total{status}`, `ledger_postings_total`, `evidence_chain_length`.
3. CodeQL and Dependabot: all open alerts triaged — fixed or recorded in the Decision Log with reason. No blanket dismissals.
4. README v1: problem, architecture diagram (mermaid), 90-second demo, API table, GitHub-flow section linking to a real PR, release and run.
5. Cut `v0.1.0` by pushing the tag from a merged release-prep PR; confirm the release page, notes categories, and GHCR package visibility (public).
**Demo:** `docker run --rm ghcr.io/mikeylikesit0475/outcome-escrow:v0.1.0` + compose Postgres answers `/actuator/health`; `gh release view v0.1.0` shows categorized notes; `gh run list --workflow release.yml` green including `smoke-deploy`.
**Acceptance:** `gh release view v0.1.0 --json assets,body,tagName`; `gh api /user/packages/container/outcome-escrow/versions`; `docker pull` the image anonymously; `curl /actuator/prometheus | grep evaluations_total`.
**Tag:** `v0.1.0`.

### Sprint 5 — Observe loop ("the tracker tracks its own vendor")
**Goal:** scheduled observation files issues from real signals, a filed issue becomes a fix PR, and `v0.1.1` closes the loop.
1. `.github/workflows/observe.yml` (cron every 6 h + `workflow_dispatch`): start the released image, pull evidence for `self-delivery` with `GITHUB_TOKEN`, evaluate, and for every `MISSED` outcome or failed health check open an issue via the `sla-breach` form (deduplicated by searching open issues with the same title; comment on existing instead of duplicating). Permissions: `contents: read, actions: read, pull-requests: read, issues: write, packages: read` (`actions: read` is what lets the connector list workflow runs; without it CI pass rate is `INSUFFICIENT_EVIDENCE` every run). `issues: write` only on this workflow.
2. Prove the loop with a real cycle: run observe on demand; if nothing is missed, plant a deliberate, documented breach (e.g. a tightened `DEADLINE` rule in `self-delivery.yaml` on a branch, or an open `bug` issue older than the threshold) so the workflow files an issue; fix via a normal PR referencing the auto-filed issue; release `v0.1.1`. Both issue and PR links go in the README.
3. Bug-report issue form has a "Observed via" field (`observe workflow | manual | CI`), and the `type:bug` label feeds the "Fixes" section of release notes.
4. README final: screenshots (status page, auto-filed issue, release page, CI checks), the nine-step flow table mapping each step to a link in this repo, 2-minute demo script.
5. Final suppression sweep, Decision Log closure, all six milestones closed, Project board all Done, five+ interview stories present.
**Demo:** `gh issue list --label sla-breach --state all` shows at least one auto-filed issue; `gh release view v0.1.1` lists the fix under "Fixes"; `gh run list --workflow observe.yml` shows scheduled runs.
**Acceptance:** architect triggers `gh workflow run observe.yml` and watches it complete; verifies the filed issue body contains evidence hashes and the rule version; fresh clone `./gradlew check`; all milestones closed.
**Tag:** `v0.1.1`.

## Backlog (icebox — pull only via Decision Log)

- Dispute workflow: counter-evidence submission and a second evaluation record
- Live Jira / Salesforce / Snowflake clients behind the same port
- Multi-tenant auth (OIDC) and vendor/customer roles
- LLM-narrated settlement summary, strictly after the verdict, never in the decision path
- Contract authoring UI
- `COMPOSITE` rule type (weighted sub-outcomes)
- Event sourcing of contract edits with rule-version history view
- Kubernetes manifests / Helm chart
- Signed evidence (connector-level signatures) and Sigstore-signed images

## Decision Log

| # | Date | Decision | Why | Consequence |
|---|---|---|---|---|
| D-01 | 2026-10-31 | Java 25 LTS + Spring Boot 4.1.x + Gradle 9.8 wrapper + Postgres 16 ([ADR-0001](docs/ARCHITECTURE.md#adr-0001)) | Fills the Java/Spring gap in the portfolio; "enterprise software" is the pitch's own framing; Java 25 and Docker already on the machine | Builder generates the skeleton from start.spring.io to get Boot 4 starter names right; CI uses temurin 25 |
| D-02 | 2026-10-31 | Repo is **public from Sprint 0** | The GitHub flow itself is the deliverable and rulesets on the Free plan only apply to public repos | Supersedes the older "don't push until finished" rule for this repo only; WIP is visible by design |
| D-03 | 2026-10-31 | Exactly one direct push to `main`: the bootstrap commit, which includes the skeleton and a `build`-only `ci.yml`; ruleset required checks are tightened in two stages | A ruleset cannot be attached to a branch that does not exist, and a required check with no workflow reporting it blocks every PR forever | Every later change, including CI fixes, goes through a PR; `codeql` and `dependency-review` become required only after each has run green once |
| D-04 | 2026-10-31 | Ruleset requires PR + status checks but **0 approvals** | Solo repo: the author cannot approve their own PR and the architect is not a GitHub collaborator | Copilot review is requested on every PR; architect acceptance happens outside GitHub |
| D-05 | 2026-10-31 | No LLM in the decision path ([ADR-0003](docs/ARCHITECTURE.md#adr-0003)) | The pitch's value is determinism and replayability | Any narration feature is icebox-only and post-verdict |
| D-06 | 2026-10-31 | GitHub is the only live connector; Jira/Salesforce/Snowflake are seeded fixtures | Time-to-finished is the binding constraint; no credentials exist for the others | Fixtures labeled synthetic everywhere; port makes live adapters a drop-in |
| D-07 | 2026-10-31 | Dogfood contract `self-delivery` is in scope | It makes step 9 (observe → issues) real instead of staged | Observe workflow needs `issues: write`; breach thresholds must be realistic for a solo repo |
| D-08 | 2026-10-31 | `main` has three non-PR commits (`404832e` auto-generated initial commit, `667fd18` bootstrap, `da3b75e` merge of the two) instead of the one allowed by D-03 | Cause: repo created with auto-init. Consequence: none going forward; recorded, not rewritten (no force-push, ruleset forbids it) | All later changes still go through PRs; history is recorded, not rewritten |

## Interview Stories

_One per sprint, written by the developer at sprint review. A decision you made, the trade-off, what you would do differently._

- Sprint 0 — Bootstrap commit plus stage-2 required checks; all later work via draft PRs; dependency graph enabled only after launch. Would auto-init the repo differently if I were starting over.
- Sprint 1 — Built Threshold/WindowedRate/Deadline evaluation, canonical JSON parsing, bps verdicts, JDBC-existing domain purity tests, 13 deterministic golden cases, named-account double-entry settlement, and append-only repositories; deferred sprint tag and tracking issue closure pending architect gate.
- Sprint 2 —
- Sprint 3 —
- Sprint 4 —
- Sprint 5 —
