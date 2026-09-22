---
phase: 03-interest-and-maturity-processing
reviewed: 2026-09-22T00:00:00Z
depth: standard
files_reviewed: 13
files_reviewed_list:
  - BACKEND/src/main/java/com/bank/fd/domain/FdInterestCalculator.java
  - BACKEND/src/main/java/com/bank/fd/entity/FdInterestAccrual.java
  - BACKEND/src/main/java/com/bank/fd/repository/FdInterestAccrualRepository.java
  - BACKEND/src/main/resources/db/migration/V8__fd_accrual_maturity.sql
  - BACKEND/src/test/java/com/bank/fd/domain/FdInterestCalculatorTest.java
  - BACKEND/src/test/java/com/bank/fd/FdAccrualSchemaTest.java
  - BACKEND/src/main/java/com/bank/fd/application/AccrualService.java
  - BACKEND/src/main/java/com/bank/fd/application/MaturityService.java
  - BACKEND/src/main/java/com/bank/fd/api/dto/AccrualResponse.java
  - BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java
  - BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml
  - BACKEND/src/test/java/com/bank/fd/application/AccrualMaturityServiceTest.java
  - BACKEND/src/test/java/com/bank/fd/api/FdV1AccrualMaturityContractTest.java
findings:
  critical: 0
  high: 0
  medium: 3
  low: 4
  total: 7
status: issues
---

# Phase 03: Code Review Report — Interest and Maturity Processing

**Reviewed:** 2026-09-22
**Depth:** standard (per-file analysis + threat-model cross-check; tests and OpenAPI skimmed)
**Files Reviewed:** 13
**Status:** issues (advisory only — non-blocking; no Critical/High findings)

## Summary

Reviewed the Phase 3 interest/maturity work (plans 03-01, 03-02) against the
plan threat models (T-03-01…T-03-08), AGENTS.md working rules, and PROJECT.md
constraints. The core is solid: pure-`BigDecimal` Actual/365 math with explicit
scale/rounding and no `double` leakage, holder-ownership checks on every
service path (matching the `FundingService` pattern), ACTIVE-only gating for
both accrual and maturity, server-side amount recomputation with
`business_date` clamping, explicit DTOs (no entity serialization), and an
additive idempotent V8 migration. No secrets, injection, or auth-bypass issues
found.

Seven advisory findings below (3 Medium, 4 Low). None blocks the milestone;
items 2 and 4 are worth addressing before Phase 4 builds closure/history on
top of accrual totals.

## Findings

| ID | Severity | File:Line | Title |
|----|----------|-----------|-------|
| M-01 | Medium | `BACKEND/src/main/java/com/bank/common/exception/GlobalExceptionHandler.java:35-38`, `BACKEND/src/main/java/com/bank/fd/exception/InvalidFdTransitionException.java:7-12`, `BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml:218-219` | Invalid-transition maps to 400 but OpenAPI documents 409 |
| M-02 | Medium | `BACKEND/src/main/java/com/bank/fd/application/AccrualService.java:68-71,118-121` | Accrual amounts are full-period, so `cumulative()` sums overlapping periods |
| M-03 | Medium | `BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java:173-181` | Unguarded `LocalDate.parse` turns bad `business_date` into HTTP 500 |
| L-01 | Low | `BACKEND/src/main/java/com/bank/fd/application/AccrualService.java:68-71`, `BACKEND/src/main/java/com/bank/fd/application/MaturityService.java:85-97` | Response scale inconsistency: accrual rows scale 4 vs maturity response scale 2 |
| L-02 | Low | `BACKEND/src/main/java/com/bank/fd/application/AccrualService.java:63-72` | Pre-booking `business_date` persists a zero-amount accrual row |
| L-03 | Low | `BACKEND/src/main/java/com/bank/fd/application/MaturityService.java:103-114` | Duplicate `MATURITY` transaction possible under concurrent double-POST |
| L-04 | Low | `BACKEND/src/main/java/com/bank/fd/application/MaturityService.java:124`, `BACKEND/src/main/java/com/bank/fd/api/dto/AccrualResponse.java:36-48` | `matured()` cumulative ignores prior accrual rows |

---

## Medium

### M-01: Invalid-transition maps to 400 but OpenAPI documents 409

**File:** `BACKEND/src/main/java/com/bank/common/exception/GlobalExceptionHandler.java:35-38`
**Also:** `BACKEND/src/main/java/com/bank/fd/exception/InvalidFdTransitionException.java:7-12`,
`BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml:218-219`
**Issue:** `InvalidFdTransitionException extends BankingException`, and the only
matching handler is `handleBankingException` → HTTP 400. The OpenAPI contract
for `matureAccount` (and the accrual `409` response) advertises `409 Conflict`
for invalid transitions. A PENDING/CLOSED maturity therefore returns 400 at
runtime while clients coded against the spec expect 409 — a runtime/OpenAPI
drift. (Plan verification anticipated "409/400", so this is a spec-precision
issue, not a logic bug.)
**Fix:**
```java
@ExceptionHandler(InvalidFdTransitionException.class)
public ResponseEntity<Map<String, Object>> handleInvalidTransition(InvalidFdTransitionException ex) {
    return buildResponse(HttpStatus.CONFLICT, ex.getMessage());
}
```
Place it before the `BankingException` handler and keep the OpenAPI `409`
entries as the source of truth.

### M-02: Accrual amounts are full-period, so `cumulative()` sums overlapping periods

**File:** `BACKEND/src/main/java/com/bank/fd/application/AccrualService.java:68-71,118-121`
**Issue:** `runAccrual` always computes interest over `[bookingDate, endDate)`
(the full period to date), not the incremental slice since the last accrual.
Each stored row therefore holds a full-period value, and `cumulative()` sums
`accruedAmount` across rows. Two runs (e.g. 6-month then 12-month) persist
~3250 + ~6500 and report a cumulative of ~9750 instead of ~6500. The stored
`cumulativeAccrued` likewise equals the row's own amount, not a running total,
so the repeat-guard path (line 76-80) and the `cumulative()` endpoint disagree
on what "cumulative" means. Acceptable within the single-period academic scope,
but the OpenAPI advertises history/cumulative endpoints that Phase 4
(closure/history) will build on.
**Fix (pick one and document it):** either (a) make accrual incremental —
`accrued = fullPeriodTo(endDate) − sumOfPriorRows` with `cumulative` as the
running total; or (b) keep the single-snapshot semantic and define
`cumulative()` as the latest row's value (not the sum), rejecting or
overwriting earlier-date runs. Option (a) matches the endpoint names.

### M-03: Unguarded `LocalDate.parse` turns bad `business_date` into HTTP 500

**File:** `BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java:173-181`
**Issue:** `extractBusinessDate` calls `LocalDate.parse(text.trim())` with no
try/catch (and no null-guard on a non-string `business_date` value, which falls
through to `timePolicy.businessDate()` silently). A malformed date string
throws `DateTimeParseException`, which has no handler and falls into
`handleGlobalException` → HTTP 500 with the raw parser message echoed
(`GlobalExceptionHandler.java:78-81`). Client-supplied bad input should be 400.
**Fix:**
```java
if (raw instanceof String text && !text.isBlank()) {
    try {
        return LocalDate.parse(text.trim());
    } catch (DateTimeParseException e) {
        throw new InvalidInputException("business_date must be ISO-8601 (yyyy-MM-dd)");
    }
}
```

---

## Low

### L-01: Response scale inconsistency (scale 4 vs scale 2)

**File:** `BACKEND/src/main/java/com/bank/fd/application/AccrualService.java:68-71`
**Also:** `BACKEND/src/main/java/com/bank/fd/application/MaturityService.java:85-97`
**Issue:** The calculator returns scale 2; `AccrualService` re-scales to 4 for
persistence, so GET accrual payloads carry `6500.0000`, while `MaturityService`
returns the scale-2 `6500.00` in the maturity response. Consumers doing exact or
string comparison between accrual and maturity amounts will see a mismatch.
Monetary value is correct in both cases — this is presentation consistency.
**Fix:** Normalize in the DTO factories, e.g.
`row.getAccruedAmount().setScale(2, RoundingMode.HALF_UP)` in
`AccrualResponse.accrued(...)`, keeping scale-4 only in the JPA column.

### L-02: Pre-booking `business_date` persists a zero-amount accrual row

**File:** `BACKEND/src/main/java/com/bank/fd/application/AccrualService.java:63-72`
**Issue:** If `businessDate` is before `bookingDate`, `interestForPeriod`
returns `0.00` (days ≤ 0 path) and the service persists a zero-value accrual
row instead of rejecting the request. Harmless but pollutes accrual history
with meaningless rows; a client typo (wrong year) gets 200 instead of 400.
**Fix:**
```java
if (endDate.isBefore(account.getBookingDate())) {
    throw new InvalidInputException("business_date precedes the FD booking date");
}
```

### L-03: Duplicate `MATURITY` transaction possible under concurrent double-POST

**File:** `BACKEND/src/main/java/com/bank/fd/application/MaturityService.java:103-114`
**Issue:** Sequential double-maturity is correctly blocked by the ACTIVE status
guard (line 76). But the `MATURITY` row is inserted with a fresh random UUID
and no per-account uniqueness guard, so two concurrent requests that both pass
the status check before either commits can insert two `MATURITY` transactions.
General idempotency hardening is explicitly owned by Phase 5, so this is noted
for that phase, not for fixing here.
**Fix (Phase 5):** add a per-account idempotency guard for the maturity record
(e.g. unique partial index or check-then-insert on `(account_id, txn_type)`),
consistent with the per-date accrual guard from plan 01.

### L-04: `matured()` cumulative ignores prior accrual rows

**File:** `BACKEND/src/main/java/com/bank/fd/application/MaturityService.java:124`
**Also:** `BACKEND/src/main/java/com/bank/fd/api/dto/AccrualResponse.java:36-48`
**Issue:** The maturity response passes `interest` for both `accrued` and
`cumulative`, ignoring any previously persisted accrual rows. After partial
accruals exist, the maturity payload's cumulative disagrees with the
`GET .../accrual/cumulative` total. Follows directly from M-02; fixing M-02
(option a) resolves this too by making the closing row carry the true running
total.
**Fix:** Derive the maturity cumulative from persisted rows
(`sum of prior rows + closing slice`) once the incremental semantic is chosen.

---

## Explicitly checked and clean

- **BigDecimal discipline:** calculator uses scale-12 intermediate / scale-2
  `HALF_UP` output; no `double`/`Math.pow` in new code; legacy
  `FdCalculatorService` untouched per plan.
- **Lifecycle:** accrual rejects non-ACTIVE (400); maturity rejects non-ACTIVE
  via the `FdLifecycleStateMachine` (`MATURE` command) and pre-maturity dates
  via `InvalidInputException`; sequential double-maturity blocked.
- **Ownership/auth:** `findByIdForUpdate` + `FdActorResolver` holder-match +
  `AccessDeniedException` on all four service paths including reads; controller
  roles (`CUSTOMER` for mutations, `isAuthenticated` for reads) match the plan.
- **Migration V8:** forward-only, `ON CONFLICT DO NOTHING` code provisioning,
  `IF NOT EXISTS` unique indexes, duplicate-`fia_uuid` preflight; no edits to
  V1–V7. Entity column mapping matches the V1 schema; per-date repeat guard
  uses UTC start-of-day consistently on both write and read paths.
- **Threat mitigations T-03-01…T-03-08:** verified implemented as specified
  (pure-function calculator, clamped client date, holder checks, MATURITY audit
  row with internal-only remark, DTO-only responses).
- **No secrets/credentials, no injection surfaces** (`business_date` is parsed
  to `LocalDate`, never interpolated), no sensitive data in logs.

---

_Reviewed: 2026-09-22_
_Reviewer: the agent (gsd-code-reviewer)_
_Depth: standard_
