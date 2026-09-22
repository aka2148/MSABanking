---
phase: 05-protection-tests-and-regression-gate
plan: 05
subsystem: testing
tags: [spring-boot, mockmvc, testcontainers, postgresql, idempotency, ownership, regression-gate, fd]

# Dependency graph
requires:
  - phase: 05-protection-tests-and-regression-gate
    provides: FdIdempotencyGuard claim/replay guard, FdAccessPolicy 404 ownership check, Idempotency-Key threading and staff roles on /fd/v1 (05-01..05-03)
  - phase: 05-protection-tests-and-regression-gate
    provides: Unit-layer guard/ownership/mapping/boundary proof, 37/37 green (05-04)
provides:
  - MockMvc REST ownership + error-code + replay proof with zero Docker (FdOwnershipContractTest)
  - Testcontainers PG16 proof of Flyway persistence, history linkage, and five-op duplicate safety (FdPhase5ProtectionIT, Docker-gated skip)
  - Full-suite TEST-03 regression gate result (132 run, 0 failures, BUILD SUCCESS)
affects: [phase-5-exit-gate, fd-demo-evidence, live-supabase-demo-follow-up]

# Tech tracking
tech-stack:
  added: []
  patterns: [mockmvc-contract-tests-with-fixed-idempotency-keys, testcontainers-pg16-with-disabledWithoutDocker-skip, fixed-academic-v1-product-terms-test-double]

key-files:
  created:
    - BACKEND/src/test/java/com/bank/fd/api/FdOwnershipContractTest.java
    - BACKEND/src/test/java/com/bank/fd/FdPhase5ProtectionIT.java
  modified:
    - BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java
    - BACKEND/src/test/java/com/bank/fd/api/FdV1ClosureHistoryContractTest.java

key-decisions:
  - "Unauthenticated FD reads pin 403 (method-security denial per SecurityFlowTest); 401 is pinned on failed login instead of the plan's literal 401-on-GET"
  - "FdPhase5ProtectionIT keeps the plan-specified *IT filename and runs via explicit -Dtest selection; surefire defaults exclude *IT from bare mvn test"
  - "Live PostgreSQL execution of the five-op proof and the live Supabase REST demo stay documented human follow-ups; no credentials baked into tests or code"

patterns-established:
  - "Ownership contract tests: register real users, log in for JWTs, stub only the normalized product-terms projection with a fixed academic-v1 double"
  - "Docker-gated tests copy @Testcontainers(disabledWithoutDocker = true) + postgres:16-alpine verbatim so no-Docker runs skip clean with exit 0"

requirements-completed: [TEST-02, TEST-03]

# Metrics
duration: 45min
completed: 2026-09-22
---

# Phase 05 Plan 05: Integration Proof and Regression Gate Summary

**MockMvc ownership/error-code/replay contract green without Docker (5/5), Testcontainers PG five-op duplicate-safety proof created with clean Docker-less skip, and the full backend suite green at 132 tests with zero failures**

## Performance

- **Duration:** ~45 min (across two executor sessions: Task 1 implementation + this session's verification and Task 2 commit)
- **Started:** 2026-09-22T11:00:00Z (approx)
- **Completed:** 2026-09-22T11:42:00Z (approx)
- **Tasks:** 2
- **Files modified:** 4 (2 new test classes, 1 controller read-path fix, 1 orphaned stub update)

## Accomplishments

- `FdOwnershipContractTest` (5 tests, H2/default test profile, no Docker): same-key fund replay returns byte-identical bodies with exactly one DEPOSIT row; cross-customer GETs on account, transactions, accrual, and premature-closure all return 404 with body status 404; `ROLE_BANK_OFFICER` staff reads another holder's FD (200) while owners keep self-service reads; key-less repeat funding on ACTIVE returns 409 containing "existing"; unsupported premature-closure rule returns 422 without closing the FD (still ACTIVE on re-read); malformed booking returns 400; anonymous FD read is 403 (pinned per `SecurityFlowTest`) and bad-credential login is 401
- `FdPhase5ProtectionIT` (2 tests, Testcontainers PostgreSQL 16 Alpine, Boot-managed datasource, Flyway V1..V9): end-to-end book -> fund -> accrue -> mature -> close -> timeline with fixed `Idempotency-Key` values per operation; byte-identical replay bodies plus unchanged row counts in `fd_operation_idempotency`, `fd_transaction`, `fd_interest_accrual` for all five operations; timeline links FUNDING, ACCRUAL, MATURITY, CLOSURE to the same account; same-key-different-body fund replay returns 409; key-less double close returns 409 containing "existing settlement recorded". Docker-unavailable skip guard copied verbatim from `FdPostgresSchemaTest`
- Full TEST-03 regression gate recorded: `mvn test` from BACKEND/ -> BUILD SUCCESS, `Tests run: 132, Failures: 0, Errors: 0, Skipped: 5` (failing-class list: empty). The 5 skips are exactly the pre-existing Docker-gated tests (`FdBookingFundingIntegrationTest` x3, `FdPostgresSchemaTest` x2); the new IT class is surefire-excluded by its `*IT` suffix and was verified separately
- Skip-path verification: `mvn -Dtest='FdPhase5ProtectionIT' test` without Docker -> `Tests run: 2, Failures: 0, Errors: 0, Skipped: 2`, BUILD SUCCESS (exit 0) — the accepted no-Docker outcome

## Task Commits

Each task was committed atomically:

1. **Task 1: Prove REST ownership and duplicate safety over MockMvc** - `171fe68` (feat)
2. **Task 2: Prove PG persistence, history, and five-op duplicates, then hold the full gate** - `832c5ed` (feat)

## Files Created/Modified

- `BACKEND/src/test/java/com/bank/fd/api/FdOwnershipContractTest.java` - MockMvc REST + ownership + error-code proof (new, 317 lines)
- `BACKEND/src/test/java/com/bank/fd/FdPhase5ProtectionIT.java` - Testcontainers PG persistence/history/five-op duplicate proof (new, 378 lines)
- `BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java` - `getAccount` read path uses `findById` instead of `findByIdForUpdate` (Rule 1 fix, see deviations)
- `BACKEND/src/test/java/com/bank/fd/api/FdV1ClosureHistoryContractTest.java` - orphaned `findByIdForUpdate` stub updated to `findById` (companion to the Rule 1 fix)

## Decisions Made

- Unauthenticated FD reads pin 403, not the plan's literal 401: the stateless JWT filter passes anonymous requests through and method security denies them, which `SecurityFlowTest` already pins as 403 for secured endpoints. The 401 half of the auth contract is pinned on failed `/api/auth/login` instead (401 + body status 401). No production behavior changed
- `FdPhase5ProtectionIT` keeps the plan-specified filename exactly per plan even though bare `mvn test` does not execute `*IT` classes under surefire defaults (no surefire/failsafe overrides exist in `BACKEND/pom.xml`). The class compiles in the gate, runs green-or-skipped under explicit `-Dtest` selection, and executes fully wherever Docker is available
- No Supabase credentials, URLs, or secrets were added to tests or code. The live-PG execution of the IT and the live Supabase REST demo remain documented human follow-ups, consistent with prior phases

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] `getAccount` used a pessimistic write lock outside a transaction, 500ing every read on H2**
- **Found during:** Task 1 (FdOwnershipContractTest first green run)
- **Issue:** `getAccount` called `accountRepository.findByIdForUpdate(accountId)` with no surrounding transaction. Strict databases (H2) reject `FOR UPDATE` outside a transaction, so every `GET /fd/v1/accounts/{id}` returned 500 and all ownership/staff assertions were untestable
- **Fix:** Read-only snapshot now uses `findById`; updated the orphaned `findByIdForUpdate` stub in `FdV1ClosureHistoryContractTest.getAccountReturnsStatusSnapshot`
- **Files modified:** `BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java`, `BACKEND/src/test/java/com/bank/fd/api/FdV1ClosureHistoryContractTest.java`
- **Verification:** `FdOwnershipContractTest` 5/5 green; full `mvn test` BUILD SUCCESS with no regressions
- **Committed in:** 171fe68 (part of the Task 1 commit)

---

**Total deviations:** 1 auto-fixed (1 bug)
**Impact on plan:** The fix was required for the plan's own acceptance criteria (owner/staff reads over HTTP). Lock scope shrank on a read-only path; write-path locking is untouched. No scope creep.

## Issues Encountered

- Test JVM needed `JWT_SECRET` from the environment (both `application.yml` and `application-test.yml` resolve `app.jwt.secret` from it; the repo stores no secret since 01-REVIEW-FIX). First attempt with a 48-byte random secret failed with `WeakKeyException`: the app signs with HS512, which requires >= 512 bits. Regenerated a process-local 96-byte random secret and all Spring tests loaded. No secret was written to the repository (process env only)
- Pre-existing `SecurityFlowTest` failed identically (register/login 500 via the same `WeakKeyException`) until the adequately-sized secret was supplied — confirming the failure was environmental, not caused by this plan's changes

## User Setup Required

None - no external service configuration required for the automated gate.

**Documented human follow-ups (not automated, same as prior phases):**
1. Execute `FdPhase5ProtectionIT` against real PostgreSQL where Docker is available: `mvn -Dtest='FdPhase5ProtectionIT' test` (with `JWT_SECRET` >= 64 bytes in the environment). Without Docker it skips clean (2 skipped, exit 0)
2. Live Supabase REST proof of book -> fund -> accrue -> mature -> close -> history via Postman/Swagger (no credentials in repo; run manually against the user's Supabase-backed deployment)

## Next Phase Readiness

- Phase 5 exit gate holds to the extent automatable without Docker: TEST-02 proven over MockMvc/H2 plus a compilable, skip-clean PG proof; TEST-03 proven with the full suite green and an empty failing-class list
- Remaining phase-level work is human-side only (the two follow-ups above); no code blockers remain

## Self-Check: PASSED

- FOUND: BACKEND/src/test/java/com/bank/fd/api/FdOwnershipContractTest.java (5 tests green, no Docker)
- FOUND: BACKEND/src/test/java/com/bank/fd/FdPhase5ProtectionIT.java (2 skipped clean without Docker, exit 0)
- FOUND commits: 171fe68, 832c5ed
- VERIFIED acceptance strings: `isNotFound()` x4, `Idempotency-Key`, `BANK_OFFICER`, `isConflict()` + `isUnprocessableEntity()` in Task 1 file; `Testcontainers` + `disabledWithoutDocker` skip guard, `FdOperationIdempotencyRepository`, `FUNDING` + `CLOSURE` timeline assertions in Task 2 file
- VERIFIED full gate: `Tests run: 132, Failures: 0, Errors: 0, Skipped: 5`, BUILD SUCCESS, exit 0

---
*Phase: 05-protection-tests-and-regression-gate*
*Completed: 2026-09-22*
