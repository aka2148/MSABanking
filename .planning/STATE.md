---
gsd_state_version: 1.0
milestone: v1.0
milestone_name: milestone
status: executing
stopped_at: None
last_updated: "2026-09-22T14:38:54.706Z"
last_activity: 2026-09-22
progress:
  total_phases: 6
  completed_phases: 5
  total_plans: 21
  completed_plans: 19
  percent: 90
---

# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-09-18)

**Core value:** Students must be able to demonstrate a complete, understandable fixed-deposit workflow from product selection through booking, funding, interest calculation, maturity, and closure using the existing application.
**Current focus:** Phase 06 — angular-fd-frontend

## Current Position

Phase: 06 (angular-fd-frontend)
Plan: 02 complete; next 03
Status: In Progress
Last activity: 2026-09-22

Progress: [█████████░] 90%

## Performance Metrics

**Velocity:**

- Total plans completed: 12
- Average duration: 9 min
- Total execution time: 0.5 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 1 | 3 | 3 | 9 min |
| 2 | 1 | 18 min | 18 min |
| 3 | 2 | 27 min | 14 min |
| 4 | 4 | 49 min | 12 min |
| 5 | 5 | 5 | 9 min |
| 6 | 0 | TBD | N/A |
| 02 | 3 | - | - |

**Recent Trend:**

- Last 5 plans: 01-01 (12 min), 01-02 (4 min), 01-03 (12 min)
- Trend: Established

| Phase 01 P03 | 12 min | 3 tasks | 7 files |
| Phase 02 P01 | 18 min | 2 tasks | 18 files |
| Phase 02 P02 | 12min | 2 tasks | 14 files |
| Phase 02 P03 | 25 min | 2 tasks | 10 files |
| Phase 05 P02 | 6min | 2 tasks | 8 files |
| Phase 05 P05-03 | 20min | 2 tasks | 10 files |
| Phase 06 P01 | 25 | 2 tasks | 9 files |
| Phase 06 P02 | 16 | 2 tasks | 11 files |

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- The milestone is a college-lab FD demonstration, not a production banking platform.
- Existing Spring Boot/Angular modules, services, entities, repositories, calculators, authentication, migrations, `/fd/v1`, and legacy endpoints are compatibility assets.
- REST/Swagger/Postman remains the backend acceptance surface; Phase 6 adds an Angular FD demonstration UI as a separate frontend phase.
- Keep lifecycle, decimal money, PostgreSQL/Flyway persistence, basic idempotency, ownership/role checks, and focused tests; avoid enterprise workflow and payment infrastructure.
- Policy authority: academic fixed policy, not a general production policy engine.
- Rate unit: decimal fraction (0.0650 represents 6.50% annually).
- Day count: Actual/365.
- Date interval: opening date included, maturity date excluded.
- Business timezone: UTC.
- Currency/display scale: 2 decimals.
- Rounding: HALF_UP.
- Actor authority: local /api/auth identity.
- Product/rate authority: existing normalized server-owned product/rule tables.
- V6 performs diagnostic legacy-data preflight before applying lifecycle checks and unique indexes.
- The legacy fa_version mapping remains for compatibility while fa_row_version is the numeric JPA lifecycle version.
- Operation idempotency status remains a constrained string instead of a global enum.
- Typed authenticated /fd/v1 account boundary retains explicit 501 placeholders until later lifecycle services
- PostgreSQL 16 Alpine Testcontainers gate uses Boot-managed test dependencies and generated credentials
- Testcontainers schema tests skip only when Docker is unavailable locally
- V7 stores an independent academic-v1 policy snapshot and diagnostic local-reference prerequisites.
- Normalized terms resolution returns local fd_*_ref identities and fails closed for missing or ambiguous authority data.
- Authenticated booking derives fd_user_ref from local JWT username
- Booking uses branch 000 and simple Actual/365 BigDecimal academic-v1 estimate
- Funding uses an authenticated exact-principal pessimistic-lock transaction with an internal DEPOSIT and the Phase 1 FUND transition.
- Funding responses explicitly describe internal academic recordkeeping without claiming external payment settlement.
- 05-01 guard foundation: durable idempotency claim/replay (same key+hash replays, mismatch 409 with existing-record reference); ownership via FdActorResolver holder-match or ROLE_BANK_OFFICER/ROLE_ADMIN else 404; duplicate errors map to 409; CORS allows Idempotency-Key.
- 05-02 service wiring: booking/funding/accrual/maturity claim guard scopes and replay the stored aggregate with zero new rows; authorities for requireOwned come from SecurityContextHolder (staff bypass live, controller still key-less); key-less double funding/maturity 409 referencing the existing record; key-less booking still creates a new pending FD.
- 05-02: authorities for requireOwned come from SecurityContextHolder so staff bypass works without changing service signatures ahead of Plan 03
- 05-03 closure/history idempotency: same-key replays rebuild stored DTOs with zero new rows; key-less CLOSED repeat is 409 referencing the existing settlement
- 05-03 controller contract: seven FD mutating endpoints forward Idempotency-Key and admit CUSTOMER/BANK_OFFICER/ADMIN; cross-customer reads 404 via FdAccessPolicy
- 05-04 unit layer: 4 new test classes (guard replay/mismatch, ownership matrix, 409/409/422/404/400 mapping, gap-only boundaries incl. one-day 17.81 and 100-point penalty floor); 37/37 green with existing domain tests untouched, no Spring or Docker
- 05-05 integration gate: FdOwnershipContractTest 5/5 green on H2 without Docker (404 ownership probes x4, staff BANK_OFFICER read, 409/422/400 contract, byte-identical fund replay); FdPhase5ProtectionIT created per plan with verbatim Docker-skip guard (2 skipped, exit 0 without Docker); full mvn test BUILD SUCCESS — 132 run, 0 failures, 5 pre-existing Docker skips
- getAccount read path uses findById (FOR UPDATE outside TX 500s on H2); write-path locking untouched
- Anonymous FD reads pin 403 per SecurityFlowTest; 401 pinned on failed login, not on anonymous GET
- Test JVMs require process-local JWT_SECRET >= 64 bytes (HS512); never stored in repo
- Preserved existing token localStorage key and auth payloads while adding role/session state.
- Kept active FD idempotency keys in FdService memory with explicit submission lifecycle.
- Scoped Angular FD transport to implemented /fd/v1 endpoints only.
- FD landing and booking keep product and final terms server-authoritative; browser performs UX bounds validation only.
- FD booking retries retain the existing FdService-managed idempotency key.
- Protected lazy route wiring remains owned by Plan 06-04.

### Pending Todos

None yet.

### Blockers/Concerns

- Phase 1 must verify the actual PostgreSQL schema and the smallest additive migration set before lifecycle code depends on it.
- Product-term authority, exact calculation convention, and premature-closure rule availability must be made explicit during the relevant phase rather than guessed.
- Existing `/fd/v1` placeholders and legacy FD methods need compatibility checks so working non-FD behavior is not disrupted.

## Deferred Items

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| Scope | Angular FD screens beyond the current shell | Promoted to Phase 6: Angular FD Frontend | 2026-09-22 |
| Scope | Real payment rails, workflow engines, distributed locks, outbox/reconciliation infrastructure | Deferred as disproportionate to the lab | 2026-09-18 |
| Scope | Recurring payouts, renewal, partial withdrawals, broad holder/nominee workflows, tax/regulatory reporting | Deferred until the core lifecycle is stable | 2026-09-18 |

## Session Continuity

Last session: Completed 06-02-PLAN.md
Stopped at: None
Resume file: None

**Planned Phase:** 06 (angular-fd-frontend) — 4 plans — 2026-09-22T13:13:07.043Z
**Completed Phase:** 3 (Interest and Maturity Processing) — 2 plans — 2026-09-22 (4/4 must-haves verified; human-needed: live REST book→fund→accrue→mature demo against Supabase)
**Completed Phase:** 4 (Closure and Demonstrable API Workflow) — 4 plans — 2026-09-22 (5/5 must-haves verified; review HI-01+MED-03 fixed in f5e1fd5; human-needed: live Postman demo against Supabase)
**Completed Phase:** 5 (Protection, Tests, and Regression Gate) — 5 plans — 2026-09-22 (5/5 must-haves verified; 3 MED + 4 LOW advisories, none goal-blocking; human-needed: live Supabase REST demo + live-PG FdPhase5ProtectionIT run)
