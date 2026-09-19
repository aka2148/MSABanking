---
phase: 02-product-booking-and-funding
reviewed: 2026-09-19T00:00:00Z
depth: standard
files_reviewed: 27
files_reviewed_list:
  - BACKEND/src/main/resources/db/migration/V7__fd_booking_snapshot.sql
  - BACKEND/src/main/java/com/bank/fd/application/NormalizedProductTermsAdapter.java
  - BACKEND/src/main/java/com/bank/fd/application/FdActorResolver.java
  - BACKEND/src/main/java/com/bank/fd/application/ProductOfferQueryService.java
  - BACKEND/src/main/java/com/bank/fd/application/AccountBookingService.java
  - BACKEND/src/main/java/com/bank/fd/application/FundingService.java
  - BACKEND/src/main/java/com/bank/fd/application/ProductTermsPort.java
  - BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java
  - BACKEND/src/main/java/com/bank/fd/api/dto/ProductOfferResponse.java
  - BACKEND/src/main/java/com/bank/fd/api/dto/BookingRequest.java
  - BACKEND/src/main/java/com/bank/fd/api/dto/FundingRequest.java
  - BACKEND/src/main/java/com/bank/fd/api/dto/AccountResponse.java
  - BACKEND/src/main/java/com/bank/fd/api/dto/FundingResponse.java
  - BACKEND/src/main/java/com/bank/fd/domain/FdTimePolicy.java
  - BACKEND/src/main/java/com/bank/fd/domain/FdLifecycleStateMachine.java
  - BACKEND/src/main/java/com/bank/fd/entity/FdAccount.java
  - BACKEND/src/main/java/com/bank/fd/entity/FdProductReference.java
  - BACKEND/src/main/java/com/bank/fd/entity/FdCurrencyReference.java
  - BACKEND/src/main/java/com/bank/fd/entity/FdRuleOutcomeReference.java
  - BACKEND/src/main/java/com/bank/fd/entity/FdUserReference.java
  - BACKEND/src/main/java/com/bank/fd/repository/FdAccountRepository.java
  - BACKEND/src/main/java/com/bank/fd/repository/FdProductReferenceRepository.java
  - BACKEND/src/main/java/com/bank/fd/repository/FdCurrencyReferenceRepository.java
  - BACKEND/src/main/java/com/bank/fd/repository/FdRuleOutcomeReferenceRepository.java
  - BACKEND/src/main/java/com/bank/fd/repository/FdUserReferenceRepository.java
  - BACKEND/src/main/java/com/bank/fd/repository/TransactionTypeRepository.java
  - BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml
findings:
  critical: 0
  warning: 6
  info: 2
  total: 8
status: issues_found
---

# Phase 02: Code Review Report

**Reviewed:** 2026-09-19T00:00:00Z  
**Depth:** standard  
**Files Reviewed:** 27  
**Status:** issues_found

## Summary

The booking and funding services establish the intended authenticated, server-authoritative flow and use a database row lock plus one Spring transaction for funding. No critical security vulnerability was found. The main risks are money-scale normalization, effective-date handling, an incomplete OpenAPI contract, and retry behavior that is explicitly deferred but can still create duplicate bookings.

## Warnings

### WR-01: Booking accepts four-decimal input but silently stores two-decimal principal

**File:** `BACKEND/src/main/java/com/bank/fd/api/dto/BookingRequest.java:15-16`; `BACKEND/src/main/java/com/bank/fd/application/AccountBookingService.java:60-75`

**Issue:** The request permits up to four fractional digits and the terms lookup and maturity estimate use the unrounded amount, but persistence rounds the principal to the two-decimal currency scale. For example, a request for `1000.004` can be validated and estimated using one amount while the account and required funding amount use `1000.00`.

**Fix:** Normalize or reject the amount before terms resolution and calculation, using the configured currency scale. Prefer rejecting values with more than two fractional digits, or consistently set the scale once and use that normalized value for validation, estimate, persistence, and funding comparison.

### WR-02: Effective date is accepted but ignored by normalized product lookup

**File:** `BACKEND/src/main/java/com/bank/fd/application/NormalizedProductTermsAdapter.java:161-169`

**Issue:** Both JDBC queries receive `effectiveDate` but do not bind or filter on product/rule effective windows. A product/rule that is inactive for the requested business date can therefore be offered or booked as long as its status is `ACTIVE`.

**Fix:** Add explicit PostgreSQL date predicates for `pro_effective_from/to` and `pr_effective_from/to` (with the repository's chosen date format), or remove the parameter and document that only status controls eligibility. Do not claim date-effective eligibility until it is enforced.

### WR-03: Implemented funding endpoint is missing from the OpenAPI paths

**File:** `BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java:67-73`; `BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml:146-163`

**Issue:** The controller exposes `POST /fd/v1/accounts/{accountId}/fund`, but the contract jumps from `/activate` to `/mature` and has no `/fund` path. Swagger/Postman users following the checked-in contract cannot discover or validate the required Phase 2 funding operation.

**Fix:** Add `/accounts/{accountId}/fund` with `FundingRequest`, `FundingResponse`, and documented `400`, `401`, `403`, `404`, and `409` responses. Keep `/activate` explicitly unsupported until a later phase so it cannot be mistaken for a second funding path.

### WR-04: Booking response is documented as the legacy `Account` schema

**File:** `BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml:51-68`; `BACKEND/src/main/java/com/bank/fd/api/dto/AccountResponse.java:10-26`

**Issue:** `POST /accounts` returns the new `AccountResponse` (including `policy_version` and `funding_recorded`, and excluding internal holder/currency IDs), but OpenAPI still declares the response as legacy `Account`. Clients generated from the contract will expect different field names/semantics and will not have a reliable pending-booking contract.

**Fix:** Change the `201` response to `#/components/schemas/AccountResponse`, mark the required Phase 2 fields, and retain the legacy `Account` schema only for explicitly legacy/placeholder operations.

### WR-05: Booking retries create additional FD accounts

**File:** `BACKEND/src/main/java/com/bank/fd/application/AccountBookingService.java:57-87`

**Issue:** Every successful call creates a new account; no operation key or unique business identity is checked. A client timeout after commit followed by a retry can create a second FD for the same intended booking. This is listed for Phase 5, but the current Phase 2 endpoint exposes the duplicate-financial-effect behavior.

**Fix:** Add an idempotency key to the booking contract and persist it under the existing durable operation-identity boundary, with a unique constraint scoped to the authenticated holder and operation. On retry, return the original account/result rather than creating another account.

### WR-06: First-time holder-reference creation can fail on concurrent bookings

**File:** `BACKEND/src/main/java/com/bank/fd/application/FdActorResolver.java:31-41`

**Issue:** Two simultaneous first bookings can both observe no `fd_user_ref`, then race to insert. V7 correctly adds a unique index, but the losing request receives a database uniqueness failure rather than re-reading the existing reference and continuing; a normal concurrent retry can consequently fail the booking transaction.

**Fix:** Handle the unique-constraint race by catching the insert conflict in a narrowly scoped retry/read path, or lock/read the user-reference identity before creation. Preserve the unique index as the database authority.

## Info

### IN-01: Duplicate `@Autowired` import

**File:** `BACKEND/src/main/java/com/bank/fd/application/AccountBookingService.java:13-14`

**Issue:** The same import appears twice. It does not change behavior but adds avoidable noise.

**Fix:** Remove one duplicate import.

### IN-02: Controller documentation contradicts implemented behavior

**File:** `BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java:23-26`

**Issue:** The class-level comment says business logic is intentionally deferred even though products, booking, and funding are implemented. This can mislead maintainers about which operations are authoritative.

**Fix:** Update the comment to describe the implemented Phase 2 operations and the remaining explicit placeholders.

---

_Reviewed: 2026-09-19T00:00:00Z_  
_Reviewer: the agent (gsd-code-reviewer)_  
_Depth: standard_
