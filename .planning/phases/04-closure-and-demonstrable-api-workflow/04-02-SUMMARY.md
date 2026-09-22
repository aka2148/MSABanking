---
phase: 04-closure-and-demonstrable-api-workflow
plan: 02
subsystem: fd-lifecycle
tags: [bigdecimal, fixed-deposit, closure, premature-closure, history, unit-tests]

# Dependency graph
requires:
  - phase: 04-closure-and-demonstrable-api-workflow
    provides: V9 CLOSURE/PENALTY codes, FdPrematureClosure entity/repository, PrematurePenalty policy (04-01)
  - phase: 03-interest-and-maturity-processing
    provides: FundingService/MaturityService lock-holder-transition-persist pattern, AccrualResponse style
provides:
  - ClosureService MATURED to CLOSED transition with CLOSURE settlement row
  - PrematureClosureService PENDING request plus same-customer approve finalization
  - HistoryService unified fd_transactions plus fd_interest_accrual timeline
affects: [04-endpoint-wiring, 04-demo-artifact, phase-5-authorization]

# Tech tracking
tech-stack:
  added: []
  patterns: [FundingService pessimistic-lock plus holder-match plus state-machine plus persist, stored-snapshot approve, id-mapped transaction-code labels]

key-files:
  created:
    - BACKEND/src/main/java/com/bank/fd/api/dto/ClosureResponse.java
    - BACKEND/src/main/java/com/bank/fd/api/dto/PrematureClosureResponse.java
    - BACKEND/src/main/java/com/bank/fd/api/dto/HistoryEntry.java
    - BACKEND/src/main/java/com/bank/fd/exception/PrematureClosureNotSupportedException.java
    - BACKEND/src/main/java/com/bank/fd/application/ClosureService.java
    - BACKEND/src/main/java/com/bank/fd/application/PrematureClosureService.java
    - BACKEND/src/main/java/com/bank/fd/application/HistoryService.java
    - BACKEND/src/test/java/com/bank/fd/application/ClosureServiceTest.java
    - BACKEND/src/test/java/com/bank/fd/application/PrematureClosureServiceTest.java
    - BACKEND/src/test/java/com/bank/fd/application/HistoryServiceTest.java
  modified: []

key-decisions:
  - "Penalty resolved from the local fd_rule_outcome_ref table via exactly-one CLOSURE_PENALTY match, never live product tables, so the quote is a stored-snapshot semantic"
  - "Approve applies the stored request-time quote verbatim and derives the closing accrual slice from stored settlement minus principal"
  - "History maps transaction codes to labels by resolving known code ids through TransactionTypeRepository, with a TRANSACTION fallback for unknown ids"

patterns-established:
  - "Closure/premature/history services mirror FundingService/MaturityService: findByIdForUpdate lock, FdActorResolver holder match, pure state-machine guard before writes, UUID on every row, explicit DTO returns"
  - "Penalty math delegates to PrematurePenalty plus FdInterestCalculator; legacy double calculator never touched"

requirements-completed: [LIFE-03, LIFE-04, HIST-01]

# Metrics
duration: 15min
completed: 2026-09-22
---

# Phase 04 Plan 02: Closure, Premature-Closure, and History Services Summary

**Holder-checked close/penalty/history business rules behind thin-service boundaries with unit proof; no endpoint exposed yet**

## Performance

- **Duration:** ~15 min
- **Started:** 2026-09-22T12:35:00Z
- **Completed:** 2026-09-22T12:52:00Z
- **Tasks:** 2
- **Files modified:** 10

## Accomplishments

- Normal closure: MATURED closes to CLOSED with one CLOSURE settlement row (amount = maturityAmount, balance_after = same); every other status rejected pre-write via FdLifecycleCommand.CLOSE
- Two-step premature closure: PENDING request persists a penalized-rate quote (182d at 0.0650 with 1.00-point penalty yields 0.0550, foregone 498.63, settlement 102742.47); same-customer approve writes PENALTY (null balance) plus CLOSURE rows, a closing accrual slice, and transitions to PREMATURELY_CLOSED
- Unsupported rule fails closed: missing, ambiguous, or unparseable CLOSURE_PENALTY raises PrematureClosureNotSupportedException naming the rule, with zero writes and the FD left open
- Unified history: one ownership-checked read merges DEPOSIT/MATURITY/CLOSURE/PENALTY/accrual rows into a time-ordered full-detail timeline with amounts, dates, and running balances
- 16 new unit tests green (5 ClosureServiceTest, 8 PrematureClosureServiceTest, 3 HistoryServiceTest); compile clean; no controller or OpenAPI file touched

## Task Commits

Each task was committed atomically:

1. **Task 1: Closure DTOs, unsupported-rule exception, and normal ClosureService** - `0110ed2` (feat)
2. **Task 2: Premature-closure two-step service and unified history service** - `9070319` (feat)

## Files Created/Modified

- `BACKEND/src/main/java/com/bank/fd/api/dto/ClosureResponse.java` - Snake_case normal-closure DTO with closed(...) factory and truthful internal-settlement remark
- `BACKEND/src/main/java/com/bank/fd/api/dto/PrematureClosureResponse.java` - PENDING/APPROVED two-step DTO with requested(...) and approved(...) factories
- `BACKEND/src/main/java/com/bank/fd/api/dto/HistoryEntry.java` - Full-detail D-06 timeline entry with nullable runningBalance and TRANSACTION|ACCRUAL source
- `BACKEND/src/main/java/com/bank/fd/exception/PrematureClosureNotSupportedException.java` - BankingException naming the missing/unusable CLOSURE_PENALTY rule (Plan 03 maps to 422)
- `BACKEND/src/main/java/com/bank/fd/application/ClosureService.java` - MATURED-only close with CLOSURE settlement row
- `BACKEND/src/main/java/com/bank/fd/application/PrematureClosureService.java` - Self-service request/approve with stored-snapshot settlement
- `BACKEND/src/main/java/com/bank/fd/application/HistoryService.java` - Merged time-ordered timeline read
- `BACKEND/src/test/java/com/bank/fd/application/ClosureServiceTest.java` - Happy path, ACTIVE/CLOSED rejection, missing/non-owner rejection, transition encoding
- `BACKEND/src/test/java/com/bank/fd/application/PrematureClosureServiceTest.java` - D-01 known-value quote, missing/unparseable-rule no-write, non-active rejection, approve snapshot application, double-approve and wrong-account rejection, missing request
- `BACKEND/src/test/java/com/bank/fd/application/HistoryServiceTest.java` - Date-ordered merge with running balances, PENALTY null-balance mapping, ownership gating before merge

## Decisions Made

- Penalty outcome resolved from the local `fd_rule_outcome_ref` table (findAll filtered to exactly one CLOSURE_PENALTY row) rather than re-querying live product/rule tables, honoring the plan's stored-snapshot requirement; the outcome row id is persisted as `appliedRuleOutcomeId` on the closure row.
- Approve derives the closing accrual slice amount from the stored snapshot (settlement minus principal, scale-4) while dating it at the approve-time clamped elapsed end, so request/approve/history amounts always agree.
- History resolves known transaction codes to labels by comparing type ids from `findIdByCode` (DEPOSIT to FUNDING, INTEREST_ACCRUAL to ACCRUAL, MATURITY, CLOSURE, PENALTY), falling back to TRANSACTION for unknown ids; no repository interface change was needed.
- Free-form reason text is trimmed and capped at 500 characters to match the `fpc_reason` column; blank reasons persist as null.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Test fixture elapsed-day off-by-one**
- **Found during:** Task 2 (first `PrematureClosureServiceTest` run: 1 failure, expected penalty 498.63 mismatch)
- **Issue:** Fixture booking date 2025-07-02 yields 183 elapsed days to the fixed 2026-01-01 business date, not the plan's 182d known-value case
- **Fix:** Moved fixture booking date to 2025-07-03 for exactly 182 elapsed days; plan's quoted values (penalized 0.0550, foregone 498.63, settlement 102742.47) then hold exactly
- **Files modified:** BACKEND/src/test/java/com/bank/fd/application/PrematureClosureServiceTest.java
- **Commit:** 9070319

**2. [Rule 2 - Missing critical functionality] Null-snapshot guards before writes**
- **Found during:** Task 1 and Task 2 implementation
- **Issue:** A MATURED account with null maturityAmount, or an ACTIVE account with null booking date/rate, would NPE mid-write or persist a corrupt row
- **Fix:** Explicit InvalidInputException guards before any write in ClosureService (null maturityAmount) and requestPrematureClosure (null booking date, null rate)
- **Files modified:** ClosureService.java, PrematureClosureService.java
- **Commit:** 0110ed2, 9070319

## Issues Encountered

- None beyond the fixture day-count above; combined run of all three test classes is 16/16 green and `mvn -DskipTests compile` passes.

## Threat Flags

None - no new network endpoints, auth paths, file access, or trust-boundary schema changes beyond the plan's threat model. T-04-05 through T-04-09 mitigations are implemented as specified: holder match in all three services, server-recomputed amounts (request body carries only optional reason text), stored-snapshot approve with ACTIVE re-validation, sequential PENDING/ACTIVE guards before writes, ownership check before history merge with DTO-only output.

## Known Stubs

None. `runningBalance` is null only for penalty records by design (no balance_after exists for those rows), which HistoryServiceTest pins explicitly.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- All close/penalty/history business rules are service-encapsulated; Plan 03 can wire the four controller placeholders (`closeAccount`, `requestPrematureClosure`, `getPrematureClosure`, `approvePrematureClosure`) thinly plus the 422 mapping for PrematureClosureNotSupportedException.
- Unrelated 501 placeholders (payouts, notices, statements, events, sync) remain untouched; concurrent-idempotency hardening stays Phase 5 per CONTEXT deferred list.

## Self-Check: PASSED

- FOUND: BACKEND/src/main/java/com/bank/fd/api/dto/ClosureResponse.java
- FOUND: BACKEND/src/main/java/com/bank/fd/api/dto/PrematureClosureResponse.java
- FOUND: BACKEND/src/main/java/com/bank/fd/api/dto/HistoryEntry.java
- FOUND: BACKEND/src/main/java/com/bank/fd/exception/PrematureClosureNotSupportedException.java
- FOUND: BACKEND/src/main/java/com/bank/fd/application/ClosureService.java
- FOUND: BACKEND/src/main/java/com/bank/fd/application/PrematureClosureService.java
- FOUND: BACKEND/src/main/java/com/bank/fd/application/HistoryService.java
- FOUND: BACKEND/src/test/java/com/bank/fd/application/ClosureServiceTest.java
- FOUND: BACKEND/src/test/java/com/bank/fd/application/PrematureClosureServiceTest.java
- FOUND: BACKEND/src/test/java/com/bank/fd/application/HistoryServiceTest.java
- FOUND: 0110ed2, FOUND: 9070319

---
*Phase: 04-closure-and-demonstrable-api-workflow*
*Completed: 2026-09-22*
