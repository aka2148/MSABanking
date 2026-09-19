---
phase: 02-product-booking-and-funding
plan: 03
subsystem: api/database/testing
tags: [fixed-deposit, funding, lifecycle, PostgreSQL, pessimistic-locking, OpenAPI]

requires:
  - phase: 02-product-booking-and-funding
    provides: authenticated pending booking snapshots, local FD references, DEPOSIT transaction type, and typed funding request
provides:
  - locked exact-principal internal funding service
  - PENDING to ACTIVE lifecycle transition with one DEPOSIT history record
  - authenticated funding endpoint and OpenAPI request/response schemas
  - unit and Docker-gated PostgreSQL funding verification
affects: [03-interest-and-maturity-processing, 04-closure-and-api, 05-protection-tests-regression]

tech-stack:
  added: []
  patterns: [transactional pessimistic account lock, authenticated holder resolution, internal-record truthful response, PostgreSQL Testcontainers flow]

key-files:
  created:
    - BACKEND/src/main/java/com/bank/fd/application/FundingService.java
    - BACKEND/src/main/java/com/bank/fd/api/dto/FundingResponse.java
    - BACKEND/src/test/java/com/bank/fd/application/FundingServiceTest.java
    - BACKEND/src/test/java/com/bank/fd/FdBookingFundingIntegrationTest.java
  modified:
    - BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java
    - BACKEND/src/main/java/com/bank/fd/entity/FdAccount.java
    - BACKEND/src/main/java/com/bank/fd/domain/FdLifecycleStateMachine.java
    - BACKEND/src/main/java/com/bank/fd/repository/FdAccountRepository.java
    - BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml
    - BACKEND/src/test/java/com/bank/fd/api/FdV1BookingContractTest.java

key-decisions:
  - "Funding locks the account first, resolves the authenticated holder, validates exact principal/currency/PENDING state, and commits transaction plus FUND transition in one service transaction."
  - "Funding is represented only as an internal academic DEPOSIT record; the response explicitly states that no external payment rail was called."
  - "The existing Phase 1 lifecycle state machine is a Spring component so the funding service cannot bypass the FUND boundary."

patterns-established:
  - "Financial mutation uses findByIdForUpdate and @Transactional rather than JVM-only synchronization."
  - "Funding response is an explicit DTO with ACTIVE and funding_recorded=true, not direct entity serialization."

requirements-completed: [FUND-01]

duration: 25min
completed: 2026-09-19
---

# Phase 2 Plan 3: Product Booking and Funding Summary

**Authenticated exact-principal funding now locks a pending FD, records one internal DEPOSIT, applies the Phase 1 FUND transition, and truthfully returns ACTIVE through `/fd/v1`.**

## Performance

- **Duration:** approximately 25 min
- **Started:** 2026-09-19T14:55:00Z
- **Completed:** 2026-09-19T15:20:00Z
- **Tasks:** 2/2
- **Files modified:** 10 application, contract, and test files

## Accomplishments

- Implemented a transactional funding service with pessimistic account locking, authenticated holder matching, exact `BigDecimal` principal and currency validation, DEPOSIT lookup, UTC transaction timestamp, and rollback-safe account/history writes.
- Applied `FdLifecycleCommand.FUND` through the existing state machine and added a truthful active funding response that identifies the operation as internal academic recordkeeping.
- Wired `POST /fd/v1/accounts/{accountId}/fund`, documented typed funding schemas and errors in OpenAPI, and added unit, contract, and PostgreSQL/Testcontainers flow coverage.

## Task Commits

Each task was committed atomically:

1. **Task 1: Implement locked exact-principal funding** - `ac8ede5` (test RED), `1a26634` (feat GREEN)
2. **Task 2: Wire fund endpoint and PostgreSQL flow** - `c9d6485` (feat)

## Verification

- `mvn -q -Dtest=FundingServiceTest test` — PASS, 3 tests.
- `mvn -q "-Dtest=FundingServiceTest,FdBookingFundingIntegrationTest,FdV1BookingContractTest,FdV1ContractBoundaryTest" test` — PASS; unit/contract assertions passed and 3 PostgreSQL integration tests skipped because Docker is unavailable.
- `mvn -q -DskipTests compile` — PASS.
- `mvn -q test` — PASS; Docker-gated PostgreSQL classes skipped only because no valid Docker environment is available locally.
- Angular files, legacy `/api/fd`, and unrelated `/fd/v1` placeholders were not changed.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 2 - Missing Critical] Added lifecycle mutation boundary required by funding**
- **Found during:** Task 1 implementation
- **Issue:** `FdAccount` exposed the lifecycle status getter but no mutation method, so a successful state-machine transition could not be persisted.
- **Fix:** Added the minimal `setStatus` entity mutator and registered the existing state machine as a Spring component for service injection.
- **Files modified:** `BACKEND/src/main/java/com/bank/fd/entity/FdAccount.java`, `BACKEND/src/main/java/com/bank/fd/domain/FdLifecycleStateMachine.java`
- **Verification:** FundingServiceTest and full Maven suite pass.
- **Committed in:** `1a26634`

---

**Total deviations:** 1 auto-fixed (Rule 2: 1).
**Impact on plan:** The minimal persistence wiring was necessary to apply the required FUND transition; no new architecture or payment path was introduced.

**2. [Rule 3 - Blocking] Repaired planning progress metadata after SDK marker mismatch**
- **Found during:** Plan completion metadata updates
- **Issue:** The roadmap and requirements handlers did not recognize this repository's checkbox/table formatting consistently.
- **Fix:** Manually marked 02-03 complete in ROADMAP.md and FUND-01 complete in the REQUIREMENTS.md traceability table.
- **Files modified:** `.planning/ROADMAP.md`, `.planning/REQUIREMENTS.md`
- **Verification:** Re-read both artifacts; Phase 2 is 3/3 Complete and FUND-01 is checked/Complete.
- **Committed in:** final plan metadata commit

---

**Total deviations:** 2 auto-fixed (Rule 2: 1, Rule 3: 1).
**Impact on plan:** Both fixes were necessary for lifecycle persistence and truthful project progress tracking; no new architecture or payment path was introduced.

## Issues Encountered

- Docker Desktop was unavailable. The Testcontainers class is configured with `disabledWithoutDocker=true`, so the PostgreSQL booking/funding assertions are skipped locally and will execute when Docker is enabled.
- Maven regenerated existing tracked/untracked `BACKEND/target` artifacts during verification; these pre-existing/generated changes were preserved and not staged.

## Known Stubs

- Existing unrelated `/fd/v1` lifecycle/history placeholders remain intentional 501 operations owned by later phases. No stub blocks the funding goal in files changed by this plan.

## Threat Surface Scan

No new unmodeled threat flags. Funding uses the planned authenticated actor boundary, row lock, exact financial comparisons, lifecycle command, and internal transaction audit fields.

## User Setup Required

None. Docker is required only to execute, rather than skip, the PostgreSQL integration assertions.

## Next Phase Readiness

Phase 2 product lookup, booking, and funding are ready for the interest/maturity phase. Active accounts now have a persisted internal DEPOSIT history record and an explicit lifecycle boundary. Full duplicate-action hardening remains deferred to Phase 5 as planned.

## Self-Check: PASSED

- Summary file exists at `.planning/phases/02-product-booking-and-funding/02-03-SUMMARY.md`.
- Task commits `ac8ede5`, `1a26634`, and `c9d6485` exist in git history.
- All created implementation/test files exist, and focused tests plus compile/full-suite verification completed successfully with Docker skips explicitly recorded.

---
*Phase: 02-product-booking-and-funding*
*Completed: 2026-09-19*
