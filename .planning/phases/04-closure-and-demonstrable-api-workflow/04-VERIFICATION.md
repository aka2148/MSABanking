---
phase: 04-closure-and-demonstrable-api-workflow
verified: 2026-09-22T13:25:00Z
status: human_needed
score: 5/5 must-haves verified
overrides_applied: 0
human_verification:
  - test: "Run the Postman collection end-to-end against a live backend (Supabase-backed)"
    expected: "Products -> book -> fund -> accrue -> mature -> close -> history succeeds; premature variant shows penalty math and the DOC 422 unsupported-rule example returns 422 without closing"
    why_human: "Requires a running server with database; cannot be verified by static checks. Docker-backed execution waived per phase instructions."
  - test: "Confirm premature-closure quote uses the server business date when a business_date body is passed"
    expected: "Quoted amounts reflect server businessDate (clamped to maturity), not the passed date; malformed dates return 400"
    why_human: "MED-01 advisory: business_date is validated but intentionally not forwarded (server-side clamping policy). Live behavior should be observed once."
---

# Phase 4: Closure and Demonstrable API Workflow Verification Report

**Phase Goal:** Students can demonstrate the complete FD lifecycle through the REST contract, including truthful normal or supported premature closure and its financial history.
**Verified:** 2026-09-22T13:25:00Z
**Status:** human_needed
**Re-verification:** No — initial verification

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|-------|--------|----------|
| 1 | REST/Swagger/Postman users can follow product lookup, booking, funding, status, interest/maturity processing, closure, and history retrieval with understandable DTOs (SC1) | ✓ VERIFIED | 6 endpoints wired in `FdV1ContractController` (close, request/list/approve premature-closure, getAccount, listTransactions); OpenAPI `fd-service-openapi.yaml` has `ClosureResponse`/`PrematureClosureResponse`/`HistoryEntry` schemas with stable operationIds; 18-request Postman collection `BACKEND/postman/fd-lifecycle.postman_collection.json` (4 folders in demo order); `PostmanCollectionConsistencyTest` green (1/1) proving every collection request maps to an implemented route |
| 2 | A matured FD can be closed normally after final calculation, with settlement recorded and visible through history (SC2) | ✓ VERIFIED | `ClosureService.close` transitions MATURED→CLOSED via state machine before writes, persists one CLOSURE settlement row (amount = maturityAmount, truthful internal remark); `ClosureServiceTest` 5/5; `FdV1ClosureHistoryContractTest` pins close 200 and 409 paths |
| 3 | Premature closure either applies the available rule with BigDecimal interest/penalty and records the outcome, or returns a clear unsupported error without closing (SC3) | ✓ VERIFIED | Post-review fix `f5e1fd5` present: `resolvePenaltyOutcome` resolves via the account's booked `appliedRateOutcomeId` with CLOSURE_PENALTY type check (no global scan); penalized rate via `PrematurePenalty` + `FdInterestCalculator` (scale-12→scale-4, floored at zero); unsupported/missing/unparseable rule raises 422 with zero writes; approve applies stored snapshot verbatim; `PrematureClosureServiceTest` 12/12 including second-product-row and non-penalty-ref cases |
| 4 | Invalid input, missing resources, unauthorized access, invalid transitions, and unsupported premature closure produce consistent REST errors, not placeholder success (SC4) | ✓ VERIFIED | `GlobalExceptionHandler` maps InvalidInput→400, InvalidFdTransition→409, PrematureClosureNotSupported→422, AccessDenied→403, NotFound→404; malformed `business_date`/reason/txn filters →400; MockMvc contract tests pin 400/409/422 shapes plus zero-service-interaction on bad input; 36 unrelated placeholders verified still 501 |
| 5 | History demonstrates funding, accrual, maturity, closure, and settlement records linked to the FD without claiming an external payment rail (SC5) | ✓ VERIFIED | `HistoryService.timeline` merges `fd_transactions` (DEPOSIT→FUNDING, MATURITY, CLOSURE, PENALTY) with `fd_interest_accrual` rows, time-ordered, DTO-only, ownership-checked; all settlement/penalty remarks state internal academic record with no external rail; collection secret scan clean; `HistoryServiceTest` 3/3 |

**Score:** 5/5 truths verified

### Required Artifacts

| Artifact | Expected | Status | Details |
|----------|----------|--------|---------|
| `BACKEND/src/main/resources/db/migration/V9__fd_closure_history.sql` | CLOSURE/PENALTY codes + fpc_status column | ✓ VERIFIED | Idempotent provisioning, guarded CHECK, V1–V8 untouched; `FdClosureSchemaTest` pins content |
| `BACKEND/src/main/java/com/bank/fd/entity/FdPrematureClosure.java` + repository | Closure persistence | ✓ VERIFIED | Full `fpc_` mapping, per-account ordered lookups |
| `BACKEND/src/main/java/com/bank/fd/domain/PrematurePenalty.java` | D-01/D-02 BigDecimal policy | ✓ VERIFIED | Rate-point reduction via shared calculator, fail-closed parse; 4/4 tests |
| `ClosureService.java` / `PrematureClosureService.java` / `HistoryService.java` + DTOs + `PrematureClosureNotSupportedException` | Business rules | ✓ VERIFIED | Substantive, wired to controller, BigDecimal discipline; HI-01 + MED-03 fixes present |
| `FdV1ContractController.java` (6 endpoints) + `GlobalExceptionHandler` (409/422) | REST wiring + errors | ✓ VERIFIED | Delegation passes `authentication.getName()` into holder checks; most-specific handlers win over generic 400 |
| `fd-service-openapi.yaml` | Contract docs | ✓ VERIFIED | 3 new schemas, 6 operations repointed, 400/403/404/409 (+422) entries, PREMATURELY_CLOSED enum |
| `BACKEND/postman/fd-lifecycle.postman_collection.json` + `PostmanCollectionConsistencyTest` | Demo artifact | ✓ VERIFIED | 18 requests, 4 folders, placeholder-only credentials, reflection drift protection |
| `FdV1ClosureHistoryContractTest` | Contract proof | ✓ VERIFIED | 10/10 MockMvc tests green |

### Key Link Verification

| From | To | Via | Status | Details |
|------|----|-----|--------|---------|
| Controller close/premature/history endpoints | Closure/PrematureClosure/History services | Direct delegation with authenticated username | WIRED | Verified in source (lines 199–250, 373–415) |
| PrematureClosureService | Calculator/state machine/repos | `PrematurePenalty`+`FdInterestCalculator`, transition guards, outcome/transaction/accrual repos | WIRED | Per-account outcome resolution; pre-write guards |
| GlobalExceptionHandler | 400/403/404/409/422 shapes | Dedicated handlers incl. most-specific 409/422 | WIRED | Source-verified; pinned by tests |
| Postman collection | OpenAPI/controller routes | Reflection-based consistency test | WIRED | 1/1 green; operation coverage asserted on hit operations |

### Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|----------|---------------|--------|--------------------|--------|
| ClosureResponse | maturityAmount → settlement row → response | Stored account maturity + CLOSURE transaction row | Yes | ✓ FLOWING |
| PrematureClosureResponse | penalized rate/settlement quote → stored snapshot → approve reuse | Booked outcome ref + BigDecimal math, snapshot persisted | Yes | ✓ FLOWING |
| HistoryEntry timeline | Merged transaction + accrual rows | `fd_transactions` + `fd_interest_accrual` DB reads | Yes | ✓ FLOWING |

### Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|----------|---------|--------|--------|
| Focused unit + contract suite | `mvn -Dtest='PrematureClosureServiceTest,ClosureServiceTest,HistoryServiceTest,FdV1ClosureHistoryContractTest,PostmanCollectionConsistencyTest,PrematurePenaltyTest,FdClosureSchemaTest' test` | 38 run, 0 failures, 0 errors | ✓ PASS |

### Requirements Coverage

| Requirement | Source Plan | Description | Status | Evidence |
|-------------|-------------|-------------|--------|----------|
| LIFE-03 | 04-01, 04-02, 04-03 | Normal closure of matured FD with settlement record | ✓ SATISFIED | ClosureService + CLOSURE row + contract tests |
| LIFE-04 | 04-01, 04-02, 04-03 | Premature closure with BigDecimal penalty or clear business error | ✓ SATISFIED | Two-step service, per-product rule, 422 path, 12 tests |
| HIST-01 | 04-02, 04-03 | Funding/accrual/maturity/closure/settlement history linked to FD | ✓ SATISFIED | Unified timeline service + history endpoint + 3 tests |
| API-01 | 04-03, 04-04 | REST endpoints for full lifecycle with understandable DTOs | ✓ SATISFIED | 6 endpoints, OpenAPI schemas, Postman collection + consistency test |
| API-02 | 04-03 | Consistent error responses | ✓ SATISFIED | 400/403/404/409/422 handlers + pinned shapes |

No orphaned requirements: all Phase 4 IDs (LIFE-03, LIFE-04, HIST-01, API-01, API-02) are claimed across 04-01–04-04 summaries.

### Anti-Patterns Found

| File | Line | Pattern | Severity | Impact |
|------|------|---------|----------|--------|
| `FdV1ContractController.java` | 373–381, 404–415 | MED-01: `business_date` validated but discarded on premature paths (service uses server `timePolicy.businessDate()`) | ℹ️ Advisory | Demo still follows server-date policy (established Phase 1–3 clamping); documented in 04-03 summary as intentional. Not goal-blocking. Human item recorded. |
| `PrematureClosureService.java` | 210–223 | MED-02: closing accrual slice stores total penalized interest (not residual over prior accruals) | ℹ️ Advisory | Running balance still equals settlement (correct total); only the slice `amount` field overstates when prior accrual rows exist; same-date guard prevents dupes. Not goal-blocking. |
| Various | — | LOW-01 (getAccount write-lock read), LOW-02 (unused repo method), LOW-03 (exponent-tolerant parse, fail-closed), LOW-04 (PENALTY as CREDIT code) | ℹ️ Info | No correctness/goal impact; safe to defer. |
| Fixed | — | HI-01 (per-product penalty) + MED-03 (approve null guards →400) | — | Confirmed present in `PrematureClosureService.java` via commit `f5e1fd5`; covered by new tests. |

No TODO/FIXME/placeholder-success patterns found in Phase 4 main paths; remaining 501s are intentional deferred surface.

### Human Verification Required

### 1. Live REST demo against Supabase-backed backend

**Test:** Import `BACKEND/postman/fd-lifecycle.postman_collection.json`, set `authToken` from `POST /api/auth/login`, run folders 1–4 in order (normal path then premature variant, incl. DOC 409/422 examples).
**Expected:** Full path succeeds with understandable DTOs; DOC 409 (close non-matured) and DOC 422 (unsupported rule) return structured errors without state change; history shows funding→accrual→maturity→closure→settlement with internal-record wording.
**Why human:** Requires a running server with database; static checks + MockMvc cannot prove live behavior. Docker-backed execution waived.

### 2. Server business-date semantics on premature quote

**Test:** Pass an explicit `business_date` in the premature-closure request body and compare the quote against a server-today quote.
**Expected:** Amounts follow the server business date (clamped to maturity); the passed date is accepted but does not change the quote; malformed dates return 400.
**Why human:** MED-01 advisory documents intentional validation-only handling; live observation confirms the contract is understood.

### Gaps Summary

No gaps. All five roadmap success criteria verify against source, wiring, OpenAPI, Postman, and 38/38 focused tests green. Review HI-01 and MED-03 fixes confirmed in-tree. Remaining review findings (MED-01, MED-02, four lows) are advisories assessed above with no impact on the must-have truths. Status is `human_needed` solely because the live end-to-end REST demo requires a running backend.

---

_Verified: 2026-09-22T13:25:00Z_
_Verifier: the agent (gsd-verifier)_
