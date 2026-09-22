---
phase: 05-protection-tests-and-regression-gate
plan: 04
subsystem: testing
tags: [spring-boot, unit-tests, idempotency, ownership, error-mapping, fd]

# Dependency graph
requires:
  - phase: 05-protection-tests-and-regression-gate
    provides: FdIdempotencyGuard durable claim/replay guard and FdAccessPolicy 404 ownership check (05-01)
provides:
  - Guard claim/replay/mismatch/blank-key unit proof (FdIdempotencyGuardTest)
  - Ownership matrix unit proof (FdAccessPolicyTest)
  - Error status-mapping unit proof (GlobalExceptionHandlerTest)
  - Boundary/invalid-case coverage beyond existing domain tests (FdPhase5BoundaryTest)
affects: [05-05 regression gate, fd demo evidence]

# Tech tracking
tech-stack:
  added: []
  patterns: [mocked-repository guard tests, direct handler invocation for status mapping, gap-only boundary tests]

key-files:
  created:
    - BACKEND/src/test/java/com/bank/fd/application/FdIdempotencyGuardTest.java
    - BACKEND/src/test/java/com/bank/fd/application/FdAccessPolicyTest.java
    - BACKEND/src/test/java/com/bank/common/exception/GlobalExceptionHandlerTest.java
    - BACKEND/src/test/java/com/bank/fd/domain/FdPhase5BoundaryTest.java
  modified: []

key-decisions:
  - "Boundary tests cover only gaps in existing domain tests; happy paths stay in FdInterestCalculatorTest, FdLifecycleStateMachineTest, PrematurePenaltyTest, FdCalculationPolicyTest"
  - "100-point penalty is the full-floor boundary case (distinct from the existing 10-point floor test)"
  - "Null/negative principal tests assert validation throws and verify a mocked repository saw zero interactions"

patterns-established:
  - "Guard tests: mock FdOperationIdempotencyRepository, never touch the DB"
  - "Handler tests: instantiate GlobalExceptionHandler directly, assert ResponseEntity status + integer body status"

requirements-completed: [TEST-01]

# Metrics
duration: 12min
completed: 2026-09-22
---

# Phase 05 Plan 04: Fast Unit Protection Tests Summary

**Four new unit test classes pinning guard replay/mismatch, the ownership matrix, the 400/401/404/409/422 status mapping, and money/lifecycle/validation boundaries — all green without Spring or Docker**

## Performance

- **Duration:** 12 min
- **Started:** 2026-09-22T10:50:00Z (approx)
- **Completed:** 2026-09-22T11:02:00Z (approx)
- **Tasks:** 2
- **Files modified:** 4 (all new test files, zero existing files touched)

## Accomplishments

- `FdIdempotencyGuardTest` (6 tests): null/blank key returns null with zero repository writes; unknown key persists a PENDING row with the given SHA-256 hash; same key+hash on a COMPLETED claim replays with no save; same key+different hash throws `FdDuplicateOperationException` referencing the stored record; `sha256Hex` is deterministic 64-char lowercase hex
- `FdAccessPolicyTest` (6 tests): holder-id match passes; stranger customer (`ROLE_CUSTOMER`, holder 9 vs account 7) gets `ResourceNotFoundException("FD account not found")`; `ROLE_BANK_OFFICER` and `ROLE_ADMIN` pass for non-owned accounts; empty authorities get 404; blank username propagates the resolver's `ResourceNotFoundException`
- `GlobalExceptionHandlerTest` (5 tests): duplicate → 409 with "existing record:" in the body; invalid transition → 409; unsupported premature closure → 422 (`UNPROCESSABLE_ENTITY`); not found → 404; invalid input → 400; every body carries integer `status` equal to the HTTP code
- `FdPhase5BoundaryTest` (8 tests): zero-day maturity equals principal exactly at scale 2; one-day Actual/365 on 100000.00 @ 0.0650 → 17.81; 100-point penalty floors the penalized rate at 0.0000 with 0.00 premature interest; FUND-on-ACTIVE, MATURE-on-PENDING, CLOSE-on-PENDING each throw `InvalidFdTransitionException`; null/negative principal throw before any persistence call (`verifyNoInteractions` on a mocked repository)
- Full plan verification green: `FdIdempotencyGuardTest,FdAccessPolicyTest,GlobalExceptionHandlerTest,FdPhase5BoundaryTest,FdInterestCalculatorTest,FdLifecycleStateMachineTest,PrematurePenaltyTest` → 37 tests, 0 failures, 0 errors, 0 skipped

## Task Commits

Each task was committed atomically:

1. **Task 1: Unit-test guard replay/mismatch and ownership matrix** - `c06fe96` (feat)
2. **Task 2: Unit-test error mapping and domain boundary cases** - `96c3e69` (feat)

## Files Created/Modified

- `BACKEND/src/test/java/com/bank/fd/application/FdIdempotencyGuardTest.java` - Guard claim/replay/mismatch/blank-key unit proof (mocked repository)
- `BACKEND/src/test/java/com/bank/fd/application/FdAccessPolicyTest.java` - Ownership matrix unit proof (mocked resolver, `SimpleGrantedAuthority` roles)
- `BACKEND/src/test/java/com/bank/common/exception/GlobalExceptionHandlerTest.java` - 409/409/422/404/400 status-mapping proof (direct handler invocation)
- `BACKEND/src/test/java/com/bank/fd/domain/FdPhase5BoundaryTest.java` - Gap-only boundary/invalid coverage (string-constructed `BigDecimal` only, no double money)

## Decisions Made

- Boundary tests deliberately cover only gaps: existing `FdInterestCalculatorTest` already proves zero-day interest and one-day rounding shape, `FdLifecycleStateMachineTest` exhaustively rejects unlisted pairs, and `PrematurePenaltyTest` covers the 10-point floor — so this plan adds the worked 17.81 value, the 100-point full floor, the three plan-named invalid transitions, and persistence-free validation
- The 100-point penalty (not 6.50) is the "100% penalty" floor boundary: it overflows any contracted rate into the floor branch rather than landing exactly on zero
- Null/negative principal tests mock `FdAccountRepository` purely as a persistence-touch sensor (`verifyNoInteractions`) since validation lives in the Spring-free calculator

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- A PowerShell `if ($?)` check after `Select-String` falsely reported a forbidden `(double)` pattern (exit status reflects command success, not match presence); re-ran with an explicit `$null -eq $m` null check and confirmed the file is clean of double-constructed money

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plan 05-05 can run the full regression gate (TEST-03) on top of these unit proofs plus the 05-02/05-03 wiring; the D-07 unit layer and D-08 scope are now pinned without Docker
- Existing domain tests untouched and still green; no new test dependencies added

## Self-Check: PASSED

- FOUND: BACKEND/src/test/java/com/bank/fd/application/FdIdempotencyGuardTest.java (6 tests green)
- FOUND: BACKEND/src/test/java/com/bank/fd/application/FdAccessPolicyTest.java (6 tests green)
- FOUND: BACKEND/src/test/java/com/bank/common/exception/GlobalExceptionHandlerTest.java (5 tests green)
- FOUND: BACKEND/src/test/java/com/bank/fd/domain/FdPhase5BoundaryTest.java (8 tests green)
- FOUND commits: c06fe96, 96c3e69
- VERIFIED: zero existing files modified (both commits contain only the 4 new test files)

---
*Phase: 05-protection-tests-and-regression-gate*
*Completed: 2026-09-22*
