# REPORT 2 — Critical Defects, Remediation Strategy & Prioritized Action Plan

**Project:** MoneyPal / Banking Technology Lab · **Audit date:** 2026-10-25 · **Mode:** strict read-only; **no fixes were implemented**
**Companion:** `REPORT_1_DELIVERABLES_COMPLIANCE_AUDIT.md` (compliance matrix, scoring method, full compliance & method statement §0 — applies to this report as well).
**Evidence:** `evidence/agent1…agent11-*.md` (finding IDs: A1-, AR-, CL-, FD-, FK-/KY-/LN-, UI-, SE-, QA-), `evidence/agent12-runtime-tests.md` (RT-, see Appendix R).

> **Compliance statement (repeated):** No application source or configuration was changed; no fixes were implemented; no Git state was altered; no existing database was modified. Only `audit-reports/` and the session scratchpad were written. What did and did not run is listed in Report 1 §0.1–0.2 and Appendix R below.

> **Evidence labels used throughout:** **CONFIRMED-STATIC** = established by reading the exact code path (file:line in evidence). **CONFIRMED-RUNTIME** = reproduced by execution (Appendix R only; currently D-02, D-03 [injected fault], D-05, D-18, D-41). **PROBABLE** / **UNVERIFIED** = needs the stated verification. No defect below is claimed as runtime-confirmed unless Appendix R says so. The runtime pass was partial and stopped early at the user's request.

---

## 1. Executive Summary — What Is Broken?

Plain-English impact of each area you asked about:

| Area | State | Impact in plain English |
|---|---|---|
| **CASA transfers** | **MISSING** in the active system (no endpoint, no gateway route, no UI). The legacy `BACKEND/` had one (`SavingsTransferService`, V31) and it was not ported. | There is nothing to click. "Transfer doesn't work" because the feature isn't deployed. Not required by the manual, but you expect it. |
| **FD premature withdrawal** | Backend exists as a **two-person** flow (customer requests → *different staff user* approves). The customer UI's "Accept closure amount" button calls the **staff-only** approve endpoint → 403. No staff screen exists. Also 409 if the product has no penalty rule, 409 if FD is MATURED, requests can never be cancelled. | A customer can never complete a premature withdrawal from the UI. Even via API, the interest is not journaled and the FD ledger goes negative. |
| **FD maturity & payout** | Batch only flips ACTIVE→MATURED; the customer must click Close to be paid. `runDaily` (demo-clock path) loses its transaction, so a failure mid-batch can leave a MATURED FD with no journal that **can never be closed**. Officers can pass a future `businessDate` to force early maturity. | Money can get stuck; officers can accelerate payouts; nothing is paid automatically; no notification. |
| **Savings→FD funding** | Best-built path: atomic, idempotent, locked, journaled. Fresh-stack caveat: seeded products/currencies don't reach the FD service until a write publishes a Kafka snapshot. | Works once reference data has propagated; on a clean start booking fails until an officer re-saves/activates a product. |
| **Interest & transaction history** | Daily compounding implemented (formula executed and cross-checked); no scheduler; daily accruals not journaled; premature interest not journaled; payout frequency never executed; no statements. | History exists for FD events but the books don't tie out. |
| **Forex** | Only a request log with an admin markup. No conversion, rate, accounts or ledger. UI honestly says "Indicative requests only". | Cannot "convert" currency; not in manual. |
| **Onboarding & KYC** | Signup works; KYC auto-verifies from nine format-valid fields (no human review); officers can set any KYC/risk value with no audit; ID/document stored unencrypted. | KYC gives a false sense of control but gates FD/loan/forex. |
| **Loans** | Applications lack occupation/income/obligations/documents; disbursement mints money with no receivable, schedule or repayment. | Not a credible lending workflow. |
| **Auth & access control** | Sound core (bcrypt, JWT, roles, ownership). But revoked/suspended tokens keep working on Product and FD for ≤24 h; no rate limiting; teller ops unlimited. | Logout/suspend doesn't fully log out. |
| **Frontend/backend integration** | Every UI call is routed; but premature-closure wiring is wrong, SPA `/fd/` collides with proxy, prod build points at `localhost:8080`, 401 doesn't redirect, no screens for calculator/reports/batch/MFA/reset. | Several backend features are invisible; one critical flow is mis-wired. |
| **Reporting & batch jobs** | JSON reports only, wrong paths vs manual, no CSV/Python/UI; no scheduler. | Reports module (5 marks) and batch criterion are weak. |
| **Deployment & startup** | Compose only; new compose/Dockerfiles uncommitted; PowerShell-only setup; JWT placeholder not base64; services build migrations from the "legacy" folder; no healthchecks/k8s/Azure/CI. | A fresh clone on macOS probably cannot start the stack. |

**High-risk unknowns (need runtime):** actual 401/403 behaviour for anonymous requests; whether a clean `docker compose up` produces a working schema; Kafka snapshot timing; concurrent duplicate idempotency key behaviour (possible 500); gateway routing under nginx.

---

## 2. Severity Classification

P0 Critical (money creation/loss, wrong balances, duplicate payouts, unauthorized financial action, demo-blocking core failure) · P1 High · P2 Medium · P3 Low. A "**potential critical risk — verification required**" label is used where severity would be P0 but the path was only read, not executed. Under this rule everything marked P0 below that lacks runtime evidence is labelled **P0 (static)**.

---

## 3. Defect Register (consolidated; duplicates merged)

Columns: Evidence cites the underlying finding IDs and file:line (see evidence files). "Conf." = root-cause confidence. Effort: XS <½d, S ≈½–1d, M 1–3d, L 3–5d.

| ID | Sev | Module | Defect | Evidence | Root cause (Conf.) | Proposed fix | Depends on | Test required | Effort |
|---|---|---|---|---|---|---|---|---|---|
| D-01 | **P0 (static)** | FD / Frontend | Customer cannot complete premature withdrawal: "Accept closure amount" calls staff-only approve → 403; no staff approval UI | FD-01, UI-01; `FdLifecycleController.java:63,69`; `fd-account-detail.html:92`; `fd.service.ts:158` | Two-person API workflow, UI wired to one role (confirmed-static) | Decide model (see §4B); implement customer `confirm` endpoint **or** staff "Premature closures" screen + read endpoint | D-05/D-06 data fixes in parallel | MockMvc: CUSTOMER confirm → 200 & status; staff self-approve → 403; UI spec | M |
| D-02 | **P0 (static)** | Savings / Security | Unbacked money creation: `APP_DEMO_OPENING_BALANCE_ENABLED=true` in compose; any customer opens unlimited accounts and credits arbitrary amounts; no journal, no KYC, no cap | CL-01, SE-004, CL-06; `docker-compose.yml:156`; `SavingsAccountController.java:52-71,80-99` | Demo feature on by default, no contra entry (confirmed-static) | Flag off by default (override file); role-restrict or one-off seed script; cap; KYC; journal Dr DEMO_SUSPENSE / Cr SAVINGS; account-count cap | none (do first) | flag=false→404; customer→403; journal balanced | S |
| D-03 | **P0 (static)** | Batch / FD | Daily batch via demo clock is non-transactional (Spring self-invocation); failure after MATURED update leaves FD with no journal → `close` fails forever; one bad account aborts all; `accountsFailed` hard-coded 0 | FD-04, CL-05, A1-010; `FdBatchService.java:19-25,27,36,51-60` | Proxy bypass (confirmed-static) | Per-account `REQUIRES_NEW` bean method, per-account catch, count failures, status update last, `SKIP LOCKED` | none | Inject failure on account 2 of 3; assert 1 & 3 mature, 2 unchanged & reported | S–M |
| D-04 | **P0 (static)** | FD / Authorization | Any BANK_OFFICER can call accrual/maturity batch with arbitrary `businessDate` (e.g. 2099) → FDs mature early and are payable | FD-05, CL-07; `FdLifecycleController.java:30-34` | Date not bounded by `clock.businessDate()` (confirmed-static) | Reject future dates; restrict to ADMIN; audit-log invoker | none | Officer + future date → 400/403 | XS |
| D-05 | P1 | Ledger | Premature closure pays principal+interest but posts no INTEREST_EXPENSE journal; FIXED_DEPOSIT account goes net-negative; no closing accrual row | FD-07, CL-03; `FdLifecycleController.java:118-134` vs `FdBatchService.java:52-58` | Interest journal only exists on maturity path (confirmed-static) | Post `INTEREST:<id>:PREMATURE` before settlement | D-14 ideally | After closure Σ FIXED_DEPOSIT lines = 0 | S |
| D-06 | P1 | FD / Config | Premature closure → 409 "not configured" for products without `CLOSURE_PENALTY`; no seed provides one | FD-02; `FdLifecycleController.java:51-52`; `FdOfferController.java:48-52` | Optional field, NULL = unsupported (confirmed-static; DB content UNVERIFIED) | Require field at offer create (bank-decided value, not invented) and show "not available" in UI | — | Offer w/o penalty → clear error; with → success | S |
| D-07 | P1 | Ledger | Journal model is partial: staff deposit/withdraw, demo opening, loan disbursement mutate balances without journals; no chart of accounts; header=Σlines not enforced; admin ledger incomplete | CL-02, CL-14, CL-17 | Ledger designed around FD only (confirmed-static) | `PostingService` for all money paths + `chart_of_accounts` + Flyway V11 + reconciliation endpoint | D-02 | Property test: per-op Σdr=Σcr and Σ SAVINGS deltas = Δ balances | M |
| D-08 | P1 | Savings | No account-to-account transfer in active stack | CL-08, QA-01; nginx has no route | Legacy not ported (confirmed-static) | Implement atomic, idempotent transfer (§4A design) | D-07 | 20 concurrent debits; A→B/B→A no deadlock; replay; rollback | M |
| D-09 | P1 | Fresh start | Seeded products/currencies never reach FD service (snapshot only on write); first booking fails | AR-03 | No startup republish (probable; runtime UNVERIFIED) | Startup full-snapshot republish or on-demand sync/backfill | D-12 | Clean compose → book FD without manual re-save | S–M |
| D-10 | P1 | Deployment | Compose modified & frontend/gateway Dockerfiles untracked; HEAD compose lacks frontend; fresh clone can't build the current stack | AR-01 (git status) | Uncommitted work (confirmed) | Commit; verify clean clone | — | Clean clone + `docker compose up` smoke | XS |
| D-11 | P1 | Deployment | `fd-account`/others build Flyway migrations from `BACKEND/…/service-migration`; README says otherwise; root `0x_*.sql` stale (V1 only, DECIMAL(18,4), no loan/forex tables) | AR-02, CL-13, A1-024, KY-11 | Migrations never moved (confirmed-static) | Move into each service's `src/main/resources/db/migration` or regenerate root SQL; CI diff | — | Clean DB + Flyway = expected schema | S |
| D-12 | P1 | Deployment | `JWT_SECRET` must be base64 but `.env.example` placeholder is not; `DEMO_ADMIN_*` missing from template; only PowerShell setup | AR-07, AR-08 | Template/script drift (confirmed-static) | Fix template; add `scripts/*.sh` | — | New dev on macOS reaches login | S |
| D-13 | P1 | Calculator | No combined category cap, single category, hard-coded +0.50/+0.75, not CUSTOMER-only, not product-linked, rate in percent vs products' fractions, `double` | A1-002/016, FD-11, QA-12 | Not implemented (confirmed-static) | Rate-matrix + cap + multi-category; reuse `FdCompounding`; `@PreAuthorize` | — | Manual table rows 6.5+2.0=8.5 capped | M |
| D-14 | P1 | Frontend | No UI for calculator, reports, batch triggers, MFA, password reset | A1-003, UI-04, KY-08/09 | Never built | Add screens | D-13, D-23 | Component specs + E2E | M–L |
| D-15 | P1 | Batch | No scheduler in active stack; no run logs | A1-001, FD-15 | Not ported from legacy | `@Scheduled` calling `runDaily(clock.businessDate())` + structured logs (conditional for demo clock) | D-03 | Cron test with fixed clock | S |
| D-16 | P1 | FD | Maturity doesn't pay out; no staff manual close; no notification | FD-13, A1-009, FD-18 | Design choice vs manual L18/L19 | Auto-settle in batch **or** visible step + notification | D-03, D-07 | Maturity → CLOSED → savings credit; rerun no dup | M |
| D-17 | P1 | Security | Revoked/suspended/logged-out tokens still valid on Product & FD for ≤24 h | SE-001, A1-011, AR-11 | Only Identity checks `tokenVersion`/status | Shared check (introspection/short TTL/Kafka user status) | — | Logout → 401 on FD & Product | M |
| D-18 | P1 | KYC | Auto-verify from format-valid fields → VERIFIED instantly; unlocks FD/loan/forex; document optional | KY-01, SE-002 | By design | Human-review state machine (§7) | — | pending→blocked; officer verify→allowed | M |
| D-19 | P1 | KYC / Security | Any officer sets arbitrary KYC/risk strings; no enum, no audit, PATCH on non-existent id | KY-02, SE-007 | No validation/audit | Enum + transition rules + audit + four-eyes | D-18 | invalid status → 400; audit row | S |
| D-20 | P1 | Teller | Staff deposit/withdraw unlimited, no dual control, no actor in ledger, no customer-status check | SE-003, CL-14 | No controls | Limits, maker-checker, remark, actor, status check | D-07 | Over-limit → 403/202-pending | S–M |
| D-21 | P1 | Loan | Disbursement creates funds with no receivable/schedule/repayment; same admin approves & disburses; no KYC re-check | LN-01/03/04, CL-04 | Module stops at disbursement | §8 minimal lending plan | D-07, D-18 | Disburse + repay → receivable 0 | L |
| D-22 | P1 | Docs | No architecture/ER/deployment diagrams, test plan, solution doc; Postman/OpenAPI stale | A1-012, QA-06 | Not produced | Produce (Report 1 §4) | — | Review checklist | M |
| D-23 | P1 | Reports | Paths/format differ from L21; no CSV/Python; no product grouping; no UI | A1-018, FD-16, T-04 | Different design | Alias + CSV + Python script + UI | D-14 | Report totals = DB aggregates | M |
| D-24 | P1 | Statements/Alerts | `fd_statements`, `notifications_log` never written; no email/SMS/WhatsApp | A1-004/005, FD-15 | Not implemented | Statement job + mock notification sink | D-15 | Maturity run → statement + log row | M |
| D-25 | P1 | DevOps | No k8s, Azure, HTTPS, probes, scaling, CI | A1-013, AR-09/10 | Not produced | Minimal manifests + probes + CI + Azure guide | D-10..12 | `kubectl apply --dry-run`, CI green | M–L |
| D-26 | P1 | QA | Integration tests skip silently without Docker; key FD money scenarios untested; frontend specs can't run; Postman dead | QA-02/03/04/05/06 | No CI, gated tests | Fail-fast option, add scenarios, test target | — | Meta: CI run shows non-skipped count | M |
| D-27 | P2 | FD | Quote frozen at request & paid unchanged; accrual vs quote formulas differ (3471.23 vs 3501.36 on day 181); penalty = rate points on simple interest vs manual's % of interest | FD-06, FD-08, CL-09/10 | Legacy design carried over | Recompute at approval or expire; decide formula & document | D-01 | Request → advance clock → approve uses approval date | S–M |
| D-28 | P2 | FD | Penalty read from live product, not booked terms | FD-03 | Missing in terms snapshot | Add to `fa_terms_snapshot` | — | Product edit after booking doesn't change penalty | S |
| D-29 | P2 | FD / UI | UI offers premature closure for MATURED (409); PENDING closure can't be rejected/cancelled | FD-09, FD-10 | — | Remove action; add REJECTED/CANCELLED + endpoint | D-01 | UI unit + reject then re-request | S–M |
| D-30 | P2 | Savings | Unlimited accounts per holder/currency; no KYC on opening; uniqueness constraint includes PK | CL-06, S-4 | — | Cap + KYC + unique | D-02 | 6th account → 409 | S |
| D-31 | P2 | Forex | Request log only: no rate, conversion, accounts, ledger, processing | FK-01..06 | By design (indicative) | Simulated, labelled rate table + same-currency-pair conversion between two savings accounts (§4D) | D-07, D-08 | direct/inverse/rounding | M |
| D-32 | P2 | Loan | Application lacks occupation/income/obligations/documents | LN-02 | — | Fields end-to-end (§8) | D-18 | Mandatory-field validation | M |
| D-33 | P2 | KYC | No duplicate prevention (email/phone/ID); signup drops address/city/postal/branch | KY-04/06 | — | Unique indexes + DTO/DB columns | — | dup → 409 | S |
| D-34 | P2 | Gateway/Kafka | No dead-letter handling; outbox publishers stop at first failed row, no multi-instance safety; consumers/clock inconsistent (product uses real date Asia/Kolkata vs FD demo clock) | AR-04/05/14 | — | DLQ/skip-and-log; `SKIP LOCKED`; clock unification | D-09 | Poison message doesn't stall consumer | M |
| D-35 | P2 | Ops | No service healthchecks; gateway/frontend start on `service_started`; no correlation IDs | AR-10/13 | — | Healthchecks, `service_healthy`, MDC trace id | — | Cold start no 502 window | S |
| D-36 | P2 | Security | MFA challenge token accepted as bearer by Product; customers can list DRAFT/INACTIVE products; published ports (DBs, Kafka, kafka-ui) exposed; no rate limiting | SE-005/005b/006/009 | — | Token typing; filter; bind to 127.0.0.1 or profile; gateway `limit_req` | — | negative auth tests | S–M |
| D-37 | P2 | Frontend | Prod env hard-codes `http://localhost:8080`; FE nginx proxies `/fd/` colliding with SPA routes; 401 doesn't redirect, no 403 handling, stale `username/roles` in storage; KYC upload >1 MB blocked by nginx default | UI-03/05/06/13, AR-12 | — | Relative `/api`, path rename, interceptor redirect, `client_max_body_size` | — | Deep-link refresh works; expired token redirects | S |
| D-38 | P2 | Product | No product search by type/date; audit user stored as `'API'` | A1-014, A1-021 | — | Add filters; record principal | — | search & audit assertions | S |
| D-39 | P2 | Planning docs | `.planning`/`CONTEXT.md`/`AGENTS.md` describe PostgreSQL legacy & 100 % complete; verification covers `BACKEND/` only | FD-17, A1-025, AR-06 | — | Re-baseline docs to services/* | — | doc review | S |
| D-40 | P3 | Misc | Idempotent hash differences (100 vs 100.00); `fd()` read paths use `FOR UPDATE`; admin ledger N+1 & inner joins; savings INACTIVE unreachable; calculator unknown basis → QUARTERLY; test oracle self-referential; i18n check fails (4 strings); bundle over budget; accessibility `outline:none`; login state disclosure; 403 vs 401 | CL-12/15/16, QA-11/13, UI-07..15, SE-010..022 | — | batch of small fixes | — | per item | S each |

---

## 4. Root-Cause Analysis of the Major Broken Workflows

### 4A. CASA transfers
1. **Expected:** customer moves funds A→B (same currency); A debited, B credited by the same amount, both histories show it, balances refresh.
2. **Actual:** no feature. `grep transfer services/**/*.java` finds only product pricing facts; `nginx.conf` has no `/transfer`; `fd.service.ts` and `staff-operations.service.ts` call no transfer API; `location /` returns 404. The legacy monolith had it (`BACKEND/.../fd/application/SavingsTransferService.java:47-142`, V31).
3. **Code paths:** none in active stack.
4. **Repro:** open the savings page; there is no transfer control. `curl -i $GW/api/v1/savings-accounts/transfers` → 404 (UNVERIFIED — see Appendix R).
5. **Root cause:** monolith → microservice split dropped the feature (CONFIRMED-STATIC).
6. **Impact:** demo gap; no money-integrity risk because it doesn't exist.
7. **Changes (atomic, idempotent design, in the style of existing JdbcTemplate code):**
   - `POST /api/v1/savings-accounts/transfers` with required `Idempotency-Key`; body `{fromAccountId, toAccountNumber, amount, remark}`; owner check on `from`; `requireVerified`.
   - One `@Transactional`: `claim(...)` on `fd_operation_idempotency` (hash canonicalised with `stripTrailingZeros`); resolve destination; reject same account, non-ACTIVE, **different currency** (no FX), FD numbers, inactive destination customer.
   - Lock both rows **in ascending id order** in one statement (`… WHERE sa_account_id IN (?,?) ORDER BY sa_account_id FOR UPDATE`); re-validate balance; `setScale(currencyScale, UNNECESSARY)`.
   - Insert two `savings_transactions` rows sharing `st_reference_id` (`TRF-<uuid>`; `uq_savings_transaction_reference_side` already enforces one per side) with correct `st_balance_after`.
   - Post journal `TRANSFER:<ref>` Dr SAVINGS(source)/Cr SAVINGS(dest) — requires per-line account id (see D-07).
   - Limits per transaction/day; audit event; return reference + new source balance.
8. **DB:** Flyway V11 — add `TRANSFER` to `fj_operation` CHECK; per-line `jl_savings_account_id` (or make `fj_savings_account_id` nullable); optional `chart_of_accounts`.
9. **Frontend:** transfer form on savings page; refresh both accounts and history after success; disable button while pending; reuse the stored idempotency key on retry (existing `busyAction` pattern).
10. **Acceptance:** (a) A→B and B→A parallel complete without deadlock; (b) 20 concurrent debits of 10 from balance 100 → exactly 10 successes, balance 0; (c) same key twice → one posting, same response; (d) same key + different amount → 409; (e) fault injected after step 6 → nothing persisted; (f) Σ balances invariant preserved.
- **Can money be debited without credit?** Not today (no feature). With the design above, no — both rows and both balances are in one transaction. **Do transaction records exist?** Not applicable yet.

### 4B. Premature FD withdrawal (the 12 points; full detail in `evidence/agent4-fd-lifecycle.md` §2)
| # | Question | Finding |
|---|---|---|
| 1 | Endpoint exists/reachable? | Yes, as request + approve (`FdLifecycleController.java:46,63`), both routed (nginx 75, 78). **Manual's single `POST /api/fd/account/withdraw` does not exist.** |
| 2 | Correct service? | Yes, fd-account-service; penalty data arrives via Kafka snapshot into `fd_product_currency_ref.premature_penalty_points`. |
| 3 | Ownership/eligibility? | Request checks owner + ACTIVE + no existing PENDING; approve requires staff, ACTIVE, PENDING closure, approver ≠ owner. Officer cannot initiate on behalf of customer; MATURED refused. |
| 4 | Accrued interest to correct date? | Date = business date clamped to [open, maturity]. Interest = **simple on principal**, not the compounded accrual (day-181 example 100,000 @7% quarterly: quote basis 3,471.23 vs accrual table 3,501.36). |
| 5 | Product-specific rules? | Penalty points read live from product ref, **not** from booked terms; NULL → 409 "not configured"; no percentage invented (none exists in repo). |
| 6 | Penalty calc? | Penalised rate = max(0, rate − points/100); paid = P × penalised rate × days/365; "penalty" = foregone interest. Differs from manual's "reduce accrued interest by x %". |
| 7 | Payout amount? | Stored at request time, paid unchanged at approval (stale). |
| 8 | Close only on success? | Yes — single `@Transactional`; status set after credit. |
| 9 | Destination? | The FD's funding savings account (owner, same currency, ACTIVE). Manual's `transferAccount` choice unsupported (safer). |
| 10 | Txn & audit records? | CREDIT row, `FD_PENALTY`, `FD_CLOSURE`, SETTLEMENT journal, event, `fpc_approved_by`, idempotency row. **Missing: interest journal; closing accrual row.** |
| 11 | Rollback? | Yes for request and approve. |
| 12 | Duplicate payout protection? | Strong: idempotency key, status ≠ ACTIVE → 409, row lock + PENDING, journal key `SETTLEMENT:<id>`. Residual: concurrent duplicate key may 500 (FD-14, UNVERIFIED). |

- **Why it "doesn't work" (ranked):** (a) customer UI → 403 (D-01); (b) product without penalty rule → 409 (D-06); (c) FD already MATURED but UI offers it → 409 (D-29); (d) stuck PENDING request (D-29).
- **Recommended change (smallest safe):** pick the model by lab rule. Manual L15 says "customer or authorized bank officer" and a single step. Option **B (recommended for lab fidelity):** add `POST /api/v1/fixed-deposits/{id}/premature-closure/{cid}/confirm` (owner) that recomputes at *today's* business date and runs `settle`; keep the staff approve endpoint for officer-initiated closures; add officer-initiated request. Option A (keep four-eyes): staff screen to list/approve/reject. Either way: recompute at settle; post `INTEREST:<id>:PREMATURE`; require penalty config at offer creation; store penalty in booked terms; add REJECTED/CANCELLED.
- **Acceptance:** customer closes an eligible FD in the UI; payout = principal + reduced interest − penalty per the documented formula; savings credited once; FD `PREMATURELY_CLOSED`; Σ FIXED_DEPOSIT journal lines for that FD = 0; replay with same key returns same result; second attempt → 409; MATURED/CLOSED FD → clear 409 and UI hides the action.

### 4C. FD maturity
1. **Expected (manual L18/L19):** batch finds ACTIVE with `maturity_date ≤ today`, computes principal+interest, records `MATURITY_PAYOUT`, credits savings, closes, notifies; re-run does nothing.
2. **Actual:** `FdBatchService.mature` sets MATURED, books interest journal (`INTEREST:<id>:MATURITY`), writes `FD_MATURITY` txn; **no savings credit**; customer calls `/close` (`settle`) later.
3. **Selection & duplicate protection (good):** `status='ACTIVE' AND DATE(fa_maturity_date) <= ?`, row re-check under lock, guarded UPDATE, unique journal key.
4. **Defects:** D-03 (self-invocation, no per-account isolation), D-04 (future date), D-16 (no payout/notification), no scheduler (D-15), `verifyMaturityJournals` blocks close of half-matured FDs permanently.
5. **Fix:** per-account `REQUIRES_NEW`; status update last; optionally auto-settle in same per-account transaction; cap date; notify.
6. **Acceptance:** run maturity twice → exactly one `FD_MATURITY` and one `SETTLEMENT`; savings balance equals prior + principal + interest; status CLOSED; failure injected on one FD does not affect others; statement + notification rows exist.

### 4D. Forex
- **Expected (user):** pick currencies, resolve rate, calculate conversion and fee, execute between two accounts, verify balances & history. (Manual: not required.)
- **Actual:** `POST /api/v1/forex-requests` stores a request with `fr_fee` = 0 and a configured markup; no rate, no accounts, no processing/cancel (FK-01..03). Failure locus: **missing feature**, not a UI/gateway/DB fault.
- **Smallest safe solution (demo-safe, honest):** table `fx_rate(base, quote, rate, source='SIMULATED', valid_from, valid_to)` seeded and *labelled "Simulated rate – not a market rate"* in API and UI; direction convention `1 base = rate quote`; inverse computed explicitly as `1/rate` with documented rounding (`HALF_EVEN`, currency scale); staleness window; unsupported pair → 422; execution between a customer's two accounts of different currencies in one transaction with a balanced journal using FX position accounts (Dr SAVINGS(src ccy)/Cr FX_POSITION(src), Dr FX_POSITION(dst)/Cr SAVINGS(dst ccy)); fee as separate fee journal line; idempotency key.
- **Acceptance:** direct & inverse conversion golden numbers; unsupported pair rejected; stale rate rejected; mid-operation failure leaves both balances unchanged; per-currency journal balance holds (no cross-currency comparison without a conversion model).

### 4E. KYC and onboarding
See §7.

### 4F. Loans
See §8.

### 4G. UI and integration defects
See §9 and `evidence/agent6-frontend.md` (UI-01…UI-15).

---

## 5. Correct Business Workflows and Financial Invariants

### 5A. Ledger model assessment (dedicated)
- **Verdict:** a *partial balanced-journal model* (FD paths only) layered over a *single-sided customer statement table*. Not a genuine double-entry ledger.
- **Per-operation expectation vs reality** (recommended mapping = proposal, not existing implementation):

| Operation | Today | Recommended entries (proposal) |
|---|---|---|
| Customer/teller deposit | balance + `savings_transactions` only | Dr CASH_TELLER / Cr SAVINGS |
| Withdrawal | same | Dr SAVINGS / Cr CASH_TELLER |
| Demo opening credit | balance only | Dr DEMO_SUSPENSE / Cr SAVINGS (plus banner) |
| Transfer | missing | Dr SAVINGS(A) / Cr SAVINGS(B) |
| FD funding | ✅ Dr SAVINGS / Cr FIXED_DEPOSIT | unchanged |
| Interest accrual | `fd_interest_accrual` only | Dr INTEREST_EXPENSE / Cr ACCRUED_INTEREST_PAYABLE (optional for lab) |
| Maturity interest | ✅ Dr INTEREST_EXPENSE / Cr FIXED_DEPOSIT | unchanged |
| Settlement (maturity) | ✅ Dr FIXED_DEPOSIT / Cr SAVINGS | unchanged |
| Premature closure | settlement only; **no interest journal** | add Dr INTEREST_EXPENSE / Cr FIXED_DEPOSIT for paid interest; penalty = foregone interest, memo only |
| Fees/penalty fees | none | Dr SAVINGS / Cr FEE_INCOME |
| Forex | none | see §4D |
| Loan disbursement/repayment | disbursement credit, no journal | Dr LOAN_RECEIVABLE / Cr SAVINGS; repayment reverse split principal/interest income |
| Reversal/correction | none | contra-journal referencing original `fj_key`; no UPDATE/DELETE |

- **Required invariants/controls missing:** header Σlines check in code or trigger; chart of accounts table + FK; reconciliation endpoint (balances vs statement vs journals); immutability; actor columns (`created_by`).
- **Reconciliation SQL** (to run after any test pass): see `evidence/agent3-casa-ledger-db.md` §7 V3.
- **Duplicate/double ledger:** none found; `savings_transactions` and `fd_journals` do not contradict each other but only the second is balanced.

### 5B. Invariants (adapted to this architecture)
**CASA transfer:** debit = credit (no fee modelled) · both `savings_transactions` rows share one reference · failure leaves no unaccounted debit (single local DB transaction — same service/DB) · same `Idempotency-Key` + same hash replays, different hash → 409 · concurrent debits cannot overspend (row locks + CHECK).
**FD funding:** validate source, product, amount, currency, tenure · FD `ACTIVE` ⇔ funding journal + `FD_FUNDING` txn + savings debit exist · all in one DB transaction (✅ today).
**Premature withdrawal:** owner/officer, ACTIVE, product terms from *booked* snapshot · accrued interest as of settlement date · net payout rounded once at currency scale · FD `PREMATURELY_CLOSED` only with savings credit + all journals · exactly-once via idempotency + status + journal key.
**Maturity:** only ACTIVE with maturity ≤ business date (not caller-supplied beyond clock) · interest journal + settlement journal exist before CLOSED · rerun is a no-op · destination balance = old + settlement.
**Forex:** per-currency journal balance · rate direction, timestamp, source labelled · fees explicit · no live-rate claim.
**KYC:** status persisted with actor/time/reason · backend enforces gate on FD book/fund, loan, forex, savings opening · UI shows next step without exposing sensitive data.
**Cross-service consistency:** Identity/Product → FD uses Kafka outbox snapshots (eventually consistent, replicas with version guard). Money operations are *local ACID inside fd-account-service*, so no distributed transaction is needed. Recommend: republish-on-start for replicas (D-09), DLQ + skip-and-log (D-34), and a periodic reconciliation (not a saga) comparing replica status with source. A saga/outbox for money is **not** justified here because all balance-changing operations live in one service and one database.

---

## 6. Architecture and Implementation Plan (P0/P1 items)

| Item | Files likely to change | API / DB / config | Failure & idempotency | Frontend | Tests |
|---|---|---|---|---|---|
| D-02 money creation | `SavingsAccountController`, `docker-compose.yml`, new `docker-compose.demo.yml`, `StaffAccountController` | flag off; optional Flyway V11 `DEMO_OPENING` journal | idempotency exists | demo banner | flag/role/cap/journal tests |
| D-07 ledger | new `PostingService`, `StaffAccountController`, `SavingsAccountController`, `LoanForexController`, `FixedDepositController`, `FdBatchService`, `FdLifecycleController`, `FdLedgerAdminController`; Flyway V11 | new `chart_of_accounts`, CHECKs; nullable/per-line account id | `fj_key` unique, same txn as balance update | ledger admin UI shows all journals | invariant & reconciliation tests |
| D-01/05/06/27/28/29 premature | `FdLifecycleController`, `FixedDepositController` (terms snapshot), `FdOfferController`, `ReferenceSnapshotConsumer`; Flyway (closure status REJECTED/CANCELLED) | new `confirm` endpoint; V11 CHECK change for `fpc_status` | idempotency key + journal key | `fd-account-detail` actions; staff closure screen | MockMvc roles; numeric golden; replay |
| D-03/04/15/16 batch & maturity | `FdBatchService` (+ new `FdBatchWorker` bean), `FdLifecycleController`, new scheduler | date bound; conditional `@Scheduled`; logs | `REQUIRES_NEW` per account; `SKIP LOCKED` | clock dashboard status | injected-failure, rerun, future-date tests |
| D-09/10/11/12 startup | `docker-compose.yml`, Dockerfiles, `pom.xml` resources, scripts | move migrations; fix `.env.example`; bash scripts | n/a | env relative API | clean-clone smoke script |
| D-17 revocation | `FDC/JwtFilter`, `PRD/JwtFilter`, Identity token endpoint/Kafka user status | shared check | cache with TTL | 401 redirect | logout → 401 tests |
| D-18/19 KYC | `KycService`, `KycController`, `CustomerAdminController`; Flyway customer V-next | new statuses/audit table | optimistic status transitions | KYC pending screen + staff review | state machine tests |

Cross-service money operations: none require sagas (single-service ACID). Where Product/Identity state gates a money operation, use idempotent re-check of the local replica and fail closed when the replica is missing.

---

## 7. KYC Implementation Plan (demo-safe)

**Principle:** use synthetic identities only; never collect real Aadhaar/PAN; no real identity-verification provider in scope.

- **Status machine:** `NOT_STARTED → DRAFT → PENDING_REVIEW → VERIFIED | REJECTED(reason) ; VERIFIED → RE_KYC_REQUIRED → PENDING_REVIEW`. Only `BANK_OFFICER`/`ADMIN` may move `PENDING_REVIEW → VERIFIED/REJECTED`; a customer can never move to VERIFIED.
- **Minimum data:** legal name, DOB, address, city, postal code, home branch (currently dropped — KY-06), nationality/residency (optional), government-ID *type* and *last 4* only for display, ID number stored **encrypted** (AES-GCM, key separate from JWT secret) or hashed for duplicate detection, one document upload (synthetic fixture), risk rating (enum LOW/MEDIUM/HIGH) set by staff only.
- **Tables/migration (Flyway customer V-next):** `kyc_profile` add `status` CHECK, `reviewed_by`, `reviewed_at`, `reject_reason`, `id_number_hash` UNIQUE, `id_number_enc`; new `kyc_audit(id, customer_id, from_status, to_status, actor, reason, at)`; unique indexes on email/phone. Existing rows: map current VERIFIED to VERIFIED with `reviewed_by='MIGRATION'`; rollback by keeping old column until a release later.
- **API:** `GET/PUT /api/v1/kyc/profile` (draft), `POST /api/v1/kyc/submit`, `GET /api/v1/staff/kyc?status=PENDING_REVIEW`, `POST /api/v1/staff/kyc/{customerId}/decision {decision, reason}` (audited), `GET` document for staff via authorised stream.
- **Gating (backend, not UI):** savings opening, FD booking/funding, loan apply/approve/disburse, forex request require `VERIFIED` and risk ≠ HIGH; checked on the Kafka user snapshot replica and **re-checked at disbursement**. Read-only features stay open.
- **Screens:** customer: completion ring + "Pending review" state; staff: review queue with masked ID, document viewer, approve/reject with reason.
- **Validation:** keep existing format validators (`KycValidation`), add Luhn/Verhoeff only if the demo needs it; duplicates → 409.
- **Tests:** verified / pending / rejected / incomplete; officer-only decision (customer → 403); duplicate ID → 409; gate on each money endpoint; audit rows; masked responses; concurrency race (existing test pattern).
- **Future real provider:** replace the staff decision with a provider webhook writing the same `kyc_audit` rows.

---

## 8. Loan Workflow Plan

**Scope status:** beyond the lab manual; present in code (existing-but-incomplete). Minimum viable plan:

| Field | Frontend | DTO | Validation | DB | Today |
|---|---|---|---|---|---|
| Product, currency, amount, tenure | ✅ | ✅ | ✅ | ✅ | persisted |
| Purpose | ✅/verify | verify | enum | verify | check |
| Occupation / employment type | ❌ | ❌ | — | ❌ | **missing (LN-02)** |
| Employer/business name | ❌ | ❌ | — | ❌ | missing |
| Monthly income & frequency | ❌ | ❌ | `> 0` | ❌ | missing |
| Existing monthly obligations | ❌ | ❌ | `≥ 0` | ❌ | missing |
| Supporting document metadata | ❌ | ❌ | type/size | ❌ | missing |
| Approved amount | — | = requested always | ≤ requested | `la_approved_amount` | redundant (LN-06) |

Flow: Applicant (KYC VERIFIED) → occupation & income → validations (amount ≤ product max, tenure within band, simple affordability `EMI ≤ 50 % of net income` *only if the bank decides this rule — do not invent*) → submit → review (officer) → approve/reject with reason → **different admin** disburses → schedule + receivable journal → repayment endpoint. Optional (not minimum): credit scoring, collateral, e-sign.
Required changes: Flyway V11 columns; DTO/validation; UI form; approval role split (maker-checker); journal (Dr LOAN_RECEIVABLE / Cr SAVINGS); schedule table; `repay` endpoint (Dr SAVINGS / Cr LOAN_RECEIVABLE + interest income); KYC/risk re-check at approve and disburse. If time-boxed: ship the **application** half only (fields → persist → review) and label disbursement/repayment as "simulated, not implemented", and disable disbursement to avoid unbacked money.

---

## 9. UI/UX Remediation Plan

Frontend register (full list: `evidence/agent6-frontend.md`). Priority order: broken workflows → misleading data → missing errors → cosmetic.

| ID | Route | Viewport | Current | Expected | Cause / fix | Regression test |
|---|---|---|---|---|---|---|
| UI-01 | `/fd/accounts/:id` | all | "Accept closure amount" → 403 | Closure completes (or shows "awaiting officer") | wiring (D-01) | Component + API contract test |
| UI-03 | FE nginx | all | refresh on `/fd/…` returns gateway 404 JSON | SPA fallback | rename proxy location or `try_files` ordering | Playwright deep-link |
| UI-05 | all auth | all | 401 logs out but no redirect; no expiry check; no 403 handling | redirect to `/login?returnUrl`, toast on 403 | interceptor | unit |
| UI-13 | prod build | all | API base `http://localhost:8080` baked in | relative `/api` via FE nginx | `environment.production.ts` | build + curl |
| UI-04 | `/login` | all | MFA/forgot-password absent | pages exist | add screens | E2E |
| UI-02 | FD detail | all | penalty only shown after request | show quote before commit | add quote preview endpoint | UI test |
| UI-08 | FD/savings | all | raw dates; fixed 2-dec formatting (JPY/KWD wrong) | currency-aware `Intl` formatting | pipe | unit |
| UI-09 | forms | all | duplicate-submit guard not found on loans/forex/KYC | disable on submit | `busy` flag pattern | unit |
| UI-10 | global | all | `outline:none` on glass inputs; low-contrast gold-on-glass | visible focus, AA contrast | CSS | axe |
| UI-11 | `/login` | 375 px | card clipped (UNVERIFIED; may be headless minimum width) | no overflow | verify in real device emulation | screenshot test |
| UI-12 | build | — | specs can't run; i18n check fails; bundle +325 kB | `ng test` target; 4 strings; lazy-load three.js landing | angular.json | CI |
| UI-14 | `/dashboard` | all | link hub only | balances/summary | new widgets | — |

Design recommendations (consistent system): 4/8 px spacing scale; one type scale (display/title/body/caption) in SCSS tokens; max content width 1200 px with 16 px mobile gutter; tables become stacked cards <640 px; status chips (ACTIVE green / PENDING amber / MATURED blue / CLOSED grey / PREMATURELY_CLOSED red); every async action: disabled button + spinner + success toast + inline error with `role="alert"`; always refetch account, balances and history after a mutating call.

---

## 10. Test Strategy & Release Gates

| Level | Scope | Tooling (existing/new) |
|---|---|---|
| Unit | `FdCompounding`, penalty formula, rounding, calculator cap | JUnit (exists); add golden tables (e.g. 100,000 @ 7 % quarterly 12 m = 107,185.90; day-181 premature numbers in `evidence/agent4-fd-lifecycle.md` §7: simple interest 3,471.23 vs accrual-table 3,501.36) |
| Repository/DB integration | Flyway migrations on clean MySQL, constraints | Testcontainers (CI with Docker; fail instead of skip) |
| API/contract | gateway path ↔ controller ↔ frontend service | MockMvc + a contract test generating the route table from `nginx.conf` |
| Service-to-service | Kafka snapshot → FD replica; republish-on-start | Testcontainers Kafka |
| **Financial invariants** | per-op Σdr = Σcr; Σ SAVINGS deltas = Δ balances; per-FD FIXED_DEPOSIT nets to 0 when closed | reconciliation SQL as a test helper |
| Security/authorization | role × endpoint matrix; IDOR; revoked token; tampered/expired JWT; `alg=none` | MockMvc with real filter chain |
| Concurrency/idempotency | 20 parallel debits; A↔B transfers; duplicate keys concurrently | JUnit + ExecutorService |
| Batch | rerun idempotency; future date; failure isolation; schedule trigger | injected-failure beans |
| Frontend | component specs; responsive screenshots 375/768/1440; axe | Vitest/Karma + Playwright |
| E2E | Journeys A–I | Playwright against compose stack + SQL assertions |

**Critical-workflow test cards (precondition → input → expected API → expected DB/balances/history):**
1. *CASA transfer:* A=100, B=0, both KYC-verified ACTIVE same ccy → transfer 40 key K → 200; A=60, B=40; two `savings_transactions` same reference; one balanced `TRANSFER` journal; replay K → 200 same body, no new rows; amount 70 → 409 insufficient; same account → 400; unknown/closed dest → 404/409; cross-ccy → 422.
2. *FD open/fund:* offer 12 m 7 % min 1,000; savings 5,000 → book 2,000 → 201 PENDING; fund → 200 ACTIVE; savings 3,000; journal `FUNDING:<id>` Dr SAVINGS 2,000 / Cr FIXED_DEPOSIT 2,000; invalid tenure/product/currency/amount → 4xx and no rows.
3. *Premature closure:* ACTIVE FD, advance 181 days → customer confirm → payout per documented formula; savings credited once; status PREMATURELY_CLOSED; Σ FIXED_DEPOSIT = 0; INTEREST_EXPENSE = paid interest; replay → idempotent; second → 409; closed/MATURED → 409.
4. *Maturity:* advance past maturity → run twice → one `FD_MATURITY`, one `SETTLEMENT`; savings = prior + principal + interest; CLOSED; officer with future date → 400/403.
5. *Forex:* seeded simulated rate USD→INR 83.00 labelled SIMULATED; convert 100 → 8,300.00; inverse rounding `HALF_EVEN`; unsupported pair 422; stale rate 422; injected failure → both balances unchanged.
6. *KYC gate:* PENDING → FD booking 403; officer VERIFIED → allowed; REJECTED → blocked; customer cannot self-verify.
7. *Authorization:* customer B GET customer A FD/savings/loan → 404; customer on `/api/v1/admin/**` → 403; logout then use token on FD → 401.

**Release gate (demo-ready = all true):** D-01, D-02, D-03, D-04, D-05, D-06, D-07 (at least FD+savings journals), D-09, D-10, D-11, D-12, D-17 resolved **and** test cards 2, 3, 4, 7 pass in a clean-clone compose run **and** a reconciliation SQL run returns zero discrepancies after the full demo script. "Application starts" and "endpoints return 200" are explicitly not criteria.

---

## 11. Seven-Day Prioritized Action Plan

Assumes 1–2 engineers. **Not realistic in 7 days (state openly):** full lending module with repayment; real forex; k8s/Azure live deployment; complete a11y pass; full ledger/GL with chart of accounts for every flow (minimum viable ledger yes). Anything marked "documented limitation" below is an honest deferral, not a shortcut.

| Day | Priority tasks (owner/workstream) | Prerequisites | Exact objective & steps | Files | Acceptance / Definition of done | Effort |
|---|---|---|---|---|---|---|
| **1 — Baseline & unblock** | D-10, D-11, D-12, D-02 (DevOps + Backend) | Docker + JDK 17 + Maven machine | Commit compose/Dockerfiles; move migrations into services; fix `.env.example` (base64) + bash setup script; turn demo-opening flag off by default (override file); run clean-clone smoke; reproduce D-01..D-04 with curl | compose, Dockerfiles, poms, scripts | Clean clone → `docker compose up` → login works on macOS; defects reproduced and recorded | 1 d |
| **2 — Core financial correctness** | D-07 minimal (PostingService for FD+savings+demo), D-05, D-03, D-04 (Backend) | Day 1 | `PostingService`; interest journal on premature path; per-account `REQUIRES_NEW` batch; clamp `businessDate` | `FdBatchService`, `FdLifecycleController`, new service, Flyway V11 | Invariant & reconciliation tests green; injected-failure test green | 1.5 d |
| **3 — FD lifecycle** | D-01, D-06, D-27, D-28, D-29, D-16 (Backend+UI) | Day 2 | Customer confirm endpoint (or staff screen); penalty required & stored in booked terms; recompute at approval; reject/cancel; auto-settle or visible step at maturity | `FdLifecycleController`, `FdOfferController`, FD UI | Test cards 2–4 pass; replay/duplicate tests pass | 2 d |
| **4 — Forex & customer workflows** | D-08 (transfer), D-18/19/33 (KYC), D-32 (loan fields), D-31 (forex *only if in demo scope*) | Day 2 ledger | Transfer endpoint+UI; KYC review state machine; loan fields; simulated forex or documented as "indicative" | savings, kyc, loan, forex controllers | Test cards 1, 5(opt), 6 pass | 2 d |
| **5 — Integration & UI** | D-09, D-13, D-14 (calc UI + cap), D-17, D-37, UI fixes | Days 2–3 | Reference republish; calculator cap + screen; revocation check; env/nginx/401 fixes; responsive pass | FE + gateway | Deep links work; expired token redirects; calculator matches manual table | 1.5 d |
| **6 — Regression & reporting** | D-15, D-23, D-24, D-26 | Days 2–5 | Scheduler; reports aliases/CSV (+Python script); statements/notification log; CI with Docker; full E2E | reports, batch, tests | Journeys B–E, I pass with SQL assertions; reconciliation zero | 1.5 d |
| **7 — Demo readiness** | D-22, D-25 (docs only), rehearsal | All | Clean-environment full run; architecture/ER diagrams, test plan, demo guide, known-limitations | docs | Full demo from clean clone on macOS; release gate satisfied or gaps stated | 1 d |

Minimum viable demo scope if time runs short: Journeys A, C, D, E, H, I (+B if ledger done). Defer F (forex) and G (loans) with clear on-screen "indicative/not implemented" labels and **disable loan disbursement**. Never ship premature/maturity flows with known ledger corruption.

---

## 12. Recommended Implementation Order

1. **Immediately:** Prompt P01 (make a clean clone runnable) and P02 (turn off/limit demo money creation) — these unblock everything and remove the only confirmed money-creation path.
2. **Next five by impact:** P03 (ledger foundation) → P06 (premature withdrawal) → P07 (maturity/batch atomicity & date clamp) → P05 (FD creation/funding hardening) → P08 (token revocation).
3. **Parallel-safe:** P09 (KYC) · P10 (loan fields, application half) · P12 (gateway/Kafka robustness) · P14 (calculator) · P18 (docs) — they touch different services (once P03's schema migration is merged).
4. **Blocked until others land:** P04 transfer (needs P03) · P11 forex (needs P03, P04 patterns) · P13 reports/statements/alerts (needs P07 data) · P15 frontend integration (needs P06/P04 APIs) · P16 responsive pass · P17 full regression (needs everything).
5. **Do not start before core transaction integrity is fixed:** loan disbursement/repayment, forex execution, new reporting totals, UI polish.
6. **Can wait:** MFA/reset UI, i18n gaps, bundle size, savings freeze/close, N+1 queries, k8s/Azure manifests (document instead).
7. **Final acceptance (demo-ready):** release gate in §10 satisfied from a clean clone; reconciliation zero; Journeys A–E and H–I demonstrated end to end; limitations listed.

**What to do first when you open the repo:** (1) `git status` and commit the untracked Dockerfiles/compose (D-10); (2) set `APP_DEMO_OPENING_BALANCE_ENABLED` off and re-seed deliberately (D-02); (3) run the compose stack once on a Docker-capable machine and reproduce D-01 with the verification steps in `evidence/agent4-fd-lifecycle.md` §6 before changing code.

---

## 13. Demo Journeys — Status

| Journey | Required by lab? | Planned for demo | Status today | Missing links / critical defects | Tests required | Demonstrable today? |
|---|---|---|---|---|---|---|
| A Onboarding | Yes (L1–L4, PII) | Yes | Register/login/KYC form exist; KYC auto-verifies | D-18/19 (no review), D-33 | KYC cards | Probably yes (UNVERIFIED), but KYC is a rubber stamp |
| B CASA transfer | No | Expected | **Missing** | D-08, D-07 | Transfer card | **No** |
| C FD opening | Yes (L12/13/16) | Yes | Works once reference data propagated | D-09, A1-007/008 deviations | Card 2 | Conditionally (after reference data sync) |
| D Premature withdrawal | Yes (L15) | Yes | Backend yes; UI path 403 | D-01, D-05, D-06, D-27 | Card 3 | **No** (UI); API only with two users |
| E Maturity | Yes (L18/19) | Yes | Batch via admin/clock; customer Close | D-03, D-04, D-16, D-15 | Card 4 | Partly (manual steps) |
| F Forex | No | Optional | Request log | D-31 | Card 5 | Only as "indicative request" |
| G Loan | No | Optional | Application w/o financials; disbursement mints | D-21, D-32 | Loan cards | Application only; **do not demo disbursement** |
| H Staff & compliance | Yes (L20, L21) | Yes | Staff lists/ledger; no closure approval, no rate-disclosure screen check | D-01, D-14 | Role cards | Partly |
| I Reporting & ops | Yes (L21, L17/18) | Yes | JSON reports API only; no UI/CSV/statements; health basic | D-14, D-23, D-24, D-15, D-35 | Report/batch tests | Partly (API only) |

---

# Appendix P — Implementation Prompt Library (FOR FUTURE USE — NOT EXECUTED)

**Do not run any of these during this audit.** Use one at a time, review the diff and test results, then proceed.

### How to use
Each prompt is self-contained for **A (identity), B (context), E (implementation), and the specific tests**; sections **C, D, F, G, H** are identical for all prompts and live in the **STANDARD BLOCK** below — paste that block at the end of every prompt (it is deliberately not repeated 18 times). Repo path for all prompts: the project root containing `services/`, `FRONTEND/`, `BACKEND/`, `docker-compose.yml`. The behaviours cited are **static audit findings**; step D1 of each prompt requires re-verifying them.

### STANDARD BLOCK (append to every prompt)
```
SCOPE (C): Fix only the stated problem and its necessary dependencies. Preserve the existing microservices
architecture (identity-customer-service, product-service, fd-account-service, nginx gateway, Angular, Kafka outbox,
MySQL per service) and established contracts unless a documented change is necessary. Do not redesign unrelated
services. Do not silently change unrelated behaviour. Do not use hardcoded demo values to hide the defect. Do not
disable validation, security checks or failing tests to get green.
INVESTIGATE FIRST (D): Reproduce the issue (run it, or if tooling is missing say so and give exact repro steps). Read
the listed code and tests. Confirm the root cause. Inspect downstream dependencies. Identify DB/API compatibility
implications. For high-risk or multi-service changes, present a concise plan and wait for my OK before editing.
VERIFY (F): Add/adjust unit tests; add integration tests (Testcontainers MySQL) where relevant; add contract or
end-to-end tests for affected workflows. Cover invalid inputs, permissions, boundaries, retries, failures. Verify
persisted DB state; for financial work verify balances AND ledger entries (debits = credits per journal; reconcile
savings_transactions vs savings_accounts.sa_balance). Run the relevant existing tests and builds and report exact
commands and real results. List tests that could not run and why. Never claim success if something is unverified.
DONE (G): State explicit acceptance criteria and which defect IDs are resolved/unresolved; residual risks and
follow-ups.
SAFETY (H): Do not commit or push unless I ask. Do not change remote git state. Do not modify unrelated modules.
Never apply migrations to shared/production DBs; use isolated test data. Do not print secrets. Report every changed
file and why. Money is BigDecimal with explicit scale/rounding; never double/float.
```

### Dependency order
`P01 → P02 → P03 → { P04, P05, P06, P07 } → P08 (independent, can run any time after P01) → { P09, P10, P11, P12, P13, P14 } → P15 → P16 → P17 (grows with each; final full run last) → P18`.
Key dependencies: P04/P06/P07/P10/P11/P13 require the ledger foundation (P03); P06 and P07 share `FdLifecycleController`/`FdBatchService` — do P07's atomic batch first or in the same change; P15 requires P04/P06/P07 APIs; do not fix UI symptoms (P15/P16) before the underlying APIs.

---

### P01 — Make a clean clone build and start (config, migrations, startup)
**A.** Defects D-10, D-11, D-12 (AR-01, AR-02, AR-07, AR-08, CL-13, KY-11). Priority **P1 / do first**: nothing else can be verified without a reproducible environment.
**B.** *Context:* `docker-compose.yml` is modified and `FRONTEND/Dockerfile`, `FRONTEND/nginx.conf`, `FRONTEND/.dockerignore`, `services/api-gateway/Dockerfile` are untracked, so HEAD cannot build the current stack. Each service's `pom.xml` and `Dockerfile` copy Flyway migrations from `BACKEND/src/main/resources/db/service-migration/{customer,product,fd}`; README says they live in each service. Root `01/02/03_moneypal_*.sql` are stale (03 equals V1 only; lacks V2–V10, DECIMAL(24,8), loan/forex tables). `.env.example` `JWT_SECRET` is not base64 though the code requires it; `DEMO_ADMIN_*` is missing from the template; the only generator is PowerShell. *Expected:* a fresh clone on macOS/Linux builds and starts, schemas created by Flyway. *Code:* `docker-compose.yml`, `services/*/Dockerfile`, `services/*/pom.xml`, `scripts/*.ps1`, `.env.example`, `README.MD`. *Questions:* move migrations into each service (recommended) vs keep shared folder?
**E.** Move migration files to `services/<svc>/src/main/resources/db/migration` (or the configured location) preserving versions and checksums; update poms/Dockerfiles/README; regenerate or delete the stale root SQL (and add a CI diff check if kept); fix `.env.example` (valid base64 placeholder, include `DEMO_ADMIN_*`, `MFA_ENCRYPTION_KEY`); add `scripts/initialize-demo-env.sh` equivalent to the PowerShell script; add service healthchecks and `condition: service_healthy` where cheap. Do **not** change Flyway checksums of already-applied versions.
**Tests:** clean DB + Flyway produces the expected tables (compare to current); compose smoke script: up → health → register → login through gateway.

### P02 — Remove unbacked money creation (demo opening credit, account limits)
**A.** D-02, D-30 (CL-01, CL-06, SE-004). **P0 (static)** — only confirmed money-creation path; small and independent.
**B.** `docker-compose.yml:156` sets `APP_DEMO_OPENING_BALANCE_ENABLED: "true"`; `SavingsAccountController.java:52-71` lets any authenticated user set a balance with no max, no KYC, no journal; `:80-99` allows unlimited accounts. *Expected:* flag off by default; demo seeding only by staff or a script; every credit is journaled to a suspense account; bounded amounts. *Unresolved:* decide cap values with me before coding (do not invent).
**E.** Default off (move to `docker-compose.demo.yml`/`.env`); `@PreAuthorize` BANK_OFFICER/ADMIN or seeding script; `@DecimalMax`; per-customer cap; KYC required on opening; if P03 not yet done, write a minimal balanced `DEMO_OPENING` journal Dr `DEMO_SUSPENSE`/Cr `SAVINGS` (op already allowed by CHECK); UI banner "demo money"; cap accounts per holder/currency and make the DB constraint real (current unique includes PK).
**Tests:** flag false → 404; customer → 403; over-cap → 400; 6th account → 409; journal balanced; second demo-opening rejected.

### P03 — Double-entry ledger foundation, duplicate-posting protection, reconciliation
**A.** D-07 (CL-02, CL-03 part, CL-12, CL-14, CL-17). **P1**, prerequisite for P04–P07, P10, P11, P13.
**B.** Only FD funding, maturity interest and settlement write `fd_journals`; staff deposit/withdraw, demo opening and loan disbursement mutate `sa_balance` + one `savings_transactions` row with no journal; `jl_account_code` is free text; header=Σlines is not enforced; `fj_savings_account_id` is NOT NULL; `settle` skips the journal if the key exists but still credits savings (`FdLifecycleController.java:131`); journals have no actor. *Expected:* every balance mutation produces a balanced journal in the same DB transaction; reconciliation reports zero discrepancies.
**E.** Add `PostingService.post(key, operation, currency, lines)` that validates Σdebit=Σcredit per currency, uses the unique `fj_key` for idempotency and rejects (409) rather than skips on a duplicate; Flyway V11: `chart_of_accounts` + FK, extend `fj_operation` CHECK (DEPOSIT, WITHDRAWAL, DEMO_OPENING, LOAN_DISBURSEMENT, TRANSFER, …), per-line savings account id or nullable header column, `fj_created_by`/`st_created_by`; route all existing money paths through it; admin ledger lists all journals and shows Σ-from-lines; add `GET /api/v1/admin/ledger/reconciliation` (unbalanced journals; Σ SAVINGS deltas vs Δ balances; statement vs balance). Migration compatibility: additive, old rows keep NULL actor.
**Tests:** property test per operation; duplicate `fj_key` → 409 and no second credit; reconciliation endpoint returns zero after the full demo script.

### P04 — CASA (savings-to-savings) transfer
**A.** D-08 (CL-08, QA-01). **P1**; depends on P03.
**B.** Feature absent in active stack; legacy reference `BACKEND/.../fd/application/SavingsTransferService.java:47-142` and V31. *Expected invariants:* debit = credit; two statement rows with one reference; no unaccounted debit; replay-safe; no overspend under concurrency; ordered locking.
**E.** Implement exactly the design in Report 2 §4A (endpoint `POST /api/v1/savings-accounts/transfers`, `Idempotency-Key`, lock both rows in ascending id in one statement, same-currency only, KYC verified, limits, `TRANSFER` journal, audit). Add Angular transfer form with refresh of both accounts and history, disabled-while-pending, retained idempotency key. Add gateway route if needed.
**Tests:** the six acceptance tests listed in §4A(10), including A↔B parallel and fault injection after the credit insert.

### P05 — FD creation and funding hardening
**A.** D-09 (part), D-28, FD-12, FD-14, A1-019/020 (decisions). **P1/P2**; depends on P03 for journal consistency.
**B.** Booking validates against Kafka-synced snapshot; terms snapshot (`fa_terms_snapshot`, `FixedDepositController.java:56`) lacks penalty points and effective dates; concurrent duplicate idempotency key may 500 (claim after reads); compounding hard-coded `QUARTERLY`/`AT_MATURITY` in `FdOfferController`; manual wants officer-created FD, branch-based account number, create-with-initial-DEPOSIT (decision needed: document deviation or add officer path).
**E.** Add penalty points + effective dates to booked terms; claim idempotency first (or read with locks); allow compounding on the band request; optionally officer-on-behalf booking endpoint and pluggable `AccountNumberGenerator` `[3][6][1]` — **only if I confirm** the deviation shouldn't be documented instead.
**Tests:** invalid tenure/principal/product/currency; concurrent duplicate key (no 500); product edit after booking doesn't alter booked penalty.

### P06 — FD premature withdrawal and penalty
**A.** D-01, D-05, D-06, D-27, D-28, D-29 (FD-01/02/03/06/07/08/09/10, UI-01/02). **P0 (static)**; depends on P03 (journal) and ideally P07 (shared batch/lifecycle code).
**B.** See Report 2 §4B (12-point table). Customer UI calls the staff-only approve endpoint (403); no staff screen; NULL penalty → 409; MATURED offered but rejected; quote frozen and paid unchanged; no interest journal; penalty = rate points on simple interest vs manual "% of accrued interest"; PENDING cannot be rejected/cancelled. *Unresolved — ask me before coding:* single-step customer withdrawal (manual L15) vs four-eyes; penalty formula; value of penalty points for demo products (do not invent a percentage).
**E.** Per my decision: add owner `confirm` endpoint recomputing at settlement date (or staff screen); keep idempotency, row locking, single `@Transactional`; post `INTEREST:<id>:PREMATURE`; compute from booked terms; add REJECTED/CANCELLED (Flyway CHECK change) + endpoint + auto-cancel at maturity; require penalty config at offer creation; UI: quote preview before committing, hide action for non-ACTIVE, show penalty summary and confirmation dialog.
**Tests:** acceptance list in §4B; golden numbers; customer 200 vs staff-self 403 matrix; replay; MATURED/CLOSED 409; Σ FIXED_DEPOSIT = 0.

### P07 — FD maturity, payout, batch atomicity, scheduler, date clamp
**A.** D-03, D-04, D-15, D-16 (FD-04/05/13/15, CL-05/07/11, A1-001/009/010). **P0 (static)**.
**B.** `FdBatchService.runDaily` self-invokes `accrue`/`mature`, bypassing `@Transactional`; `accountsFailed` hard-coded 0; `verifyMaturityJournals` makes half-matured FDs permanently unclosable; officers can pass future `businessDate`; no `@Scheduled`; maturity doesn't pay out; payout frequency stored but never executed.
**E.** Move per-account work into a separate bean with `REQUIRES_NEW`, per-account catch + counters + structured logs, status update last, `FOR UPDATE SKIP LOCKED`; reject `businessDate > clock.businessDate()` (ADMIN-only override if needed); conditional `@Scheduled` calling `runDaily(clock.businessDate())` (cron configurable, disabled under demo clock if I say so); auto-settle at maturity in the same per-account transaction **or** keep Close and add notification (ask me); implement or remove interest payout frequencies (ask me).
**Tests:** inject failure on account 2 of 3; rerun twice → single maturity/settlement; future date → 400/403; scheduler fires with fixed clock; MATURED-without-journal recovery path.

### P08 — Authentication & authorization defects
**A.** D-17, D-36, D-20 (partly), SE-001/003/005/005b/008/009/010/022, A1-011/023, UI-05/06. **P1**.
**B.** Only Identity checks `tokenVersion`/status; Product & FD filters validate signature/expiry only (24 h); MFA challenge token accepted as bearer by Product; customers can list DRAFT/INACTIVE products; unlimited refresh; no rate limiting; 403 instead of 401 for missing tokens; teller operations unlimited with no actor.
**E.** Choose smallest mechanism (shared token-version check via introspection/Kafka user snapshot with short TTL, or short-lived access tokens + refresh) ; token `typ` claim to separate MFA challenge tokens; filter product list by role; refresh rotation/cap; `limit_req` at gateway for login/register/reset; proper 401 entry point; teller: mandatory remark, per-role limits, actor recorded (maker-checker above threshold — ask me thresholds).
**Tests:** logout/suspend → 401 on FD & Product; tampered/expired/`alg=none`/wrong key; role × endpoint matrix; IDOR suite.

### P09 — Customer onboarding & KYC
**A.** D-18, D-19, D-33 (KY-01…07, SE-002/007/015). **P1**.
**B.** See Report 2 §7. Auto-verify on format-valid fields; officers set arbitrary status; ID and document unencrypted; no duplicate prevention; signup fields dropped; no audit.
**E.** Implement the state machine, audit table, encryption/hash, unique indexes, staff review queue and screens, backend gates on savings opening/FD/loan/forex; synthetic fixtures only. Migration plan for existing rows included.
**Tests:** cards in §10(6) plus decision permissions, duplicates, audit rows, masked responses.

### P10 — Loan application fields, validation, persistence (and gated disbursement)
**A.** D-21, D-32 (LN-01…07, CL-04). **P1 (money) / P2 (fields)**; application half independent of P03, disbursement/repayment depends on P03 and P09.
**B.** Report 2 §8 field table. Disbursement credits savings with no receivable/journal/repayment; same admin approves and disburses; no KYC re-check.
**E.** Phase 1: add occupation, employer, income+frequency, obligations, document metadata end to end (UI→DTO→validation→Flyway→response); re-check KYC/risk at approve/disburse; separate approver/disburser. Phase 2 (only if I approve): receivable journal, schedule, `repay`. Until phase 2, **disable disbursement** (feature flag).
**Tests:** mandatory-field validation, persistence round trip, KYC re-check, maker-checker.

### P11 — Forex rate handling and conversion
**A.** D-31 (FK-01…06). **P2**; depends on P03/P04.
**B.** Report 2 §4D. Currently a request log with a markup only.
**E.** Implement the simulated, clearly labelled rate table; conversion between two of the customer's savings accounts in one transaction with currency-separated balanced journals; fee line; idempotency; UI labels "Simulated rate"; never claim live rates.
**Tests:** direct, inverse, rounding, unsupported pair, stale rate, failure mid-way, per-currency journal balance.

### P12 — Gateway & microservice integration robustness
**A.** D-09, D-34, D-35, AR-03/04/05/10/11/14/18. **P1/P2**.
**B.** Reference data (products/currencies/users) reaches FD only when written; no DLQ; outbox publishers stop at first failure and aren't multi-instance safe; no service healthchecks; product-service uses real date (Asia/Kolkata) while FD uses a demo clock.
**E.** Startup republish of full snapshots; DLQ/skip-and-log with metrics; `SKIP LOCKED` in pollers; healthchecks + `service_healthy`; MDC correlation id propagated through nginx (`X-Request-Id`); document the clock policy.
**Tests:** clean stack books an FD with no manual re-save; poison message doesn't stall consumer; gateway route test generated from `nginx.conf`.

### P13 — Reporting, statements, notifications, exports
**A.** D-23, D-24 (A1-004/005/006/018, FD-16, T-04). **P1 (lab-graded)**; depends on P07.
**B.** Report paths/format differ from L21; no CSV; no Python script; `fd_statements`, `notifications_log` never written; no UI.
**E.** `/api/report/fd-summary`, `/api/report/customer-portfolio` (aliases), per-product grouping, interest history & maturity summary, CSV; Python `generate_fd_report.py` invoked safely (fixed argv, no user strings); statement generator in the batch; mock notification sink + `notifications_log` table; Angular report screens.
**Tests:** totals equal DB aggregates; customer isolation; CSV format; notification row per maturity.

### P14 — FD calculator rules
**A.** D-13 (A1-002/016, FD-11, QA-12). **P1 (lab-graded)**.
**B.** No combined add-on cap, single category, hard-coded boosts, no product link, percent vs fraction, `double`, not CUSTOMER-only, no UI.
**E.** Rate matrix (from product config), multi-category, cap, product max enforcement, reuse `FdCompounding` (BigDecimal), `@PreAuthorize`, unit consistency; add UI (P15 wires it). Reproduce manual scenarios (6.5+2.0=8.5 capped; 6.5+3.0 capped to 8.5; record the manual's own inconsistency, A1-032, and which reading you implemented).
**Tests:** table-driven scenarios; role test; compare calculator vs booked maturity.

### P15 — Frontend/API integration
**A.** D-14, D-37 (UI-01/03/04/05/06/13, AR-12). **P1/P2**; depends on P04/P06/P07/P13/P14 APIs.
**B.** Prod env hard-codes `http://localhost:8080`; FE nginx proxies `/fd/`, colliding with SPA routes; 401 handler doesn't redirect; no 403 handling; stale `username/roles` in storage; KYC upload >1 MB blocked; missing screens (calculator, reports, batch triggers, MFA, reset, closure approval).
**E.** Relative `/api`; fix nginx location order/`try_files`; interceptor redirect with returnUrl and 403 toast; clear all auth keys on logout and decode role from JWT; `client_max_body_size`; build missing screens; refresh balances/history after mutations.
**Tests:** deep-link refresh; expired-token redirect; component specs; Playwright flows.

### P16 — Responsive UI/UX & accessibility
**A.** UI-07…UI-15. **P3**; do last among UI work.
**B.** `outline:none` on glass inputs; contrast; 375 px login card clipped (UNVERIFIED); raw dates; currency-formatting; i18n check fails; bundle over budget; dashboard stub.
**E.** Apply the design recommendations in §9; real-device-size screenshots at 375/768/1440; axe checks; lazy-load the three.js landing.
**Tests:** screenshot diffs at three widths; axe zero serious violations; keyboard-only run of main flows.

### P17 — Automated testing & regression coverage
**A.** D-26 (QA-02…QA-15). **P1**; grows with every earlier prompt, final run last.
**B.** Docker-gated tests skip silently; no CI; key FD money scenarios, negative FD bookings, IDOR, JWT hardening untested; frontend specs can't run (no target/runner); Postman collection dead; self-referential oracle in `FdCompoundingTest`.
**E.** CI that **fails** when Testcontainers tests are skipped; implement test cards in §10; add Angular test target; replace Postman with an active collection (with `Idempotency-Key`, body assertions); Playwright E2E for Journeys A–E, H, I; reconciliation SQL helper used in every financial test.
**Tests:** CI run shows executed (not skipped) count; reconciliation zero.

### P18 — Documentation, deployment artefacts, health/readiness
**A.** D-22, D-25, D-39 (A1-012/013/025, AR-09/10/17). **P1 (lab-graded)**; can run in parallel with feature work, finalise last.
**B.** Missing architecture/ER/deployment diagrams, test plan, solution documents, API samples, versioning notes, Swagger on active services, k8s/Azure artefacts, probes, scaling, backup notes; `.planning`/`CONTEXT.md` describe the legacy PostgreSQL system.
**E.** Produce docs from the real system; add springdoc to the three services; enable actuator probe groups; minimal k8s manifests (Deployment/Service/Ingress/HPA/ConfigMap/Secret templates) validated with `--dry-run`; Azure App Service deployment guide; backup/restore note; re-baseline `.planning` docs.
**Tests:** `kubectl apply --dry-run=client`; link/diagram checks; doc review checklist.

---

# Appendix T — Traceable Fix Roadmap

`Finding → Root cause → Prompt → Required tests → Acceptance`

| Finding(s) | Root cause | Prompt | Required tests | Acceptance | Class | Order |
|---|---|---|---|---|---|---|
| AR-01/02/07/08, CL-13 | uncommitted assets, hidden build dependency, template drift | P01 | clean-clone smoke, Flyway diff | clean macOS clone starts & logs in | Demo basic | 1 |
| CL-01, SE-004, CL-06 | demo feature on by default, no contra entry | P02 | flag/role/cap/journal | no unbacked credit possible | Financial integrity | 2 |
| CL-02/03/12/14/17 | ledger only for FD | P03 | invariants, reconciliation | reconciliation zero | Financial integrity | 3 |
| FD-04/05, CL-05/07, A1-001 | proxy self-invocation, unbounded date, no scheduler | P07 | failure injection, rerun, future date | no stuck MATURED; rerun no-op | Financial integrity + Lab | 4 |
| FD-01/02/03/06/07/08/09/10, UI-01 | UI/role mismatch, config optional, missing journal | P06 | §4B acceptance | customer closes eligible FD once, ledger nets 0 | Demo basic + Lab | 5 |
| CL-08, QA-01 | not ported | P04 | §4A tests | transfer atomic/idempotent | Demo (expected) | 6 |
| FD-12/14, A1-007/008/019/020 | design deviations | P05 | negative & concurrency | documented or fixed | Lab | 7 |
| SE-001/003/005, A1-011 | per-service auth drift | P08 | role/revocation matrix | logout → 401 everywhere | Security | 8 (parallel) |
| KY-01…07, SE-002/007 | no review, no audit | P09 | KYC cards | gated, audited | Security + Demo | 9 (parallel) |
| LN-01…07, CL-04 | module incomplete | P10 | loan cards | fields persisted; disbursement gated | Optional | 10 |
| AR-03/04/05/10/14 | integration fragility | P12 | clean-stack booking | no manual re-save | Demo basic | after P01 |
| A1-002/016, FD-11 | calculator not to spec | P14 | table-driven | manual scenarios | Lab | 11 |
| A1-004/005/006/018, FD-16 | reports/alerts missing | P13 | totals/CSV/notification | L21/L17/L18 evidence | Lab | after P07 |
| FK-01…06 | request-only | P11 | forex cards | simulated conversion | Optional | after P03/P04 |
| UI-01/03/04/05/06/13 | wiring & env | P15 | deep link, redirect | flows complete in UI | Demo basic | after APIs |
| UI-07…15 | polish | P16 | screenshots, axe | AA, no overflow | Later | last UI |
| QA-02…15 | no CI, skipped tests | P17 | CI | non-skipped green | Lab + Integrity | continuous |
| A1-012/013, AR-09/10/17 | docs/deploy absent | P18 | dry-run, review | deliverables present | Lab | parallel, final |

**Parallelizable once P01–P03 merge:** P08, P09, P10(app half), P12, P14, P18. **Must wait:** P04, P06, P07 (after P03), P11, P13, P15, P16. **A small UI fix never outranks a balance-affecting defect** (P02, P03, P07 precede all UI prompts).

---

# Appendix R — Runtime test results (partial)

**What ran:** a scratch-only JDK 17 + Maven + MySQL 8.4 toolchain was downloaded into the session scratchpad; the three services were built from a scratch copy and run against a **disposable MySQL (127.0.0.1:3390)** with Kafka disabled and reference data seeded from the services' own outbox rows. API calls went **directly to the services** (no nginx/Docker, so gateway routing is UNVERIFIED). The runtime phase was **stopped early at your request** ("if it can be skipped then skip"), so it is incomplete. No browser/UI flows, no concurrency tests, no repo JUnit tests, no loan/forex/staff-deposit/report/IDOR runtime checks were executed. No source or config file was edited; MySQL and services were shut down cleanly; no pre-existing data existed or was altered. Full detail: `evidence/agent12-runtime-tests.md`; raw logs: `evidence/runtime-logs/`.

**Upgraded from CONFIRMED-STATIC to CONFIRMED-RUNTIME:**

| Defect | Runtime observation |
|---|---|
| D-02 money creation (CL-01/06, SE-004) | A customer opened 6 savings accounts and demo-credited 100,000 + 5×1,000,000; `DEMO_OPENING` journals = **0**. |
| D-18 KYC rubber stamp (KY-01) | Draft `PUT` with synthetic fields returned `VERIFIED` instantly, no review. |
| D-05 premature-closure ledger (FD-07/CL-03) | FIXED_DEPOSIT net per prematurely-closed FD = +821.92 and +164.38 (non-zero); no INTEREST_EXPENSE line. Normally closed FDs net 0.00. |
| D-03 half-matured FD (FD-04/CL-05) | With an **injected** journal failure, FD 7 ended MATURED with only its funding journal/transaction and the endpoint returned 500. (Shows the failure mode; the fault was injected.) |

**Confirmed working at runtime:** each journal balanced (0 unbalanced); statement table reconciles to balances (diff 0.00); FD booking → fund → ACTIVE with idempotent replay and 409 on key reuse with different body; role checks (customer 403 on product create and clock advance; self-registration = ROLE_CUSTOMER); input validation 400s.

**New defects found only at runtime:**

| ID | Sev | Defect | Evidence | Fix direction |
|---|---|---|---|---|
| D-41 (RT-09) | P1 | Accrual dates are **timezone-sensitive**: an IST host JVM shifted `fia_accrual_date` by a day and accrual/clock endpoints failed with **500 `DuplicateKeyException`** on `uq_fd_interest_accrual_account_date`; with `-Duser.timezone=UTC` the class of error disappeared. Compose pins the JDBC timezone but not the JVM; it bites non-Docker/local runs. | `runtime-logs/run1-IST-accrual-date-evidence.txt` | Pin `-Duser.timezone=UTC` (JAVA_TOOL_OPTIONS) and use `LocalDate`/DATE columns for accrual dates; make the idempotency guard skip rather than 500 |
| D-42 (RT-10) | P2 | Out-of-range amount → **500** (`Out of range value for column 'sa_balance'`) instead of 400 (no `@DecimalMax`) | `fd-service-run2-UTC.log:55` | `@DecimalMax` on all money DTOs; map `DataIntegrityViolation` to 400/409 |
| D-43 (RT-11/12) | P3 | Three registration attempts returned 500 (payloads not retained; cause UNVERIFIED); `GET /api/v1/auth/me` returned 404 though static review listed it (may be a wrong path) | `api-trace-run1-IST.log` | Re-test and fix error mapping |

**Still unverified at runtime (needs a Docker-capable machine or a resumed runtime pass):** D-01 (customer 403 on approve), D-04 (officer future date), D-06 (409 without penalty), D-07 staff/loan journals, D-09 (Kafka propagation on a clean stack), D-17 (revocation), gateway routing, UI, concurrency/idempotency under load, maturity re-run, JUnit suites.

**Compliance statement for this appendix:** the runtime phase did write to a database, but only to the disposable scratch MySQL created for this audit; no existing database was altered and no application code or config was modified.
