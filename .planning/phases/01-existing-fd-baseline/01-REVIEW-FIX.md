---
phase: 01-existing-fd-baseline
fixed_at: 2026-09-18T16:08:00+05:30
review_path: .planning/phases/01-existing-fd-baseline/01-REVIEW.md
iteration: 1
findings_in_scope: 8
fixed: 8
skipped: 0
status: all_fixed
---

# Phase 1: Code Review Fix Report

**Fixed at:** 2026-09-18T16:08:00+05:30  
**Source review:** `.planning/phases/01-existing-fd-baseline/01-REVIEW.md`  
**Iteration:** 1

**Summary:**
- Findings in scope: 8 Critical/Warning findings
- Fixed: 8
- Skipped: 0

## Fixed Issues

### CR-01: Hardcoded JWT signing secret

**Status:** fixed  
**Files modified:** `BACKEND/src/main/resources/application.yml`, `BACKEND/src/test/resources/application-test.yml`  
**Commit:** `639707d`  
**Applied fix:** Removed both tracked JWT literals. Production and test configuration now require `JWT_SECRET` from the environment; no replacement secret was stored.

### WR-01: FD OpenAPI document is not valid YAML

**Status:** fixed  
**Files modified:** `BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml`  
**Commit:** `d991a8e`  
**Applied fix:** Aligned the `summary` key with its sibling OpenAPI operation keys. The document was parsed successfully with PyYAML.

### WR-02: Swagger UI points at the login API instead of the FD API

**Status:** fixed  
**Files modified:** `BACKEND/src/main/resources/application.yml`  
**Commit:** `1314ab2`  
**Applied fix:** Pointed `springdoc.swagger-ui.url` to `/openapi/fd-service-openapi.yaml`.

### WR-03: Create-account validation does not match the API contract or domain invariants

**Status:** fixed  
**Files modified:** `BACKEND/src/main/java/com/bank/fd/api/dto/CreateAccountContractRequest.java`  
**Commit:** `ae0921e`  
**Applied fix:** Added positive validation to reference identifiers and required/non-negative validation to the client-supplied interest rate.

### WR-04: The request can select an arbitrary primary holder

**Status:** fixed: requires human verification  
**Files modified:** `BACKEND/src/main/java/com/bank/fd/api/dto/CreateAccountContractRequest.java`, `BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml`, `BACKEND/src/test/java/com/bank/fd/api/FdV1ContractBoundaryTest.java`  
**Commit:** `54fa646`  
**Applied fix:** Removed `primary_holder_id` from the customer create request and OpenAPI schema. The still-unimplemented baseline route cannot accept a caller-selected holder; a later lifecycle service must resolve the holder from the authenticated actor context.

### WR-05: Internal synchronization endpoints return false success to any authenticated user

**Status:** fixed  
**Files modified:** `BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java`, `BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml`  
**Commit:** `4e26ff2`  
**Applied fix:** Changed all five unimplemented sync routes to return the existing explicit `501 NOT_IMPLEMENTED` error and documented that response instead of claiming `204 Synced`.

### WR-06: Legacy days-to-months conversion silently changes the agreed term

**Status:** fixed: requires human verification  
**Files modified:** `BACKEND/src/main/java/com/bank/fd/entity/FdAccount.java`, `BACKEND/src/test/java/com/bank/fd/entity/FdPersistenceContractTest.java`  
**Commit:** `6125be3`  
**Applied fix:** Legacy term setters now accept only positive whole 30-day months and reject lossy values such as 365 days; a focused regression test covers both cases.

### WR-07: The new deterministic history finder is not the default history path

**Status:** fixed  
**Files modified:** `BACKEND/src/main/java/com/bank/fd/repository/FdTransactionRepository.java`, `BACKEND/src/main/java/com/bank/fd/controller/FdAccountController.java`  
**Commit:** `9a01df4`  
**Applied fix:** Removed the unordered repository finder and switched the legacy transaction-history caller to the date/id ordered finder.

## Verification

- `mvn -q -Dtest=FdV1ContractBoundaryTest test` — passed.
- `mvn -q -Dtest=FdPersistenceContractTest test` — passed.
- `mvn -q test` — passed with a process-local generated `JWT_SECRET`; no secret was written to the repository. PostgreSQL/Testcontainers tests were skipped because Docker was unavailable.
- OpenAPI and application YAML parsing — passed with PyYAML.

Info findings IN-01 and IN-02 were outside the requested Critical/Warning scope.

---

_Fixed: 2026-09-18T16:08:00+05:30_  
_Fixer: the agent (gsd-code-fixer)_  
_Iteration: 1_
