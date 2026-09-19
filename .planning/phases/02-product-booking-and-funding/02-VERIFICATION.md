---
phase: 02-product-booking-and-funding
verified: 2026-09-19T15:35:00Z
status: human_needed
score: 5/5 must-haves verified
overrides_applied: 0
re_verification:
  previous_status: gaps_found
  previous_score: 4/5
  gaps_closed:
    - "The booking 201 response now documents AccountResponse, and the funding route is documented with FundingResponse."
  gaps_remaining: []
  regressions: []
human_verification:
  - test: "Run the PostgreSQL/Testcontainers application-flow tests with Docker enabled."
    expected: "Flyway V1-V7 apply on PostgreSQL; the real booking/funding application path persists PENDING, then exactly one DEPOSIT and ACTIVE atomically, and invalid funding leaves no orphan transaction."
    why_human: "Docker Desktop is installed but its daemon is unavailable in this environment; Maven skips the Testcontainers class, so PostgreSQL persistence and application-flow behavior cannot be observed locally."
---

# Phase 2: Product Booking and Funding Verification Report

**Phase Goal:** An authenticated customer can select an eligible FD offer, create a correctly associated pending account with locked terms, and fund it once to become active.
**Verified:** 2026-09-19
**Status:** human_needed
**Re-verification:** Yes — after commit `bb56d1d` fixed the OpenAPI contract mismatch.

## Goal Achievement

### Observable Truths

| # | Truth | Status | Evidence |
|---|---|---|---|
| 1 | A customer can retrieve active, eligible products with supported terms, rates, amount limits, currency, and maturity information. | ✓ VERIFIED | Authenticated `GET /fd/v1/products` calls `ProductOfferQueryService`, which reads server-owned `ProductTermsPort` data and maps the offer DTO. Adapter tests pass. |
| 2 | A valid booking creates a customer-associated PENDING/unfunded account with locked terms, dates, estimate, and policy version. | ✓ VERIFIED | `FdV1ContractController.createAccount` passes the authenticated username to transactional `AccountBookingService.book`; the service resolves the holder and terms, snapshots them, saves the account, and returns `AccountResponse` with `PENDING` and `funding_recorded=false`. |
| 3 | Invalid product, amount, currency, tenure, required-field, or actor/profile combinations fail before saving. | ✓ VERIFIED | `BookingRequest` validation, fail-closed normalized terms resolution, authenticated actor resolution, and pre-save ordering are covered by `NormalizedProductTermsAdapterTest` and `AccountBookingServiceTest`. |
| 4 | Principal, rate, term, dates, maturity estimate, and policy version remain an agreed booking snapshot. | ✓ VERIFIED | `FdAccount` stores the resolved values; `V7__fd_booking_snapshot.sql` adds `fa_policy_version`; booking derives dates/estimate with the explicit BigDecimal Actual/365 policy. Schema and booking tests pass. |
| 5 | Exact-principal funding records internal DEPOSIT and transitions PENDING to ACTIVE through the lifecycle boundary. | ✓ VERIFIED (application/unit evidence; PostgreSQL pending) | `POST /fd/v1/accounts/{accountId}/fund` calls `FundingService.fund`; it locks the row, checks holder/exact amount/currency, resolves DEPOSIT, saves the transaction, and applies `FdLifecycleCommand.FUND`. `FundingServiceTest` passes. The Docker-backed persistence/application-flow assertion remains human-needed. |

**Score:** 5/5 truths verified from source and executable JVM tests; PostgreSQL/Testcontainers execution is pending human-environment verification.

## Required Artifacts

| Artifact | Expected | Status | Details |
|---|---|---|---|
| `BACKEND/src/main/java/com/bank/fd/application/NormalizedProductTermsAdapter.java` | Server-authoritative eligible terms | ✓ VERIFIED | Substantive fail-closed normalized/reference-data adapter; wired through `ProductTermsPort`. |
| `BACKEND/src/main/resources/db/migration/V7__fd_booking_snapshot.sql` | Additive booking snapshot/reference prerequisites | ✓ VERIFIED (runtime pending) | Adds policy snapshot, reference uniqueness/preflight, and DEPOSIT provisioning; PostgreSQL application is not runnable without Docker. |
| `BACKEND/src/main/java/com/bank/fd/application/AccountBookingService.java` | Authenticated transactional booking | ✓ VERIFIED | Resolves actor/terms, derives UTC dates and BigDecimal estimate, and saves a PENDING snapshot. |
| `BACKEND/src/main/java/com/bank/fd/application/FundingService.java` | Locked exact-principal funding | ✓ VERIFIED | Transactional lock, ownership and amount/currency checks, DEPOSIT creation, state-machine transition, and account save. |
| `BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java` | Products, booking, and funding routes | ✓ VERIFIED | `/products`, `/accounts`, and `/accounts/{accountId}/fund` are typed and customer-protected; later lifecycle placeholders remain intentional. |
| `BACKEND/src/main/java/com/bank/fd/api/dto/AccountResponse.java` | Runtime booking response | ✓ VERIFIED | Explicit DTO returned by the 201 booking method. |
| `BACKEND/src/main/java/com/bank/fd/api/dto/FundingResponse.java` | Runtime funding response | ✓ VERIFIED | Explicit ACTIVE response with truthful internal-record/no-external-rail wording. |
| `BACKEND/src/test/java/com/bank/fd/FdBookingFundingIntegrationTest.java` | PostgreSQL booking/funding proof | ⚠️ PENDING | Test class exists, but all 3 tests were skipped because Docker is unavailable; its SQL assertions do not substitute for an executed application-flow test. |

## OpenAPI Contract Confirmation

| Route | Runtime implementation | OpenAPI documentation | Status |
|---|---|---|---|
| `POST /fd/v1/accounts` | `FdV1ContractController.createAccount` returns `ResponseEntity<AccountResponse>` | 201 response references `#/components/schemas/AccountResponse` | ✓ VERIFIED |
| `POST /fd/v1/accounts/{accountId}/fund` | `fundAccount` calls `FundingService` and returns `ResponseEntity<FundingResponse>` | Route is present; 200 response references `#/components/schemas/FundingResponse` and documents `FundingRequest`/errors | ✓ VERIFIED |

Commit `bb56d1d` closes the former booking-schema mismatch. `FdV1BookingContractTest` (4/4) and `FdV1ContractBoundaryTest` (4/4) pass against the current source/classpath contract.

## Key Link Verification

| From | To | Via | Status | Details |
|---|---|---|---|---|
| `FdV1ContractController.createAccount` | `AccountBookingService` | authenticated principal + `bookingService.book` | ✓ WIRED | Username is passed from `Authentication`; customer role is required. |
| `FdV1ContractController.listProducts` | `ProductOfferQueryService` | `findEligibleOffers(timePolicy.businessDate())` | ✓ WIRED | Customer-protected route maps server terms to offer DTOs. |
| `AccountBookingService` | `ProductTermsPort` | `resolveActiveTerms` before save | ✓ WIRED | Terms resolve before account construction/persistence. |
| `AccountBookingService` | `FdAccountRepository.save` | transactional PENDING snapshot | ✓ WIRED | Server-owned values are copied to the account and saved. |
| `FdV1ContractController.fundAccount` | `FundingService` | `POST /accounts/{accountId}/fund` | ✓ WIRED | Typed request and authenticated username are passed through. |
| `FundingService` | `FdAccountRepository.findByIdForUpdate` | pessimistic write lock | ✓ WIRED | Account is locked before ownership and funding checks. |
| `FundingService` | `FdLifecycleStateMachine` | `FdLifecycleCommand.FUND` | ✓ WIRED | ACTIVE is obtained from the state-machine transition, not direct assignment. |
| Runtime booking/funding DTOs | OpenAPI | schema references | ✓ WIRED | Booking is `AccountResponse`; funding is `FundingResponse`; confirmed after `bb56d1d`. |

## Data-Flow Trace (Level 4)

| Artifact | Data Variable | Source | Produces Real Data | Status |
|---|---|---|---|---|
| `ProductOfferQueryService` | eligible `ProductTerms` | normalized product/rule/currency queries through `NormalizedProductTermsAdapter` | Yes when configured rows exist; no hardcoded offer fallback | ✓ FLOWING |
| `AccountBookingService` | locked terms and estimate | terms port + UTC time policy + BigDecimal calculation | Yes; copied into `FdAccount` | ✓ FLOWING |
| `FundingService` | account and transaction state | locked account row + DEPOSIT lookup + lifecycle transition | Yes in implementation; PostgreSQL execution pending Docker | ? HUMAN NEEDED |

## Behavioral Spot-Checks

| Behavior | Command | Result | Status |
|---|---|---|---|
| Focused Phase 2 tests | `mvn -q "-Dtest=FdBookingSchemaTest,NormalizedProductTermsAdapterTest,AccountBookingServiceTest,FundingServiceTest,FdV1BookingContractTest,FdV1ContractBoundaryTest,FdBookingFundingIntegrationTest" test` | 19 non-PostgreSQL tests passed; 3 Testcontainers tests skipped; no failures | ✓ PASS (Docker limitation) |
| Full backend Maven suite | `mvn -q test` | 32 tests passed; 5 PostgreSQL/Testcontainers tests skipped; no failures | ✓ PASS (Docker limitation) |
| OpenAPI/runtime wiring | source inspection plus contract tests | Booking 201 → `AccountResponse`; funding 200 → `FundingResponse`; route and schema assertions pass | ✓ PASS |
| Docker-backed PostgreSQL application flow | `docker info` / Testcontainers | Docker CLI present, daemon unavailable at `dockerDesktopLinuxEngine`; integration class reports 3/3 skipped | ? PENDING HUMAN |

## Requirements Coverage

| Requirement | Source Plan | Status | Evidence |
|---|---|---|---|
| PROD-01 | 02-01, 02-02 | ✓ SATISFIED | Authenticated offer route, terms adapter, offer DTO, and passing focused tests. |
| BOOK-01 | 02-02 | ✓ SATISFIED | Authenticated username resolves the server-owned holder and transactional booking route persists the account. |
| BOOK-02 | 02-01, 02-02 | ✓ SATISFIED | DTO validation, fail-closed eligibility checks, actor resolution, and save ordering. |
| BOOK-03 | 02-02 | ✓ SATISFIED | Account snapshot fields and V7 policy version are populated from server-owned terms/policy. |
| FUND-01 | 02-03 | ✓ SATISFIED with PostgreSQL verification pending | Exact amount/currency, PENDING check via state machine, internal DEPOSIT, ACTIVE response, and passing unit test; Docker-backed application flow not executed. |

No additional requirement mapped to Phase 2 is orphaned. Duplicate-action hardening, broad ownership protection, interest/maturity, closure/history, and regression gates assigned to later phases were not treated as Phase 2 failures.

## Anti-Patterns Found

| File | Pattern | Severity | Impact |
|---|---|---|---|
| `BACKEND/src/test/java/com/bank/fd/FdBookingFundingIntegrationTest.java` | PostgreSQL test uses direct SQL and is skipped without Docker rather than invoking the service | ⚠️ Warning | Application-level PostgreSQL atomicity remains unproven until Docker-enabled execution; this is the human-needed gate. |
| `BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java` | Unrelated later lifecycle operations return explicit 501 placeholders | ℹ️ Info | Intentional phase boundary; not a Phase 2 booking/funding stub. |

No blocker anti-pattern was found in the Phase 2 implementation. Generated `BACKEND/target` changes were not considered application source and were not modified by this verification.

## Human Verification Required

### 1. Docker-backed PostgreSQL application-flow verification

**Test:** Start Docker Desktop and run `mvn -q "-Dtest=FdBookingFundingIntegrationTest" test` from `BACKEND`; verify the real service/HTTP path, not only direct SQL setup, for list offers → book → PENDING/unfunded → exact fund → ACTIVE/one DEPOSIT.
**Expected:** Flyway V1-V7 apply successfully on PostgreSQL; valid booking/funding persists the expected account and transaction atomically, while invalid funding creates no orphan transaction or false activation.
**Why human:** The Docker daemon is unavailable in this environment, and the current integration class was skipped. This is an Escalation Gate for developer/environment action, not a resolved local test result.

## Gaps Summary

The previous OpenAPI gap is closed by `bb56d1d`: the booking 201 response now references `AccountResponse`, and the documented funding route is present and references `FundingResponse`, matching the controller return types. PROD-01, BOOK-01, BOOK-02, BOOK-03, and FUND-01 are supported by current source, focused tests, and a passing full Maven suite. The only outstanding verification is Docker-backed PostgreSQL/Testcontainers application-flow execution; it is explicitly marked human-needed because the Docker daemon is unavailable and the integration tests are skipped.

---

_Verified: 2026-09-19T15:35:00Z_
_Verifier: the agent (gsd-verifier)_
