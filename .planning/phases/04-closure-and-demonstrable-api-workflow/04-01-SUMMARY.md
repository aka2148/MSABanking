---
phase: 04-closure-and-demonstrable-api-workflow
plan: 01
subsystem: database
tags: [postgres, flyway, jpa, bigdecimal, fixed-deposit, premature-closure]

# Dependency graph
requires:
  - phase: 03-interest-and-maturity-processing
    provides: Shared FdInterestCalculator math and V8 transaction-code provisioning pattern
  - phase: 01-existing-fd-baseline
    provides: fd_premature_closure table definition and lifecycle state machine vocabulary
provides:
  - V9 migration provisioning CLOSURE/PENALTY transaction codes plus fpc_status PENDING/APPROVED lifecycle column
  - FdPrematureClosure JPA entity and repository for per-account closure lookups
  - PrematurePenalty pure BigDecimal penalty policy (D-01 parse/compute, D-02 fail-closed signal)
affects: [04-closure-service, 04-demo-artifact, phase-5-authorization]

# Tech tracking
tech-stack:
  added: []
  patterns: [V8-pattern Flyway migration with diagnostic DO-block preflight, explicit @Column JPA mapping style, Spring-free pure domain helper]

key-files:
  created:
    - BACKEND/src/main/resources/db/migration/V9__fd_closure_history.sql
    - BACKEND/src/main/java/com/bank/fd/entity/FdPrematureClosure.java
    - BACKEND/src/main/java/com/bank/fd/repository/FdPrematureClosureRepository.java
    - BACKEND/src/main/java/com/bank/fd/domain/PrematurePenalty.java
    - BACKEND/src/test/java/com/bank/fd/domain/PrematurePenaltyTest.java
    - BACKEND/src/test/java/com/bank/fd/FdClosureSchemaTest.java
  modified: []

key-decisions:
  - "ADD COLUMN IF NOT EXISTS plus guarded CHECK-constraint DO-block keeps V9 rerunnable without changing its additive contract"
  - "parsePenaltyPoints strips one trailing % and rejects null/blank/unparseable/negative with Optional.empty as the D-02 fail-closed signal"
  - "penalizedRate uses scale-12 HALF_UP intermediate and scale-4 HALF_UP floored-at-zero output matching FdInterestCalculator discipline"

patterns-established:
  - "Closure schema tests mirror FdAccrualSchemaTest: entity column mapping plus migration content guards"
  - "Premature math delegates to FdInterestCalculator.interestForPeriod with the penalized rate; legacy double calculator never touched"

requirements-completed: [LIFE-03, LIFE-04]

# Metrics
duration: 5min
completed: 2026-09-22
---

# Phase 04 Plan 01: Closure Persistence and Penalty-Math Foundation Summary

**V9 migration provisioning CLOSURE/PENALTY codes with fpc_status lifecycle, JPA closure entity/repository, and a pure BigDecimal penalty policy with known-value and fail-closed tests**

## Performance

- **Duration:** ~5 min
- **Started:** 2026-09-22T12:44:15Z
- **Completed:** 2026-09-22T12:50:00Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments

- V9 forward-only migration provisions CLOSURE and PENALTY transaction codes idempotently and adds the fpc_status PENDING/APPROVED lifecycle column
- FdPrematureClosure entity maps every fpc_ column with @PrePersist UUID generation; repository exposes per-account ordered and status-filtered lookups
- PrematurePenalty implements D-01 (percentage-point rate reduction via FdInterestCalculator, floored at zero) with D-02 fail-closed Optional.empty signal
- PrematurePenaltyTest (4 tests) and FdClosureSchemaTest (3 tests) green; V1-V8 migrations unmodified

## Task Commits

Each task was committed atomically:

1. **Task 1: Add V9 closure/history migration** - `45385a9` (feat)
2. **Task 2: Create premature-closure entity, repository, and penalty policy with tests** - `ca0695f` (feat)

## Files Created/Modified

- `BACKEND/src/main/resources/db/migration/V9__fd_closure_history.sql` - Diagnostic preflight, CLOSURE/PENALTY code provisioning, fpc_status column plus PENDING/APPROVED check
- `BACKEND/src/main/java/com/bank/fd/entity/FdPrematureClosure.java` - JPA mapping of public.fd_premature_closure with PENDING default status
- `BACKEND/src/main/java/com/bank/fd/repository/FdPrematureClosureRepository.java` - findByAccountIdOrderByClosureDateAscIdAsc and findByAccountIdAndStatus
- `BACKEND/src/main/java/com/bank/fd/domain/PrematurePenalty.java` - Pure static penalty-rate parsing and premature payout math
- `BACKEND/src/test/java/com/bank/fd/domain/PrematurePenaltyTest.java` - Known-value, zero-floor, unsupported-rule, and settlement tests
- `BACKEND/src/test/java/com/bank/fd/FdClosureSchemaTest.java` - Entity column mapping, repository contract, and migration content guards

## Decisions Made

- Used `ADD COLUMN IF NOT EXISTS` plus a pg_constraint-guarded DO-block for the CHECK constraint so V9 is safely rerunnable; the additive contract (new column, new codes, no V1-V8 edits) is unchanged.
- `parsePenaltyPoints` strips one trailing `%` after trimming (accepting "1.00%" as well as "1.00") and rejects negative values as fail-closed, since a negative penalty is operator-data corruption, not a bonus.
- `foregoneInterest` floors at 0.00 so callers comparing full vs penalized interest can never report a negative penalty amount.

## Deviations from Plan

None - plan executed exactly as written. (The IF NOT EXISTS / constraint-guard hardening in V9 is an idempotency-equivalent spelling of the specified V8 pattern, not a scope change.)

## Issues Encountered

None. `mvn -Dtest='PrematurePenaltyTest,FdClosureSchemaTest' test` passed 7/7 on first run; full compile succeeded with only a pre-existing SecurityConfig deprecation warning.

## Threat Flags

None - no new network endpoints, auth paths, file access, or trust-boundary schema changes beyond the plan's threat model. T-04-01 through T-04-03 mitigations are implemented as specified; T-04-04 (free-text reason) accepted per plan.

## Known Stubs

None.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Closure persistence and penalty math are ready for the downstream closure service plan (two-step request/approve flow per D-03) and unified history read (D-05/D-06).
- Follow-up plans need the `CLOSURE_PENALTY` rule wiring via NormalizedProductTermsAdapter and the controller replacement of the four closure placeholders; unrelated 501 placeholders stay untouched.

## Self-Check: PASSED

- FOUND: BACKEND/src/main/resources/db/migration/V9__fd_closure_history.sql
- FOUND: BACKEND/src/main/java/com/bank/fd/entity/FdPrematureClosure.java
- FOUND: BACKEND/src/main/java/com/bank/fd/repository/FdPrematureClosureRepository.java
- FOUND: BACKEND/src/main/java/com/bank/fd/domain/PrematurePenalty.java
- FOUND: BACKEND/src/test/java/com/bank/fd/domain/PrematurePenaltyTest.java
- FOUND: BACKEND/src/test/java/com/bank/fd/FdClosureSchemaTest.java
- FOUND: 45385a9, FOUND: ca0695f

---
*Phase: 04-closure-and-demonstrable-api-workflow*
*Completed: 2026-09-22*
