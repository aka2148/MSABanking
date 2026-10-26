# Agent 3 + 8: CASA / transfers / core transactions + Database / Ledger / Financial integrity

Scope: read-only static audit of the extracted repo
(`/private/tmp/claude-501/-Users-yashralhan-Projects-PokerThen-/e0312acd-4510-4ec5-98db-ebead3794720/scratchpad/repo`).
Paths below are relative to that root. All runtime claims are UNVERIFIED (no MySQL/JDK17 available; JDK11 only).
Method: full read of the fd-account-service money controllers, Flyway migrations, root SQL, nginx gateway, FRONTEND service layer, legacy BACKEND transfer service.

## 0. Headline answers

1. **Account-to-account (CASA/savings) transfer in the ACTIVE system: MISSING.**
   - No transfer endpoint in any `services/*` controller (grep of `transfer` in `services/**/*.java`: only product-service pricing facts `TRANSFER_AMOUNT`).
   - Gateway `services/api-gateway/nginx.conf:64-85` routes only `/api/v1/savings-accounts[/**]` (open, list, get, currencies, demo-options, demo-opening, transactions), `/fd/v1/staff/**` (staff deposit/withdraw), loans, forex, FD, ledger. There is no `/transfer` route, and `location /` returns 404.
   - FRONTEND `core/services/fd.service.ts:68-90` and `staff-operations.service.ts:19-25` call no transfer API. The word "transfer" appears only in demo disclaimers (`deposit-messages.ts`, `fd-account-detail.html:22,48,101`).
   - **It existed in the legacy monolith**: `BACKEND/src/main/java/com/bank/fd/application/SavingsTransferService.java:47-142` (paired DEBIT/CREDIT rows sharing one reference, ID-ordered row locks, idempotency claim) and migration `BACKEND/.../db/migration/V31__savings_transfers_and_fd_deposit_limits.sql`. It was NOT ported to `services/fd-account-service`. This is a regression from legacy, not a gap against the manual.
   - Manual: no CASA-transfer requirement (confirmed in task brief). Classification: **demo-scope / user-expected feature gap (P2)**, not a manual-compliance defect. The manual's ledger requirement (L19 line 185 "Apply debit/credit operations with correct balance classifications and GL mappings"; lines 1275, 1522, 1634 "GL entries for debit/credit") IS relevant to the double-entry findings below.

2. **Where the real schema comes from.** There are no Flyway files under `services/*/src/main/resources`. `services/fd-account-service/pom.xml:26` copies `BACKEND/src/main/resources/db/service-migration/fd/V1..V10` into the classpath at `db/service-migration/fd`; `application.yml` has `flyway.locations: classpath:db/service-migration/fd`, and the Dockerfile copies `BACKEND/.../service-migration`. So **legacy `BACKEND/` is a hard build dependency of the "active" service**; deleting it breaks schema creation. Note that `V1__initial_schema.sql` is byte-identical to root `03_moneypal_fd.sql` (diff of the CREATE section is empty), but root 03 lacks V2-V10 (DECIMAL(24,8) widening in V3, `fd_product_*_ref`, loan/forex tables in V7, penalty column, demo clock). **Root `03_moneypal_fd.sql` is stale and must not be used to build a DB** (its DDL cannot run the service: no `loan_applications`, no `fd_product_currency_ref`). The legacy `BACKEND/.../db/migration/V1-V31` is PostgreSQL (BIGSERIAL, TIMESTAMPTZ) and unrelated to MySQL runtime.

3. **Demo opening balance (money creation).** `docker-compose.yml:156` hard-codes `APP_DEMO_OPENING_BALANCE_ENABLED: "true"`; the code default is false (`SavingsAccountController.java:31`). With it on, any authenticated user can credit themselves (see CL-01).

## 1. Money-movement path table

| # | Entry point (role) | Balance mutation | Ledger rows written | Atomic / idempotent | Verdict |
|---|---|---|---|---|---|
| 1 | `POST /api/v1/savings-accounts/{id}/demo-opening` (any authenticated, flag on) `SavingsAccountController.java:52-71` | `savings_accounts.sa_balance = amount` under `FOR UPDATE` (l.61,67) | `savings_transactions` CREDIT only (l.68). **No `fd_journals`** (CHECK allows op `DEMO_OPENING` but code never writes it) | `@Transactional`; idempotency table + header; one-shot guard (balance 0 and no txns) | Balance without journal; unbacked money creation (CL-01, CL-02) |
| 2 | `POST /fd/v1/staff/savings-accounts/{id}/deposit` (BANK_OFFICER/ADMIN) `StaffAccountController.java:71-111` | `balance +/- amount` under `FOR UPDATE` (l.94,106) | `savings_transactions` single row (l.108). No journal, no cash/GL contra leg | `@Transactional`; idempotency (scope, actor, key) unique | Single-entry; invisible in admin ledger (CL-02) |
| 3 | `.../withdraw` (same) | same, `signum<0` -> 409 (l.105) plus DB `CHECK sa_balance>=0` | single `savings_transactions` DEBIT | same | Same as #2 |
| 4 | `POST /api/v1/fixed-deposits/{id}/fund` (customer) `FixedDepositController.java:65-88` | savings `-principal` (l.78) under `FOR UPDATE` on FD row then savings row (l.68,74) | `savings_transactions` DEBIT, `fd_transactions` (balance_after NULL), `fd_journals FUNDING:<id>` + 2 lines SAVINGS Dr / FIXED_DEPOSIT Cr (l.82-84) | One `@Transactional`; idempotency header; journal key unique; FD status guard `PENDING` | Good; this is the best-built path |
| 5 | Maturity batch `FdBatchService.mature` l.36-63 (`/api/v1/admin/fixed-deposits/maturity-batch`, officer/admin; also clock advance via `runDaily`) | No savings change. FD -> `MATURED`, `fa_maturity_amount` | `fd_journals INTEREST:<id>:MATURITY` lines INTEREST_EXPENSE Dr / FIXED_DEPOSIT Cr (l.52-57); `fd_transactions FD_MATURITY` | `@Transactional` only when invoked via proxy (controller). **Not transactional via `runDaily`** (CL-05). Journal key guards duplicates | Balanced; daily accruals NOT journalled (only at maturity) |
| 6 | Normal close `POST /api/v1/fixed-deposits/{id}/close` `FdLifecycleController.java:36-44` -> `settle` l.118-134 | savings `+maturityAmount` under `FOR UPDATE` (l.120-123) | `savings_transactions` CREDIT; `fd_transactions FD_CLOSURE`; journal `SETTLEMENT:<id>` FD Dr / SAVINGS Cr (l.131) | `@Transactional`, idempotency, FD row lock; pre-check `verifyMaturityJournals` (l.85-95) | Good. Ledger skip-if-exists (l.131) could mask a double credit (CL-12) |
| 7 | Premature close request `.../premature-closure` (l.46-61) + approve `/api/v1/admin/.../approve` (l.63-73) | savings `+net` where net = principal + reduced interest (quote frozen at request time) | Same SETTLEMENT journal for **net** (principal+interest) but FD liability was credited only for principal; no interest journal ever posted; `FD_PENALTY` `fd_transactions` row has no journal | `@Transactional`; claim; four-eyes (approver != owner) | **GL inconsistent (CL-03)**; stale quote (CL-10) |
| 8 | Loan disburse `POST /api/v1/admin/loans/{id}/disburse` `LoanForexController.java:123-143` (ADMIN) | savings `+approved amount` under `FOR UPDATE` (l.131-136) | `savings_transactions` CREDIT ref `LOAN:<id>` (unique with side). **No journal, no loan receivable, no schedule, no repayment endpoint** | `@Transactional` + loan row lock + status guard | Money creation w/o ledger and no repayment (CL-04) |
| 9 | Loan repayment | n/a | n/a | n/a | **MISSING** (status enum has ACTIVE/CLOSED but no code path) |
| 10 | Forex `POST /api/v1/forex-requests` `LoanForexController.java:156-183` | none | none (request record only; `fr_fee` hard-coded 0) | idempotency unique `(customer,key)` | Out-of-scope by design (UI says indicative); no FX posting/conversion |
| 11 | Savings-to-savings transfer | n/a | n/a | n/a | **MISSING** (legacy had it) |
| 12 | Interest payout (`fd_interest_payout`) / periodic payout | n/a | n/a | n/a | Table exists; no code writes it (`grep` finds no reference). Payout frequency is stored but never executed (CL-11) |

Accrual (`FdBatchService.accrueOne` l.65-97) writes `fd_interest_accrual` only (no GL), safe via `uq_fd_interest_accrual_account_date` (root 03 line 436; present in V1 index section, UNVERIFIED in V1 text but identical to root 03).

## 2. Double-entry assessment

Verdict: **partial journal, not a true double-entry ledger.**

What exists (good):
- `fd_journals` header + `fd_journal_lines` (`03_moneypal_fd.sql:192-218`): header CHECK `total_debit = total_credit` and `> 0`; per-line CHECK exactly one side positive; `fj_key` UNIQUE (`FUNDING:<id>`, `INTEREST:<id>:MATURITY`, `SETTLEMENT:<id>`); amount columns are DECIMAL(24,8) after V3.
- Every journal written by code is two lines, equal amounts (static review of all 4 writers: fund l.82-84, mature l.55-56, settle l.131, journal()/lines() helpers). So **each journal individually balances** (UNVERIFIED at runtime; see verification V3).
- Admin ledger and per-FD journal views read these tables.

What is missing / wrong:
1. **Header-to-lines integrity is only by app convention.** No FK-enforced or trigger-enforced `SUM(lines.debit) = header.total_debit`; the SQL comment itself says "application must also check journal line totals" (`03_moneypal_fd.sql:491`). Nothing in code checks it.
2. **No chart of accounts / GL mapping.** `jl_account_code` is a free VARCHAR(50) holding the literals `SAVINGS`, `FIXED_DEPOSIT`, `INTEREST_EXPENSE` with no table, no FK, no per-product/currency GL (manual L19 asks for "GL mappings"). `gl_code_mapping` exists only in the product DB (`02_moneypal_product.sql:296`) and is never consumed by fd-account-service. No cash/nostro/suspense/equity account, so deposits, withdrawals, demo credits and loan disbursements have no contra side.
3. **Not every balance mutation has a journal.** Savings deposit, withdraw, demo-opening, loan disbursement mutate `sa_balance` and write one `savings_transactions` row but no `fd_journals`. `savings_transactions` is a customer-side statement (one row per movement), not a balanced ledger. Net effect: **the admin ledger (`FdLedgerAdminController`) shows only FD-related journals** and silently omits all other money movement. Sum of balances cannot be reconciled against journals.
4. **Journals without matching balance mutation:** the INTEREST journal debits INTEREST_EXPENSE / credits FIXED_DEPOSIT and legitimately has `fj_savings_balance_after` NULL, so this is consistent. However the **premature-close path never posts interest** (CL-03) so FD liability account goes net-negative by `paidInterest`.
5. **Accrued-but-unpaid interest never hits GL until maturity**, so daily accrual is off-ledger (acceptable for a lab, but contradicts "GL entries for debit/credit").
6. **`fj_savings_account_id NOT NULL`** forces every journal to name a savings account even for pure-GL entries (INTEREST). Ledger is thus structurally tied to the funding account instead of generic accounts.
7. Journals are mutable by SQL (no immutability triggers, no reversal/contra workflow). Corrections impossible without UPDATE/DELETE.
8. Statuses: `fj_operation` enum includes `DEMO_OPENING` (dead). `PREMATURE` is folded into `SETTLEMENT` (key comment lines 485-486), which loses the distinction in reporting.

Sign convention check: Funding Dr SAVINGS (customer liability decreases), Cr FIXED_DEPOSIT (liability increases): correct for a bank. Settlement Dr FIXED_DEPOSIT, Cr SAVINGS: correct. Interest Dr INTEREST_EXPENSE, Cr FIXED_DEPOSIT: correct. Labels are fine; the accounting model just stops there.

## 3. Admin ledger correctness (`FdLedgerAdminController.java`)

- `@PreAuthorize("hasRole('ADMIN')")` class-level; gateway route exists (`nginx.conf:70-71`). Read-only; page/size clamped (l.41-42); filters mutually exclusive.
- **N+1 query**: for every journal row it issues a second query for lines (l.63-66): up to 100 extra queries per page. P3 performance.
- **INNER JOIN `savings_accounts` and `fd_currency_ref`** (l.53-54): if either row is ever missing the journal vanishes from the list while `COUNT(*)` (l.47, no joins) still counts it, producing total/rows mismatch. FKs make this unlikely; P3.
- `ORDER BY fj_posted_at DESC, fj_id DESC` with `OFFSET` on a table lacking an index on `fj_posted_at` / `fj_savings_account_id` / `fj_fd_account_id`: see schema finding S-3. `fd_journal_lines` PK `(journal_id, sequence)` covers the lines lookup.
- `totalDebit`/`totalCredit` come from the header, **not summed from lines**, so a corrupted or partial line set will not be flagged. No reconciliation endpoint or "unbalanced journals" report.
- Account numbers masked to last 4 (`mask`, l.77-80), fine; but masking is for display only and filters take internal IDs.
- Omits non-FD money (see Double-entry #3); an auditor reading this screen would conclude the bank's ledger is complete when it is not (CL-02).
- No export, no date-range filter, no operation filter.

## 4. Schema vs code findings

- **S-1 (stale root SQL)** `03_moneypal_fd.sql` == V1 only. Running it per its header ("Run directly in MySQL Workbench") gives DECIMAL(18,4) money columns and no loan/forex/product-ref tables; the service then fails on the first loan/forex/booking query. Real schema = Flyway V1-V10 from `BACKEND/`.
- **S-2 (precision mismatch)** Code permits currency scale up to 8 (`FixedDepositController.java:47`) and stores accrual at scale 8, while V1 savings/journal columns are DECIMAL(18,4); only V3 widens to (24,8). On the root-03 schema, 8-dp values would be rounded by MySQL at insert (silent) and the 18-digit magnitude is 10^14. With V3 applied it is consistent; flagged as environment-dependent. Rate columns: `fa_interest_rate DECIMAL(9,4)` vs code `rate` BigDecimal from Product (`ro_outcome_value`) may carry 8 dp (`LoanForexController.rate` uses `setScale(8)` for loan/forex, `la_annual_rate DECIMAL(9,8)` allows <10.0 only). FD `fa_interest_rate (9,4)` silently rounds a rate such as 0.06125 to 0.0613 (if rates are stored as fractions: 4 dp = 0.01% steps), but accrual uses the stored rounded rate (`lockAccount` reads `fa_interest_rate`) while `fa_terms_snapshot` JSON holds the true rate: **FD accrues on a rate different from the contractual snapshot** (CL-09, UNVERIFIED which scale the product emits).
- **S-3 (missing indexes)** No explicit index on `fd_journals(fj_fd_account_id)`, `(fj_savings_account_id)`, `(fj_posted_at)`; InnoDB creates FK indexes automatically for the two FK columns, so lookups by FD/savings are fine, but admin list ordering by `fj_posted_at` is a filesort. P3. `savings_transactions` has composite `(st_account_id, st_posted_at, st_id)` (V1 l.428) - good.
- **S-4 (unique constraint oddity)** `uq_savings_holder_currency UNIQUE(sa_account_id, sa_holder_id, sa_currency_id)` (V1 l.302) includes the PK, so it never restricts anything; its only purpose is to serve as a composite FK target for `fk_fd_funding_savings`. Intended "one savings account per holder per currency" is NOT enforced (users may open unlimited accounts per currency; see CL-06).
- **S-5 (CHECKs)** Good: balance >= 0 on `savings_accounts` (active schema keeps it; the legacy PG V30 dropped it - make sure nobody ports V30), `st_amount > 0`, `st_balance_after >= 0`, entry type, FD status, journal balanced, idempotency status. Missing: no CHECK `sa_status` includes CLOSED/FROZEN (only ACTIVE/INACTIVE, no freeze/dormant/closed lifecycle), no CHECK on `fa_maturity_amount >= principal`, no CHECK on `fj_total_credit > 0` (implied by equality), no max amount.
- **S-6 (savings has no opening-balance concept)** column `sa_row_version` is incremented manually (`SET sa_row_version=sa_row_version+1`) but is never part of a WHERE, so it provides no optimistic locking; protection actually comes from `SELECT ... FOR UPDATE` (adequate).
- **S-7 (cross-service references)** `fa_primary_holder_id`, `sa_holder_id`, `fa_product_id`, `la_customer_id`, `fr_customer_id`, currency ids are IDs owned by other DBs. They are handled by **local replica tables** (`fd_user_ref`, `fd_product_ref`, `fd_currency_ref`) filled by Kafka consumers (`ReferenceSnapshotConsumer`, manual ack, version-guarded for products; user upsert guarded by `fur_source_version`) and real local FKs to the replicas. This is a reasonable pattern, but: (a) eventual consistency, so first use may fail with "Customer reference is not available" (409, handled); (b) replicas are never deleted/tombstoned, so a deactivated customer stays usable until the status event arrives and most money paths (staff deposit/withdraw, settle, disburse) **do not check `fur_user_status`** (only `create`, loans, forex do); (c) `KAFKA_ENABLED` defaults to false in `application.yml`, so a non-compose run has empty replicas and nothing works; (d) currency identity: `fd_currency_ref` PK is the Product service's currency id, but `LoanForexController.currency()` and `FixedDepositController` look currencies up by ISO code (UNIQUE on `UPPER(iso)`), fine.
- **S-8 (orphan/dangling)** Loan: `la_disbursement_transaction_id` FK to `savings_transactions(st_id)` with UNIQUE: good. `fd_accounts.fa_funding_savings_account_id` is **nullable** in V1 but is `NOT NULL`-assumed by code (`r.getLong(5)` returns 0 on NULL; settle would then target account 0). Composite FK `(savings, holder, currency)` stops a mismatched account at booking (good) but NULL bypasses it. P3.
- **S-9 (event/outbox)** `fd_account_events` has `fae_aggregate_version`; the SQL comments promise "outbox write shares one transaction" but there is no publisher polling this table (`FdEventPublisher` exists but not part of money paths; UNVERIFIED). Events for events/analytics only.
- **S-10 (enum drift)** Status strings are repeated in 5 places (StaffAccountController l.40, 56; SQL CHECK; frontend). `StaffAccountController.savings` validates `ACTIVE`,`INACTIVE`; there is no endpoint to change savings status at all (no freeze/close), so INACTIVE is unreachable.
- **S-11 (JSON column)** `fa_terms_snapshot` is `CAST(? AS JSON)`; fine on MySQL 8.4 (UNVERIFIED on this host).
- **S-12 (collation)** `utf8mb4_0900_as_cs` is accent- and case-sensitive. Account numbers/keys are case-sensitive (good); `idempotency` columns ascii_bin (good).

## 5. Concurrency, atomicity, idempotency analysis

Strengths (static):
- Every balance-mutating path reads the savings row with `FOR UPDATE` inside a `@Transactional` method, computes in Java, then writes (fund l.74-78, settle l.120-123, staff l.94-106, demo l.61-67, disburse via `requireSavings(...,true)`). Lost update / double spend is prevented by row lock; DB `CHECK sa_balance >= 0` is a backstop.
- Lock ordering: FD row -> savings row everywhere (fund, settle, approve); loan row -> savings; staff -> savings only. No opposing order, so no deadlock cycle identified.
- Idempotency: `fd_operation_idempotency` UNIQUE `(scope, actor, key)`, request hash comparison, claimed as first statement in the same transaction. Concurrent identical requests: second INSERT blocks on the unique index until the first commits or rolls back, then returns the stored aggregate or proceeds. MySQL (unlike PostgreSQL) does not poison the transaction after the duplicate-key error, so the handler's follow-up SELECT works. The PENDING/FAILED states are never durably visible, which is acceptable here because claim + work are one transaction.
- Additional hard guards: `fj_key` UNIQUE, `uq_savings_transaction_reference_side` (loan `LOAN:<id>`), loan status transitions under row lock.

Weaknesses:
- **Idempotency key is scoped to actor but staff deposit/withdraw identity is the JWT subject** (`actor.getName()`), good; customer paths use numeric user id. OK.
- **Replay returns the *current* state**, not the original response, for staff transact (`savingsRow(id)`), fund/close (`find`/`closeResponse` read live balances). Cosmetic.
- **Hash weakness:** demo-opening hashes `amount.toPlainString()` so `100` and `100.00` hash differently and the second is rejected as "different request" (409) instead of replay. Minor.
- **Retry after client timeout:** safe by design (above), but UI behaviour untested.
- `fd()` helper in lifecycle locks the FD row even for read-only GETs (`transactions`, `accruals`, `journals` call `fd(id)` which is `FOR UPDATE`, l.136) **outside a transaction** (no `@Transactional` on those GET methods): in autocommit mode the lock is released immediately, but if a transaction is ever added the GETs would serialise against money operations. Today it is only a smell, though under any open connection setting `autocommit=false` it would hold locks. P3.
- `FdBatchService.runDaily` calls `this.accrue/this.mature`; **Spring proxy bypass => no transaction** (CL-05).
- Admin batch endpoints accept an arbitrary `businessDate` (CL-07).
- Time source: `UTC_TIMESTAMP(6)` in SQL but `clock.businessDate()` for FD logic; mixing DB time and simulated business date for `fj_posted_at` makes journals' dates not match the demo clock. P3.

## 6. Findings

Severity: P0 blocker, P1 high, P2 medium, P3 low. Confidence H/M/L. All UNVERIFIED at runtime unless stated.

### CL-01 (P1, Security/Financial) Self-service money creation via demo-opening, enabled in compose
- Evidence: `docker-compose.yml:156` (`APP_DEMO_OPENING_BALANCE_ENABLED: "true"`); `SavingsAccountController.java:52-71, 80-83, 115`; `StaffAccountController`/gateway route `/api/v1/savings-accounts/**` open to any authenticated JWT.
- Scenario: a customer (even KYC-unverified, even a staff user) opens N savings accounts (`POST /api/v1/savings-accounts`, no limit, no KYC check) and calls `/demo-opening` on each with `amount=999999999999999.9999`-scale values (only `@DecimalMin("0.01")`, no max; column allows 10^14). Each credit is unbacked: no journal, no offset account. Funds can then be used for FD funding/premature/closure, so interest accrues on fabricated principal and FD product limits (`pcc.maximum_amount`) become the only cap.
- Root cause: demo feature wired on by default in the delivered compose file, no per-account/total cap, no ledger contra entry, no role restriction, no KYC check. Confidence H (static).
- Fix: keep flag OFF in compose (move to `docker-compose.demo.yml` override or `.env`); require a role (`hasAnyRole('BANK_OFFICER','ADMIN')`) or a one-off seeding script; add `@DecimalMax` (e.g. 1,000,000) and a per-customer lifetime cap; require KYC VERIFIED (`CustomerProductAccess.requireVerified`); post a balanced journal `DEMO_OPENING`: Dr `DEMO_FUNDING_SUSPENSE`/Cr `SAVINGS` (the CHECK constraint already allows this op); log an audit event. Show a persistent "DEMO MONEY" banner.
- Test: integration test that flag=false returns 404, flag=true by CUSTOMER returns 403/limit 400, and that each credit writes a balanced journal.
- Effort: S (0.5-1 day).

### CL-02 (P1, Financial integrity / Ledger) Savings deposits, withdrawals, demo credits and loan disbursements bypass the journal; admin ledger incomplete
- Evidence: `StaffAccountController.java:106-108`, `SavingsAccountController.java:67-68`, `LoanForexController.java:136-138`; journal writers only in `FixedDepositController.java:82-84`, `FdBatchService.java:55-56`, `FdLifecycleController.java:131`; admin view `FdLedgerAdminController.java:47-55`.
- Scenario: auditor sums journals vs balances: they do not reconcile. A teller withdrawal of 1,000,000 never appears in the "ledger"; the admin sees a clean ledger while savings balances moved. Manual L19 requires debit/credit with GL mappings.
- Root cause: ledger designed around FD only; savings movement recorded in a single-sided statement table. Confidence H.
- Fix: introduce one `PostingService.post(journalKey, op, currency, lines[])` used by ALL money paths (staff deposit: Dr `CASH_TELLER`/Cr `SAVINGS`; withdraw reverse; loan disburse: Dr `LOAN_RECEIVABLE`/Cr `SAVINGS`; demo: Dr `DEMO_SUSPENSE`/Cr `SAVINGS`). Extend `fj_operation` CHECK via new Flyway V11 (`DEPOSIT, WITHDRAWAL, LOAN_DISBURSEMENT, TRANSFER, ...`), make `fj_savings_account_id` nullable or add per-line `jl_savings_account_id`. Add `chart_of_accounts` table and FK from `jl_account_code`. The service must verify `SUM(lines)` debit = credit before insert.
- Test: property test "for each op, sum(debits)=sum(credits) and sum of SAVINGS-line deltas = change in sum(sa_balance)"; reconciliation SQL (V3).
- Effort: M (2-4 days).

### CL-03 (P1, Financial correctness) Premature-closure settlement is not balanced against the FD liability; interest expense never recognised
- Evidence: `FdLifecycleController.java:56-58, 71, 118-131`: payout `net = principal + paidInterest`, journal `SETTLEMENT:<id>` Dr FIXED_DEPOSIT net / Cr SAVINGS net. FD liability was only credited with `principal` at funding (`FixedDepositController.java:84`) and no `INTEREST` journal exists on this path (only `FdBatchService.mature` writes one).
- Scenario: FD 100,000 closed prematurely paying 100,000 + 2,000 interest. Ledger: FIXED_DEPOSIT credits 100,000, debits 102,000, giving a -2,000 balance; INTEREST_EXPENSE never debited. Each journal "balances", but the chart of accounts does not.
- Root cause: interest journal only generated in maturity batch; premature path pays interest without posting expense. Confidence H.
- Fix: in `settle`, for premature path, first post `INTEREST:<id>:PREMATURE` Dr INTEREST_EXPENSE / Cr FIXED_DEPOSIT for `paidInterest` (and optionally disclose penalty as non-posted memo), then settlement for the sum. Add invariant check `credits(FIXED_DEPOSIT) - debits(FIXED_DEPOSIT) = 0` after closure.
- Test: premature closure integration test asserts per-FD net of FIXED_DEPOSIT account = 0 and INTEREST_EXPENSE = paidInterest.
- Effort: S.

### CL-04 (P1, Financial / Completeness) Loan disbursement creates funds with no receivable, no repayment, no schedule; same admin approves and disburses
- Evidence: `LoanForexController.java:99-143`; migration V7 status enum includes `ACTIVE`/`CLOSED` but no code path; no repayment controller anywhere in `services/**`.
- Scenario: admin approves and disburses a loan to a customer's savings; customer withdraws via staff; the principal is never owed back; also one ADMIN can approve and disburse (no maker-checker, unlike premature closure which has it, l.69).
- Root cause: loan module stops at "disbursement"; Confidence H. The manual's loan/repayment scope (if required by the lab) is not implemented. UNVERIFIED which parts of the manual need repayment; flag to the scope agent.
- Fix: add `loan_repayment_schedule` and `repay` endpoint (Dr SAVINGS Cr LOAN_RECEIVABLE, interest split), journal on disbursement, maker-checker (`decision_admin_id != disbursing admin`), cap disbursal to `la_approved_amount`.
- Test: disburse then repay all instalments -> LOAN_RECEIVABLE nets to 0; same-admin disburse -> 403.
- Effort: L (3-5 days) or document as out of scope.

### CL-05 (P1, Atomicity) Clock-advance daily batch runs without a transaction (self-invocation) and can leave half-matured FDs
- Evidence: `FdBatchService.java:19-25` `runDaily` calls `accrue(date)`/`mature(date)` via `this`, bypassing the `@Transactional` proxy on l.27, 36; called from `FdClockController.advance` (`FdClockController.java` `/advance`). In `mature` the status update (l.51), journal (l.55-56), `fd_transactions` (l.59) and event (l.60) are separate statements; `lockAccount` `FOR UPDATE` (l.99) is released immediately in autocommit.
- Scenario: advance the demo clock by 365 days; an exception (e.g. missing `FD_MATURITY` transaction type or deadlock) after `UPDATE fd_accounts SET fa_status='MATURED'` leaves an FD MATURED with no interest journal. `close` then fails permanently with "Verified maturity interest records are required" (`FdLifecycleController.java:85-90`): the customer's money is stuck. Also two concurrent clock advances can both read ACTIVE and both proceed (no lock held).
- Root cause: Spring AOP self-invocation; also the controller-path batch is one transaction for ALL accounts, so one bad account rolls back the whole batch. Confidence H.
- Fix: move per-account work to a separate bean method annotated `@Transactional(propagation=REQUIRES_NEW)` (or inject self/TransactionTemplate), loop with per-account try/catch and fill `failed_account_ids` (the DTO already has `accounts_failed`/`failed_account_ids`, hard-coded `0, List.of()` at l.24). Add `SELECT ... FOR UPDATE SKIP LOCKED`.
- Test: inject a failure on account 2 of 3 and assert accounts 1 and 3 mature and 2 reports failed with no partial rows.
- Effort: S-M.

### CL-06 (P2, Domain) Unlimited savings accounts, no uniqueness per holder/currency, no KYC on opening; account number collision handled as 500
- Evidence: `SavingsAccountController.java:80-99` (no count limit, no `requireVerified`), `03_moneypal_fd.sql:305` (unique includes PK), account number `"SA-"+16 hex` from UUID substring (64 bits).
- Scenario: scripted creation of thousands of accounts (DoS of storage, enables CL-01 amplification); duplicate display names allowed.
- Fix: cap accounts per customer (e.g. 5 per currency) with `UNIQUE` or a pre-check under a customer-level lock; require KYC verified; generate number via sequence/Luhn; catch `DuplicateKeyException` and retry.
- Test: 6th account -> 409; unverified user -> 403. Effort: S.

### CL-07 (P1, Business logic/Financial) Staff batch endpoints accept arbitrary `businessDate`, enabling early maturity and future accrual
- Evidence: `FdLifecycleController.java:30-34` (`@RequestParam LocalDate businessDate` passed straight to `batches.accrue/mature`), `FdBatchService.java:37-45` (maturity decision uses the supplied `date`), `FdClockController` is the only sanctioned time control.
- Scenario: a BANK_OFFICER (non-admin) calls `POST /api/v1/admin/fixed-deposits/maturity-batch?businessDate=2099-01-01`. All ACTIVE FDs flip to MATURED immediately with interest recorded for the contractual maturity date, and customers can then close and be paid, with no clock simulation by admin and no premature penalty.
- Root cause: parameter not validated against `clock.businessDate()`; role is officer-or-admin while the clock endpoints are ADMIN only. Confidence H.
- Fix: ignore or reject any `businessDate` greater than `clock.businessDate()`; restrict to ADMIN; audit-log invoker.
- Test: officer call with future date -> 400/403.
- Effort: XS.

### CL-08 (P2, Atomicity/Design) No account-to-account transfer; legacy implementation not ported
- Evidence: section 0.1. Gateway has no route; fd-account-service has no transfer controller.
- Impact: user-expected CASA function absent; manual has no requirement. Classification: demo-scope gap.
- Recommended design (atomic + idempotent, MySQL/JdbcTemplate, in style of existing code):
  1. `POST /api/v1/savings-accounts/transfers`, headers `Idempotency-Key` (required, <=255), body `{fromAccountId, toAccountNumber, amount, remark}`. Authenticated customer must own `from`; `requireVerified`.
  2. In one `@Transactional`: `claim("SAVINGS_TRANSFER", userId, key, sha256(canonicalised request with amount.stripTrailingZeros))` via the existing `fd_operation_idempotency` insert; replay returns the stored transfer by `foi_response_reference`.
  3. Resolve destination id by account number; reject same account, non-ACTIVE, different currency (no FX), FD numbers, and check destination customer ACTIVE.
  4. **Lock both rows in ascending `sa_account_id` order**: `SELECT ... WHERE sa_account_id IN (?,?) ORDER BY sa_account_id FOR UPDATE` (single statement acquires in index order; prevents A->B vs B->A deadlock). Re-validate balance after lock.
  5. `amount.setScale(currencyScale, UNNECESSARY)`; reject if `balance < amount`. Update both balances (+`sa_row_version`).
  6. Insert two `savings_transactions` rows with the same `st_reference_id` (`TRF-<uuid>`) and types DEBIT/CREDIT (the table's `uq_savings_transaction_reference_side` already allows exactly one of each per reference), each with correct `st_balance_after`.
  7. Insert a balanced journal `TRANSFER:<reference>` (op `TRANSFER`, add to CHECK in Flyway V11): Dr SAVINGS(source) / Cr SAVINGS(dest) - requires per-line account id (see CL-02).
  8. `complete(...)` the idempotency row; return reference and the source balance after. Add per-transaction and daily limits; write an `fd_account_events`/audit row.
  9. DB backstops: `CHECK sa_balance>=0` stays; add `CHECK` that debit and credit rows of a reference sum equal (verified by reconciliation job).
- Test: (a) two parallel opposite-direction transfers (A->B, B->A) complete without deadlock; (b) 20 concurrent debits of 10 from balance 100 produce exactly 10 successes and balance 0; (c) same key twice -> one posting, same response; (d) same key different amount -> 409; (e) kill after step 6 (fault injection) -> nothing persisted; (f) sum of balances invariant.
- Effort: M (2-3 days incl. frontend).

### CL-09 (P2, Precision) Rate/amount rounding inconsistencies between layers
- Evidence: `fa_interest_rate DECIMAL(9,4)` (`03_moneypal_fd.sql:28`) vs accrual using stored rate (`FdBatchService.lockAccount` l.99 -> `rate`) while the exact rate is in `fa_terms_snapshot` (`FixedDepositController.java:56`); principal stored `setScale(scale,HALF_UP)` at 24,8 only after V3; premature quote uses *simple* interest (`FdLifecycleController.java:56-57`, 365 days, scale 12 then HALF_UP) while accrual uses *compounded* daily balance, so a premature payout of "interest" does not match `fd_interest_accrual` cumulative figure shown in the UI (`/accruals/cumulative`). Rounded-to-scale values are persisted at each step, so there is no cumulative drift across tables (positive).
- Scenario: customer sees accrued 1,234.56 but premature quote computed from a different formula; or a 6.125 percent offer is booked and accrued at 6.13 percent.
- Fix: widen `fa_interest_rate` to DECIMAL(12,8) (Flyway V11) and verify product rate scale; base the premature quote on accrued/cumulative data (`fia_cumulative_accrued` less penalty) or document the formula; add unit tests with HALF_EVEN vs HALF_UP boundaries.
- Test: round-trip test that booked rate equals offered rate to 8 dp; premature quote equals accrual sum minus penalty.
- Effort: S-M. Confidence M (depends on Product's emitted scale; UNVERIFIED).

### CL-10 (P2, Business logic) Premature closure payout uses a stale quote and mixes frozen/new state
- Evidence: `FdLifecycleController.java:46-61` computes `net` at request time; `approvePremature` l.63-73 pays `q.get("net")` without recomputation; no expiry on PENDING requests; request flow does not freeze the FD (batch can mature it between request and approval, then approval rejects with 409 but the PENDING request lingers and blocks new requests (l.50) until cleaned manually, since no cancel/expire endpoint exists).
- Scenario: customer requests premature closure; approver waits 3 weeks; payout equals day-0 quote (customer under-paid) or request is stuck forever.
- Fix: store quote date + expiry; recompute at approval and require within tolerance; add cancel/expire; reject request when FD matures within N days.
- Test: advance the clock between request and approval. Effort: S-M.

### CL-11 (P2, Completeness) Interest payout frequency stored but never executed; `fd_interest_payout`, `fd_statements`, `fd_notices`, `fd_maturity_instructions`, `fd_payout_accounts` are dead tables
- Evidence: grep of `services/fd-account-service/src/main` for those table names finds only the reports reading none of them (no INSERT anywhere); `payout_frequency` captured at booking (`FixedDepositController.java:53,58`).
- Impact: monthly/quarterly payout products silently behave as cumulative. Auto-renewal field `fa_auto_renewal` unused. Maturity instruction (credit to savings vs renew) not honoured; maturity always credits the funding savings account on close (and requires the customer to call `close`; no auto-credit).
- Fix: either implement payout posting (Dr INTEREST_EXPENSE Cr SAVINGS per period, journal `INTEREST:<id>:<period>` as documented in SQL comments) or remove tables/columns and the UI claims.
- Effort: M. Confidence H on static finding.

### CL-12 (P3, Ledger safety) `settle` skips journal creation if key exists but still credits savings
- Evidence: `FdLifecycleController.java:131` `if(!existsJournal(journalKey)){...}` while savings credit (l.123-124) is unconditional.
- Scenario: any state where a SETTLEMENT journal exists but FD status is still MATURED/ACTIVE (manual SQL, partial restore) -> second payout credited with no journal.
- Fix: fail with 409 if the journal exists; rely on the UNIQUE key. Test: pre-insert journal then close -> 409. Effort: XS.

### CL-13 (P2, Data) Dead/unsafe legacy dependency and stale root SQL
- Evidence: `services/fd-account-service/pom.xml:26`, `Dockerfile:5`, root `03_moneypal_fd.sql` header ("Run directly in MySQL Workbench"), `BACKEND/.../db/migration/V30` (drops balance CHECK, PostgreSQL).
- Impact: new contributors build from root SQL and get a broken or lower-precision schema; deleting "legacy" BACKEND breaks the service; Flyway V1 header comment says "FRESH EMPTY DATABASE ONLY" while `FD_FLYWAY_BASELINE` exists.
- Fix: move `service-migration/fd` into `services/fd-account-service/src/main/resources/db/migration`; regenerate root `03_*.sql` from V1..V10 (or delete it, or add a CI diff check); document.
- Effort: S.

### CL-14 (P2, Controls) Staff teller operations lack limits, approval, customer-status check and reason
- Evidence: `StaffAccountController.java:71-111`, MoneyRequest `@DecimalMin("0.01")` only; `remarks` optional.
- Scenario: a single officer deposits or withdraws any amount with no second approver, no daily limit, no check that the customer is still ACTIVE/KYC verified, no mandatory reason; no journal (CL-02).
- Fix: mandatory remark, per-role limit, maker-checker above a threshold, check `fur_user_status`, log audit event.
- Effort: S-M.

### CL-15 (P3) Savings status lifecycle unreachable; no close/freeze; no account-level locking of INACTIVE
- Evidence: `savings_accounts_sa_status_check` allows ACTIVE/INACTIVE only; no controller sets INACTIVE. Fix: add staff freeze/close endpoint with zero-balance requirement. Effort: S.

### CL-16 (P3) Read-path locks and N+1 queries
- Evidence: `FdLifecycleController.java:136` (`fd()` is `FOR UPDATE` and used by GET endpoints at l.76, 80, 99, 106, 113); `FdLedgerAdminController.java:63-66` N+1. Fix: separate non-locking `fdReadOnly()`; batch-load lines by `IN (...)`. Effort: XS.

### CL-17 (P3) Missing audit of who credited demo funds / actor columns in ledger
- Evidence: `fd_journals` has no `created_by`; savings_transactions has no actor; `fd_transactions` has `ft_created_by` (staff name) only for FD. V9 removed redundant audit columns. Fix: add `fj_created_by`, `st_created_by`. Effort: S.

## 7. Verification steps (UNVERIFIED items; run on a machine with Docker + JDK17)

V1. Boot: `docker compose up --build fd-db fd-account-service kafka` with `.env` set; confirm Flyway applied V1..V10 (`SELECT version,success FROM flyway_schema_history`). Then diff `SHOW CREATE TABLE savings_accounts` vs root 03 to confirm DECIMAL(24,8) vs (18,4) (S-1/S-2).
V2. CL-01: as a fresh CUSTOMER JWT (via gateway) call `POST /api/v1/savings-accounts`, then `POST /{id}/demo-opening` with `{"amount": 99999999999999.9999}` and a UUID `Idempotency-Key`. Expect 200 and balance set. Then `SELECT COUNT(*) FROM fd_journals WHERE fj_operation='DEMO_OPENING'` -> expect 0 (confirms no journal).
V3. Ledger balance/reconciliation SQL after running the full happy-path and premature flows:
   - `SELECT j.fj_id FROM fd_journals j JOIN fd_journal_lines l ON l.jl_journal_id=j.fj_id GROUP BY j.fj_id,j.fj_total_debit,j.fj_total_credit HAVING SUM(l.jl_debit)<>j.fj_total_debit OR SUM(l.jl_credit)<>j.fj_total_credit;` (expect 0 rows)
   - `SELECT jl_account_code, SUM(jl_debit)-SUM(jl_credit) FROM fd_journal_lines GROUP BY jl_account_code;` After a premature closure expect FIXED_DEPOSIT net != 0 (CL-03) and non-zero SAVINGS net.
   - `SELECT (SELECT SUM(sa_balance) FROM savings_accounts) - (SELECT SUM(CASE st_entry_type WHEN 'CREDIT' THEN st_amount ELSE -st_amount END) FROM savings_transactions);` expect 0 (statement table reconciles with balances) - it should; if not, a path skipped a statement row.
V4. CL-05: set `spring.jpa`-agnostic test: run `POST /api/admin/clock/advance {"days":400}` with a DB trigger or temporary rename of `transaction_types` row `FD_MATURITY` to force failure after the status update; check FD is MATURED without journal; then `POST /fixed-deposits/{id}/close` -> 409.
V5. CL-07: call maturity batch as BANK_OFFICER with `?businessDate=2099-01-01`; verify FDs flip to MATURED.
V6. Concurrency: run 20 parallel staff withdraws of 10 each (distinct idempotency keys) against balance 100 (e.g. `xargs -P20 curl`); expect exactly 10 successes and balance 0, plus 10 DEBIT rows. Repeat with identical key x20: expect 1 posting. Repeat fund-vs-withdraw race on the same savings account.
V7. Deadlock probe: parallel fund (FD->savings lock order) and loan disburse and staff deposits; check `SHOW ENGINE INNODB STATUS` for deadlocks (none expected).
V8. Unit tests exist for happy path only (`FdAccountMigrationTest`, requires Docker; `disabledWithoutDocker=true`, so it silently skips in CI without Docker): run `mvn -pl services/fd-account-service test` with Docker and confirm it is not skipped.

## 8. Positive observations (for balance)

- Consistent row-level pessimistic locking and a DB-enforced non-negative balance.
- Idempotency table with scope/actor/key unique constraint and request hash; same pattern used by FD, savings, loan, forex.
- Header-balanced journals with CHECKs, unique journal keys for FD funding/maturity/settlement, FK from journals to FD and savings.
- `setScale(..., UNNECESSARY)` used at input boundaries so over-precise amounts are rejected rather than silently rounded.
- Cross-service references handled with local replica tables and FKs plus version-guarded upserts.
- Four-eyes check on premature-closure approval; KYC gating on FD/loan/forex.

## 9. Not in this audit's scope / handoffs

- JWT/role design, gateway auth (Agent for security). CL-01/CL-07/CL-14 depend on role assignment; flag.
- Product service rate scale (CL-09) belongs to the product/pricing audit.
- Frontend transfer screen absence is covered above; no UI to inspect ledger beyond `ledger-admin`.
