---
phase: 05-protection-tests-and-regression-gate
plan: 02
subsystem: api
tags: [spring-boot, idempotency, ownership, fd, booking, funding, accrual, maturity]

# Dependency graph
requires:
  - phase: 05-protection-tests-and-regression-gate
    provides: FdIdempotencyGuard durable claim/replay guard and FdAccessPolicy 404 ownership check (05-01)
provides:
  - Idempotent booking/funding/accrual/maturity services with same-key replay returning the original DTO
  - Ownership enforcement (404, staff-aware) on all four money-creating paths including reads
  - Key-less 409 duplicate errors referencing the existing record for funding and maturity
  - InvalidFdTransitionException message constructor for duplicate-aware 409 errors
affects: [05-03 controller threading, 05-04 closure/history wiring, 05-05 replay-proof tests]

# Tech tracking
tech-stack:
  added: []
  patterns: [idempotencyKey overloads with key-less-compatible delegates, claim-before-transition inside locked transactions, SecurityContextHolder-fed ownership policy]

key-files:
  created: []
  modified:
    - BACKEND/src/main/java/com/bank/fd/application/AccountBookingService.java
    - BACKEND/src/main/java/com/bank/fd/application/FundingService.java
    - BACKEND/src/main/java/com/bank/fd/application/AccrualService.java
    - BACKEND/src/main/java/com/bank/fd/application/MaturityService.java
    - BACKEND/src/main/java/com/bank/fd/exception/InvalidFdTransitionException.java

key-decisions:
  - "Authorities for requireOwned come from SecurityContextHolder so staff bypass works without changing service signatures ahead of Plan 03"
  - "Maturity replay recomputes interest via FdInterestCalculator so the replayed DTO is identical to the original"
  - "Booking replay carries productCode|currencyCode in the claim responseReference to rebuild the identical pending DTO"

patterns-established:
  - "Service overloads: book/fund/runAccrual/processMaturity with trailing idempotencyKey plus a key-less delegate passing null"
  - "Replay rule: claim with a recorded aggregateId reloads the stored aggregate and rebuilds the DTO with zero new rows"
  - "Canonical request hashes: productId|principal|tenureMonths|CURRENCY, accountId|amount|CURRENCY, accountId|businessDate, accountId|MATURITY"

requirements-completed: [FUND-02, AUTH-01, AUTH-02]

# Metrics
duration: 6min
completed: 2026-09-22
---

# Phase 05 Plan 02: Service Idempotency and Ownership Wiring Summary

**Booking, funding, accrual, and maturity services replay same-key retries from the stored aggregate, hide cross-customer FDs behind 404, and reject key-less funding/maturity repeats with 409s referencing the existing record**

## Performance

- **Duration:** 6 min
- **Started:** 2026-09-22T16:01:17Z
- **Completed:** 2026-09-22T16:06:48Z
- **Tasks:** 2
- **Files modified:** 8 (5 main, 3 test)

## Accomplishments

- Booking claims `BOOKING` scope after holder resolution and terms validation; COMPLETED-claim replays reload the FD and return the identical pending DTO, key-less bookings still create new pending FDs
- Funding claims `FUNDING` inside the pessimistic-lock transaction before the FUND transition; replays rebuild the active DTO with no second DEPOSIT row, key-less repeats on ACTIVE get `already ACTIVE; existing funding transaction recorded`
- Accrual claims `ACCRUAL` after the ACTIVE check; replays reload the (accountId, accrualDate) row, key-less repeats keep the natural per-date read-back, and both read paths (`listAccruals`, `cumulative`) now 404 via the access policy
- Maturity claims `MATURITY` before the ACTIVE check; replays recompute interest deterministically for an identical matured DTO, key-less repeats on MATURED get `already MATURED; existing maturity transaction recorded`
- Zero remaining `new AccessDeniedException("Authenticated customer does not own...")` in the four services; controller untouched (Plan 03 owns it) with old overloads delegating null keys

## Task Commits

Each task was committed atomically:

1. **Task 1: Harden booking and funding with idempotency plus ownership** - `401a421` (feat)
2. **Task 2: Harden accrual and maturity with idempotency plus ownership** - `d0267a0` (feat)

## Files Created/Modified

- `BACKEND/src/main/java/com/bank/fd/application/AccountBookingService.java` - BOOKING claim/replay/complete plus `book(username, request, idempotencyKey)` overload
- `BACKEND/src/main/java/com/bank/fd/application/FundingService.java` - FUNDING claim/replay/complete, requireOwned, key-less ACTIVE 409
- `BACKEND/src/main/java/com/bank/fd/application/AccrualService.java` - ACCRUAL claim/replay/complete, requireOwned on mutating + both read paths
- `BACKEND/src/main/java/com/bank/fd/application/MaturityService.java` - MATURITY claim/replay/complete, requireOwned, key-less MATURED 409
- `BACKEND/src/main/java/com/bank/fd/exception/InvalidFdTransitionException.java` - New message constructor for duplicate-aware 409 errors
- `BACKEND/src/test/java/com/bank/fd/application/FundingServiceTest.java` - Wired mock guard + real access policy
- `BACKEND/src/test/java/com/bank/fd/application/AccountBookingServiceTest.java` - Wired mock guard
- `BACKEND/src/test/java/com/bank/fd/application/AccrualMaturityServiceTest.java` - Wired mock guard + real access policy

## Decisions Made

- Authorities for `requireOwned` are pulled from `SecurityContextHolder` inside the services (actor still the JWT username parameter), so staff bypass is live without pre-empting Plan 03's controller threading; unit tests with an empty context fall back to holder-match.
- Maturity replay recomputes interest with `FdInterestCalculator` from stored fields rather than deriving it from persisted amounts, keeping the replayed DTO bit-identical to the original.
- Booking replay parses `productCode|currencyCode` from the claim's response reference (written at `complete()` time) instead of re-resolving terms, with a terms-derived fallback that cannot trigger for claims this plan writes.
- Test files use a real `FdAccessPolicy` (backed by the existing `actorResolver` mock) plus a Mockito `FdIdempotencyGuard` returning null claims, so existing tests exercise the real 404 ownership path while staying on the natural key-less duplicate behavior.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

- Single-line acceptance grep patterns (`idempotencyGuard.claim("BOOKING"`, the `already ACTIVE/MATURED` messages) initially spanned line breaks; reformatted those two call sites to keep the matched substrings contiguous, re-ran the suite green, then committed.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plans 05-03/05-04 can thread `Idempotency-Key` from `FdV1ContractController` into the new overloads and wire closure/history services the same way; controller still calls the key-less delegates, so behavior is unchanged until then.
- Full replay-proof tests (same-key double-submit asserting zero new rows) arrive in Plan 05; current verification is existing service tests green (12/12) plus acceptance greps.

## Self-Check: PASSED

- FOUND: BACKEND/src/main/java/com/bank/fd/application/AccountBookingService.java (claim BOOKING)
- FOUND: BACKEND/src/main/java/com/bank/fd/application/FundingService.java (claim FUNDING, ACTIVE 409)
- FOUND: BACKEND/src/main/java/com/bank/fd/application/AccrualService.java (claim ACCRUAL)
- FOUND: BACKEND/src/main/java/com/bank/fd/application/MaturityService.java (claim MATURITY, MATURED 409)
- FOUND commits: 401a421, d0267a0

---
*Phase: 05-protection-tests-and-regression-gate*
*Completed: 2026-09-22*
