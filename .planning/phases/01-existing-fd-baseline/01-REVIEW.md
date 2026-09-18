---
phase: 01-existing-fd-baseline
reviewed: 2026-09-18T10:27:58Z
depth: standard
files_reviewed: 28
files_reviewed_list:
  - BACKEND/src/main/java/com/bank/fd/domain/FdLifecycleCommand.java
  - BACKEND/src/main/java/com/bank/fd/domain/FdLifecycleStateMachine.java
  - BACKEND/src/main/java/com/bank/fd/domain/FdTransition.java
  - BACKEND/src/main/java/com/bank/fd/exception/InvalidFdTransitionException.java
  - BACKEND/src/main/java/com/bank/fd/domain/FdCalculationPolicy.java
  - BACKEND/src/main/java/com/bank/fd/domain/FdTimePolicy.java
  - BACKEND/src/main/java/com/bank/fd/application/FdActorContext.java
  - BACKEND/src/main/java/com/bank/fd/application/ProductTermsPort.java
  - BACKEND/src/main/java/com/bank/fd/application/ProductTermsQuery.java
  - BACKEND/src/main/java/com/bank/fd/application/ProductTerms.java
  - BACKEND/src/main/java/com/bank/fd/entity/FdAccount.java
  - BACKEND/src/main/java/com/bank/fd/entity/FdTransaction.java
  - BACKEND/src/main/java/com/bank/fd/entity/FdOperationIdempotency.java
  - BACKEND/src/main/java/com/bank/fd/repository/FdAccountRepository.java
  - BACKEND/src/main/java/com/bank/fd/repository/FdTransactionRepository.java
  - BACKEND/src/main/java/com/bank/fd/repository/FdOperationIdempotencyRepository.java
  - BACKEND/src/main/java/com/bank/fd/api/dto/CreateAccountContractRequest.java
  - BACKEND/src/main/java/com/bank/fd/api/dto/UpdateAccountContractRequest.java
  - BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java
  - BACKEND/src/main/resources/db/migration/V6__fd_lifecycle_baseline.sql
  - BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml
  - BACKEND/src/main/resources/application.yml
  - BACKEND/pom.xml
  - BACKEND/src/test/java/com/bank/fd/domain/FdLifecycleStateMachineTest.java
  - BACKEND/src/test/java/com/bank/fd/domain/FdCalculationPolicyTest.java
  - BACKEND/src/test/java/com/bank/fd/entity/FdPersistenceContractTest.java
  - BACKEND/src/test/java/com/bank/fd/api/FdV1ContractBoundaryTest.java
  - BACKEND/src/test/java/com/bank/fd/FdPostgresSchemaTest.java
findings:
  critical: 1
  warning: 7
  info: 2
  total: 10
status: issues_found
---

# Phase 1: Code Review Report

**Reviewed:** 2026-09-18T10:27:58Z  
**Depth:** standard  
**Files Reviewed:** 28  
**Status:** issues_found

## Summary

The lifecycle state machine and additive Flyway baseline are clear and the clean Maven suite passed; the two PostgreSQL Testcontainers tests were skipped because Docker was unavailable. The reviewed boundary still contains a production JWT secret, an invalid OpenAPI indentation, contract validation gaps, and placeholder/internal routes that can claim successful synchronization. These should be addressed before the FD API is implemented on top of this baseline.

## Critical Issues

### CR-01: Hardcoded JWT signing secret

**File:** `BACKEND/src/main/resources/application.yml:39`  
**Issue:** A literal JWT signing key is committed in the application configuration. It is consumed by the JWT provider and allows anyone with repository access to mint tokens accepted by the application; it also violates the project rule not to expose credentials in source.
**Fix:** Remove the literal and require an externally supplied secret, failing startup when it is absent. Rotate/revoke the exposed key.

```yaml
app:
  jwt:
    secret: ${JWT_SECRET}
```

## Warnings

### WR-01: FD OpenAPI document is not valid YAML

**File:** `BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml:109`  
**Issue:** `summary` is indented one space deeper than its sibling `tags` and `operationId`. This makes the document invalid or changes its mapping structure, so Swagger/OpenAPI consumers may fail to load it.
**Fix:** Align the key with its siblings:

```yaml
      tags: [Accounts]
      summary: Update mutable account instructions (e.g. auto_renewal)
      operationId: updateAccount
```

### WR-02: Swagger UI points at the login API instead of the FD API

**File:** `BACKEND/src/main/resources/application.yml:59`  
**Issue:** The configured Swagger document is `/openapi/login-service-openapi.yaml`, while this phase modifies and tests `fd-service-openapi.yaml`. The FD controller therefore is not the contract presented by the configured Swagger UI.
**Fix:** Point the UI at `/openapi/fd-service-openapi.yaml`, or expose separate explicitly named Swagger configurations for the login and FD contracts.

### WR-03: Create-account validation does not match the API contract or domain invariants

**File:** `BACKEND/src/main/java/com/bank/fd/api/dto/CreateAccountContractRequest.java:20-48`  
**Issue:** `product_id`, `currency_id`, and `primary_holder_id` only reject null, so zero/negative identifiers reach the application/database. `interest_rate` is required by the OpenAPI schema but is nullable in the DTO, and its `@Digits` constraint permits negative rates. This produces late database errors or invalid financial input rather than a clean boundary error.
**Fix:** Add positive constraints to identifiers and `@NotNull` plus a non-negative constraint to any client-supplied rate, or remove the rate from the request entirely and resolve it from authoritative product terms as required by the project scope.

### WR-04: The request can select an arbitrary primary holder

**File:** `BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java:19-32`; `BACKEND/src/main/java/com/bank/fd/api/dto/CreateAccountContractRequest.java:28-30`  
**Issue:** The whole controller requires only authentication, while the create contract accepts a caller-provided `primary_holder_id`. When the placeholder is replaced, a customer can submit another customer's holder ID unless every implementation path independently overrides this value. This is a direct-object-reference/ownership risk.
**Fix:** Derive the customer/holder identity from an authenticated `FdActorContext` and omit it from customer requests. If staff may book for another customer, expose that as a separately role-protected operation with an explicit authorization check.

### WR-05: Internal synchronization endpoints return false success to any authenticated user

**File:** `BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java:151-164`  
**Issue:** The `/internal/sync/*` methods accept arbitrary maps and return `204 No Content` without validating, authenticating as a service, or persisting anything. An ordinary authenticated customer can invoke an internal route and receive a response claiming synchronization succeeded, which is inconsistent with the otherwise truthful `501` placeholders.
**Fix:** Until implemented, return the same explicit `501 NOT_IMPLEMENTED` response. When implemented, use typed/validated event DTOs and require a service credential or dedicated staff/service role rather than `isAuthenticated()` alone.

### WR-06: Legacy days-to-months conversion silently changes the agreed term

**File:** `BACKEND/src/main/java/com/bank/fd/entity/FdAccount.java:62-63`  
**Issue:** `setTermDays` truncates every value by integer division (`365` becomes `12` months and `1` becomes `1` month after clamping). This can change a legacy request's term and therefore its maturity date without an error.
**Fix:** Do not use a lossy compatibility accessor as the authoritative term. Keep days and months as distinct fields, or reject values that cannot be represented exactly in months before converting.

### WR-07: The new deterministic history finder is not the default history path

**File:** `BACKEND/src/main/java/com/bank/fd/repository/FdTransactionRepository.java:10-12`  
**Issue:** The repository retains an unordered `findByAccountId` alongside the ordered finder. Existing FD history code uses the unordered method, so callers can still receive transaction history in database-dependent order despite the baseline's deterministic-history contract.
**Fix:** Replace the unordered finder with `findByAccountIdOrderByTransactionDateAscIdAsc`, or update every history caller to use the ordered method and remove the ambiguous finder.

## Info

### IN-01: Policy configuration is currently inert

**File:** `BACKEND/src/main/resources/application.yml:62-73`; `BACKEND/src/main/java/com/bank/fd/domain/FdCalculationPolicy.java:32-42`  
**Issue:** The YAML records the selected policy values, but no configuration binding or Spring bean constructs `FdCalculationPolicy`/`FdTimePolicy` from them. A later consumer can silently use different defaults.
**Fix:** Bind and validate the properties in one configuration bean, or keep the policy construction explicit and make the application use that single bean.

### IN-02: Contract test does not parse the OpenAPI document

**File:** `BACKEND/src/test/java/com/bank/fd/api/FdV1ContractBoundaryTest.java:62-69`  
**Issue:** The test checks string slices only, so it passed while the OpenAPI file had the indentation error in WR-01. This leaves syntax and schema-shape regressions undetected.
**Fix:** Add a test-time YAML/OpenAPI parser validation, or use the project's OpenAPI tooling to load and validate the resource.

---

_Reviewed: 2026-09-18T10:27:58Z_  
_Reviewer: the agent (gsd-code-reviewer)_  
_Depth: standard_
