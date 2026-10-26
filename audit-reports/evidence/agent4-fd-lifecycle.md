# Agent 4 - Fixed Deposit lifecycle audit (read-only)

Repo: `.../scratchpad/repo` (abbrev. `R/`). Active code: `R/services/fd-account-service/src/main/java/com/bank/fdservice` (abbrev. `FD/`), `R/services/product-service/.../productservice` (`PS/`). Legacy: `R/BACKEND/src/main/java/com/bank/fd`.
Nothing was run (JDK 11 only, no MySQL). Anything about runtime behaviour is **UNVERIFIED** and has verification steps. Python hand-computation was used to check formulas (scratch dir only).

## 0. Headline

The premature-withdrawal flow **exists in the active services but cannot be completed by a customer through the UI**. It is a two-person flow (customer requests, a different staff user approves). The only UI that calls the approve endpoint is the customer's FD detail page, and that endpoint is staff-only. Details are in section 2 and FD-01.

Other big items:
- No scheduler exists in the active FD service. Accrual and maturity run only from the ADMIN demo-clock "advance" action or a manual officer POST.
- Maturity does not pay out. It sets MATURED and the customer must click "Close".
- No statements or notifications exist anywhere (tables only).
- `.planning` "VERIFIED / no gaps" claims describe the legacy BACKEND monolith, not the services that are deployed.

## 1. Lifecycle step table

Gateway = `R/services/api-gateway/nginx.conf`. UI = `R/FRONTEND/src/app/core/services/fd.service.ts` unless noted.

| Step | Endpoint | Gateway route? | UI caller? | Code path:line | Status | Issue |
|---|---|---|---|---|---|---|
| Eligible products | `GET /api/v1/fd-products/eligible` | Yes (nginx:52-53 to product-service) | fd.service.ts:95 | `PS/EligibleFdOfferController.java:21-77` | Works (UNVERIFIED at runtime) | Filters only by active product, effective dates and category. No per-customer eligibility (KYC, age, senior). It is `permitAll` for GET (`PS/SecurityConfiguration`), so it is public. Rates are DECIMAL_FRACTION (0.07). |
| Offer admin | `/api/v1/fd-products*` | Yes | fd-product-admin.service.ts:42-58 | `PS/FdOfferController.java:22` (`@PreAuthorize` officer/admin) | Works | `create` hard-codes `QUARTERLY` / `AT_MATURITY` for every band (line ~56), so compounding is not configurable. See FD-12. |
| Rate calculator | `POST /api/calculator/estimate` | Yes (nginx:87) | **None** (grep of FRONTEND/src finds no caller) | `FD/FdCalculatorController.java:23-52` | Orphaned | Takes the rate as a **percent** (`/100`, line 32). Product rates are **fractions**. Uses `Math.pow` double. Does not match the booking maturity estimate. See FD-11. |
| Book (create) | `POST /api/v1/fixed-deposits` + `Idempotency-Key` | Yes (nginx:74-75) | fd.service.ts:107 | `FD/FixedDepositController.java:40-62` | Works, differs from manual | Customer self-books (no BANK_OFFICER create). Account number `FD-`+16 hex. Status PENDING. No `fd_transactions` row at booking. KYC and risk gate OK. Product, tenure and amount validated against the Kafka-synced snapshot (line 51). |
| Fund | `POST /api/v1/fixed-deposits/{id}/fund` | Yes | fd.service.ts:117 | `FixedDepositController.java:65-88` | Works | Ownership, ACTIVE savings, same currency, balance, FOR UPDATE, journal, txn, savings txn and idempotency are all present. Atomic via `@Transactional`. Concurrent duplicate key may 500 (FD-14). |
| ACTIVE | `fa_status` set in `fund` | n/a | n/a | `FixedDepositController.java:84` | OK | Guarded by `AND fa_status='PENDING'`. |
| Accrual | `POST /api/v1/admin/fixed-deposits/accrual-batch?businessDate=` | Yes (nginx:76) | **None** | `FD/FdLifecycleController.java:30-31` to `FdBatchService.accrue` / `accrueOne:28-97` | Works manually | Officer-supplied date is not capped at the demo clock (FD-05). No `@Scheduled`. Idempotent by last accrual date. |
| Daily batch | `POST /api/admin/clock/advance` | Yes (nginx:79-80) | clock-dashboard (fd-clock-admin.service.ts:36) | `FD/FdClockController.java:30-34` to `FdBatchService.runDaily:19-25` | Works but not atomic | Self-invocation, no transaction (FD-04). `accountsFailed` is always 0. |
| Maturity | `POST /api/v1/admin/fixed-deposits/maturity-batch` | Yes (nginx:77) | **None** | `FdLifecycleController.java:33-34` to `FdBatchService.mature:37-63` | Works, but no payout | ACTIVE to MATURED, interest journal INTEREST_EXPENSE/FIXED_DEPOSIT, `FD_MATURITY` txn, event. No savings credit. No notification. |
| Close at maturity | `POST /api/v1/fixed-deposits/{id}/close` | Yes | fd.service.ts:136 | `FdLifecycleController.java:36-44`, `settle:118-134` | Works | Owner-only. MATURED to CLOSED. Credits savings. Verifies funding and interest journals. |
| Premature request | `POST /api/v1/fixed-deposits/{id}/premature-closure` | Yes | fd.service.ts:150 | `FdLifecycleController.java:46-61` | Works if the product has penalty points | See section 2. |
| Premature approve | `POST /api/v1/admin/fixed-deposits/{id}/premature-closure/{cid}/approve` | Yes (nginx:78) | fd.service.ts:158, button in `fd-account-detail.html:92` (customer page) | `FdLifecycleController.java:63-73` | **BROKEN end to end via UI** | Staff-only (`@PreAuthorize` line 63), approver must differ from owner (line 69). The customer UI calls it with the customer token and gets 403. There is no staff UI for it. |
| Closure list | `GET /api/v1/fixed-deposits/{id}/premature-closure` | Yes | fd.service.ts:154 | `FdLifecycleController.java:111-116` | Owner-only | Staff cannot read a customer's requests. |
| History | `GET /api/v1/fixed-deposits/{id}/transactions`, `/accruals`, `/accruals/cumulative` | Yes | fd.service.ts:128, 132, 170 | `FdLifecycleController.java:75, 97, 104` | Works | Owner-only. |
| Journals | `GET /api/v1/fixed-deposits/{id}/journals` | Yes | fd.service.ts:61 | `FdLifecycleController.java:78` (ADMIN) | Works | |
| Staff lists | `GET /fd/v1/staff/accounts` | Yes (nginx:81-82) | staff-operations.service.ts:16 | `FD/StaffAccountController.java:27-48` | Read-only | No staff action on FDs (no approve, close or reject). |
| Reports | `GET /api/reports/portfolio`, `/portfolio/{u}`, `/operational` | Yes (nginx:84-86) | **None** | `FD/FdReportController.java:27-45` | API only | Manual names `/report/fd-summary` and `/report/customer-portfolio` are absent. No CSV. No per-product grouping. |
| Statements | none | n/a | none | `fd_statements` table (`R/03_moneypal_fd.sql:158`) | **Not implemented** | No writer in services or legacy main code. |
| Notifications | none | n/a | none | tables `fd_notices`, `fd_maturity_instructions` | **Not implemented** | Only a Kafka outbox `FdEventPublisher.java` (topic `fd.lifecycle`). No consumer found in the repo. |

## 2. Premature withdrawal - the 12 points (user's #1 broken flow)

1. **Endpoint exists and is reachable?** Yes, as two endpoints (request and approve), not the manual's single `POST /api/fd/account/withdraw`. Both are in `fd-account-service` (`FdLifecycleController.java:46, 63`). Both are routed by nginx (`^~ /api/v1/fixed-deposits/` at 75, `^~ /api/v1/admin/fixed-deposits/` at 78). The Angular code calls both (fd.service.ts:150, 158). **The approve call is made by the customer page with the customer's JWT and the endpoint requires BANK_OFFICER or ADMIN, so the UI path always ends in 403.** The Docker-gated test `FdAccountMigrationTest.java:196-209` passes only because it approves with an admin token. UNVERIFIED at runtime; to verify, log in as a customer, request closure, press "Accept closure amount", and expect 403 in the network tab.
2. **Correct service?** Yes. fd-account-service owns FD data. The penalty config lives in product-service and arrives through the Kafka snapshot (`ReferenceSnapshotConsumer.java`, column `fd_product_currency_ref.premature_penalty_points`).
3. **Ownership and eligibility?** The request checks owner (`:49`, 404 otherwise), status ACTIVE (`:49`), and no existing PENDING request (`:50`). Approve checks ACTIVE, PENDING closure, and approver != owner (`:66-69`). Deviations from the manual: an officer cannot initiate for a customer (the manual allows "customer or authorized bank officer"), and MATURED FDs are refused. The UI still offers REQUEST_PREMATURE for MATURED (`fd-account-detail.ts:38`), which yields a 409. There is no "withdrawal date >= creation and <= maturity" input. The server uses the business date clamped to maturity (`:55`).
4. **Accrued interest to the correct date?** Partly. The date is `clock.businessDate()` clamped to [open, maturity] (`:55`). The interest used is **simple interest on principal at the contract rate** (`:56-57`), not the compounded accrual stored in `fd_interest_accrual`. Hand check (P=100000, 7% p.a., quarterly, day 181): quote basis `fullInterest` = 3471.23, while the accrual table shows 3501.36, a 30.13 gap. The customer sees "accrued" 3501.36 but the quote is based on 3471.23.
5. **Product-specific rules?** Penalty points are read live from `fd_product_currency_ref` (`:51`), not from the account's booked terms snapshot. They are missing from `fa_terms_snapshot` (the JSON built at `FixedDepositController.java:56`). A product edit after booking therefore changes the penalty of existing FDs (FD-03). If the points are NULL the request fails with 409 "not configured" (`:52`). No percentage is invented. Penalty points come only from the product's `CLOSURE_PENALTY` rule outcome (`PS/FdOfferController.java:48-52`, validated 0-100 at line 114). Seed and demo products without a CLOSURE_PENALTY rule cannot be prematurely closed (the likely first symptom the user saw; verify in section 6).
6. **Penalty calculation?** Penalised rate = max(0, rate - points/100) (`:54`). Interest paid = P x penalised_rate x days/365. Penalty amount = full simple interest minus paid interest (foregone interest). Hand check: points=1, day 181 gives paid 2975.34, foregone 495.89. The manual's L15 rule is "reduce accrued interest by 1% or as configured" (`interest x pct / 100`). On the same inputs that would be 34.71. The code's 1 percentage point of rate removes about 14% of interest. This is a design deviation. It matches the legacy `PrematurePenalty` and is documented in the README, but it differs from the manual's formula (FD-08).
7. **Payout amount?** net = principal + paid interest, rounded to the currency scale, stored at request time in `fpc_net_payable_amount` (`:58-59`). **Approve pays the stored quote without recomputing** (`:71`). If approval happens days later, the amount is stale (FD-06).
8. **Close only on success?** Yes. `settle` runs the savings credit, transactions, journal and status update inside the `@Transactional` approve method (`:63-72`, `:118-134`). Status becomes PREMATURELY_CLOSED only after the credit.
9. **Payout destination?** The funding savings account (`fa_funding_savings_account_id`), validated for holder = owner, same currency and ACTIVE (`:120-121`). The manual's `transferAccount` choice is not supported. It is the original funding account only, which is acceptable and safer.
10. **Transaction and audit records?** Yes. A `savings_transactions` CREDIT row (`:124`), `FD_PENALTY` txn (amount = foregone interest, `:127`), `FD_CLOSURE` txn (`:129`), a `SETTLEMENT` journal with two lines (`:131`), a `PREMATURELY_CLOSED` event (`:133`), `fpc_approved_by`, and the idempotency row. Gaps: the settlement debits FIXED_DEPOSIT by principal+interest, but no INTEREST_EXPENSE/FIXED_DEPOSIT journal is posted for the paid premature interest (the maturity path posts one at `FdBatchService.java:52-58`). The FIXED_DEPOSIT ledger account therefore goes negative by the interest (FD-07). The `fd_interest_accrual` table also gets no closing row, unlike the legacy service.
11. **Rollback?** Yes for approve and close (single `@Transactional`; any exception rolls back savings, txns and status). The idempotency claim is rolled back with it. The premature **request** is also transactional.
12. **Duplicate-payout protection?** Good. (a) The idempotency key replays through `claim` (`:140`). (b) A different key on an already closed FD gives 409 because the status is not ACTIVE (`:66`). (c) The closure is locked FOR UPDATE and must be PENDING (`:67-68`). (d) The settlement journal key `SETTLEMENT:{id}` is checked (`:130-131`). One residual risk is a concurrent duplicate key giving a 500 (FD-14, UNVERIFIED).

**Why the user sees it broken (ranked, all UNVERIFIED at runtime):**
- (a) The customer UI hits the staff-only approve endpoint, giving 403 (FD-01).
- (b) The product has no penalty points, giving 409 "Premature closure is not configured for this product" (FD-02).
- (c) The FD is MATURED but the UI offers premature closure, giving 409 (FD-09).
- (d) A PENDING request blocks a new request and can never be rejected or cancelled (FD-10).

## 3. Maturity analysis

- **Selection:** `status='ACTIVE' AND DATE(fa_maturity_date) <= ?` (`FdBatchService.java:39`), then re-checked under row lock (`:42-45`). Double processing is prevented because MATURED rows are not selected, the update is guarded by `AND fa_status='ACTIVE'` (`:51`), and the interest journal has key `INTEREST:{id}:MATURITY` (`:53-54`). Accrual runs to the maturity date even if the batch date is later (`accrueOne(id, maturity)`, `:46`). The final period is force-capitalised (`d.equals(maturity)`, `:89`).
- **Formula check:** the Python replica gives 107,185.90 for 100,000 at 7% quarterly for 12 months from 2026-01-01. It uses ACTUAL/365 on the compounding balance, 12-dp division, 8-dp daily amounts, and capitalisation rounded to currency scale. This matches `FdCompounding.maturity`. Simple interest is not supported at booking (the compounding basis defaults to QUARTERLY for unknown values, `FdCompounding.months`, `:13`), and the calculator offers SIMPLE. The 3-case unit test exists (`FdCompoundingTest`).
- **Payout:** none at maturity. The savings account is credited only when the customer calls `/close` (`:36-44`). Manual L18/L19 expect the batch (or an automatic step) to pay out to savings. The interest expense is booked at maturity while cash moves later, so MATURED rows hold unpaid funds indefinitely (FD-13).
- **Status consistency:** ACTIVE to MATURED (batch) to CLOSED (customer). The UI's `close` hard-codes `status:'CLOSED'` (fd.service.ts:136-140). The manual's ACTIVE/CLOSED is a simplification, so MATURED is fine. There is no manual "mature" or "renew" endpoint in the services. Legacy `/fd/v1/accounts/{id}/mature`, `/renew` and `/activate` are absent (the `fd_maturity_instructions` table is unused).
- **Manual close:** owner-only. Staff cannot close or settle on a customer's behalf (the manual's L19 "Optional for Manual Closure" API is customer-only here).
- **Verify guard:** `verifyMaturityJournals` (`:85-91`) blocks close if the funding or interest journals are missing. If the maturity batch failed halfway (FD-04), the FD sits MATURED forever and cannot be closed.

## 4. Statements, notifications, reports, roles, manual deviations

| Item | Result |
|---|---|
| fd_statements | Table only. No generation code in services or legacy `main`. Manual L17 requires monthly statements. Gap (FD-15). |
| Notifications / alerts (L18) | None (no email, WhatsApp or in-app). Only a Kafka outbox event with no consumer (`FdEventPublisher`, enabled when `KAFKA_ENABLED`). Gap (FD-15). |
| Scheduler | Active services: none. Legacy `FdBatchScheduler` (cron 00:00 and 01:00) was not carried over. |
| Reports (L21) | `/api/reports/portfolio` (CUSTOMER, BANK_OFFICER, ADMIN own data), `/portfolio/{username}` (staff), `/operational` (staff, totals per currency and maturing in 30 days). Paths differ from `/report/fd-summary` and `/report/customer-portfolio`. No CSV/PDF. No UI caller. Customer isolation is correct (own username only). Staff calling `/portfolio` get their own, probably empty or 404. |
| Roles | Book and fund: any authenticated user with a `userId` claim acts only on their own FDs. There is no BANK_OFFICER/ADMIN create-on-behalf (manual L13 says only officer/admin may create). Defensible for a self-service model, but it is a deviation. |
| Manual deviations (acceptable) | DOUBLE vs DECIMAL(24,8) (fine). The account number pattern is `FD-<16 hex>` rather than branch+6 digits+checksum. Create plus initial DEPOSIT transaction in one DB transaction (L16) is replaced by booking PENDING and an `FD_FUNDING` transaction at funding, so it is atomic at funding, not at creation. |

## 5. Findings

Format: ID | severity | category | evidence | expected vs actual | root cause (confidence) | minimal fix | tests | effort

**FD-01 | P0 | Functional / RBAC / UI**
- Evidence: `FRONTEND/src/app/features/fd/account-detail/fd-account-detail.html:92`, `fd-account-detail.ts:150-160, 280`, `fd.service.ts:158`; `FD/FdLifecycleController.java:63, 69`.
- Expected: a customer's "Accept closure amount" completes premature closure (manual L15 is a one-step customer/officer withdrawal). Actual: the customer UI calls the admin approve endpoint; 403 for customers, and 403 for an owner who is also staff (approver != owner). No staff screen can approve (`staff-account-dashboard` only lists).
- Root cause: two-person workflow implemented in the API, UI wired to one role (high, by code reading; runtime UNVERIFIED).
- Minimal fix: choose one of (a) add a staff "Premature closures" screen (list pending, approve, reject) plus a staff read endpoint; or (b) make the customer "accept" call a customer-owned endpoint (`POST /api/v1/fixed-deposits/{id}/premature-closure/{cid}/confirm`) that runs `settle` directly and remove the staff gate. For the manual's single-step semantics, option (b) is closest.
- Tests: MockMvc with a CUSTOMER token should get 200 and status PREMATURELY_CLOSED. A STAFF token approving its own FD should get 403. A UI spec should cover the call.
- Effort: M (b: 0.5-1 day; a: 1-2 days).

**FD-02 | P1 | Config / data**
- Evidence: `FdLifecycleController.java:51-52`; `PS/FdOfferController.java:48-52`; no `CLOSURE_PENALTY` in `R/02_moneypal_product.sql` or seeds (searched).
- Expected: premature closure works for demo products. Actual: 409 "not configured" for any product created without `prematurePenaltyPoints`.
- Root cause: penalty is optional and NULL means unsupported (high). Whether a given deployed product has the rule: UNVERIFIED (check DB).
- Fix: require the field on offer create, or default to an explicit 0 only when the bank decides so (do not invent a number). Show "premature closure not available" in the UI before the user asks.
- Tests: create offer without points, request should return a clear error. With points, request should succeed.
- Effort: S.

**FD-03 | P2 | Data integrity / business rule**
- Evidence: `FdLifecycleController.java:51` (live `fd_product_currency_ref`); `FixedDepositController.java:56` (snapshot lacks penalty).
- Expected: penalty fixed at booking (legacy review HI-01 and `.planning` Phase 4 intent). Actual: the current product setting applies to old FDs, and `DELETE`+`INSERT` runs on every product snapshot (`ReferenceSnapshotConsumer`).
- Root cause: penalty was left out of terms snapshot (high).
- Fix: add `prematurePenaltyPoints` to the booking terms JSON, and read from it with fallback to the live product only for old rows.
- Tests: change product points after booking; the quote must use the booked value.
- Effort: S.

**FD-04 | P1 | Transactionality / reliability**
- Evidence: `FdBatchService.java:19-25` (`runDaily` not transactional; calls `this.accrue/mature`, so the `@Transactional` on lines 27 and 36 is bypassed); called from `FdClockController.java:32-33`. `accountsFailed` is hard-coded 0, so there is no per-account error handling.
- Expected: per-account atomic processing with failure reporting. Actual: in the demo-clock path every statement autocommits, `FOR UPDATE` locks release immediately, and an exception midway (e.g. after the MATURED update at `:51` but before the journal at `:55`) leaves a MATURED FD without interest journal or `FD_MATURITY` txn; `/close` then fails permanently on `verifyMaturityJournals`. One bad account aborts the whole run.
- Root cause: Spring proxy self-invocation (high; runtime UNVERIFIED).
- Fix: move per-account work to a separate bean method with `REQUIRES_NEW`, catch per account, count failures. Reorder so the status update comes last.
- Tests: inject a failure in the journal insert and assert the FD stays ACTIVE and the other accounts are processed.
- Effort: M.

**FD-05 | P2 | Authorization / integrity**
- Evidence: `FdLifecycleController.java:30-34` (BANK_OFFICER allowed), arbitrary `businessDate` passed to `accrue/mature` (`FdBatchService.java:28-39`); clock endpoints are ADMIN-only (`FdClockController.java:17`).
- Expected: only the effective business date (or earlier) is accepted; the clock control is the only way to simulate time. Actual: an officer can send a future date, creating future accrual rows and maturing deposits early.
- Fix: reject `businessDate` after `clock.businessDate()` unless ADMIN.
- Tests: officer with a future date gets 400.
- Effort: S.

**FD-06 | P2 | Financial correctness**
- Evidence: `FdLifecycleController.java:56-59` (quote at request time), `:71` (approve pays stored `net`).
- Expected: payout computed as of the approval/withdrawal date. Actual: stale quote paid. Interest accruing between request and approval is lost, or if approved after maturity the quote is capped at the request date.
- Fix: recompute at approve (or expire quotes after a fixed window and require re-request).
- Tests: request on day N, advance the clock, approve; the net must reflect the approval date.
- Effort: S-M.

**FD-07 | P1 | Accounting**
- Evidence: premature path `settle` (`:131`) debits FIXED_DEPOSIT by `net` with no INTEREST_EXPENSE posting; maturity path posts it (`FdBatchService.java:52-58`).
- Expected: balanced FD ledger (credit P at funding, credit interest, debit all at payout). Actual: FIXED_DEPOSIT balance is negative by the paid interest after a premature closure. No closing accrual row either, so `fd_interest_accrual` totals do not match what was paid.
- Fix: in approve, post `INTEREST_EXPENSE` / `FIXED_DEPOSIT` for `net - principal` (key `INTEREST:{id}:PREMATURE`) before the settlement.
- Tests: after premature closure the FIXED_DEPOSIT lines for that FD sum to zero.
- Effort: S.

**FD-08 | P2 | Spec deviation**
- Evidence: `FdLifecycleController.java:54-58`; manual L15 section 4/6.3.
- Expected: penalty = accrued interest x configured % (e.g. 1% of interest). Actual: percentage points deducted from the annual rate, on simple interest. For 1 point at 7% about 14% of interest is removed. The quote also ignores compounding capitalised to date (hand check: 3471.23 vs 3501.36 accrued).
- Root cause: legacy design carried forward (high).
- Fix: product decision. Document clearly, or compute from accrued interest (`fd_interest_accrual`).
- Tests: golden numbers from `calc/c.py` figures above.
- Effort: S (docs) to M (formula change).

**FD-09 | P2 | UI**
- Evidence: `fd-account-detail.ts:38` (`MATURED: ['CLOSE','REQUEST_PREMATURE']`) vs backend ACTIVE-only (`FdLifecycleController.java:49`).
- Expected: premature closure offered only for ACTIVE FDs. Actual: a matured FD shows a button that always fails with 409.
- Fix: remove `REQUEST_PREMATURE` from MATURED.
- Tests: unit test on `actionsForStatus`.
- Effort: XS.

**FD-10 | P2 | Workflow**
- Evidence: `R/03_moneypal_fd.sql:107` (`fpc_status` limited to PENDING and APPROVED); `FdLifecycleController.java:50`.
- Expected: reject and cancel paths. Actual: none; a stuck PENDING blocks new requests forever, and it stays PENDING if the FD matures first.
- Fix: add REJECTED/CANCELLED statuses (migration) with an endpoint and auto-cancel on maturity.
- Tests: reject, then a new request is allowed.
- Effort: M.

**FD-11 | P3 | Units / orphan API**
- Evidence: `FdCalculatorController.java:30-37` (rate percent), vs product rates as fractions (`PS/FdOfferController.java:59`, "DECIMAL_FRACTION"); no FRONTEND caller.
- Expected: one unit convention. Actual: any future UI feeding `annual_rate` from eligible offers to the calculator would give 100x wrong results (e.g. 0.07% instead of 7%). The calculator also uses `double` pow and supports SIMPLE, which booking does not.
- Fix: accept fractions or convert explicitly, wire to the UI, and reuse `FdCompounding.maturity`.
- Effort: S.

**FD-12 | P3 | Product config**
- Evidence: `PS/FdOfferController.java` (~line 56): compounding is hard-coded `QUARTERLY`, payout `AT_MATURITY`; booking uses the snapshot rate without effective dates (`FixedDepositController.java:51`), though compliance uses them (`PS/FdComplianceService.java:15-30`).
- Expected: configured compounding, and rates consistent with the compliance check. Actual: compounding cannot vary through the offer API, and a rate whose window has expired in product-service can still be booked until the snapshot refreshes. The product effective window is not checked at booking either (the eligible endpoint does check).
- Fix: carry effective dates in the snapshot and filter; allow compounding on the band request.
- Effort: M.

**FD-13 | P2 | Spec gap**
- Evidence: `FdBatchService.mature:51-60` (no savings credit); manual L18/L19 (payout to savings, batch or user initiated).
- Expected: the matured amount reaches the savings account (automatically or via a clearly surfaced step). Actual: funds wait until the customer clicks Close. No reminder or notification. Interest expense is booked at maturity but cash moves later.
- Fix: auto-settle in the maturity batch (call `settle`), or keep the manual step and add notification (FD-15).
- Effort: M.

**FD-14 | P3 | Concurrency (UNVERIFIED)**
- Evidence: `FixedDepositController.java:42, 90` (`requireVerified` reads before `claim`), `claim` catch reads the existing row under REPEATABLE READ.
- Expected: a concurrent duplicate request replays or returns 409. Actual: possible 500 (`EmptyResultDataAccessException`) because the committed row is invisible to the earlier snapshot. Sequential retries are fine.
- Verify: fire two identical POSTs concurrently with the same key.
- Fix: do `claim` first, or read with `FOR UPDATE`/`LOCK IN SHARE MODE`.
- Effort: S.

**FD-15 | P1 | Spec gap (L17/L18)**
- Evidence: no `@Scheduled` in `FD/` (only `FdEventPublisher` outbox); no statement or notification writer for `fd_statements`, `fd_notices`.
- Expected: scheduled accrual and maturity with logging, monthly statements, and maturity notifications. Actual: manual or demo-clock only, no statements, no notifications. The legacy `FdBatchScheduler` exists in BACKEND only.
- Fix: add a scheduled job calling `runDaily(clock.businessDate())`, a statement generator, and a notice insert. At minimum document it as a demo-clock-driven design.
- Effort: L.

**FD-16 | P3 | Reporting / spec**
- Evidence: `FdReportController.java`; manual L21 paths `/report/fd-summary`, `/report/customer-portfolio`.
- Actual: different paths, no CSV, no per-product grouping, no UI. Needs an alias or documentation.
- Effort: S-M.

**FD-17 | P2 | Documentation / evidence integrity**
- Evidence: `.planning/STATE.md` (100% complete), `phases/0[3-5]-VERIFICATION.md` ("No gaps", status `human_needed`). These verify `BACKEND/src/**` (`PrematureClosureService`, `FdV1ContractController`). The services' tests are `FdAccountMigrationTest` (Docker-gated, disabled without Docker), `FdCompoundingTest`, and `CustomerProductAccessTest`.
- Expected: claims map to the deployed code. Actual: the active service has one gated lifecycle test, and the premature flow is tested only with an admin token (which hides FD-01). The verification reports say `human_needed` and none was run for the services.
- Fix: re-baseline verification on `services/*`; add UI-role end-to-end tests.
- Effort: M.

**FD-18 | P3 | Role/UX**
- Evidence: `FdLifecycleController.java:37-44, 49`: close and request are owner-only; `getMine`/`find` filter by holder. Staff cannot view a customer's FD detail or premature requests, close, or assist.
- Manual: L15 allows an authorised bank officer to initiate; L19 allows user or batch to close. Fix with the staff screen in FD-01.
- Effort: M.

## 6. Verification steps (UNVERIFIED items)

1. Premature end to end (needs MySQL, Kafka, the 3 services and the gateway): book and fund an FD as a customer. Advance the demo clock as ADMIN (`POST /api/admin/clock/advance {"days":30}`). Request closure as the customer (expect 200 or 409 per FD-02). Press "Accept closure amount" in the UI (expect 403, FD-01). Approve with a different officer token via curl (expect 200, status PREMATURELY_CLOSED).
2. Penalty configured? `SELECT product_id,currency_id,premature_penalty_points FROM fd_product_currency_ref;` in the fd DB. NULL means a 409.
3. Ledger balance (FD-07): `SELECT jl_account_code,SUM(jl_debit)-SUM(jl_credit) FROM fd_journal_lines jl JOIN fd_journals j ON j.fj_id=jl.jl_journal_id WHERE j.fj_fd_account_id=? GROUP BY 1;` after premature closure. Expect a non-zero FIXED_DEPOSIT balance.
4. Batch atomicity (FD-04): force a failure after the status update (rename `fd_journals` temporarily in a throw-away DB), advance the clock, and observe MATURED with no journal.
5. Run `mvn -pl services/fd-account-service test` with Docker for `FdAccountMigrationTest` and `FdCompoundingTest`.
6. Gateway: `curl -i -H "Authorization: Bearer <customer>" -X POST http://localhost:8080/api/v1/admin/fixed-deposits/1/premature-closure/1/approve -H "Idempotency-Key: x"` expects 403 from the service.
7. JWT role claim: the filter maps the `roles` claim directly to authorities (`FD/JwtFilter.java:22`). `hasRole('ADMIN')` needs `ROLE_ADMIN` in the token. Confirm identity-service issues the `ROLE_` prefix.

## 7. Hand-computed reference numbers (`calc/c.py`, scratchpad)

| Case | Result |
|---|---|
| 100,000 @7%, quarterly, 12 months from 2026-01-01 (ACTUAL/365 on compounding balance, matches `FdCompounding.maturity`) | 107,185.90 (simple would be 107,000.00) |
| Premature on day 181, 1 point penalty: full simple interest / paid / foregone / net | 3,471.23 / 2,975.34 / 495.89 / 102,975.34 |
| Compounded accrual shown in the accrual table, day 181 | 3,501.36 (differs from the quote basis by 30.13) |
