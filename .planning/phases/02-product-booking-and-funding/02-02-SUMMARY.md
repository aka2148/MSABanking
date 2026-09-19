---
phase: 02-product-booking-and-funding
plan: 02
subsystem: api/booking
tags: [fixed-deposit, booking, authentication, BigDecimal, OpenAPI]
requires:
  - phase: 02-product-booking-and-funding
    provides: authoritative terms adapter, booking DTOs, local FD reference repositories
provides:
  - authenticated eligible FD offer lookup
  - actor-to-fd_user_ref resolution
  - transactional pending booking snapshots and Actual/365 estimates
affects: [02-03 funding, 03 interest and maturity processing]
tech-stack:
  added: []
  patterns: [server-authoritative booking, authenticated actor resolution, explicit pending response]
key-files:
  created:
    - BACKEND/src/main/java/com/bank/fd/application/FdActorResolver.java
    - BACKEND/src/main/java/com/bank/fd/application/ProductOfferQueryService.java
    - BACKEND/src/main/java/com/bank/fd/application/AccountBookingService.java
    - BACKEND/src/main/java/com/bank/fd/api/dto/AccountResponse.java
    - BACKEND/src/test/java/com/bank/fd/api/FdV1BookingContractTest.java
    - BACKEND/src/test/java/com/bank/fd/application/AccountBookingServiceTest.java
  modified:
    - BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java
    - BACKEND/src/main/java/com/bank/fd/application/NormalizedProductTermsAdapter.java
    - BACKEND/src/main/java/com/bank/fd/application/ProductTermsPort.java
    - BACKEND/src/main/java/com/bank/fd/domain/FdTimePolicy.java
    - BACKEND/src/main/java/com/bank/fd/entity/FdCurrencyReference.java
    - BACKEND/src/main/java/com/bank/fd/entity/FdUserReference.java
    - BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml
key-decisions:
  - "Customer booking derives holder identity exclusively from the authenticated local username and creates the minimal fd_user_ref when absent."
  - "Booking uses server branch 000 and simple Actual/365 BigDecimal interest with HALF_UP for the academic-v1 estimate."
  - "Legacy /api/fd and unrelated /fd/v1 operations remain unchanged explicit placeholders."
requirements-completed: [PROD-01, BOOK-01, BOOK-02, BOOK-03]
duration: 12min
completed: 2026-09-19
---

# Phase 2 Plan 2: Product Booking and Funding Summary

**Authenticated REST product offers and server-authoritative pending FD booking snapshots with locked terms and simple Actual/365 maturity estimates.**

## Performance

- **Duration:** approximately 12 min
- **Tasks:** 2/2
- **Implementation commits:** `3e56509`, `67df4a3`, `f4ce06b`

## Accomplishments

- Added authenticated `GET /fd/v1/products` projection from the normalized terms authority with supported amount, tenure, rate, frequency, penalty, and estimate-basis information.
- Added actor resolution from Spring Security username through `CustomerService` to a server-owned `fd_user_ref`; request DTOs contain no holder/customer identity.
- Implemented transactional `POST /fd/v1/accounts` booking with server-resolved terms, UTC dates, branch `000`, policy snapshot, PENDING status, and `funding_recorded=false`.
- Added explicit BigDecimal simple-interest Actual/365 calculation and rejected unsupported compounding rather than using legacy floating-point calculator behavior.
- Updated the static OpenAPI contract while keeping unrelated versioned operations and legacy `/api/fd` behavior as placeholders.

## Verification

- `mvn -q "-Dtest=FdV1BookingContractTest,FdV1ContractBoundaryTest" test` — PASS.
- `mvn -q "-Dtest=AccountBookingServiceTest" test` — PASS.
- `mvn -q "-Dtest=AccountBookingServiceTest,FdV1BookingContractTest,FdV1ContractBoundaryTest" test` — PASS.
- `mvn -q -DskipTests compile` — PASS.
- `mvn -q test` — PASS; 2 Docker/Testcontainers PostgreSQL tests skipped because no valid Docker environment is available locally.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Blocking wiring bug] Explicitly selected the booking service constructor**
- **Found during:** plan-level full-suite verification
- **Issue:** Spring could not choose between the test-friendly and policy-explicit constructors, preventing the application context from loading.
- **Fix:** Annotated the dependency-injected constructor with `@Autowired`.
- **Files modified:** `BACKEND/src/main/java/com/bank/fd/application/AccountBookingService.java`
- **Verification:** compile and full Maven suite pass.
- **Commit:** `f4ce06b`

**Total deviations:** 1 auto-fixed (Rule 1: 1).
**Impact:** No scope expansion; application context startup is now compatible with the new service.

## Authentication Gates

None.

## Known Stubs

None in files created or modified by this plan. Unrelated `/fd/v1` lifecycle operations remain intentional 501 placeholders for later phases.

## Threat Surface Scan

No new unmodeled threat flags. Implemented booking mitigates the planned JWT-to-holder spoofing and client-controlled financial-term tampering boundaries.

## Next Phase Readiness

Ready for `02-03-PLAN.md`, which can use the pending account snapshot and transaction-type/reference contracts for exact-principal internal funding.

## Self-Check: PASSED

- Summary key files exist on disk.
- Task commits `3e56509`, `67df4a3`, and `f4ce06b` exist in git history.
- Focused tests, compile, and full suite verification completed; Docker limitation is explicitly recorded.
