---
phase: 05-protection-tests-and-regression-gate
verified: 2026-09-22T13:40:00Z
status: human_needed
score: 5/5 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Run the Postman collection end-to-end against a live backend (Supabase-backed)"
    expected: "Products -> book -> fund -> accrue -> mature -> close -> history succeeds with Idempotency-Key retries returning identical bodies; cross-customer FD access returns 404; staff role can still read"
    why_human: "Requires a running server with database; cannot be verified by static checks. Docker-backed execution waived per phase instructions."
  - test: "Execute FdPhase5ProtectionIT against real PostgreSQL where Docker is available"
    expected: "mvn -Dtest='FdPhase5ProtectionIT' test -> 2 run, 0 failures (book -> fund -> accrue -> mature -> close -> timeline with byte-identical replays and unchanged row counts)"
    why_human: "Docker is not available in this environment; the IT skips clean by design. Live PG execution proves Flyway persistence, history linkage, and five-op duplicate safety on a real database."
---

# Phase 5: Protection, Tests, and Regression Gate Verification Report

**Phase Goal:** The demonstrable FD workflow is safe for ordinary retries and basic customer/staff use, and evidence exists that it did not regress the existing college lab application.
**Verified:** 2026-09-22T13:40:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | Repeating booking, funding, accrual, maturity, or closure for the same operation returns the existing result or a clear conflict without duplicate effects (SC1; FUND-02, HIST-02) | ✓ VERIFIED | `FdIdempotencyGuard` claim/replay wired into all 7 paths (BOOKING/FUNDING/ACCRUAL/MATURITY/CLOSURE/PREMATURE_REQUEST/PREMATURE_APPROVE) reusing `FdOperationIdempotency`; same-key+same-hash replays rebuild the stored DTO with zero new rows; same-key+different-hash → 409 with existing-record reference via `FdDuplicateOperationException` → `GlobalExceptionHandler` 409; key-less terminal repeats (ACTIVE/MATURED/CLOSED) → 409 referencing the existing record; `FdOwnershipContractTest` pins byte-identical fund replay with exactly one DEPOSIT row; surefire: 5/5 green |
| 2 | FD endpoints derive customer identity from the authenticated actor; cross-customer access 404s without leaking existence; staff/admin still work (SC2; AUTH-01, AUTH-02) | ✓ VERIFIED | `FdAccessPolicy.requireOwned` (holder-match → `ROLE_BANK_OFFICER`/`ROLE_ADMIN` bypass → 404) wired into all 7 services + both controller reads (`getAccount`, `getPrematureClosure`) + history timeline + both accrual reads; zero remaining `does not own` AccessDeniedException in FD scope; controller's 7 mutating endpoints widened to `hasAnyRole('CUSTOMER','BANK_OFFICER','ADMIN')` with `Idempotency-Key` threading; MockMvc pins 4 cross-customer 404s + staff 200 read; `FdAccessPolicyTest` 6/6 ownership-matrix green |
| 3 | Focused unit tests cover decimal interest, maturity, penalty/unsupported-premature, validation, lifecycle boundaries, invalid transitions (SC3; TEST-01) | ✓ VERIFIED | 4 new classes, 25 tests, all green without Spring/Docker: `FdIdempotencyGuardTest` 6/6 (replay/mismatch/blank-key/sha256), `FdAccessPolicyTest` 6/6, `GlobalExceptionHandlerTest` 5/5 (409/409/422/404/400 + integer body status), `FdPhase5BoundaryTest` 8/8 (zero-day principal identity, one-day 17.81, 100-point penalty floor, 3 invalid transitions, persistence-free validation); existing `FdInterestCalculatorTest`/`FdLifecycleStateMachineTest`/`PrematurePenaltyTest` untouched and green |
| 4 | PostgreSQL-backed integration tests cover persistence, REST flows, ownership failures, history, duplicate protection (SC4; TEST-02) | ✓ VERIFIED | `FdOwnershipContractTest` 5/5 MockMvc proof on H2 without Docker (ownership probes, staff read, 409/422/400 contract, byte-identical replay); `FdPhase5ProtectionIT` (Testcontainers PG16, Flyway V1–V9, five-op duplicate safety + timeline linkage) compiles in the gate and skips clean without Docker (2 run, 0 failures, 2 skipped, exit 0) per the Docker-unavailable constraint; live-PG execution recorded as human follow-up |
| 5 | Existing auth/customer/product/calculator/reporting/migration behavior remains passing (SC5; TEST-03) | ✓ VERIFIED | Surefire aggregate from `BACKEND/target/surefire-reports`: 134 run, 0 failures, 0 errors, 7 skipped (5 pre-existing Docker-gated + 2 new Docker-gated IT skips); no changes to non-FD behavior outside FD protection wiring; `SecurityConfig` matchers otherwise untouched, CORS change additive (Idempotency-Key header only) |

**Score:** 5/5 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `FdIdempotencyGuard.java` + `FdDuplicateOperationException.java` | Durable claim/replay guard, 409 with reference | ✓ VERIFIED | Substantive (PENDING/COMPLETED claim, same-hash replay, mismatch 409, blank-key null, sha256Hex); wired into all 7 services |
| `FdAccessPolicy.java` | 404-hiding ownership + staff bypass | ✓ VERIFIED | Holder-match via `FdActorResolver`, `BANK_OFFICER`/`ADMIN` bypass, else `ResourceNotFoundException`; wired into services + controller reads + history/accrual reads |
| `GlobalExceptionHandler.java` (409) + `SecurityConfig.java` (CORS) | Error contract + key header | ✓ VERIFIED | Duplicate → 409 via `buildResponse` envelope; `Idempotency-Key` in allowedHeaders; existing 403 handler/matchers untouched |
| 7 hardened services + `FdV1ContractController` key/role threading | Duplicate safety + ownership end-to-end | ✓ VERIFIED | Claim-before-transition with replay-from-stored-aggregate; 7 keys + 7 widened roles; read-only `getAccount` uses `findById` (Rule-1 fix in `171fe68`) |
| 4 unit test classes (25 tests) | TEST-01 proof | ✓ VERIFIED | All green; zero existing files modified by 05-04 commits |
| `FdOwnershipContractTest` (5) + `FdPhase5ProtectionIT` (2) | TEST-02 proof | ✓ VERIFIED | 5/5 green; IT skip-clean 2/2 without Docker, exit 0 |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| Controller 7 mutating endpoints | Service keyed overloads | `Idempotency-Key` header forwarded verbatim | WIRED | Source-verified: 7 header params, key-less delegates preserved |
| Services | `FdIdempotencyGuard` / `FdAccessPolicy` | claim/complete + requireOwned inside transactions | WIRED | 17 grep hits across 7 services; SecurityContextHolder-fed authorities |
| `FdDuplicateOperationException` | HTTP 409 | `GlobalExceptionHandler` dedicated handler | WIRED | Source-verified lines 52–55; pinned by handler + contract tests |
| Same-key replay | Stored aggregate → identical DTO | Reload + rebuild, zero new rows | WIRED | MockMvc byte-identical + row-count assertions; PG five-op IT |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| Fund replay DTO | Stored FD + DEPOSIT row | Reloaded aggregate, no second row | Yes | ✓ FLOWING |
| Maturity replay DTO | Deterministic recompute via `FdInterestCalculator` | Stored account fields | Yes | ✓ FLOWING |
| Closure replay DTO | Stored settlement (maturity amount) | Stored CLOSURE transaction row | Yes | ✓ FLOWING (amount; date drifts per MED-01 advisory) |
| Premature request/approve replay | Stored closure row | Reloaded row, requested/approved shapes | Yes | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| MockMvc ownership/replay contract | surefire `FdOwnershipContractTest` | 5 run, 0 failures | ✓ PASS |
| Docker-gated IT skip-clean | surefire `FdPhase5ProtectionIT` | 2 run, 0 failures, 2 skipped | ✓ PASS |
| Unit layer (new + domain) | surefire guard/policy/handler/boundary | 25 new + existing domain green | ✓ PASS |
| Full-suite regression gate | surefire aggregate | 134 run, 0 failures, 0 errors, 7 skipped | ✓ PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| FUND-02 | 05-01, 05-02, 05-05 | No duplicate FD/principal on booking/funding repeat | ✓ SATISFIED | BOOKING/FUNDING claim/replay + 409s + byte-identical replay proof |
| HIST-02 | 05-01, 05-03, 05-05 | No duplicate accrual/maturity/closure history on repeat | ✓ SATISFIED | ACCRUAL/MATURITY/CLOSURE/PREMATURE_* scopes + zero-new-row replays |
| AUTH-01 | 05-01, 05-02, 05-03 | Identity from authenticated actor, never caller-supplied | ✓ SATISFIED | `fd-user-<resolved-id>` actor keys; 05-RESEARCH gap (zero services used identity) closed |
| AUTH-02 | 05-01, 05-02, 05-03 | Cross-customer 404, staff roles preserved | ✓ SATISFIED | 404 probes x4 + staff read; 05-RESEARCH gap (403 + CUSTOMER-only) closed |
| TEST-01 | 05-04 | Unit coverage incl. boundaries/invalid | ✓ SATISFIED | 25 new tests + untouched domain suites green |
| TEST-02 | 05-05 | Integration coverage (persistence/flows/ownership/history/duplicates) | ✓ SATISFIED | MockMvc 5/5 + PG IT skip-clean; live run is human follow-up |
| TEST-03 | 05-05 | Full existing suite green | ✓ SATISFIED | 134 run, 0 failures |

No orphaned requirements: all 7 Phase 5 IDs (FUND-02, HIST-02, AUTH-01, AUTH-02, TEST-01, TEST-02, TEST-03) are claimed across 05-01–05-05 summaries.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `ClosureService.java` | 93 vs 115/131 | MED-01: closure replay stamps current `businessDate()` instead of stored close date — cross-day replay body drifts, vs D-01 "same success body" | ℹ️ Advisory | Same-day replays byte-identical (all the IT proves); settlement amount identical across days; zero duplicate rows. Core SC1 (no duplicate effects) holds. Fix needs stored-date persistence + clock-advance test; safe to defer to a polish pass. |
| `MaturityService.java:88-108`, `ClosureService.java:81-98`, `PrematureClosureService.java:110-119,179-193` | claim-before-validate | MED-02: keyed invalid attempts (mature-while-PENDING, close-while-ACTIVE) persist orphan PENDING claims; concurrent same-key race escapes as 500 not D-11 409 | ℹ️ Advisory | Orphan rows are audit noise, not duplicate financial effects; race window needs true concurrency to hit in a college demo. Fix is claim-after-validate (accrual precedent) + catch-and-reread in guard; safe to defer, no locks per AGENTS.md. |
| `FdV1ContractController.java` | 387–389, 419–421 | MED-03: premature `business_date` validated but discarded (04 MED-01 carry-over) | ℹ️ Advisory | Server-date clamping is the established Phase 1–3 policy, documented as intentional in Phase 4 verification; unchanged by this phase. Not a Phase 5 SC failure. |
| Various | — | LOW-01 (`aggregate:null` PENDING-collision reference), LOW-02 (`listProducts` still CUSTOMER-only), LOW-03 (booking hash NPEs pre-validation off-HTTP), LOW-04 (test-only fixture password) | ℹ️ Info | No correctness/goal impact; safe to defer. |

No TODO/FIXME/placeholder-success patterns in Phase 5 paths; no secrets in source (test fixture password only, consistent with `SecurityFlowTest` style).

### Assessment: Review MED Findings vs Phase 5 Success Criteria

None of the 3 MED findings fails a Phase 5 success criterion: MED-01 affects only the `closedDate` field of cross-day replays (settlement amount + zero-new-rows hold — SC1's anti-duplication core is intact); MED-02 concerns orphan audit rows and an untested concurrency race (no duplicate financial effect — SC1 intact); MED-03 is pre-existing documented server-date policy, not a Phase 5 regression (SC1–SC5 intact). All 4 LOWs are non-blocking. No gap-closure plan required; MED-01/MED-02 are recommended polish items.

### Human Verification Required

### 1. Live REST demo against Supabase-backed backend

**Test:** Import `BACKEND/postman/fd-lifecycle.postman_collection.json`, set `authToken` from `POST /api/auth/login`, run folders 1–4 in order; retry booking/funding/close with the same `Idempotency-Key` and confirm identical bodies; fetch another customer's FD id as a plain customer (expect 404) and as staff (expect 200).
**Expected:** Full path succeeds with understandable DTOs; retries return identical results with no duplicate rows; cross-customer access 404s; staff reads work.
**Why human:** Requires a running server with database; static checks + MockMvc cannot prove live behavior. Docker-backed execution waived.

### 2. Live PostgreSQL run of FdPhase5ProtectionIT

**Test:** `mvn -Dtest='FdPhase5ProtectionIT' test` with `JWT_SECRET` >= 64 bytes where Docker is available.
**Expected:** 2 run, 0 failures — five-op duplicate safety and timeline linkage on real PostgreSQL.
**Why human:** Docker is unavailable here; the IT skips clean by design.

### Gaps Summary

No gaps. All five roadmap success criteria verify against source, wiring, and test evidence (MockMvc 5/5, unit layer 25/25 new, full suite 134 run / 0 failures). The 3 MED + 4 LOW review findings are advisories assessed above with no impact on the must-have truths. Status is `human_needed` solely because the live end-to-end REST demo and the live-PG IT run require a running backend/database.

---

_Verified: 2026-09-22T13:40:00Z_
_Verifier: the agent (gsd-verifier)_
