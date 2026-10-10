# FD Module — Implementation Notes

How the fixed-deposit demonstration was implemented in the existing Spring Boot/Angular
Banking Lab, phase by phase and layer by layer. Covers the backend logic that drives the
FD lifecycle, the protection layer, the REST surface, and the state of the reports module.

Milestone: `authenticate -> products -> book -> fund -> ACTIVE -> accrue -> MATURED -> close -> history`

---

## 1. Scope and Targets

| Requirement | Delivered by | Where |
|---|---|---|
| LIFE-01 lifecycle transitions only | Phase 1 | `fd/domain/FdLifecycleStateMachine.java` |
| DATA-01/02 schema + additive migrations | Phase 1 | Flyway `V6`–`V9` (+ `V10`/`V11` pending) |
| PROD-01 eligible product offers | Phase 2 | `NormalizedProductTermsAdapter`, `ProductOfferQueryService` |
| BOOK-01/02/03 pending booking with locked terms | Phase 2 | `AccountBookingService` |
| FUND-01 exact-principal funding → ACTIVE | Phase 2 | `FundingService` |
| CALC-01/02 reproducible decimal interest | Phase 3 | `FdInterestCalculator`, `AccrualService`, `MaturityService` |
| LIFE-03/04 closure + premature closure | Phase 4 | `ClosureService`, `PrematureClosureService` |
| HIST-01 unified transaction history | Phase 4 | `HistoryService` |
| API-01/02 REST + consistent errors | Phase 4 | `FdV1ContractController`, `GlobalExceptionHandler` |
| FUND-02/HIST-02 duplicate safety | Phase 5 | `FdIdempotencyGuard` |
| AUTH-01/02 ownership + staff roles | Phase 5 | `FdAccessPolicy`, `FdActorResolver` |
| TEST-01/02/03 unit + integration + regression | Phase 5 | 31 test classes, `mvn test` |
| EXT-04 Angular FD UI | Phase 6 | `FRONTEND/src/app/features/fd/**` |

Excluded on purpose (per `AGENTS.md`): payment rails, workflow engines, distributed locks,
outbox/reconciliation, auth rewrite, renewals/payouts/statements/notices.

---

## 2. Money and Time Policy (`academic-v1`)

Centralized in `FdCalculationPolicy` (versioned, immutable) and `FdTimePolicy` (UTC clock):

- Rate unit: decimal fraction — `0.0650` means 6.50 % p.a. No percentage auto-detection.
- Day count: **Actual/365**, simple interest. Only `SIMPLE`/`NONE` compounding is bookable
  (`AccountBookingService.validateCompounding`, `AccountBookingService.java:135`).
- Interval: opening date **included**, maturity date **excluded** (`[start, end)`).
- Intermediate division: scale **12**, `HALF_UP`. Money results: scale **2**, `HALF_UP`
  (accrual rows stored at scale 4).
- Business date/time: UTC (`FdTimePolicy.businessDate()`), server-authoritative — a client
  `business_date` may be validated but never overrides the server date.
- Currency reference resolved from `fd_currency_reference` by ISO code; booking fails if
  the reference row is missing (`AccountBookingService.resolveCurrencyReferenceId`).

---

## 3. Domain Layer

### 3.1 Lifecycle state machine — `FdLifecycleStateMachine.transition()`

```
PENDING            --FUND--------------> ACTIVE
ACTIVE             --MATURE------------> MATURED
ACTIVE             --PREMATURE_CLOSE---> PREMATURELY_CLOSED
MATURED            --CLOSE-------------> CLOSED
CLOSED / PREMATURELY_CLOSED             -> terminal (any command throws)
```

- Single switch expression on `(status, command)`; `null` target →
  `InvalidFdTransitionException` → HTTP 409.
- Pure function: no I/O, so every service can call it **before** writing anything
  (e.g. `ClosureService.java:100-102` performs the transition check first so invalid
  statuses throw with zero writes).
- `FdTransition` carries `from`/`to`; services set `account.setStatus(transition.to())`.

### 3.2 Interest math — `FdInterestCalculator`

- `interestForPeriod(principal, rate, startInclusive, endExclusive)` →
  `principal × rate × days / 365`, divide at scale 12 HALF_UP, result scale 2 HALF_UP.
  Returns `0.00` for `days <= 0`.
- `maturityAmount(...)` = principal + interest, scale 2.
- Rejects null/negative inputs with `IllegalArgumentException`.
- Numerically identical to `AccountBookingService.maturityEstimate` (booking-time
  estimate reuses the same formula), so booking estimate and later calculations agree.

### 3.3 Premature-closure penalty — `PrematurePenalty`

- `parsePenaltyPoints` fails closed: unparseable/missing rule value →
  `PrematureClosureNotSupportedException` → HTTP 422, no writes.
- `penalizedRate = rate − penaltyPoints` (floored at zero), interest recomputed with the
  shared calculator, `foregoneInterest = full − penalized`,
  `prematureSettlement = principal + penalizedInterest` (scale 2).

---

## 4. Persistence (Flyway, PostgreSQL)

| Migration | Purpose |
|---|---|
| `V1`–`V5` | Pre-existing banking schema + id-type alignment (untouched) |
| `V6__fd_lifecycle_baseline` | `fa_status` CHECK (5 statuses), positive principal/tenure CHECKs, `fa_row_version` (JPA `@Version`), **`fd_operation_idempotency`** table, unique `fa_uuid` indexes, `(status, maturity_date)` index |
| `V7__fd_booking_snapshot` | `fa_policy_version` (`academic-v1`), normalized `fd_user_ref`/product/rule-outcome reference tables + unique `fd_user_ref` UUID index (diagnostic local-reference prerequisites) |
| `V8__fd_accrual_maturity` | `fd_interest_accrual` with **unique `(account, accrual_date)`** — the DB-level per-date duplicate guard, `MATURITY`/`INTEREST_ACCRUAL` transaction codes |
| `V9__fd_closure_history` | `fd_premature_closure` (`fpc_` columns + status), `CLOSURE`/`PENALTY` transaction codes |
| `V10`/`V11` (uncommitted) | Align `fd_interest_accrual` / `fd_premature_closure` ID columns to `BIGINT`, backing up and restoring FKs around the `ALTER` |

Entities are plain JPA mappings over the existing `fa_`/`fia_`/`fpc_` columns
(`FdAccount`, `FdTransaction`, `FdInterestAccrual`, `FdPrematureClosure`,
`FdOperationIdempotency`, plus reference entities). `FdAccount.accruedInterest` is
`@Transient` — accrual is always read from `fd_interest_accrual`, never denormalized.

Key repository contract: `FdAccountRepository.findByIdForUpdate` = `SELECT … FOR UPDATE`
for every write path. The read path (`getAccount`) deliberately uses `findById` because
`FOR UPDATE` outside a transaction fails on H2 in tests.

---

## 5. Backend Operation Flows

All seven mutating operations follow the same four-step shape:

```
1. load account (FOR UPDATE)          → 404 if missing
2. FdAccessPolicy.requireOwned(...)   → holder match | BANK_OFFICER/ADMIN bypass | 404
3. FdIdempotencyGuard.claim(scope, "fd-user-<id>", key, sha256(canonical))
      ├─ same key + same hash + completed → replay stored aggregate, zero new rows
      ├─ same key + different hash       → 409 with existing-record reference
      └─ blank key                       → null → natural duplicate detection below
4. validate state/inputs → write rows → guard.complete(claim, aggregateId, reference)
```

Canonical request strings are hand-built (e.g. `productId|principal|tenure|currency`) and
SHA-256 hashed by `FdIdempotencyGuard.sha256Hex`.

### 5.1 Booking — `AccountBookingService.book` (`:68`)

1. Resolve the authenticated holder via `FdActorResolver` (JWT username → local
   `fd_user_ref`; never a caller-supplied id).
2. `termsPort.resolveActiveTerms(query, businessDate)` — server-owned product terms;
   the adapter fails closed for missing/ambiguous authority data, and rejects ineligible
   amount/tenure/currency combinations **before any row is saved**.
3. Guard scope `BOOKING`; replay returns `AccountResponse.pending(...)` of the original.
4. `openDate = businessDate`, `maturityDate = openDate.plusMonths(tenure)`,
   estimate via the shared Actual/365 formula.
5. Persist `FdAccount` with **snapshot fields**: principal (scale 4), rate, compounding,
   payout frequency, currency id, `appliedRateOutcomeId`, dates, estimate,
   `policyVersion = academic-v1`, generated `accountNumber` (branch `000`),
   status `PENDING`.
6. `guard.complete(claim, id, productCode|currencyCode)`.

Term snapshot means later product edits can never change a booked FD.

### 5.2 Funding — `FundingService.fund` (`:72`)

1. `findByIdForUpdate`, ownership check, then validate:
   - currency matches the booked account currency,
   - `amount == principal` **exactly** (`compareTo == 0`) — partial funding is rejected,
   - status must still be `PENDING` (already `ACTIVE` + key-less → 409-style duplicate
     message referencing the account number).
2. State machine `PENDING --FUND--> ACTIVE`.
3. Insert one `fd_transactions` row: type `DEPOSIT`, `amount = principal`,
   `balanceAfter = principal`, remark
   `"Internal academic funding record; no external payment rail was called."`
4. Save status, `guard.complete(claim, accountId, accountNumber)` → `FundingResponse.active`.

### 5.3 Accrual — `AccrualService.runAccrual` (`:63`)

1. Load/own-check; only `ACTIVE` accounts accrue (`InvalidInputException` otherwise).
2. `endDate = min(businessDate, maturityDate)` — never accrues past maturity.
3. Guard scope `ACCRUAL`, canonical `accountId|businessDate`.
4. Interest = `interestForPeriod(principal, rate, bookingDate, endDate)` stored at scale 4.
5. **Natural duplicate guard**: `findByAccountIdAndAccrualDate` — if a row for that date
   exists it is returned as-is (belt-and-braces with the `V8` unique index).
6. Reads: `listAccruals` (ordered rows) and `cumulative` (sum of `accruedAmount`,
   scale 2), both ownership-checked and read-only.

### 5.4 Maturity — `MaturityService.processMaturity` (`:81`)

1. Load/own-check; replay path recomputes interest deterministically from stored fields.
2. Only `ACTIVE` allowed; `businessDate` must be `>= maturityDate`
   (`"FD has not reached its maturity date"` → 400).
3. Final interest through the maturity date; `finalMaturity = principal + interest`.
4. Writes a closing accrual row at `maturityDate` if absent, then one `MATURITY`
   transaction with `amount = balanceAfter = finalMaturity` and an internal-record remark.
5. `ACTIVE --MATURE--> MATURED`, updates `fa_maturity_amount`, completes the guard.

### 5.5 Normal closure — `ClosureService.close` (`:74`)

1. Load/own-check; guard scope `CLOSURE`.
2. Transition check first: only `MATURED` accepts `CLOSE`; a key-less repeat on a
   `CLOSED` account throws 409 referencing the account number.
3. Settlement = stored `fa_maturity_amount` (never recalculated at close); null → 400.
4. One `CLOSURE` transaction row (internal academic settlement remark),
   `MATURED --CLOSE--> CLOSED`, guard completed.

### 5.6 Premature closure — `PrematureClosureService` (two-step)

**Request** (`:106`) — quote + persist snapshot:
1. Load with `FOR UPDATE`, ownership check, guard scope `PREMATURE_REQUEST`.
2. Only `ACTIVE` accounts may request (else 409).
3. `resolvePenaltyOutcome(account)` (`:304`) reads the account's **booked**
   `appliedRateOutcomeId` and requires `outcomeType = CLOSURE_PENALTY`; anything else
   fails closed with `PrematureClosureNotSupportedException` → 422, zero writes.
4. `elapsedEndExclusive = min(businessDate, maturityDate)` floored at booking date.
5. Compute penalized rate, penalized interest, foregone interest, settlement — all
   `BigDecimal` through the shared calculator.
6. Persist `fd_premature_closure` with `status = PENDING`, penalty %, penalty amount,
   net payable, rule-outcome id, truncated reason (max 500 chars) — this row is the
   **authoritative quote**.

**Approve** (`:175`) — apply the stored snapshot verbatim:
1. Load/own-check, guard scope `PREMATURE_APPROVE`; closure must belong to this account
   and be `PENDING`, account must still be `ACTIVE` (else 409).
2. Corrupt-row guards: null principal/rate/quoted amounts → 400 instead of a 500.
3. Write `PENALTY` transaction (foregone) + `CLOSURE` transaction (net payable),
   a closing accrual row (`penalizedInterest = settlement − principal`), mark the closure
   `APPROVED` with approver id, then `ACTIVE --PREMATURE_CLOSE--> PREMATURELY_CLOSED`.
4. **No requote** — request, approve, and history all show the same numbers.

### 5.7 History / timeline — `HistoryService.timeline` (`:66`)

1. Load account + ownership check (read-only transaction).
2. Merge **all** `fd_transactions` rows (labelled `FUNDING`/`MATURITY`/`CLOSURE`/
   `PENALTY` via the transaction-type reference table) with **all** `fd_interest_accrual`
   rows (labelled `ACCRUAL`, running balance = principal + cumulative accrued).
3. Sort by timestamp then id; return DTOs only.
4. Every accrual remark states it is an internal academic record with no external rail —
   the API never claims a payment settlement occurred.

---

## 6. Protection Layer (Phase 5)

### 6.1 `FdIdempotencyGuard` (`FdIdempotencyGuard.java:41`)

- Durable claim/replay over `fd_operation_idempotency` keyed by
  `(operationScope, actorKey, idempotencyKey)`.
- `claim` returns `null` for a blank key (caller falls back to natural duplicate
  detection — e.g. "already ACTIVE/MATURED/CLOSED" 409s), the existing claim for a
  same-hash replay, or a new `PENDING` claim.
- Different body hash for a used key → `FdDuplicateOperationException` → **409** with
  `responseReference`/`aggregate:<id>` so the client can recover the original result.
- `complete` stamps `aggregateId`, `responseReference`, `COMPLETED`, `completedAt`.
- Actor key is always `fd-user-<resolved local id>` — never caller-supplied.

Scopes wired: `BOOKING`, `FUNDING`, `ACCRUAL`, `MATURITY`, `CLOSURE`,
`PREMATURE_REQUEST`, `PREMATURE_APPROVE`.

### 6.2 `FdAccessPolicy.requireOwned` (`FdAccessPolicy.java:25`)

Order: resolve holder from the authenticated username → if holder id equals
`primaryHolderId`, allow → else if authorities contain `ROLE_BANK_OFFICER` or
`ROLE_ADMIN`, allow → else `ResourceNotFoundException("FD account not found")` → **404**,
so existence is never leaked. Applied in all seven services plus controller reads,
history, and both accrual reads.

### 6.3 Controller threading (`FdV1ContractController`)

- `@PreAuthorize("hasAnyRole('CUSTOMER','BANK_OFFICER','ADMIN')")` on the 7 mutating
  endpoints (`:132, :142, :195, :206, :265, :382, :413`).
- Optional `@RequestHeader("Idempotency-Key")` forwarded verbatim to the keyed service
  overload; key-less delegates preserved for legacy callers.
- Cross-customer reads 404 through `FdAccessPolicy`; anonymous FD reads remain 403.

### 6.4 Error contract (`GlobalExceptionHandler`)

| Exception | HTTP |
|---|---|
| `InvalidInputException`, bean-validation | 400 |
| `AccessDeniedException` | 403 |
| `ResourceNotFoundException` (incl. ownership 404) | 404 |
| `InvalidFdTransitionException`, `FdDuplicateOperationException` | 409 |
| `PrematureClosureNotSupportedException` | 422 |
| `AuthenticationException` | 401 (login path) |
| unhandled | 500 envelope (no stack traces) |

CORS (`SecurityConfig`) allows the `Idempotency-Key` header; no other matcher was changed.

---

## 7. REST Surface (`/fd/v1`)

Implemented (11) — the demo path:

| Method/Path | Service |
|---|---|
| `GET /fd/v1/products` | `ProductOfferQueryService` (role: `CUSTOMER` only) |
| `POST /fd/v1/accounts` | `AccountBookingService.book` |
| `POST /fd/v1/accounts/{id}/fund` | `FundingService.fund` |
| `GET /fd/v1/accounts/{id}` | read (`findById`) |
| `POST /fd/v1/accounts/{id}/accrual/run` | `AccrualService.runAccrual` |
| `GET /fd/v1/accounts/{id}/accrual` and `/cumulative` | `AccrualService` reads |
| `POST /fd/v1/accounts/{id}/mature` | `MaturityService.processMaturity` |
| `POST /fd/v1/accounts/{id}/close` | `ClosureService.close` |
| `POST|GET /fd/v1/accounts/{id}/premature-closure[/{closureId}/approve]` | `PrematureClosureService` |
| `GET /fd/v1/accounts/{id}/transactions` | `HistoryService.timeline` |

The other **37 mappings still return `501 NOT_IMPLEMENTED` by design** (holders, payouts,
payout accounts, maturity instructions, notices, statements, events, internal sync,
`/ref/*`) — pre-existing contract placeholders, intentionally not exposed by the frontend.
Legacy `FdAccountController`, `BatchTriggerController`, product/customer/auth/calculator/
reporting controllers were left intact.

Artifacts: `BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml` (schemas
`ClosureResponse`, `PrematureClosureResponse`, `HistoryEntry`, stable operationIds) and
`BACKEND/postman/fd-lifecycle.postman_collection.json` (18 requests / 4 folders) guarded
by `PostmanCollectionConsistencyTest`, which reflects over the collection and fails if a
request drifts from an implemented route.

---

## 8. Reports Module

Existing `com.bank.reports` was preserved (compatibility asset), not rewritten:

- `GET /api/reports/operational` (`ReportService.getBankOperationalReport`) works off
  `fdAccountRepository.findByStatus(ACTIVE)`: active count, total principal, average rate,
  and a 30-day "maturing soon" list — so it reflects FD data created by the new lifecycle.
  **Known limitation:** `FdAccount.accruedInterest` is `@Transient` and defaults to
  `ZERO`, so `totalAccruedInterestLiability` currently always reports `0.00`.
- `GET /api/reports/portfolio[/{username}]` still throws
  `UnsupportedOperationException` (pre-existing: "Portfolio reports require an FD user
  reference mapping…"). Wiring it to `FdUserReference` + `fd_interest_accrual` is a
  natural follow-up now that accrual rows exist.
- Customer-facing FD history is served by the FD timeline endpoint instead
  (`GET /fd/v1/accounts/{id}/transactions`).

---

## 9. Frontend (Phase 6, brief)

- Typed transport `FRONTEND/src/app/core/services/fd.service.ts` covers only the 11
  implemented endpoints; every write carries `Idempotency-Key` (keys retained across
  retries by the service), bearer token added by `authInterceptor` — credentials never in
  URLs or logs.
- Screens: `fd-home` (eligible products + lookup), `fd-booking` (3-step wizard, server
  terms authoritative — browser only does UX bounds checks), `fd-account-lookup`,
  `fd-account-detail` (status-aware actions, server-rendered money, unified history),
  `fd-not-found`.
- Actions are derived from the five backend statuses — no unconditional mutating button;
  final actions use a local confirm dialog while reusing the same idempotency key.
- Guarded lazy `/fd` route tree (`app.routes.ts`) with the existing auth guard; lookup
  route registered before the dynamic `:accountId` route; dashboard is a minimal
  authenticated entry with real `/fd` navigation and logout.
- Visual system unchanged: `GlassShell`, glass cards, shared tokens.

---

## 10. Verification Evidence

- `mvn compile` → success; `npm run build` → success (lazy chunks emitted).
- Full backend suite: **128 tests, 0 code failures** — the only 2 errors are the
  documented environment prerequisite (process-local `JWT_SECRET` ≥ 64 bytes for HS512,
  never stored in the repo); rerunning `SecurityFlowTest` + `FdOwnershipContractTest`
  with `JWT_SECRET` set → 6/6 green. Docker-gated `FdPhase5ProtectionIT` skips clean
  without Docker (exit 0).
- Highlights: `FdOwnershipContractTest` 5/5 (4 cross-customer 404 probes, staff read,
  409/422/400 contract, byte-identical fund replay with exactly one DEPOSIT row),
  `FdIdempotencyGuardTest` 6/6, `FdAccessPolicyTest` 6/6, `GlobalExceptionHandlerTest`
  5/5, `FdPhase5BoundaryTest` 8/8 (one-day interest `17.81`, 100-point penalty floor,
  3 invalid transitions), `PrematureClosureServiceTest` 12/12, contract suites for
  booking/accrual/closure, schema tests pinning migration content.
- Human-only follow-ups recorded in `.planning/phases/*/…-VERIFICATION.md`: live Postman
  run against Supabase, live-PG `FdPhase5ProtectionIT`, live frontend walkthrough.

## 11. Known Gaps / Advisories

| Item | Severity | Note |
|---|---|---|
| Closure replay stamps today's date instead of the stored close date | MED | Same-day replay byte-identical; settlement amount unaffected (Phase 5 MED-01) |
| Keyed invalid attempts persist orphan `PENDING` idempotency claims | MED | Audit noise only, no duplicate financial effect (MED-02) |
| Premature `business_date` validated but not forwarded | MED | Intentional server-date clamping policy (Phase 4 MED-01) |
| Reports portfolio endpoint unsupported; operational accrued interest always 0 | Gap | Pre-existing, see §8 |
| Angular `test` architect target missing | Tooling | Route specs written but not runnable without adding a runner |
| `V10`/`V11` migrations + minor frontend fixes uncommitted | Working tree | See `git status` |

## 12. Key Files

```
BACKEND/src/main/java/com/bank/fd/
├── api/controller/FdV1ContractController.java   # REST surface, roles, Idempotency-Key
├── api/dto/                                     # Account/Funding/Accrual/Closure/History DTOs
├── application/
│   ├── AccountBookingService.java               # pending booking with term snapshot
│   ├── FundingService.java                      # exact-principal funding
│   ├── AccrualService.java                      # per-date accrual + reads
│   ├── MaturityService.java                     # ACTIVE -> MATURED
│   ├── ClosureService.java                      # MATURED -> CLOSED
│   ├── PrematureClosureService.java             # two-step quote/approve
│   ├── HistoryService.java                      # merged timeline
│   ├── FdIdempotencyGuard.java                  # claim/replay/409
│   ├── FdAccessPolicy.java                      # 404-hiding ownership + staff bypass
│   ├── FdActorResolver.java                     # JWT username -> local fd_user_ref
│   └── NormalizedProductTermsAdapter.java       # fail-closed product authority
├── domain/
│   ├── FdLifecycleStateMachine.java             # transition table
│   ├── FdInterestCalculator.java                # Actual/365 BigDecimal math
│   ├── FdCalculationPolicy.java                 # academic-v1 contract
│   └── PrematurePenalty.java                    # penalty math
├── entity/  repository/  exception/
BACKEND/src/main/resources/db/migration/V6..V11
BACKEND/postman/fd-lifecycle.postman_collection.json
FRONTEND/src/app/features/fd/**                  # booking, lookup, lifecycle hub
```
