---
phase: 03-interest-and-maturity-processing
verified: 2026-09-22T07:00:00Z
status: human_needed
score: 4/4 must-haves verified
overrides_applied: 0
re_verification: null
human_verification:
  - test: "Live REST demo book→fund→accrue→mature against Supabase/Postgres"
    expected: "POST accrual/run returns 200 with accrued_amount; GET accrual reads back rows; POST mature on/after maturity date returns MATURED; pre-maturity returns 400; PENDING accrue/mature rejected (400/409), not 200"
    why_human: "Requires a running server with Supabase/Postgres; Docker-backed integration execution is waived per Phase 2 gate, and contract tests mock services"
---

# Phase 3: Interest and Maturity Processing Verification Report

**Phase Goal:** An active FD can be processed with one consistent, explainable money policy through interest accrual and maturity.
**Verified:** 2026-09-22T07:00:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
| --- | --- | --- | --- |
| 1 | Interest and maturity amounts are reproducible with BigDecimal, explicit currency scale, and explicit rounding rather than binary floating-point arithmetic (SC1) | ✓ VERIFIED | `FdInterestCalculator` uses only `BigDecimal` with scale-12 `HALF_UP` intermediate and scale-2 `HALF_UP` money output; zero `double`/`Math.pow` in new code; legacy `FdCalculatorService` untouched; `FdInterestCalculatorTest` (5 tests) passes incl. known 6500.00 value |
| 2 | The same documented calculation rules produce compatible estimates, accrual amounts, maturity values, and later closure inputs for the supported lab product (SC2) | ✓ VERIFIED | `AccrualService` and `MaturityService` both call `FdInterestCalculator.interestForPeriod` over `[bookingDate, endDate)`; `maturityAmount` numerically identical to `AccountBookingService.maturityEstimate`; booking-parity asserted in unit tests; `AccrualMaturityServiceTest` known-value 6500.00 passes |
| 3 | An eligible active FD can accrue for its supported business date(s), and the resulting accrual information can be read back for demonstration (SC3) | ✓ VERIFIED | `AccrualService.runAccrual` (ACTIVE-only, per-date repeat guard, clamps to maturity date) + `listAccruals`/`cumulative` reads wired to `GET /accounts/{id}/accrual` and `/accrual/cumulative`; `FdV1AccrualMaturityContractTest` (6 tests) passes; `FdInterestAccrual` entity + repository + V8 migration verified |
| 4 | When the stored maturity date is reached, processing brings the calculation through the required end date and transitions the FD to matured; pending or already-closed FDs are not matured (SC4) | ✓ VERIFIED | `MaturityService.processMaturity` recomputes final interest through `maturityDate`, requires `businessDate >= maturityDate` (else `InvalidInputException`), rejects non-ACTIVE via `FdLifecycleCommand.MATURE` state machine, writes closing accrual row + `MATURITY` transaction, sets `MATURED`; unit tests cover pre-maturity throw and PENDING/CLOSED rejection |

**Score:** 4/4 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
| -------- | -------- | ------ | ------- |
| `BACKEND/src/main/java/com/bank/fd/domain/FdInterestCalculator.java` | Shared BigDecimal Actual/365 math (`interestForPeriod`, `maturityAmount`) | ✓ VERIFIED | Exists, substantive (75 lines, pure static, no Spring), wired (called by both services) |
| `BACKEND/src/main/java/com/bank/fd/entity/FdInterestAccrual.java` | JPA mapping of `public.fd_interest_accrual` | ✓ VERIFIED | Exists, maps `fia_accrual_id` + all V1 columns, `@PrePersist` uuid |
| `BACKEND/src/main/java/com/bank/fd/repository/FdInterestAccrualRepository.java` | Ordered + per-date accrual lookups | ✓ VERIFIED | Exists with `findByAccountIdOrderByAccrualDateAscIdAsc` and `findByAccountIdAndAccrualDate` |
| `BACKEND/src/main/resources/db/migration/V8__fd_accrual_maturity.sql` | `INTEREST_ACCRUAL`/`MATURITY` codes + uniqueness guards | ✓ VERIFIED | Idempotent `ON CONFLICT DO NOTHING` provisioning, preflight + 2 unique indexes, V1–V7 untouched |
| `BACKEND/src/main/java/com/bank/fd/application/AccrualService.java` | ACTIVE-only accrual run backed by calculator | ✓ VERIFIED | Contains `FdInterestCalculator.interestForPeriod` + `findByIdForUpdate`, holder-match, repeat guard |
| `BACKEND/src/main/java/com/bank/fd/application/MaturityService.java` | Eligibility-checked ACTIVE→MATURED transition | ✓ VERIFIED | Contains `FdLifecycleCommand.MATURE` + `MATURITY` code lookup + `FdInterestCalculator` call |
| `BACKEND/src/main/java/com/bank/fd/api/dto/AccrualResponse.java` | Typed accrual/maturity response | ✓ VERIFIED | Record with `accrual_date` JSON property, `accrued`/`matured` factories, no entity leak |
| `BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java` | Implemented accrual/maturity endpoints | ✓ VERIFIED | Contains `accrualService.runAccrual` + `maturityService.processMaturity`; `notImplemented("closeAccount")` preserved |
| `BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml` | Documented accrual/maturity operations | ✓ VERIFIED | `runAccrual`, `listAccrual`, `getCumulativeAccrual`, `matureAccount` + `AccrualResponse` schema |

### Key Link Verification

| From | To | Via | Status | Details |
| ---- | --- | --- | ------ | ------- |
| `FdInterestCalculator.java` | `AccountBookingService.java` | Same Actual/365 formula as `maturityEstimate` | WIRED | `divide(...,365...HALF_UP)` pattern; booking-parity test asserts equality |
| `FdInterestAccrual.java` | `public.fd_interest_accrual` | JPA column mapping validated by schema test | WIRED | `fia_accrual_id` mapping; `FdAccrualSchemaTest` (3 tests) passes |
| `AccrualService.java` | `FdInterestCalculator.java` | Interest computation call | WIRED | `FdInterestCalculator.interestForPeriod` line 68 |
| `MaturityService.java` | `FdLifecycleStateMachine` | MATURE command transition | WIRED | `FdLifecycleCommand.MATURE` line 77 + `stateMachine.transition(ACTIVE, MATURE)` line 116 |
| `FdV1ContractController.java` | `AccrualService`/`MaturityService` | Endpoint delegation | WIRED | `accrualService.runAccrual`, `accrualService.listAccruals`, `accrualService.cumulative`, `maturityService.processMaturity` |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
| -------- | ------------- | ------ | ------------------ | ------ |
| `AccrualService.runAccrual` | `interest` | `FdInterestCalculator.interestForPeriod(principal, rate, bookingDate, endDate)` from locked `FdAccount` row | ✓ FLOWING | Server-owned principal/rate/dates → persisted `FdInterestAccrual` row |
| `MaturityService.processMaturity` | `finalMaturity` | `principal + interestForPeriod(...bookingDate, maturityDate)` from locked row | ✓ FLOWING | Persisted to account `maturityAmount` + `MATURITY` transaction row |
| `AccrualResponse` | amounts | DTO factories from persisted rows/account | ✓ FLOWING | No hardcoded empty props; no entity serialization |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
| -------- | ------- | ------ | ------ |
| Focused unit + contract tests | `mvn -Dtest=FdInterestCalculatorTest,FdAccrualSchemaTest,AccrualMaturityServiceTest,FdV1AccrualMaturityContractTest test` | Tests run: 21, Failures: 0, Errors: 0, Skipped: 0 — BUILD SUCCESS | ✓ PASS |
| Known-value math | `FdInterestCalculatorTest` asserts 100000 @ 0.0650 × 365d = 6500.00 / 106500.00 | Pass (in suite above) | ✓ PASS |
| Full suite (prior evidence) | Full-suite result noted in context: 64 run, 0 failures, 5 Docker-skipped | Recorded, not re-run (focused run sufficient for this phase) | ✓ PASS (prior) |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
| ----------- | ---------- | ----------- | ------ | -------- |
| CALC-01 | 03-01 | BigDecimal/decimal arithmetic with explicit scale and rounding, no binary float | ✓ SATISFIED | Calculator scale-12/scale-2 HALF_UP; no `double` in new code; legacy calculator untouched |
| CALC-02 | 03-01, 03-02 | Same rules reused for estimates, accrual, maturity, closure inputs | ✓ SATISFIED | Single shared calculator used by booking estimate, accrual, and maturity; parity tested |
| LIFE-02 | 03-02 | Maturity processing for eligible active FD → matured via stored term/maturity date | ✓ SATISFIED | `MaturityService` eligibility + transition + tests; PENDING/CLOSED rejected |

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
| ---- | ---- | ------- | -------- | ------ |
| (none in phase files) | - | TODO/FIXME/placeholder/empty-return/`501` in new services | - | Clean — grep found no matches |

### Review Findings Assessment (03-REVIEW.md: 0 critical/high, 3 medium, 4 low)

All 7 findings are advisory and none breaks a success criterion:

- **M-01** (invalid-transition maps to 400, OpenAPI documents 409): spec-precision drift only; SC4 requires rejection, which happens. Phase 4 (API-02) owns error-shape consistency.
- **M-02** (full-period accrual rows summed by `cumulative()`): within the single-period academic scope, accrual = full-period interest and maturity = principal + interest are mutually compatible (SC2/SC3). Multi-period cumulative semantics is a Phase 4 closure/history concern.
- **M-03** (unguarded `LocalDate.parse` → 500 on bad `business_date`): malformed-input handling belongs to API-02 (Phase 4), not Phase 3 SCs.
- **L-01** (scale-4 vs scale-2 response presentation): monetary values correct; presentation normalization is Phase 4 polish.
- **L-02** (pre-booking date persists zero row): harmless; input-validation hardening is Phase 4/5.
- **L-03** (concurrent double-POST duplicate MATURITY): explicitly owned by Phase 5 idempotency gate.
- **L-04** (maturity cumulative ignores prior rows): follows from M-02; same Phase 4 resolution.

### Human Verification Required

### 1. Live REST demo book→fund→accrue→mature against Supabase

**Test:** Against a running backend with Supabase/Postgres: book → fund → `POST /fd/v1/accounts/{id}/accrual/run` → `GET` accrual + cumulative → `POST /fd/v1/accounts/{id}/mature` (pre-maturity must return 400; PENDING accrue/mature must be rejected, not 200).
**Expected:** 200s with `accrued_amount`/`cumulative_accrued`/MATURED status on the happy path; truthful 400/409 rejections on ineligible paths.
**Why human:** Requires a running server with Supabase/Postgres; Docker-backed integration execution is waived per the Phase 2 gate and contract tests mock services, so no automated end-to-end proof exists yet.

### Gaps Summary

No gaps. All 4 roadmap success criteria verified against source, wiring, OpenAPI, and 21 passing focused tests. The 7 advisory review findings do not fail any must-have truth and are routed to their owning phases (4 → closure/history error-shape and cumulative semantics; 5 → idempotency hardening). Only the live REST demonstration against Supabase awaits human execution.

---

_Verified: 2026-09-22T07:00:00Z_
_Verifier: the agent (gsd-verifier)_
