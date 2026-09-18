# Project State

## Project Reference

See: .planning/PROJECT.md (updated 2026-09-18)

**Core value:** Students must be able to demonstrate a complete, understandable fixed-deposit workflow from product selection through booking, funding, interest calculation, maturity, and closure using the existing application.
**Current focus:** Phase 1 — Existing FD Baseline

## Current Position

Phase: 1 of 5 (Existing FD Baseline)
Plan: TBD in current phase
Status: Ready to plan
Last activity: 2026-09-18 — Created the five-phase academic FD roadmap and requirement traceability.

Progress: [░░░░░░░░░░] 0%

## Performance Metrics

**Velocity:**
- Total plans completed: 0
- Average duration: N/A
- Total execution time: 0.0 hours

**By Phase:**

| Phase | Plans | Total | Avg/Plan |
|-------|-------|-------|----------|
| 1 | 0 | TBD | N/A |
| 2 | 0 | TBD | N/A |
| 3 | 0 | TBD | N/A |
| 4 | 0 | TBD | N/A |
| 5 | 0 | TBD | N/A |

**Recent Trend:**
- Last 5 plans: None
- Trend: Not established

## Accumulated Context

### Decisions

Decisions are logged in PROJECT.md Key Decisions table.
Recent decisions affecting current work:

- The milestone is a college-lab FD demonstration, not a production banking platform.
- Existing Spring Boot/Angular modules, services, entities, repositories, calculators, authentication, migrations, `/fd/v1`, and legacy endpoints are compatibility assets.
- REST/Swagger/Postman is the acceptance surface; a new Angular FD UI is deferred unless existing functionality requires it.
- Keep lifecycle, decimal money, PostgreSQL/Flyway persistence, basic idempotency, ownership/role checks, and focused tests; avoid enterprise workflow and payment infrastructure.

### Pending Todos

None yet.

### Blockers/Concerns

- Phase 1 must verify the actual PostgreSQL schema and the smallest additive migration set before lifecycle code depends on it.
- Product-term authority, exact calculation convention, and premature-closure rule availability must be made explicit during the relevant phase rather than guessed.
- Existing `/fd/v1` placeholders and legacy FD methods need compatibility checks so working non-FD behavior is not disrupted.

## Deferred Items

| Category | Item | Status | Deferred At |
|----------|------|--------|-------------|
| Scope | Angular FD screens beyond the current shell | Deferred to v2 unless required | 2026-09-18 |
| Scope | Real payment rails, workflow engines, distributed locks, outbox/reconciliation infrastructure | Deferred as disproportionate to the lab | 2026-09-18 |
| Scope | Recurring payouts, renewal, partial withdrawals, broad holder/nominee workflows, tax/regulatory reporting | Deferred until the core lifecycle is stable | 2026-09-18 |

## Session Continuity

Last session: 2026-09-18
Stopped at: Roadmap and state artifacts created; awaiting phase planning.
Resume file: None
