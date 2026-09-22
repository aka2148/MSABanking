---
phase: 04-closure-and-demonstrable-api-workflow
reviewed: 2026-09-22T00:00:00Z
depth: standard
files_reviewed: 23
files_reviewed_list:
  - BACKEND/src/main/resources/db/migration/V9__fd_closure_history.sql
  - BACKEND/src/main/java/com/bank/fd/entity/FdPrematureClosure.java
  - BACKEND/src/main/java/com/bank/fd/repository/FdPrematureClosureRepository.java
  - BACKEND/src/main/java/com/bank/fd/domain/PrematurePenalty.java
  - BACKEND/src/test/java/com/bank/fd/domain/PrematurePenaltyTest.java
  - BACKEND/src/test/java/com/bank/fd/FdClosureSchemaTest.java
  - BACKEND/src/main/java/com/bank/fd/api/dto/ClosureResponse.java
  - BACKEND/src/main/java/com/bank/fd/api/dto/PrematureClosureResponse.java
  - BACKEND/src/main/java/com/bank/fd/api/dto/HistoryEntry.java
  - BACKEND/src/main/java/com/bank/fd/exception/PrematureClosureNotSupportedException.java
  - BACKEND/src/main/java/com/bank/fd/application/ClosureService.java
  - BACKEND/src/main/java/com/bank/fd/application/PrematureClosureService.java
  - BACKEND/src/main/java/com/bank/fd/application/HistoryService.java
  - BACKEND/src/test/java/com/bank/fd/application/ClosureServiceTest.java
  - BACKEND/src/test/java/com/bank/fd/application/PrematureClosureServiceTest.java
  - BACKEND/src/test/java/com/bank/fd/application/HistoryServiceTest.java
  - BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java
  - BACKEND/src/main/java/com/bank/common/exception/GlobalExceptionHandler.java
  - BACKEND/src/main/java/com/bank/fd/exception/InvalidFdTransitionException.java
  - BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml
  - BACKEND/src/test/java/com/bank/fd/api/FdV1ClosureHistoryContractTest.java
  - BACKEND/postman/fd-lifecycle.postman_collection.json
  - BACKEND/src/test/java/com/bank/fd/api/PostmanCollectionConsistencyTest.java
findings:
  critical: 0
  high: 1
  medium: 3
  low: 4
  total: 8
status: issues
---

# Phase 04: Code Review Report — Closure and Demonstrable API Workflow

**Reviewed:** 2026-09-22
**Depth:** standard
**Files Reviewed:** 23
**Status:** issues

## Summary

Reviewed all Phase 4 main-code paths (V9 migration, `PrematurePenalty`, `ClosureService`, `PrematureClosureService`, `HistoryService`, `FdV1ContractController`, `GlobalExceptionHandler`, OpenAPI diff, Postman collection) and skimmed the 7 test classes. No hardcoded secrets, no injection/XSS vectors, no lifecycle bypass (state-machine guards run before writes, holder checks on every service, DTO-only responses). BigDecimal discipline is good: scale-12 intermediate, scale-4 rates, scale-2 settlements, floors at zero.

One High-severity correctness issue: the premature penalty is resolved globally (`findAll` + exactly-one `CLOSURE_PENALTY` row) instead of per-account/product, so a second product/penalty row breaks every premature closure. Three Medium issues (silently-ignored `business_date`, closing-accrual cumulative reset, approve-time null guards → 500). Four Low/Info notes. No Critical (no auth bypass, no data loss, no crash on the happy path). All findings are advisory; duplicate-action hardening and staff-approval remain Phase 5 per 04-CONTEXT deferred list and are not flagged.

## High Issues

### HI-01: Penalty outcome resolved globally, not per account/product

**File:** `BACKEND/src/main/java/com/bank/fd/application/PrematureClosureService.java:238-248`
**Issue:** `resolvePenaltyOutcome` loads `ruleOutcomeRepository.findAll()`, filters only on `outcomeType == CLOSURE_PENALTY`, and throws `PrematureClosureNotSupportedException` unless exactly one row exists system-wide. It never joins the booked account's product, `appliedRateOutcomeId`, or stored `appliedRuleOutcomeId`. Consequences: (a) with two products carrying different penalties, every premature request fails with D-02 unsupported even though each product has a valid rule; (b) with zero global rows, unsupported is correct by accident but for the wrong reason; (c) the persisted `appliedRuleOutcomeId` is write-only — never used to re-resolve. Plan 04-02 required "the account's contracted terms snapshot … for THIS account without re-querying live product tables". The current code satisfies "never live tables" but not "for THIS account".
**Fix:**
```java
// Resolve via the account's stored outcome reference, e.g.:
FdRuleOutcomeReference penaltyOutcome = ruleOutcomeRepository
    .findById(account.getAppliedRateOutcomeId()) // or productId-scoped lookup
    .filter(o -> CLOSURE_PENALTY_RULE.equalsIgnoreCase(o.getOutcomeType()))
    .orElseThrow(() -> new PrematureClosureNotSupportedException(...));
```
At minimum, scope the `findAll` filter by the account's product/outcome id and keep the exactly-one check within that scope. Add a unit test with two `CLOSURE_PENALTY` rows for different products asserting the correct per-account value is picked.

## Medium Issues

### MED-01: `business_date` body param validated but silently discarded on premature request/approve

**File:** `BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java:373-381, 404-415`
**Issue:** Both `requestPrematureClosure` and `approvePrematureClosure` call `extractBusinessDate(body)` for validation only and discard the result; `PrematureClosureService` always quotes/clamps against `timePolicy.businessDate()`. A demo caller passing `{reason, business_date}` (as the Postman collection and OpenAPI suggest is meaningful) gets a 201/200 computed at server-today with no indication the date was ignored — quote-snapshot drift between what the student asked for and what was stored. `AccrualService.runAccrual` by contrast threads the date through.
**Fix:** Either (a) forward the parsed date into the service (`requestPrematureClosure(accountId, username, reason, businessDate)`) and clamp there, or (b) reject a non-null `business_date` with 400 "not supported in v1" / remove it from the Postman examples and OpenAPI request-body docs so the contract is truthful. Option (a) matches the accrual precedent; option (b) is the minimal truthful-response fix.

### MED-02: Closing accrual slice resets `cumulativeAccrued` instead of accumulating

**File:** `BACKEND/src/main/java/com/bank/fd/application/PrematureClosureService.java:194-207`
**Issue:** The approve-time closing slice sets both `accruedAmount` and `cumulativeAccrued` to `settlement - principal` (the total penalized interest). If prior `fd_interest_accrual` rows exist for the account (e.g. student ran `accrual/run` before the premature variant, as the Postman demo order encourages), the new row's cumulative ignores them, and `HistoryService` (`principal + cumulative`) renders a running balance that steps backward on the timeline. `AccrualService.cumulative()` sums `accruedAmount`s, so the stored cumulative and the displayed total also disagree.
**Fix:**
```java
// Derive the slice as the residual over existing accruals, or accumulate:
BigDecimal prior = accrualRepository.findByAccountIdOrderByAccrualDateAscIdAsc(accountId)
    .stream().map(FdInterestAccrual::getAccruedAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
BigDecimal slice = penalizedInterest.subtract(prior).max(BigDecimal.ZERO);
closing.setAccruedAmount(slice);
closing.setCumulativeAccrued(penalizedInterest); // total, consistent with maturity path
```
Note `MaturityService:92-101` has the same reset pattern, but it fires at maturity date where a same-date guard makes collision rarer; the premature path collides whenever the demo runs accrual first, so fix here first. Add a test: seed one accrual row, approve, assert history running balances are monotonically non-decreasing.

### MED-03: Approve path can 500 on null stored snapshot / account fields

**File:** `BACKEND/src/main/java/com/bank/fd/application/PrematureClosureService.java:168-169, 194, 218-219`
**Issue:** `requestPrematureClosure` guards null booking date/rate before writing (good, per 04-02 summary), but `approvePrematureClosure` dereferences the stored snapshot and live account without re-guards: `settlement.subtract(account.getPrincipalAmount())` NPEs if principal is null; `PrematurePenalty.penalizedRate(rate, points)` throws `IllegalArgumentException` if rate or stored `penaltyPctApplied` is null. Both escape as 500 via `handleGlobalException` instead of a structured 400. Reachability is low (requires corrupt pre-existing rows), but the request path already established the `InvalidInputException` convention for exactly these nulls.
**Fix:** Mirror the request guards at the top of approve (null principal, null rate, null `penaltyAmount`/`netPayableAmount`/`penaltyPctApplied` → `InvalidInputException` with a clear message) before writing PENALTY/CLOSURE rows.

## Low / Info

### LOW-01: `getAccount` read takes a pessimistic write lock

**File:** `BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java:162`
**Issue:** The read-only `GET /accounts/{id}` uses `accountRepository.findByIdForUpdate`, acquiring a row write lock outside any `@Transactional` (lock likely released immediately / provider-dependent) while `getPrematureClosure` and `HistoryService` correctly use plain `findById`. Unnecessary contention risk and inconsistent read pattern; no correctness bug today.
**Fix:** Switch `getAccount` to `findById` like the other reads.

### LOW-02: Unused repository method

**File:** `BACKEND/src/main/java/com/bank/fd/repository/FdPrematureClosureRepository.java:12`
**Issue:** `findByAccountIdAndStatus` has no callers (controller lists via `findByAccountIdOrderByClosureDateAscIdAsc`). Harmless Spring Data derived query, but dead surface.
**Fix:** Either use it for a PENDING-only listing or delete it; if kept for Phase 5 idempotency work, add a comment saying so.

### LOW-03: `parsePenaltyPoints` accepts exponent notation

**File:** `BACKEND/src/main/java/com/bank/fd/domain/PrematurePenalty.java:49-58`
**Issue:** `new BigDecimal(trimmed)` accepts `"1E+2"` (= 100 points → floored to zero rate) and leading `+`. Operator-data quirk only; fail-closed cases (null/blank/unparseable/negative) are all handled, and the floor-at-zero keeps it safe.
**Fix (optional):** If strictness is desired, validate with `^[0-9]+(\.[0-9]+)?%?$` before parsing. Safe to defer.

### LOW-04: V9 `PENALTY` row recorded as `CREDIT`

**File:** `BACKEND/src/main/resources/db/migration/V9__fd_closure_history.sql:13-18`
**Issue:** The `PENALTY` transaction code is provisioned with `tt_debit_credit = 'CREDIT'`, matching the plan text but semantically odd for a foregone-interest deduction (service writes it as a positive amount with null `balance_after`, and history renders it as a standalone PENALTY entry, so nothing breaks). Flagged only so a ledger-aware reader does not assume debit/credit balancing.
**Fix:** No change required for v1; if the ledger ever balances on `tt_debit_credit`, revisit whether PENALTY should be `DEBIT`. Keep the service's null-`balance_after` convention documented (already is).

## Explicitly Checked — No Issue

- **Lifecycle bypass:** `ClosureService` transitions `CLOSE` before any write (only MATURED succeeds); `PrematureClosureService` requires ACTIVE + PENDING + same-account at approve. No bypass found.
- **Auth/ownership:** Every service resolves `FdActorResolver` → holder match → 403; mutations are `CUSTOMER`-gated, reads `isAuthenticated`. D-04 self-service approve is per CONTEXT (staff approval deferred to Phase 5 — not flagged).
- **BigDecimal scale/rounding:** `penalizedRate` scale-12 intermediate → scale-4 floored at zero; settlement scale-2; `foregoneInterest` floored at 0.00. Matches `FdInterestCalculator` discipline.
- **Migration idempotency:** `ON CONFLICT DO NOTHING`, `ADD COLUMN IF NOT EXISTS`, constraint-guard DO-block; V1–V8 untouched. Preflight duplicate-UUID check present.
- **Error-shape drift:** 409 `InvalidFdTransitionException`, 422 `PrematureClosureNotSupportedException`, 400 `InvalidInputException` (incl. M-03 date fix) all wired; OpenAPI contains matching 400/403/404/409/422 entries and `ClosureResponse`/`PrematureClosureResponse`/`HistoryEntry` schemas.
- **Postman/contract:** 4 folders in demo order, `{{baseUrl}}/{{accountId}}/{{closureId}}/{{authToken}}` variables, `YOUR_JWT_HERE` placeholder only (no `eyJ`/password literals found), DOC 409/422 examples reuse implemented routes so the consistency test stays green.
- **History ordering:** `nullsFirst` date + id tiebreak merge of transactions + accruals with DTO-only output; `PENALTY` null-balance mapping is by design and pinned by tests.

---

_Reviewed: 2026-09-22_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
