---
phase: 05-protection-tests-and-regression-gate
reviewed: 2026-09-22T00:00:00Z
depth: standard
files_reviewed: 29
files_reviewed_list:
  - BACKEND/src/main/java/com/bank/common/exception/GlobalExceptionHandler.java
  - BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java
  - BACKEND/src/main/java/com/bank/fd/application/AccountBookingService.java
  - BACKEND/src/main/java/com/bank/fd/application/AccrualService.java
  - BACKEND/src/main/java/com/bank/fd/application/ClosureService.java
  - BACKEND/src/main/java/com/bank/fd/application/FdAccessPolicy.java
  - BACKEND/src/main/java/com/bank/fd/application/FdIdempotencyGuard.java
  - BACKEND/src/main/java/com/bank/fd/application/FundingService.java
  - BACKEND/src/main/java/com/bank/fd/application/HistoryService.java
  - BACKEND/src/main/java/com/bank/fd/application/MaturityService.java
  - BACKEND/src/main/java/com/bank/fd/application/PrematureClosureService.java
  - BACKEND/src/main/java/com/bank/fd/exception/FdDuplicateOperationException.java
  - BACKEND/src/main/java/com/bank/fd/exception/InvalidFdTransitionException.java
  - BACKEND/src/main/java/com/bank/security/config/SecurityConfig.java
  - BACKEND/src/test/java/com/bank/common/exception/GlobalExceptionHandlerTest.java
  - BACKEND/src/test/java/com/bank/fd/api/FdOwnershipContractTest.java
  - BACKEND/src/test/java/com/bank/fd/api/FdV1AccrualMaturityContractTest.java
  - BACKEND/src/test/java/com/bank/fd/api/FdV1BookingContractTest.java
  - BACKEND/src/test/java/com/bank/fd/api/FdV1ClosureHistoryContractTest.java
  - BACKEND/src/test/java/com/bank/fd/application/AccountBookingServiceTest.java
  - BACKEND/src/test/java/com/bank/fd/application/AccrualMaturityServiceTest.java
  - BACKEND/src/test/java/com/bank/fd/application/ClosureServiceTest.java
  - BACKEND/src/test/java/com/bank/fd/application/FdAccessPolicyTest.java
  - BACKEND/src/test/java/com/bank/fd/application/FdIdempotencyGuardTest.java
  - BACKEND/src/test/java/com/bank/fd/application/FundingServiceTest.java
  - BACKEND/src/test/java/com/bank/fd/application/HistoryServiceTest.java
  - BACKEND/src/test/java/com/bank/fd/application/PrematureClosureServiceTest.java
  - BACKEND/src/test/java/com/bank/fd/domain/FdPhase5BoundaryTest.java
  - BACKEND/src/test/java/com/bank/fd/FdPhase5ProtectionIT.java
findings:
  critical: 0
  high: 0
  medium: 3
  low: 4
  total: 7
status: issues
---

# Phase 05: Code Review Report — Protection, Tests, and Regression Gate

**Reviewed:** 2026-09-22
**Depth:** standard
**Files Reviewed:** 29
**Status:** issues

## Summary

Reviewed all Phase 5 main-code paths (`FdIdempotencyGuard`, `FdAccessPolicy`, `FdDuplicateOperationException`, booking/funding/accrual/maturity/closure/premature/history wiring, `FdV1ContractController` key threading + staff roles, `GlobalExceptionHandler` 409 mapping, `SecurityConfig` CORS) and skimmed the 15 touched/added test classes. No hardcoded prod secrets, no injection/XSS vectors, no ownership bypass (holder-match → 404 on every service + both controller reads + history/accrual reads, staff bypass limited to `ROLE_BANK_OFFICER`/`ROLE_ADMIN`), no lifecycle bypass (claim-before-transition with replay-from-stored-aggregate, key-less 409s on FUNDING/MATURITY/CLOSURE terminal repeats). BigDecimal and truthful-remark discipline preserved.

No High-severity issues. Three Medium issues: closure replay stamps the current business date instead of the stored close date (replay body drifts across days, D-01); keyed claims are persisted before transition validation with no concurrent-duplicate handling (orphan PENDING rows + potential 500 on unique collision instead of 409); premature `business_date` is still validated-but-discarded (carried over from 04 MED-01, still present after this phase touched the controller). Four Low/Info notes. All findings are advisory; the regression gate (132 tests green) and demo flow are unaffected.

## High Issues

None.

## Medium Issues

### MED-01: Closure replay returns the current business date instead of the stored close date

**File:** `BACKEND/src/main/java/com/bank/fd/application/ClosureService.java:93`
**Issue:** The same-key replay path rebuilds the response as `ClosureResponse.closed(stored, settlement, currencyCode, timePolicy.businessDate())` — the *current* date — while the original close (line 131) stamps `closedDate` at close time. Same-day replays are byte-identical (which is all the IT proves), but a replay on a later day returns a different body for the same operation, violating D-01 ("same success body") and D-12 (replay references the already-recorded result). Funding/maturity/accrual replays are deterministic (reloaded rows / recomputed interest); only closure drifts.
**Fix:**
```java
// Reuse the stored settlement timestamp instead of today, e.g.:
// derive closedDate from the stored CLOSURE transaction's transactionDate
OffsetDateTime storedClose = transactionRepository
    .findByAccountIdOrderByTransactionDateAscIdAsc(stored.getId()).stream()
    .filter(t -> closureTypeId.equals(t.getTransactionTypeId()))
    .map(FdTransaction::getTransactionDate).max(Comparator.naturalOrder())
    .map(OffsetDateTime::toLocalDate).orElse(timePolicy.businessDate());
// then: return ClosureResponse.closed(stored, settlement, currencyCode, storedClose);
```
At minimum, persist the close date in the claim's `responseReference` at `complete()` time and parse it back on replay. Add a test: close, advance the clock, replay with the same key, assert byte-identical bodies.

### MED-02: Keyed claims persist before validation; concurrent duplicates have no 409 mapping

**File:** `BACKEND/src/main/java/com/bank/fd/application/MaturityService.java:88-108`, `BACKEND/src/main/java/com/bank/fd/application/ClosureService.java:81-98`, `BACKEND/src/main/java/com/bank/fd/application/PrematureClosureService.java:110-119, 179-193`, `BACKEND/src/main/java/com/bank/fd/application/FdIdempotencyGuard.java:41-67`
**Issue:** (a) Maturity, closure, and premature request/approve call `idempotencyGuard.claim(...)` *before* the status/transition check, so every keyed invalid attempt (e.g. mature-while-PENDING, close-while-ACTIVE) persists a PENDING claim row with null `aggregateId` that never completes — orphan rows accumulate and a later `findAll`-style audit overstates in-flight operations. Accrual and funding claim after their guards and do not share this. (b) `claim()` does find-then-save with no handling for a concurrent same-key insert: if two threads race past the `findBy...` empty check, the second `save()` throws a uniqueness/`DataIntegrityViolationException` that escapes as 500 via `handleGlobalException` instead of the D-11 409-with-reference. The retry story this phase protects is exactly the double-submit window where this race lives.
**Fix:**
```java
// (a) Move claim() after the transition/status validation, or delete the
// orphan claim on validation failure before rethrowing.
// (b) In FdIdempotencyGuard.claim(), catch the constraint violation and
// re-read + apply replay/mismatch semantics:
try {
    return repository.save(claim);
} catch (DataIntegrityViolationException race) {
    FdOperationIdempotency retry = repository
        .findByOperationScopeAndActorKeyAndIdempotencyKey(scope, actorKey, key)
        .orElseThrow(() -> race);
    // ... same sameHash ? return : throw FdDuplicateOperationException logic
}
```
For a college scope, (a) alone (claim-after-validate, matching the accrual precedent) removes the orphan path; (b) is the minimal race fix without introducing locks (which AGENTS.md forbids).

### MED-03: Premature `business_date` still validated but silently discarded (04 MED-01 carry-over)

**File:** `BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java:387-389, 419-421`
**Issue:** `requestPrematureClosure` and `approvePrematureClosure` still call `extractBusinessDate(body)` for validation only and discard the result; `PrematureClosureService` quotes/clamps against `timePolicy.businessDate()`. A caller passing `{reason, business_date}` gets a 201/200 computed at server-today with no indication the date was ignored. This phase touched both methods (key threading) but left the drift in place, so the 04 finding stands.
**Fix:** Same as 04 MED-01: either forward the parsed date into the service (`requestPrematureClosure(accountId, username, reason, businessDate)`) or reject non-null `business_date` with 400 / remove it from OpenAPI + Postman examples so the contract is truthful.

## Low / Info

### LOW-01: PENDING-collision 409 reference renders as `aggregate:null`

**File:** `BACKEND/src/main/java/com/bank/fd/application/FdIdempotencyGuard.java:54-56`
**Issue:** The mismatch fallback is `"aggregate:" + claim.getAggregateId()` when `responseReference` is unset. For an in-flight PENDING claim both are null, so the 409 body reads `existing record: aggregate:null` — confusing and not truthful per D-12 (nothing was recorded yet). Reachability is narrow (same key + different body before first completion).
**Fix:** Fall back to scope + key when no aggregate exists: `"pending " + scope + " operation for key: " + key`.

### LOW-02: `listProducts` still CUSTOMER-only while all seven mutations widened to staff

**File:** `BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java:156-160`
**Issue:** The seven mutating endpoints now accept `CUSTOMER/BANK_OFFICER/ADMIN` (per D-06), but `GET /products` remains `hasRole('CUSTOMER')`, so a staff principal can fund/mature/close but cannot list eligible products to book from. Preserves pre-existing behavior (not a regression), but the staff demo flow is asymmetric.
**Fix (optional):** Widen `listProducts` to the same three roles, or document that staff booking goes through the same self-service product list.

### LOW-03: Booking canonical-hash derivation NPEs before validation on null fields

**File:** `BACKEND/src/main/java/com/bank/fd/application/AccountBookingService.java:77-79`
**Issue:** `request.principalAmount().stripTrailingZeros()` and `request.currencyCode().toUpperCase()` dereference before any null check. Over HTTP this is unreachable (`@Valid BookingRequest` rejects nulls at the controller with 400), but direct service callers get a 500 NPE instead of `IllegalArgumentException`/`InvalidInputException`. Also `responseReference.split("\\|")` assumes product/currency codes never contain `|`.
**Fix (optional):** Null-guard the three fields with `InvalidInputException` before hashing; codes with `|` are unrealistic — no change needed beyond the guard.

### LOW-04: Test-only hardcoded credentials (no prod secret exposure)

**File:** `BACKEND/src/test/java/com/bank/fd/api/FdOwnershipContractTest.java:60`, `BACKEND/src/test/java/com/bank/fd/FdPhase5ProtectionIT.java:116`
**Issue:** `PASSWORD = "securepwd123"` is hardcoded in two test classes. This is a test-fixture credential for locally-registered users (consistent with `SecurityFlowTest` style, never a prod secret, no JWT/URL baked in) — not a finding against AGENTS.md, flagged only so a secrets-scanner exception is on record.
**Fix:** No change required; do not "rotate" — if the scanner flags it, allowlist `BACKEND/src/test/**` fixture passwords.

## Explicitly Checked — No Issue

- **Ownership / 404-hiding (D-04/D-05/D-06):** `FdAccessPolicy` holder-match → staff-bypass (`BANK_OFFICER`/`ADMIN`) → 404, zero `AccessDeniedException` in FD scope; wired into all mutating services, both controller reads, history timeline, and both accrual reads. Actor key is always `fd-user-<resolved-id>`, never caller-supplied.
- **Duplicate safety (D-01/D-02/D-03):** Same key + same hash replays the stored aggregate with zero new rows on all seven paths (pinned by MockMvc byte-identical + row-count assertions and the PG five-op IT); same key + different hash → 409 with existing reference; key-less FUNDING/MATURITY/CLOSURE terminal repeats → 409 with reference. `FdOperationIdempotency` reused, no parallel mechanism.
- **Error contract (D-11/D-12):** 400/404/409/422 mappings pinned by `GlobalExceptionHandlerTest`; 422 premature path throws before any write and never completes a claim; remarks stay internal-record worded.
- **Premature penalty scoping:** `resolvePenaltyOutcome` now resolves via the account's stored `appliedRateOutcomeId` (04 HI-01 fixed, verified still in place at `PrematureClosureService.java:304-315`); approve carries the null-snapshot guards from 04 MED-03 (lines 198-210).
- **Secrets/crypto:** No hardcoded prod secrets, URLs, or JWTs in main or tests; `sha256Hex` uses `MessageDigest SHA-256` correctly (UTF-8, `%02x` hex, null→empty); Testcontainers uses generated credentials via `postgres::getPassword`.
- **CORS:** `Idempotency-Key` added to allowed headers only; origins/methods/matchers untouched.
- **Regression gate (TEST-03):** Full-suite claim (132 run, 0 failures) is a process claim, not re-verified here; scope of this review is the diff, and the diff shows no changes to auth/customer/product/calculator/reporting/migration behavior outside the FD protection wiring.

---

_Reviewed: 2026-09-22_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
