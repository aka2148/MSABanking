---
phase: 05-protection-tests-and-regression-gate
plan: 03
subsystem: api
tags: [spring-boot, idempotency, ownership, fd, closure, history, controller]

# Dependency graph
requires:
  - phase: 05-protection-tests-and-regression-gate
    provides: FdIdempotencyGuard durable claim/replay guard and FdAccessPolicy 404 ownership check (05-01)
  - phase: 05-protection-tests-and-regression-gate
    provides: Key-accepting booking/funding/accrual/maturity service overloads (05-02)
provides:
  - Idempotent normal closure plus two-step premature closure with same-key replay returning the stored rows
  - 404-hiding ownership on closure, premature closure, history timeline, and the two controller read paths
  - Idempotency-Key threading plus CUSTOMER/BANK_OFFICER/ADMIN roles on all seven FD mutating endpoints
affects: [05-04 replay-proof tests, 05-05 regression gate, fd close/history demo]

# Tech tracking
tech-stack:
  added: []
  patterns: [idempotencyKey overloads with key-less-compatible delegates, claim-before-transition inside locked transactions, policy-bean ownership in controller reads]

key-files:
  created: []
  modified:
    - BACKEND/src/main/java/com/bank/fd/application/ClosureService.java
    - BACKEND/src/main/java/com/bank/fd/application/PrematureClosureService.java
    - BACKEND/src/main/java/com/bank/fd/application/HistoryService.java
    - BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java

key-decisions:
  - "Closure replay rebuilds the stored ClosureResponse from the recorded aggregate (settlement = stored maturity amount) with zero new rows"
  - "Premature request/approve replays reload the stored closure row and return requested/approved shapes; 422 paths never complete a claim"
  - "Contract tests that asserted the pre-plan CUSTOMER-only roles and key-less signatures now assert the planned widened contract"

patterns-established:
  - "Service overloads: close/requestPrematureClosure/approvePrematureClosure with trailing idempotencyKey plus a key-less delegate passing null"
  - "Canonical request hashes: accountId, accountId|reason, accountId|closureId"
  - "Controller is pass-through only: Idempotency-Key header forwarded verbatim, zero business logic"

requirements-completed: [HIST-02, AUTH-01, AUTH-02]

# Metrics
duration: 20min
completed: 2026-09-22
---

# Phase 05 Plan 03: Closure, History, and Controller Wiring Summary

**Idempotent closure and two-step premature closure with stored-response replays, 404-hiding ownership across closure/history/reads, and end-to-end Idempotency-Key plus staff-role threading on all seven FD mutating endpoints**

## Performance

- **Duration:** 20 min
- **Started:** 2026-09-22T10:35:00Z (approx)
- **Completed:** 2026-09-22T10:55:00Z (approx)
- **Tasks:** 2
- **Files modified:** 10 (4 main, 6 test)

## Accomplishments

- Closure claims `CLOSURE` scope before the transition; same-key replays rebuild the stored settlement DTO with no second settlement/transaction row, key-less repeats on CLOSED get `already CLOSED; existing settlement recorded`
- Premature request claims `PREMATURE_REQUEST` and approve claims `PREMATURE_APPROVE`; replays return the stored request/approved rows; unsupported-rule 422 paths throw before any write and never complete a claim
- History timeline plus controller `getAccount`/`getPrematureClosure` enforce ownership through `FdAccessPolicy.requireOwned` (cross-customer reads 404, staff bypass live)
- `FdV1ContractController` forwards `Idempotency-Key` on all seven mutating endpoints (booking, funding, accrual, maturity, closure, premature request, premature approve) and widens exactly those seven to `hasAnyRole('CUSTOMER','BANK_OFFICER','ADMIN')`; all 501 placeholders, read endpoints, filters, and DTOs untouched
- Zero remaining `new AccessDeniedException("Authenticated customer does not own...")` in FD scope

## Task Commits

Each task was committed atomically:

1. **Task 1: Harden closure, premature closure, and history services** - `5e70783` (feat)
2. **Task 2: Thread Idempotency-Key and staff roles through /fd/v1** - `899e48d` (feat)

## Files Created/Modified

- `BACKEND/src/main/java/com/bank/fd/application/ClosureService.java` - CLOSURE claim/replay/complete plus `close(accountId, username, idempotencyKey)` overload and key-less CLOSED 409
- `BACKEND/src/main/java/com/bank/fd/application/PrematureClosureService.java` - PREMATURE_REQUEST/PREMATURE_APPROVE claim/replay/complete plus key overloads on both steps
- `BACKEND/src/main/java/com/bank/fd/application/HistoryService.java` - `FdAccessPolicy` ownership replacing the inline 403 throw
- `BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java` - Key threading, seven widened roles, policy-backed 404 reads
- `BACKEND/src/test/java/com/bank/fd/application/ClosureServiceTest.java` - Wired mock guard + real access policy
- `BACKEND/src/test/java/com/bank/fd/application/PrematureClosureServiceTest.java` - Wired mock guard + real access policy
- `BACKEND/src/test/java/com/bank/fd/application/HistoryServiceTest.java` - Wired real access policy
- `BACKEND/src/test/java/com/bank/fd/api/FdV1ClosureHistoryContractTest.java` - Keyed service stubs, policy constructor arg, widened-role assertions
- `BACKEND/src/test/java/com/bank/fd/api/FdV1AccrualMaturityContractTest.java` - Keyed service stubs, policy constructor arg, widened-role assertions
- `BACKEND/src/test/java/com/bank/fd/api/FdV1BookingContractTest.java` - Widened-role assertion on the keyed fund signature

## Decisions Made

- Closure replay returns `ClosureResponse.closed` rebuilt from the recorded aggregate (stored maturity amount, resolved currency, current business date), so the replayed settlement amount is identical to the original with no new rows.
- Premature request replay returns the requested shape for PENDING rows and the approved shape for later-approved rows, keeping request, approve, and history views consistent.
- The approve method reuses one `holder` resolution for the idempotency actor key and a separate `approver` resolution for the stored approval stamp (avoids a duplicate-local compile error while keeping both semantics).

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Updated six test files the plan did not list**
- **Found during:** Tasks 1 and 2
- **Issue:** New constructor parameters (`FdIdempotencyGuard`, `FdAccessPolicy`) broke compilation of `ClosureServiceTest`, `PrematureClosureServiceTest`, `HistoryServiceTest`, `FdV1ClosureHistoryContractTest`, and `FdV1AccrualMaturityContractTest`; controller key forwarding orphaned the key-less Mockito stubs.
- **Fix:** Wired `mock(FdIdempotencyGuard.class)` + real `FdAccessPolicy(actorResolver)` in service tests (same pattern as 05-02); added `mock(FdAccessPolicy.class)` to controller constructor calls; re-pointed stubs at the keyed overloads with `isNull()` (MockMvc sends no header).
- **Files modified:** the six test files listed above
- **Commit:** 5e70783 (service tests), 899e48d (contract tests)

**2. [Rule 1 - Bug] Updated stale role/signature assertions that contradicted the plan**
- **Found during:** Task 2
- **Issue:** `FdV1BookingContractTest.fundingRouteIsAuthenticatedAndTyped`, `FdV1ClosureHistoryContractTest.endpointRolesAndUnrelatedPlaceholdersPreserved`, and `FdV1AccrualMaturityContractTest.endpointRolesAndUnrelatedPlaceholdersPreserved` asserted the old `hasRole('CUSTOMER')` roles and key-less method signatures via `getDeclaredMethod` — the plan mandates widening roles and adding the header parameter, so these tests could not stay green unmodified.
- **Fix:** Updated the assertions to the planned contract: new signatures including the `String idempotencyKey` parameter and `hasAnyRole('CUSTOMER','BANK_OFFICER','ADMIN')` on exactly the seven mutating endpoints; read-endpoint and 501-placeholder assertions unchanged.
- **Files modified:** `FdV1BookingContractTest.java`, `FdV1ClosureHistoryContractTest.java`, `FdV1AccrualMaturityContractTest.java`
- **Commit:** 899e48d

## Issues Encountered

- A duplicate `var holder` local in `approvePrematureClosure` (one added for the actor key, one pre-existing for the approval stamp) would not compile; renamed the approval-stamp resolution to `approver` before running tests.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plans 05-04/05-05 can add replay-proof tests (same-key double-submit asserting zero new rows) and run the full regression gate; all FD services now accept keys and the controller threads them end to end.
- Full `mvn test` green remains the exit gate (TEST-03); this plan verified the FD-scoped suites: 44/44 green across ClosureServiceTest (5), PrematureClosureServiceTest (12), HistoryServiceTest (3), FdV1ContractBoundaryTest (4), FdV1BookingContractTest (4), FdV1ClosureHistoryContractTest (10), FdV1AccrualMaturityContractTest (6).

## Self-Check: PASSED

- FOUND: BACKEND/src/main/java/com/bank/fd/application/ClosureService.java (claim CLOSURE, CLOSED 409)
- FOUND: BACKEND/src/main/java/com/bank/fd/application/PrematureClosureService.java (PREMATURE_REQUEST, PREMATURE_APPROVE)
- FOUND: BACKEND/src/main/java/com/bank/fd/application/HistoryService.java (no inline 403 throw)
- FOUND: BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java (7 keys, 7 widened roles, requireOwned x2)
- FOUND commits: 5e70783, 899e48d

---
*Phase: 05-protection-tests-and-regression-gate*
*Completed: 2026-09-22*
