---
phase: 02-product-booking-and-funding
plan: 01
subsystem: api/database
tags: [fixed-deposit, Flyway, PostgreSQL, JPA, product-terms, DTO]

# Dependency graph
requires:
  - phase: 01-existing-fd-baseline
    provides: pending-safe FD persistence, policy contracts, lifecycle boundary, and authenticated /fd/v1 contract baseline
provides:
  - V7 booking snapshot policy-version column and deterministic reference prerequisites
  - JPA reference projections/repositories for local FD foreign-key identities and DEPOSIT lookup
  - fail-closed normalized product/rule/currency terms adapter
  - typed product-offer, booking, and funding DTO contracts
affects: [02-02 product booking, 02-03 funding, 03 interest and maturity processing]

# Tech tracking
tech-stack:
  added: []
  patterns: [forward-only Flyway preflight, local-reference versus normalized-ID separation, fail-closed terms resolution, immutable record DTOs]

key-files:
  created:
    - BACKEND/src/main/resources/db/migration/V7__fd_booking_snapshot.sql
    - BACKEND/src/main/java/com/bank/fd/application/NormalizedProductTermsAdapter.java
    - BACKEND/src/main/java/com/bank/fd/api/dto/ProductOfferResponse.java
    - BACKEND/src/main/java/com/bank/fd/api/dto/BookingRequest.java
    - BACKEND/src/main/java/com/bank/fd/api/dto/FundingRequest.java
    - BACKEND/src/main/java/com/bank/fd/entity/FdProductReference.java
    - BACKEND/src/main/java/com/bank/fd/entity/FdCurrencyReference.java
    - BACKEND/src/main/java/com/bank/fd/entity/FdRuleOutcomeReference.java
    - BACKEND/src/main/java/com/bank/fd/entity/FdUserReference.java
    - BACKEND/src/main/java/com/bank/fd/repository/FdProductReferenceRepository.java
    - BACKEND/src/main/java/com/bank/fd/repository/FdCurrencyReferenceRepository.java
    - BACKEND/src/main/java/com/bank/fd/repository/FdRuleOutcomeReferenceRepository.java
    - BACKEND/src/main/java/com/bank/fd/repository/FdUserReferenceRepository.java
    - BACKEND/src/main/java/com/bank/fd/repository/TransactionTypeRepository.java
    - BACKEND/src/test/java/com/bank/fd/FdBookingSchemaTest.java
    - BACKEND/src/test/java/com/bank/fd/application/NormalizedProductTermsAdapterTest.java
  modified:
    - BACKEND/src/main/java/com/bank/fd/entity/FdAccount.java
    - BACKEND/src/test/java/com/bank/fd/FdPostgresSchemaTest.java

key-decisions:
  - "V7 adds fa_policy_version with the documented academic-v1 legacy default rather than overloading fa_version or fa_row_version."
  - "Normalized product IDs and local fd_*_ref foreign-key IDs remain distinct; the adapter returns local product, currency, and rate-reference identities."
  - "Missing, inactive, ambiguous, malformed, or out-of-range normalized terms fail closed; transient FdProduct fields and caller financial terms are never fallback authority."

patterns-established:
  - "Flyway migrations run diagnostic duplicate checks before adding uniqueness and provision the stable DEPOSIT transaction code idempotently."
  - "HTTP booking/funding records contain only caller choices; server-owned rate, status, dates, identity, and maturity fields are excluded."

requirements-completed: [PROD-01, BOOK-02]

# Metrics
duration: 18min
completed: 2026-09-19
---

# Phase 2 Plan 1: Product Booking and Funding Summary

**V7 policy-versioned booking persistence, local FD reference repositories, fail-closed normalized terms resolution, and typed booking/funding contracts.**

## Performance

- **Duration:** 18 min
- **Started:** 2026-09-19T08:57:00Z
- **Completed:** 2026-09-19T09:15:00Z
- **Tasks:** 2/2
- **Files modified:** 18 application/schema/test files

## Accomplishments

- Added V7 without editing V1-V6: legacy duplicate-user UUIDs raise a diagnostic migration error, `fa_policy_version` defaults legacy rows to `academic-v1`, local user UUID uniqueness is enforced, and `DEPOSIT` is provisioned with `ON CONFLICT DO NOTHING`.
- Added explicit JPA mappings and repository contracts for `fd_product_ref`, `fd_currency_ref`, `fd_rule_outcome_ref`, `fd_user_ref`, and transaction-type lookup, while extending `FdAccount` with the independent policy snapshot.
- Implemented normalized-table terms resolution with deterministic local-reference mapping and fail-closed validation, plus record DTOs that omit holder/customer/status/rate/date/maturity authority from caller input.

## Task Commits

Each task followed the TDD red/green sequence and was committed atomically:

1. **Task 1: Add booking snapshot schema and reference persistence contracts** - `e1a2d6a` (test RED), `b544999` (feat GREEN)
2. **Task 2: Implement fail-closed normalized terms adapter and DTO contracts** - `19babc3` (test RED), `b2b8041` (feat GREEN)

## Verification

- `mvn -q -Dtest=FdBookingSchemaTest test` — PASS.
- `mvn -q -Dtest=NormalizedProductTermsAdapterTest test` — PASS.
- `mvn -q "-Dtest=FdBookingSchemaTest,NormalizedProductTermsAdapterTest,FdPostgresSchemaTest" test` — focused contracts PASS; PostgreSQL schema class skipped because Docker is unavailable.
- `mvn -q -DskipTests compile` — PASS.
- `mvn -q test` — FAILS only at the pre-existing `FdV1ContractBoundaryTest.updateAccountOnlyExposesAutoRenewalAndCannotPatchStatus` OpenAPI assertion; see `deferred-items.md`. The Wave 1 source did not modify OpenAPI, Angular, or legacy `/api/fd` behavior.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Preserve local reference identity in terms resolution**
- **Found during:** Task 2 implementation review
- **Issue:** The initial normalized query selected the normalized product ID for `ProductTerms`, which could be written to the account's local `fd_product_ref` foreign key.
- **Fix:** Query by and return `fpr_product_id`, keeping normalized product identity only in the lookup join.
- **Files modified:** `BACKEND/src/main/java/com/bank/fd/application/NormalizedProductTermsAdapter.java`
- **Verification:** `mvn -q -Dtest=NormalizedProductTermsAdapterTest test` and compile — PASS.
- **Committed in:** `b2b8041`

---

**Total deviations:** 1 auto-fixed (Rule 1: 1).
**Impact on plan:** Corrected a direct foreign-key integrity risk without expanding the architecture or API scope.

## Issues Encountered

- Docker is unavailable, so Testcontainers PostgreSQL assertions were skipped rather than independently executed.
- The full suite exposes a pre-existing OpenAPI status-field assertion failure outside this Wave 1 scope; it is recorded in `deferred-items.md` and no unrelated contract file was changed.

## Known Stubs

None in files created or modified by this plan.

## Threat Surface Scan

No new threat flags. The adapter and DTO changes implement the plan's modeled tampering and elevation-of-privilege mitigations by sourcing terms/reference IDs server-side and excluding caller-controlled identity/status/rate/date/maturity fields.

## User Setup Required

None - no external service configuration required. Docker is required to execute the authoritative PostgreSQL migration assertions instead of skipping them.

## Next Phase Readiness

Plan 02-02 can consume `NormalizedProductTermsAdapter`, local reference repositories, and typed booking/offer contracts to implement authenticated eligible-offer lookup and pending booking. Plan 02-03 can use the policy snapshot and transaction-type repository for exact-principal funding. Docker-enabled PostgreSQL verification remains pending.

## Self-Check: PASSED

- All 18 implementation/schema/test files listed in this summary exist.
- Task commits `e1a2d6a`, `b544999`, `19babc3`, and `b2b8041` are present in git history.
- Focused tests and compile passed; the Docker-gated test was explicitly skipped because Docker is unavailable.

---
*Phase: 02-product-booking-and-funding*
*Completed: 2026-09-19*
