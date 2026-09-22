---
phase: 03-interest-and-maturity-processing
plan: 01
subsystem: fd-interest
tags: [bigdecimal, actual-365, jpa, flyway, postgres]

# Dependency graph
requires:
  - phase: 02-product-booking-and-funding
    provides: booking maturityEstimate formula and funded-account lifecycle baseline
provides:
  - Shared BigDecimal Actual/365 FdInterestCalculator (interestForPeriod, maturityAmount)
  - FdInterestAccrual JPA entity + repository with per-date lookup
  - V8 migration provisioning INTEREST_ACCRUAL/MATURITY codes with uniqueness guards
affects: [03-02 accrual and maturity service behavior, maturity closure]

# Tech tracking
tech-stack:
  added: []
  patterns: [pure-static BigDecimal domain math, additive idempotent Flyway migrations]

key-files:
  created:
    - BACKEND/src/main/java/com/bank/fd/domain/FdInterestCalculator.java
    - BACKEND/src/test/java/com/bank/fd/domain/FdInterestCalculatorTest.java
    - BACKEND/src/main/java/com/bank/fd/entity/FdInterestAccrual.java
    - BACKEND/src/main/java/com/bank/fd/repository/FdInterestAccrualRepository.java
    - BACKEND/src/main/resources/db/migration/V8__fd_accrual_maturity.sql
    - BACKEND/src/test/java/com/bank/fd/FdAccrualSchemaTest.java
  modified: []

key-decisions:
  - "Calculator stays a Spring-free pure static function with explicit principal/rate/date parameters"
  - "Schema test mirrors FdBookingSchemaTest reflection style so it passes without Docker"

patterns-established:
  - "Actual/365 BigDecimal math: scale-12 HALF_UP intermediate, scale-2 HALF_UP money output"
  - "Additive V8 migration: diagnostic preflight plus ON CONFLICT DO NOTHING provisioning"

requirements-completed: [CALC-01, CALC-02]

# Metrics
duration: 12min
completed: 2026-09-22
---

# Phase 03 Plan 01: Interest Math and Accrual Foundation Summary

**Shared BigDecimal Actual/365 interest/maturity calculator plus accrual JPA mapping and V8 transaction-code migration**

## Performance

- **Duration:** 12 min
- **Started:** 2026-09-22T00:00:00Z
- **Completed:** 2026-09-22T00:12:00Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments

- `FdInterestCalculator` with `interestForPeriod` and `maturityAmount`, numerically identical to the booking estimate
- `FdInterestAccrual` entity + `FdInterestAccrualRepository` with ordered and per-date lookups
- V8 migration provisioning `INTEREST_ACCRUAL` and `MATURITY` codes with duplicate-uuid preflight and uniqueness indexes
- `FdInterestCalculatorTest` and `FdAccrualSchemaTest` both passing; `mvn -DskipTests compile` passing

## Task Commits

Each task was committed atomically:

1. **Task 1: Create shared BigDecimal interest calculator with unit tests** - `957c74a` (feat)
2. **Task 2: Map fd_interest_accrual persistence and provision V8 transaction codes** - `2c6fd67` (feat)

## Files Created/Modified

- `BACKEND/src/main/java/com/bank/fd/domain/FdInterestCalculator.java` - Pure static Actual/365 BigDecimal interest/maturity math
- `BACKEND/src/test/java/com/bank/fd/domain/FdInterestCalculatorTest.java` - Known-value, rounding, rejection, and booking-parity tests
- `BACKEND/src/main/java/com/bank/fd/entity/FdInterestAccrual.java` - JPA mapping of public.fd_interest_accrual
- `BACKEND/src/main/java/com/bank/fd/repository/FdInterestAccrualRepository.java` - Ordered and per-date accrual lookups
- `BACKEND/src/main/resources/db/migration/V8__fd_accrual_maturity.sql` - INTEREST_ACCRUAL/MATURITY codes plus uniqueness guards
- `BACKEND/src/test/java/com/bank/fd/FdAccrualSchemaTest.java` - Entity mapping, repository contract, and migration content assertions

## Decisions Made

- Calculator stays a Spring-free pure static function with explicit principal/rate/date parameters, keeping the academic-v1 formula portable for accrual and maturity services.
- Schema test mirrors FdBookingSchemaTest reflection style so it passes without Docker, consistent with the plan's Docker-gating allowance.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plan 03-02 can build accrual and maturity service behavior on the shared calculator, accrual repository per-date guard, and V8 transaction codes.
- No blockers. Legacy double-based calculator untouched; migrations V1-V7 unmodified.

## Self-Check: PASSED

- All 6 created files verified present; task commits `957c74a` and `2c6fd67` verified in git log.

---
*Phase: 03-interest-and-maturity-processing*
*Completed: 2026-09-22*
