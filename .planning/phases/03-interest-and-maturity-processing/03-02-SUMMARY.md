---
phase: 03-interest-and-maturity-processing
plan: 02
subsystem: fd-lifecycle
tags: [bigdecimal, actual-365, accrual, maturity, spring-mvc, mockmvc, openapi]

# Dependency graph
requires:
  - phase: 03-interest-and-maturity-processing plan 01
    provides: shared FdInterestCalculator, FdInterestAccrual entity/repository, V8 MATURITY codes
provides:
  - ACTIVE-only AccrualService with per-date repeat guard backed by FdInterestCalculator
  - Eligibility-checked MaturityService with ACTIVE to MATURED transition and MATURITY record
  - Four live /fd/v1 accrual/maturity endpoints with AccrualResponse DTOs and OpenAPI schemas
affects: [03-03 closure work, phase 04 transaction history and closure endpoints]

# Tech tracking
tech-stack:
  added: []
  patterns: [pessimistic-lock holder-match service mutations, explicit accrual DTOs, MockMvc standalone contract tests]

key-files:
  created:
    - BACKEND/src/main/java/com/bank/fd/application/AccrualService.java
    - BACKEND/src/main/java/com/bank/fd/application/MaturityService.java
    - BACKEND/src/main/java/com/bank/fd/api/dto/AccrualResponse.java
    - BACKEND/src/test/java/com/bank/fd/application/AccrualMaturityServiceTest.java
    - BACKEND/src/test/java/com/bank/fd/api/FdV1AccrualMaturityContractTest.java
  modified:
    - BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java
    - BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml

key-decisions:
  - "Accrual rejects non-ACTIVE accounts with InvalidInputException; maturity rejects with InvalidFdTransitionException via the lifecycle state machine"
  - "Controller accepts business_date (with accrual_date fallback) and defaults to FdTimePolicy.businessDate() when absent"
  - "Contract test uses MockMvc standaloneSetup with a JavaTimeModule mapper and asserts roles via annotations"

patterns-established:
  - "Accrual/maturity mutations mirror FundingService: findByIdForUpdate lock, FdActorResolver holder match, server-computed BigDecimal amounts"
  - "Repeat-date guard returns the existing accrual row instead of surfacing the plan-01 unique-index violation"

requirements-completed: [CALC-02, LIFE-02]

# Metrics
duration: 15min
completed: 2026-09-22
---

# Phase 03 Plan 02: Accrual and Maturity Processing Summary

**ACTIVE-only accrual runs with per-date repeat guard plus eligibility-checked ACTIVE-to-MATURED processing, exposed on four typed /fd/v1 endpoints with OpenAPI schemas**

## Performance

- **Duration:** 15 min
- **Started:** 2026-09-22T12:00:00Z
- **Completed:** 2026-09-22T12:15:00Z
- **Tasks:** 2
- **Files modified:** 7

## Accomplishments

- `AccrualService.runAccrual` computes period interest with the shared plan-01 calculator, clamps to the stored maturity date, and returns the existing row on repeat dates
- `MaturityService.processMaturity` rejects PENDING/CLOSED via the lifecycle state machine, rejects pre-maturity dates, writes the closing accrual row plus a `MATURITY` transaction, and transitions to MATURED with principal+interest
- Four `/fd/v1` endpoints (run accrual, list accrual, cumulative accrual, mature) return typed `AccrualResponse` payloads; unrelated 501 placeholders untouched
- OpenAPI documents the four operations with an `AccrualResponse` schema and 400/403/404/409 error responses reusing the existing error schema
- Unit and MockMvc contract tests pass; `mvn -DskipTests compile` passes

## Task Commits

Each task was committed atomically:

1. **Task 1: Implement accrual and maturity services with unit tests** - `576ad2b` (feat)
2. **Task 2: Wire accrual and maturity endpoints with OpenAPI and contract tests** - `eb04cc6` (feat)

## Files Created/Modified

- `BACKEND/src/main/java/com/bank/fd/api/dto/AccrualResponse.java` - Typed accrual/maturity response with `accrued`/`matured` factories; no entity serialization
- `BACKEND/src/main/java/com/bank/fd/application/AccrualService.java` - ACTIVE-only accrual run with repeat-date guard plus holder-checked list/cumulative reads
- `BACKEND/src/main/java/com/bank/fd/application/MaturityService.java` - Eligibility-checked maturity transition with final through-end-date BigDecimal math and MATURITY record
- `BACKEND/src/test/java/com/bank/fd/application/AccrualMaturityServiceTest.java` - Known 6500.00 full-year value, repeat-guard, cumulative, pre-maturity and lifecycle rejection tests
- `BACKEND/src/test/java/com/bank/fd/api/FdV1AccrualMaturityContractTest.java` - MockMvc coverage of the four endpoints, structured error shape, role annotations, preserved closeAccount placeholder
- `BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java` - Constructor-injected services with four implemented endpoints; other placeholders unchanged
- `BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml` - AccrualResponse schema plus documented accrual/maturity operations and error responses

## Decisions Made

- Accrual rejects non-ACTIVE accounts with `InvalidInputException` (there is no accrual command in the lifecycle state machine), while maturity rejects PENDING/CLOSED with `InvalidFdTransitionException(MATURE)` through the Phase 1 lifecycle boundary.
- The controller accepts `business_date` (with an `accrual_date` fallback for contract compatibility) and defaults to `FdTimePolicy.businessDate()` when absent, keeping client dates clamped server-side per threat mitigation T-03-04.
- The contract test uses MockMvc `standaloneSetup` with a `JavaTimeModule` mapper and asserts endpoint roles via annotations, since standalone MockMvc does not enforce Spring Security.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Accrual history and MATURED accounts are ready for Phase 4 closure and transaction-history work.
- No blockers. Booking/funding behavior untouched; `mvn -DskipTests compile` passes.

## Self-Check: PASSED

- All 7 created/modified files verified present; task commits `576ad2b` and `eb04cc6` verified in git log.

---
*Phase: 03-interest-and-maturity-processing*
*Completed: 2026-09-22*
