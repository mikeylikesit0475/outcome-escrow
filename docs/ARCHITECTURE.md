# Architecture — Outcome Escrow

> v1, 2026-10-31. Binding for Sprints 0–5. Changes go through the Decision Log in [MASTER_SCRUM.md](../MASTER_SCRUM.md).

## 1. What the system does

An outcome-based contract says: *"Vendor is paid X when outcome O is demonstrably achieved."* This service makes "demonstrably" mechanical.

```
contract.yaml ──► Outcome specs (rule + tier table + window)
                         │
   connectors ──► Evidence (immutable, hash-chained) ──► Evaluator (pure) ──► Verdict
                                                                             │
                                                     Settlement (payout tier / clawback)
                                                                             │
                                              Ledger postings (double-entry, idempotent) ──► webhook stub
```

Three guarantees the design is built around:

1. **Determinism.** `Evaluator.evaluate(outcome, evidence, now)` is a pure function. Same inputs, same verdict, forever. `ruleVersion` pins the rule text and evaluator version so a verdict can be replayed years later.
2. **Tamper evidence.** Evidence is append-only at the database (trigger), and each record carries `sha256(canonicalPayload)` and `prevHash`, forming a chain per contract. Verification walks the chain.
3. **Idempotent money.** Every ledger posting has an idempotency key derived from (outcome, evidence head hash, rule version). Re-evaluating without new evidence cannot post twice.

## 2. Domain model

| Type | Fields (essentials) | Notes |
|---|---|---|
| `Money` | `long minorUnits`, `String currency` | Never `double`. Arithmetic via explicit methods; basis points via integer math with `RoundingMode.HALF_EVEN`. |
| `Contract` | `id`, `vendor`, `customer`, `totalValue: Money`, `period`, `outcomes[]` | Loaded from YAML seeds in v0; CRUD is icebox. |
| `Outcome` | `id`, `contractId`, `title`, `value: Money`, `rule: RuleSpec`, `tiers: TierTable`, `window: Window`, `connector`, `metric`, `params` | `value` is the slice of the contract at stake. |
| `RuleSpec` (sealed) | `Threshold(op, target)` · `WindowedRate(numeratorMetric, denominatorMetric, op, target)` · `Deadline(eventMetric, dueAt)` | Closed set. New types = new ADR. |
| `TierTable` | ordered `Tier(minScore, payoutBps)[]` descending, `clawbackBps` | Score is `0..10000` (bps) computed by the rule; highest tier whose `minScore ≤ score` wins; below the lowest tier, clawback applies. |
| `Window` | `Duration lookback` or `Instant from/to` | Resolved against the injected clock. |
| `Evidence` | `id`, `contractId`, `outcomeId`, `connector`, `sourceRef`, `capturedAt`, `payload` (canonical JSON), `payloadHash`, `prevHash`, `synthetic: boolean` | Canonical JSON = sorted keys, no whitespace, UTF-8. |
| `Verdict` | `id`, `outcomeId`, `evaluatedAt`, `score`, `status`, `tier`, `ruleVersion`, `evidenceHeadHash`, `evidenceCount`, `explanation` | `status ∈ {MET, PARTIAL, MISSED, INSUFFICIENT_EVIDENCE}`. `explanation` is a deterministic string built by the rule, not prose. |
| `Settlement` | `verdictId`, `kind ∈ {PAYOUT, CLAWBACK, NONE}`, `amount: Money` | Derived, not stored separately; postings are the record. |
| `LedgerAccount` | `ESCROW`, `VENDOR_PAYABLE`, `CUSTOMER_REFUND` per contract | Balances are sums over postings; never stored. |
| `LedgerPosting` | `id`, `idempotencyKey`, `debit`, `credit`, `amount`, `verdictId`, `postedAt` | Unique on `idempotencyKey`. Append-only. |

### Rule semantics (v0)

- `Threshold`: evidence payload supplies one numeric `value`; score = `10000` if `op(value, target)` holds, else scaled distance (documented per op) clamped to `0..10000`. Multiple evidence records in the window: the **latest by `capturedAt`** wins; ties broken by `payloadHash` (deterministic).
- `WindowedRate`: sum numerator and denominator over the window; score = `numerator / denominator` in bps; `INSUFFICIENT_EVIDENCE` if denominator is `0`.
- `Deadline`: evidence supplies `eventAt`; score = `10000` if `eventAt ≤ dueAt`; else `0`; `INSUFFICIENT_EVIDENCE` if no event and `now < dueAt`; `MISSED` if no event and `now ≥ dueAt`.

Evidence order never affects the result. A test shuffles evidence and asserts equality.

## 3. Packages

```
io.outcomeescrow
├── domain/        pure Java: Money, Contract, Outcome, RuleSpec, TierTable, Evidence, Verdict, Evaluator, Settlement
├── ledger/        LedgerService, postings, idempotency
├── evidence/      EvidenceSource port, registry, hash chain repository, canonical JSON
├── connectors/
│   ├── github/    live read-only adapter (GITHUB_TOKEN), recorded-response tests
│   ├── jira/      fixture adapter (synthetic)
│   ├── salesforce/ fixture adapter (synthetic)
│   └── snowflake/ fixture adapter (synthetic)
├── api/           REST controllers, problem details, OpenAPI, /status page
├── seed/          YAML contract loader
└── config/        clock, metrics, JSON, security headers
```

Rule: `domain/` depends on nothing but the JDK. ArchUnit test enforces it.

## 4. Persistence

Postgres 16, Flyway migrations, Spring Data JPA for the mutable tables, plain `JdbcClient` for evidence and postings (append-only tables are not JPA entities; nothing should be able to "save" an edit).

Migration `V2__domain.sql` installs:

```sql
create or replace function reject_mutation() returns trigger as $$
begin raise exception 'append-only table: % on %', tg_op, tg_table_name; end $$ language plpgsql;
create trigger evidence_append_only before update or delete on evidence for each row execute function reject_mutation();
create trigger postings_append_only before update or delete on ledger_postings for each row execute function reject_mutation();
```

Integration tests use Testcontainers Postgres. The tamper test (Sprint 2) disables the trigger inside a test-only transaction (`alter table evidence disable trigger ...`) to corrupt a row and prove `verify` catches it; this is the only place the trigger is bypassed and it is commented as such.

## 5. Connectors

```java
public interface EvidenceSource {
    String id();                                   // "github", "jira-fixture", ...
    boolean synthetic();                           // true for fixtures
    List<Evidence> fetch(Outcome outcome, Window window, Instant now);
}
```

| Connector | Metrics | Source |
|---|---|---|
| `github` (live) | `ci_runs_passed`, `ci_runs_total` (workflow runs on `main`, needs `actions: read`), `pr_median_hours_to_merge`, `milestone_closed_at`, `open_bugs_older_than_days` | REST v3 via `RestClient`, token from env, read-only, paginated, rate-limit aware |
| `jira-fixture` | `p1_resolved_within_sla_rate`, `backlog_age_p90_days` | `fixtures/jira/*.json`, seed 42 |
| `salesforce-fixture` | `case_csat_avg`, `case_first_response_hours_p50` | `fixtures/salesforce/*.json`, seed 42 |
| `snowflake-fixture` | `nightly_load_completed_at`, `row_count_delta_pct` | `fixtures/snowflake/*.json`, seed 42 |

Fixture files carry `"synthetic": true` and the generator script lives in `tools/gen-fixtures/` so they are reproducible.

## 6. API (v0)

| Method | Path | Purpose |
|---|---|---|
| GET | `/contracts` · `/contracts/{id}` | read |
| POST | `/contracts/{id}/evidence:pull` | run connectors for all outcomes |
| GET | `/contracts/{id}/evidence` · `/contracts/{id}/evidence/verify` | list / walk hash chain |
| POST | `/contracts/{id}/evaluate` | verdicts + settlement + postings |
| GET | `/contracts/{id}/verdicts` · `/verdicts/{id}` | read |
| POST | `/verdicts/{id}/replay` | re-evaluate from stored evidence, compare |
| GET | `/contracts/{id}/ledger` | balances per account + postings |
| GET | `/contracts/{id}/report` | markdown settlement statement |
| GET | `/status` | server-rendered read-only page |
| GET | `/actuator/health` · `/actuator/prometheus` | observability |
| GET | `/v3/api-docs` · `/swagger-ui` | OpenAPI |

Errors are RFC 9457 problem details. No auth in v0 (single-tenant PoC, documented in README under "What this is not").

## 7. Observability

- Structured JSON logs with `requestId`, `contractId`, `outcomeId` where applicable.
- Micrometer meters: `evaluations_total{status}`, `ledger_postings_total{kind}`, `evidence_chain_length{contract}`, `connector_fetch_seconds{connector}`.
- `/actuator/health` includes a DB check and a `chainIntegrity` indicator (last verify result, cached).
- The `observe.yml` workflow is the external observer: it runs the released image against the live `self-delivery` contract and files issues on `MISSED` or unhealthy.

## 8. Seeds

`contracts/self-delivery.yaml` (real data, GitHub connector):

| Outcome | Rule | Tiers |
|---|---|---|
| Sprint milestones closed by due date | `Deadline(milestone_closed_at ≤ due)` per milestone | 100% / clawback 20% |
| CI pass rate on `main`, trailing 30 d | `WindowedRate(ci_runs_passed / ci_runs_total ≥ 0.95)` | ≥95%: 100 · ≥90%: 60 · else clawback 10% |
| PR median hours to merge, trailing 30 d | `Threshold(≤ 72)` | ≤72: 100 · ≤120: 50 · else 0 |
| Open `type:bug` issues older than 7 d | `Threshold(== 0)` | 0: 100 · else 0, clawback 5% |

`contracts/acme-vendor.yaml` (synthetic, fixture connectors): one Jira, one Salesforce, one Snowflake outcome, tuned so the demo shows one payout at full tier, one partial tier, and one clawback.

## 9. ADRs

### ADR-0001 — Stack: Java 25, Spring Boot 4.1, Gradle 9.8, Postgres 16
**Context.** Portfolio lacks a Java/Spring repo; the pitch is enterprise software; Java 25 LTS and Docker are installed; Gradle 9.8 supports Java 25.
**Decision.** Skeleton generated from start.spring.io with Boot 4.1.x; Boot's jackson-bom (2.x, `com.fasterxml`) is the single JSON library used outside `domain/`, and `domain/` keeps the JDK-only `CanonicalJson` parser. No separate `tools.jackson` dependency is introduced. Gradle wrapper pinned; Spotless + `-Werror`; Testcontainers for integration tests; springdoc for OpenAPI.
**Consequences.** Builder must not hand-write Boot 3-era starter names and must not add a second JSON binding library. CI uses `actions/setup-java` temurin 25 with Gradle caching.

### ADR-0002 — Public repository and solo-repo ruleset from Sprint 0
**Context.** The GitHub flow is a deliverable; rulesets on GitHub Free apply to public repos only; one human, one bot.
**Decision.** Public from the first commit; ruleset `protect-main` requires PR + checks + linear history, 0 approvals; Copilot review requested on every PR; architect acceptance outside GitHub.
**Consequences.** WIP is visible; the README states the repo is a worked example of the flow.

### ADR-0003 — Deterministic core, no LLM in the decision path
**Context.** The product promise is "real data, not arguments".
**Decision.** `domain/` is pure Java with injected clock; evaluator golden-tested; narration (if ever) is post-verdict and icebox.
**Consequences.** ArchUnit enforces dependency rules; any nondeterminism is a bug.

### ADR-0004 — Rule types and tier table (Sprint 1, builder writes)
### ADR-0005 — Ledger model and idempotency key (Sprint 1, builder writes)
### ADR-0006 — Evidence hash chain (Sprint 2, builder writes)
### ADR-0007 — Connector port and fixture policy (Sprint 2, builder writes)
### ADR-0008 — Settlement idempotency and replay semantics (Sprint 3, builder writes)

ADR files live in `docs/adr/NNNN-title.md`; this section keeps the index.
