---
phase: 05-protection-tests-and-regression-gate
plan: 01
subsystem: api
tags: [spring-boot, idempotency, ownership, security, fd]

# Dependency graph
requires:
  - phase: 01-existing-fd-baseline
    provides: FdOperationIdempotency entity + repository durable operation identity
provides:
  - FdIdempotencyGuard durable claim/replay guard (claim/complete/sha256Hex)
  - FdDuplicateOperationException 409 conflict carrying existing-record reference
  - FdAccessPolicy holder-match + staff-bypass ownership check with 404 hiding
  - GlobalExceptionHandler 409 mapping + SecurityConfig Idempotency-Key CORS
affects: [05-02 service wiring, 05-03 tests, fd booking funding accrual maturity closure]

# Tech tracking
tech-stack:
  added: []
  patterns: [actor-scoped idempotency claims, 404-hiding ownership policy, single-envelope error mapping]

key-files:
  created:
    - BACKEND/src/main/java/com/bank/fd/exception/FdDuplicateOperationException.java
    - BACKEND/src/main/java/com/bank/fd/application/FdIdempotencyGuard.java
    - BACKEND/src/main/java/com/bank/fd/application/FdAccessPolicy.java
  modified:
    - BACKEND/src/main/java/com/bank/common/exception/GlobalExceptionHandler.java
    - BACKEND/src/main/java/com/bank/security/config/SecurityConfig.java

key-decisions:
  - "Same key + same hash replays the recorded claim; same key + different hash is a 409 with existing-record reference"
  - "Cross-customer FD reads throw ResourceNotFoundException (404), never AccessDeniedException (403)"
  - "Actor key is the resolved fd_user_ref identity, never caller-supplied"

patterns-established:
  - "Idempotency: claim(scope, actorKey, key, sha256Hex(canonicalJson)) then complete(claim, aggregateId, responseReference)"
  - "Ownership: FdAccessPolicy.requireOwned(username, account, authorities/authentication)"

requirements-completed: [AUTH-01, AUTH-02]

# Metrics
duration: 12min
completed: 2026-09-22
---

# Phase 05 Plan 01: Protection Foundation Summary

**Durable idempotency guard with replay/409 semantics, holder-match ownership policy with staff bypass hidden behind 404, and 409/CORS error-contract wiring**

## Performance

- **Duration:** 12 min
- **Started:** 2026-09-22T15:55:29Z
- **Completed:** 2026-09-22T10:27:00Z (approx, UTC+05:30 local test run 15:56-15:57 IST)
- **Tasks:** 3
- **Files modified:** 5

## Accomplishments

- `FdDuplicateOperationException` carries `existingReference` with truthful internal wording (`Duplicate <scope> operation; existing record: <ref>`)
- `FdIdempotencyGuard` persists PENDING claims, returns same-key+same-hash replays, throws 409 on hash mismatch, blanks return null for natural-duplicate fallback, plus `sha256Hex` helper
- `FdAccessPolicy` resolves holder via `FdActorResolver`, passes holder-match or `ROLE_BANK_OFFICER`/`ROLE_ADMIN`, else throws `ResourceNotFoundException("FD account not found")` (404, no existence leak, zero `AccessDeniedException`)
- `GlobalExceptionHandler` maps duplicates to 409 via existing `buildResponse` envelope; `SecurityConfig` CORS allows `Idempotency-Key`; existing 403 handler and matchers untouched

## Task Commits

Each task was committed atomically:

1. **Task 1: Create duplicate exception and durable idempotency guard** - `8936b67` (feat)
2. **Task 2: Create ownership policy with 404 semantics and staff bypass** - `5a4f82f` (feat)
3. **Task 3: Extend error mapping and CORS for the protection contract** - `150f62d` (feat)

## Files Created/Modified

- `BACKEND/src/main/java/com/bank/fd/exception/FdDuplicateOperationException.java` - 409 conflict carrying existing-record reference
- `BACKEND/src/main/java/com/bank/fd/application/FdIdempotencyGuard.java` - Durable claim/replay guard over FdOperationIdempotencyRepository
- `BACKEND/src/main/java/com/bank/fd/application/FdAccessPolicy.java` - Holder-match + staff-bypass ownership check throwing 404
- `BACKEND/src/main/java/com/bank/common/exception/GlobalExceptionHandler.java` - New 409 handler for FdDuplicateOperationException
- `BACKEND/src/main/java/com/bank/security/config/SecurityConfig.java` - Idempotency-Key added to CORS allowedHeaders

## Decisions Made

- Same key + same hash returns the stored claim regardless of PENDING/COMPLETED so in-flight double-submits also replay safely (plan's COMPLETED branch generalized without changing the COMPLETED path).
- Mismatch reference falls back to `aggregate:<id>` when `responseReference` is not yet set (PENDING collision), keeping the 409 truthful.
- `sha256Hex(null)` hashes the empty string so callers never NPE on absent bodies.

## Deviations from Plan

None - plan executed exactly as written.

## Issues Encountered

None. `mvn -q compile` succeeded; `mvn -Dtest='Fd*Test' test` → Tests run: 58, Failures: 0, Errors: 0, Skipped: 5 (Docker-gated Testcontainers tests skip, per established pattern).

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

- Plans 05-02/05-03 can wire `FdIdempotencyGuard.claim/complete` into booking/funding/accrual/maturity/closure services and call `FdAccessPolicy.requireOwned` at the 9 ownership sites.
- Note for planners: booking has no natural key-less dedup signal (two key-less bookings legitimately create two pending FDs); key-less 409 applies to fund/accrue/mature/close repeats.

## Self-Check: PASSED

- FOUND: BACKEND/src/main/java/com/bank/fd/exception/FdDuplicateOperationException.java
- FOUND: BACKEND/src/main/java/com/bank/fd/application/FdIdempotencyGuard.java
- FOUND: BACKEND/src/main/java/com/bank/fd/application/FdAccessPolicy.java
- FOUND commits: 8936b67, 5a4f82f, 150f62d

---
*Phase: 05-protection-tests-and-regression-gate*
*Completed: 2026-09-22*
