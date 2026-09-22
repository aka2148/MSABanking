---
phase: 04-closure-and-demonstrable-api-workflow
plan: 03
subsystem: api
tags: [rest, fixed-deposit, closure, premature-closure, history, openapi, mockmvc, error-handling]

# Dependency graph
requires:
  - phase: 04-closure-and-demonstrable-api-workflow
    provides: ClosureService, PrematureClosureService, HistoryService plus ClosureResponse/PrematureClosureResponse/HistoryEntry DTOs (04-02)
  - phase: 03-interest-and-maturity-processing
    provides: Accrual/maturity endpoint wiring pattern, AccrualResponse style, 04-02 endpoint precedent
provides:
  - Six wired /fd/v1 closure/status/history endpoints with CUSTOMER/isAuthenticated roles
  - InvalidFdTransitionException to 409 plus PrematureClosureNotSupportedException to 422 handlers (M-01)
  - ISO-8601 business_date validation to 400 instead of 500 (M-03)
  - OpenAPI ClosureResponse/PrematureClosureResponse/HistoryEntry schemas with truthful error entries
  - FdV1ClosureHistoryContractTest MockMvc proof of happy paths plus 400/409/422/501 shapes
affects: [04-demo-artifact, phase-5-authorization, fd-api-consumers]

# Tech tracking
tech-stack:
  added: []
  patterns: [controller delegates to application services with Authentication username, controller-side ownership-checked reads via FdActorResolver plus reference lookups, code-to-label txn-type mapping for HistoryEntry filtering]

key-files:
  created:
    - BACKEND/src/test/java/com/bank/fd/api/FdV1ClosureHistoryContractTest.java
  modified:
    - BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java
    - BACKEND/src/main/java/com/bank/common/exception/GlobalExceptionHandler.java
    - BACKEND/src/main/java/com/bank/fd/api/dto/AccountResponse.java
    - BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml
    - BACKEND/src/test/java/com/bank/fd/api/FdV1AccrualMaturityContractTest.java

key-decisions:
  - "getAccount and getPrematureClosure do ownership-checked reads in the controller via FdActorResolver plus reference lookups, since no account-query service exists and the plan forbids service-logic changes"
  - "txn_type_id filtering maps ids to HistoryEntry labels through the same code-to-label table HistoryService uses, because HistoryEntry carries no txn type id"
  - "OpenAPI getAccount references the runtime-truthful AccountResponse schema (not Account) and premature-closure request bodies are optional, keeping the spec honest with runtime behavior"

patterns-established:
  - "Implemented endpoints delegate to application services passing authentication.getName(); read endpoints resolve holder plus product/currency references in the controller and return DTOs only"
  - "Optional JSON bodies are validated for shape (business_date ISO-8601, reason text) before service delegation; amounts stay server-computed"

requirements-completed: [LIFE-03, LIFE-04, HIST-01, API-01, API-02]

# Metrics
duration: 20min
completed: 2026-09-22
---

# Phase 04 Plan 03: Closure/Status/History Endpoints Summary

**Close, two-step premature closure, account-status, and unified-history REST endpoints with truthful 400/409/422 errors, matching OpenAPI schemas, and MockMvc contract proof**

## Performance

- **Duration:** ~20 min
- **Started:** 2026-09-22T12:55:00Z
- **Completed:** 2026-09-22T13:15:00Z
- **Tasks:** 2
- **Files modified:** 6

## Accomplishments

- Six endpoints wired with typed DTOs: `POST .../close` (200 ClosureResponse, CUSTOMER), `POST .../premature-closure` (201 PENDING, CUSTOMER), `GET .../premature-closure` (time-ordered list with server-recomputed penalized rates, isAuthenticated), `POST .../premature-closure/{id}/approve` (200 APPROVED, CUSTOMER), `GET .../accounts/{id}` (AccountResponse status snapshot, isAuthenticated), `GET .../transactions` (HistoryEntry timeline with from_date/to_date/txn_type_id filters, isAuthenticated)
- Consistent error shapes: malformed business_date (and from/to filters, non-text reason) yield 400 via InvalidInputException; bad transitions yield 409; unsupported premature closure yields 422; ownership violations stay 403 and missing resources 404 through existing handlers
- M-01 closed: GlobalExceptionHandler gains InvalidFdTransitionException-to-409 and PrematureClosureNotSupportedException-to-422 handlers (most-specific wins over the BankingException 400 handler; existing mappings untouched)
- M-03 closed: extractBusinessDate wraps LocalDate.parse in try/catch to InvalidInputException; reused for premature-closure request/approve optional bodies so malformed dates never reach the 500 path
- OpenAPI documents ClosureResponse, PrematureClosureResponse, and HistoryEntry component schemas matching DTO JSON names; all six operations repointed with 400/403/404/409 entries (plus 422 on request); operationIds unchanged
- 10 new MockMvc contract tests green alongside all 6 prior accrual/maturity tests; full suite 97 run, 0 failures (5 Docker-gated Testcontainers skips, as before)
- 36 unrelated placeholders (payouts, notices, statements, events, sync, refs) verified still 501

## Task Commits

Each task was committed atomically:

1. **Task 1: Implement closure/status/history endpoints with consistent error handling** - `2f1db03` (feat)
2. **Task 2: Document OpenAPI schemas and prove contract with MockMvc tests** - `ebe024b` (feat)

## Files Created/Modified

- `BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java` - Six implemented endpoint bodies with role annotations; fixed date extractor plus date/reason/txn-type filter helpers; @Autowired ctor extended with closure services and read repositories (no-arg test seam kept)
- `BACKEND/src/main/java/com/bank/common/exception/GlobalExceptionHandler.java` - 409 transition plus 422 unsupported-rule handlers; no existing mapping altered
- `BACKEND/src/main/java/com/bank/fd/api/dto/AccountResponse.java` - New status(...) snapshot factory (funding_recorded = status != PENDING); pending(...) factory untouched
- `BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml` - Three new component schemas; six operations repointed with error entries; AccountResponse status enum extended with PREMATURELY_CLOSED
- `BACKEND/src/test/java/com/bank/fd/api/FdV1ClosureHistoryContractTest.java` - 10 MockMvc tests: close 200/409, premature 201/422, approve 200, premature-list with recomputed 0.0550 rate, account snapshot, history timeline, malformed-date 400 with zero service interaction, roles plus runPayout-501 preservation
- `BACKEND/src/test/java/com/bank/fd/api/FdV1AccrualMaturityContractTest.java` - Full-ctor construction; stale closeAccount-501 assertion replaced with runPayout-501 (closeAccount is now implemented)

## Decisions Made

- Controller-side reads: getAccount/getPrematureClosure resolve the holder via FdActorResolver and product/currency codes via reference repositories directly in the controller, mirroring FundingService lookups, because no account-query service exists and the plan forbids service-logic changes.
- txn_type_id filtering: HistoryEntry carries no txn type id, so the controller maps the id to an entry label through the same DEPOSIT-to-FUNDING code table HistoryService uses (unknown ids yield an empty list); date filters compare entryDate day parts and malformed values are 400. Documented in code.
- Premature-closure bodies are optional (`required = false`); the fixed extractor runs for validation only while the service clamps business dates server-side, and only free-form reason text is forwarded (amounts stay server-computed per T-04-11).
- OpenAPI truthfulness over literal plan wording: getAccount references AccountResponse (the actual runtime shape) rather than Account, and the premature-closure requestBody is optional with reason/business_date fields rather than the required CreatePrematureClosureRequest (component left untouched); AccountResponse status enum gains PREMATURELY_CLOSED since a prematurely closed account can be read.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Prior accrual contract test no longer compiled or asserted correctly**
- **Found during:** Task 1 (constructor extension plus closeAccount implementation)
- **Issue:** `FdV1AccrualMaturityContractTest` used the 6-arg controller constructor (compile break after adding closure/history params) and asserted `closeAccount` returns 501 via the no-arg seam (now implemented, would NPE)
- **Fix:** Moved the test to the full constructor with mocks for the new dependencies; replaced the stale assertion with `runPayout` still returning 501, exactly as the plan's Task 2 action anticipates
- **Files modified:** BACKEND/src/test/java/com/bank/fd/api/FdV1AccrualMaturityContractTest.java
- **Verification:** `mvn -Dtest='FdV1ClosureHistoryContractTest,FdV1AccrualMaturityContractTest' test` — 16/16 green
- **Committed in:** 2f1db03 (part of Task 1 commit)

**2. [Rule 2 - Missing critical functionality] Non-string reason and unknown txn-type behaviour unspecified**
- **Found during:** Task 1 (controller edge cases)
- **Issue:** A non-text `reason` value would flow into the 500-char trim path unpredictably, and `txn_type_id` had no defined mapping onto HistoryEntry (which carries no type id)
- **Fix:** Non-string reason throws InvalidInputException (400); txn_type_id maps through the HistoryService code-to-label table with unknown ids yielding an empty list instead of silently ignoring the filter
- **Files modified:** BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java
- **Verification:** Covered by design review plus full-suite green; date/reason validation paths mirror the pinned 400 contract test
- **Committed in:** 2f1db03 (part of Task 1 commit)

---

**Total deviations:** 2 auto-fixed (1 blocking, 1 missing critical)
**Impact on plan:** Both required for compilation correctness and truthful filtering. No scope creep; no service-logic changes; InvalidFdTransitionException left as-is.

## Issues Encountered

- PowerShell 5.1 environment lacks `tail`/`head`, so plan verify commands were run unpiped (`mvn -DskipTests compile`, `mvn -Dtest=... test`, `mvn test`) with equivalent coverage: compile success, 16/16 contract tests green, full suite 97 run with 0 failures.

## Threat Flags

None - all new endpoint/auth surface was already in the plan's threat model. T-04-10 mitigated (mutations CUSTOMER, reads isAuthenticated, every service call passes authentication.getName() into holder matching); T-04-11/T-04-13 mitigated (date try/catch to 400 before services, server-side clamping unchanged); T-04-12 mitigated (handlers return status/error/message only, no parser internals echoed); T-04-14 accepted per D-04 (self-service approve, staff approval deferred to Phase 5).

## Known Stubs

None. The 36 remaining 501 placeholders (payouts, notices, statements, events, sync, ref reads, holders, renew, activate) are intentional deferred surface for later phases, not stubs of this plan's goal. `runningBalance` nullability and `approved_by` null for PENDING are by-design DTO semantics pinned by tests.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Full demo path is now reachable via typed REST: book → fund → accrue → mature → close → history, plus the two-step premature-closure variant with the unsupported-rule 422 path.
- Remaining Phase 4 work (demo artifact: Postman collection per D-07) can be built directly against these endpoints plus the Phase 2/3 operations.
- Duplicate-action hardening and broader ownership/role protection stay Phase 5 per CONTEXT deferred list.

## Self-Check: PASSED

- FOUND: BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java
- FOUND: BACKEND/src/main/java/com/bank/common/exception/GlobalExceptionHandler.java
- FOUND: BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml
- FOUND: BACKEND/src/test/java/com/bank/fd/api/FdV1ClosureHistoryContractTest.java
- FOUND: 2f1db03, FOUND: ebe024b

---
*Phase: 04-closure-and-demonstrable-api-workflow*
*Completed: 2026-09-22*
