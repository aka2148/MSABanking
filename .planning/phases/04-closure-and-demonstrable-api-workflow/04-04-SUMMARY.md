---
phase: 04-closure-and-demonstrable-api-workflow
plan: 04
subsystem: api
tags: [rest, fixed-deposit, postman, openapi, contract-test, demo]

# Dependency graph
requires:
  - phase: 04-closure-and-demonstrable-api-workflow
    provides: Six wired /fd/v1 closure/status/history endpoints with 400/409/422 shapes plus OpenAPI schemas (04-03)
  - phase: 03-interest-and-maturity-processing
    provides: Accrual/maturity endpoint wiring plus BookingRequest/FundingRequest shapes reused as example payloads
provides:
  - Importable Postman collection narrating products to book to fund to accrue to mature to close to history plus premature-closure variant (D-07)
  - PostmanCollectionConsistencyTest proving every collection request maps to an implemented controller route
affects: [phase-5-authorization, fd-api-consumers, student-demo]

# Tech tracking
tech-stack:
  added: []
  patterns: [postman-collection-v2.1-demo-artifact, reflection-based contract-consistency test with normalized route comparison]

key-files:
  created:
    - BACKEND/postman/fd-lifecycle.postman_collection.json
    - BACKEND/src/test/java/com/bank/fd/api/PostmanCollectionConsistencyTest.java
  modified: []

key-decisions:
  - "DOC 409/422 error examples reuse the implemented close and premature-closure routes with error-expecting pm.tests, so the documented errors stay green in the consistency check"
  - "Coverage assertion maps collection requests to controller method names (not just controller capabilities), so removing a demo step from the collection fails the build"
  - "Response-id chaining scripts read tolerant field names (id/account_id/accountId, closure_id/closureId) with manual variable fallback documented in the collection description"

patterns-established:
  - "Demo artifacts live under BACKEND/postman/ as versioned JSON with placeholder-only credentials"
  - "Consistency tests locate repo artifacts with a working-dir fallback chain so they run from the BACKEND module"

requirements-completed: [API-01]

# Metrics
duration: 12min
completed: 2026-09-22
---

# Phase 04 Plan 04: Postman Demo Collection Summary

**Importable 18-request Postman collection narrating the full FD lifecycle plus premature-closure penalty variant, with a reflection-based JUnit test proving every step hits an implemented /fd/v1 route**

## Performance

- **Duration:** ~12 min
- **Started:** 2026-09-22T13:00:00Z
- **Completed:** 2026-09-22T13:12:00Z
- **Tasks:** 2
- **Files modified:** 2

## Accomplishments

- Postman Collection v2.1 "FD Lifecycle Demo (academic-v1)" with 4 folders in demo order: 1 Products & Booking (3 requests), 2 Status & Interest (5), 3 Normal Closure (3, incl. DOC 409 close-non-matured example), 4 Premature Variant (7, incl. DOC 422 unsupported-rule example)
- Every request carries bearer `{{authToken}}` auth, `{{baseUrl}}`/`{{accountId}}`/`{{accountId2}}`/`{{closureId}}` variables with localhost defaults, example JSON bodies matching BookingRequest/FundingRequest/`{business_date}`/`{reason}` shapes, expected status plus key response fields in the description, and a `pm.test` asserting the expected status
- Collection description states truthful internal-record semantics (no external payment rail) and the `POST /api/auth/login` token flow; only `YOUR_JWT_HERE` placeholder is committed
- `PostmanCollectionConsistencyTest` green (1 run, 0 failures, ~0.2s, Docker-free): valid v2.1 JSON, all 18 requests resolve to reflected `FdV1ContractController` routes, all 10 demo-critical operations hit by the collection, no 501-placeholder success steps, no JWT/password literals

## Task Commits

Each task was committed atomically:

1. **Task 1: Create the end-to-end Postman collection** - `366ac12` (feat)
2. **Task 2: Prove collection-to-contract consistency** - `381816a` (test)

## Files Created/Modified

- `BACKEND/postman/fd-lifecycle.postman_collection.json` - 18-request importable demo: products, book, fund, status, accrual run/list/cumulative, mature, close, history, premature book/fund/request/list/approve/history, plus DOC 409 and DOC 422 error examples
- `BACKEND/src/test/java/com/bank/fd/api/PostmanCollectionConsistencyTest.java` - Reflection-based consistency test: route mapping, operation coverage, placeholder exclusion, secret scan

## Decisions Made

- DOC 409/422 error examples reuse the implemented close and premature-closure routes with error-expecting `pm.test` scripts (409/422) instead of inventing documentation-only paths, so the documented errors stay green in the consistency check and import cleanly as runnable requests.
- Coverage assertion maps collection requests to controller method names through the reflected route table rather than asserting on controller capabilities, so deleting a demo step from the collection fails the build (drift protection per T-04-16).
- Response-id chaining scripts accept tolerant field names with manual variable fallback documented in the collection description, since exact DTO field names may evolve and the collection must stay usable by students either way.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Coverage assertion initially checked controller capabilities instead of collection coverage**
- **Found during:** Task 2 (consistency test implementation)
- **Issue:** First draft asserted the 10 demo-critical operation names against `implemented.values()` (all mapped controller methods), which passes regardless of collection content and would not catch a deleted demo step
- **Fix:** Collect the controller method name hit by each collection request into `hitOperations` and assert critical operations against that set
- **Files modified:** BACKEND/src/test/java/com/bank/fd/api/PostmanCollectionConsistencyTest.java
- **Verification:** `mvn -Dtest='PostmanCollectionConsistencyTest' test` — 1/1 green after fix
- **Committed in:** 381816a (part of Task 2 commit)

---

**Total deviations:** 1 auto-fixed (1 bug)
**Impact on plan:** Required for the test to actually prove what the plan demands. No scope creep; no production code touched.

## Issues Encountered

- PowerShell 5.1 has no `tail`, so plan verify commands ran unpiped (`mvn -Dtest='PostmanCollectionConsistencyTest' test`) with equivalent coverage: BUILD SUCCESS, 1/1 green.
- `Select-String` on the OpenAPI YAML returned empty matches in this shell; operation IDs were extracted via Python regex instead (48 operationIds confirmed, all 10 demo-critical present).

## Threat Flags

None - all new surface was already in the plan's threat model. T-04-15 mitigated (secret scan for `eyJ`/password literals; only `YOUR_JWT_HERE` placeholder plus example principals committed). T-04-16 mitigated (reflection assertion fails the build on endpoint drift; DOC error examples reuse implemented routes). T-04-17 mitigated (collection description states internal academic recordkeeping with no external settlement claim).

## Known Stubs

None. `{{accountId}}`/`{{accountId2}}`/`{{closureId}}` empty defaults and `YOUR_JWT_HERE` are the intended Postman variable scheme (chained at runtime via test scripts or set manually), not unwired data. The DOC 409/422 requests are intentional error-path documentation on implemented routes.

## User Setup Required

None - no external service configuration required. Students import the collection, set `authToken` from `POST /api/auth/login`, and run folders in order against a local backend.

## Next Phase Readiness

- Phase 4 demo surface is complete: students can narrate funding through closure (normal and premature) from one importable file with automated proof the demo matches the contract.
- No production code was touched (`mvn -DskipTests compile` unaffected — compile step reported "Nothing to compile - all classes are up to date").
- Remaining work (duplicate-action hardening, broader ownership/role protection, regression gates) stays Phase 5 per CONTEXT deferred list.

## Self-Check: PASSED

- FOUND: BACKEND/postman/fd-lifecycle.postman_collection.json
- FOUND: BACKEND/src/test/java/com/bank/fd/api/PostmanCollectionConsistencyTest.java
- FOUND: 366ac12, FOUND: 381816a

---
*Phase: 04-closure-and-demonstrable-api-workflow*
*Completed: 2026-09-22*
