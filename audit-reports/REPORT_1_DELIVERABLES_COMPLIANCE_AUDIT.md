# REPORT 1 — Deliverables Compliance & Completeness Audit

**Project:** MoneyPal / Banking Technology Lab (CSF 3142), `BankingTech MSA.zip` (extracted copy, HEAD `8dce24e` plus uncommitted changes)
**Manual:** `Banking-Technology-Lab-Manual.docx` (L1–L25 plus evaluation plan; extracted text in `evidence/lab-manual-extracted.txt`)
**Audit date:** 2026-10-25 · **Mode:** strict read-only audit; nothing was fixed

> Companion document: `REPORT_2_CRITICAL_DEFECTS_AND_ACTION_PLAN.md`. Detailed per-workstream evidence (158-row manual matrix, 24+ module findings, endpoint authorization matrix, etc.) is in `evidence/agent*.md`. Finding IDs used below (A1-, AR-, CL-, FD-, KY-, LN-, FK-, SE-, UI-, QA-, RT-) refer to those files.

---

## 0. Compliance and method statement (required)

| Item | Statement |
|---|---|
| Source changes | **No application source code or configuration was changed.** No fixes were implemented. The only repo copy touched is a scratch extraction; the ZIP and manual in `~/Downloads` were not modified. |
| Git | Git history/status/diff of the extracted copy were *read*. No commit, branch change, fetch, pull, push or reset was run. |
| Secrets | `.env` was present in the ZIP but its values were not read or reproduced. Reports mention only that it exists and is git-ignored. |
| What was actually run | See §0.1. |
| What could not be run | See §0.2. |
| Temporary artefacts | Extracted repo copy, converted manual text, scratch frontend copy, toolchain downloads and disposable MySQL data live under the session scratchpad `/private/tmp/claude-501/-Users-yashralhan-Projects-PokerThen-/e0312acd-4510-4ec5-98db-ebead3794720/scratchpad/` (`src/`, `repo` symlink, `manual/`, `fe-copy/`, `qa/`, `run/`, `tools/`). Reports, screenshots and logs are in `/Users/yashralhan/Projects/PokerThen/audit-reports/`. |
| Existing data altered? | No existing database was touched (none existed on this machine). Any database writes during runtime testing were to a disposable MySQL instance in the scratch directory only (see §0.1). |

### 0.1 What was actually executed

| Activity | Result |
|---|---|
| Full read of manual; static review of all three active services, nginx gateway, Angular app, SQL/Flyway, compose, planning docs | Done (8 independent workstreams). |
| `npm ci` + `ng build` of **a scratch copy** of `FRONTEND` | Build passes with warnings (initial bundle 837 kB vs 512 kB budget; 2 SCSS size warnings). |
| `node scripts/check-i18n.mjs` | **Fails**: 4 untranslated strings. |
| Frontend unit tests | **Could not run** (no `test` target in `angular.json`, no test runner installed). |
| Headless Chrome screenshots (`/login`, `/signup`, `/dashboard` at 375/768/1440 px) against a mocked API | Done; 9 PNGs in `evidence/screenshots/`. Authenticated flows not exercised (no backend at the time). |
| FD compounding logic compiled with JDK 11 from a scratch copy and cross-checked against an independent Python Decimal implementation | Done; agrees with `FdCompoundingTest` expectations (10,000 @ 6% / 1 yr: 10616.77 monthly, 10613.64 quarterly, 10600.00 yearly). |
| Runtime attempt: scratch-only JDK 17 + Maven + MySQL 8.4 downloaded to the scratchpad; the three services built from a scratch copy and run against a **disposable MySQL (port 3390)**, Kafka disabled, reference data seeded from outbox rows; direct API calls (no nginx/Docker); read-only ledger SQL | **Partial — stopped early at the user's request.** Results in **Appendix R of Report 2**. Confirmed at runtime: unbacked demo money (no journals), KYC auto-verify, FIXED_DEPOSIT ledger imbalance after premature closure, half-matured FD under an injected fault, plus a timezone-dependent accrual 500. Not run: UI, gateway, concurrency, JUnit. |

### 0.2 What could not be executed, and why

| Not run | Reason |
|---|---|
| Any Spring/JUnit/Testcontainers test from the repo | (Services were later run with a scratch JDK 17 — see §0.1 — but the repo's JUnit suites were still not executed.) Machine has JDK 11 only (services need 17), no Maven, **no Docker** — all 6 Docker-backed service test classes use `@Testcontainers(disabledWithoutDocker = true)` and would skip silently. |
| `docker compose up`, nginx gateway routing, Kafka event flow, Kafka UI | No Docker. Gateway routing was verified by *reading* `nginx.conf` against controller paths, not by sending requests through nginx. |
| Authenticated browser flows, full UI click-through | Depends on a running backend; see Appendix R. |
| Azure/Kubernetes deployment | No manifests exist to run. |

Anything that depends on one of these is labelled **UNVERIFIED** with a reproduction step.

---

## 1. Executive Summary

### 1.1 Readiness verdict

**The application is NOT ready for an end-to-end demonstration of the lab's FD lifecycle as written in the manual, and several workflows the team expects (CASA transfer, forex conversion, customer premature withdrawal) do not work or do not exist in the active system.** All statements below are from code reading unless marked otherwise; none of the headline defects below has been disproved by a runtime check.

### 1.2 Key architectural fact the team may not have realised

- The **active** system = 3 Spring Boot services (`identity-customer-service`, `product-service`, `fd-account-service`), an Nginx gateway, Kafka, 3 MySQL DBs, and an Angular app.
- `BACKEND/` is a **legacy modular monolith** (PostgreSQL-era). It is *not* started by compose, **but the three services build their Flyway migrations out of it** (`BACKEND/src/main/resources/db/service-migration/{customer,product,fd}`; copied by each service's `pom.xml`/`Dockerfile`). Deleting "legacy" `BACKEND/` breaks the schema of all three services (AR-02).
- Several things you remember working live **only in the legacy monolith**: the `@Scheduled` batch, the **savings-to-savings transfer** (`SavingsTransferService`, migration V31), Swagger/OpenAPI, and the Postman collection. They were not ported.
- `.planning/` and `CONTEXT.md` describe the legacy PostgreSQL system (and claim 100 % complete). They do not describe what is deployed (A1-025, FD-17).

### 1.3 Completion level (defined scoring method)

Method: map the manual's own evaluation plan (85 continuous-assessment marks; viva 15 excluded) to *code-evidence* status. The 35-mark FD module is split into seven equal 5-mark sub-criteria (creation, transactions, batch, maturity, alerts, file extract, scaling/cloud). PASS = 100 %, PARTIAL = 50 %, FAIL/MISSING = 0 %. **This is a static-evidence readiness index, not a predicted grade, and no criterion is credited as runtime-verified.**

| Criterion (marks) | Status | Credit |
|---|---|---|
| Login & UI integration (10) | PASS (static) | 10.0 |
| User auth, hashing, authorization (5) | PARTIAL | 2.5 |
| Customer module — PII masking/setup (5) | PARTIAL | 2.5 |
| Product & Pricing (10) | PARTIAL | 5.0 |
| FD Calculator simple & compound (10) | PARTIAL | 5.0 |
| FD: creation (5) | PARTIAL | 2.5 |
| FD: transactions (5) | PARTIAL | 2.5 |
| FD: batch processing (5) | **FAIL** | 0 |
| FD: maturity (5) | PARTIAL | 2.5 |
| FD: alerts (5) | **MISSING** | 0 |
| FD: file extract (5) | **MISSING** | 0 |
| FD: scaling / cloud deployment (5) | **MISSING** | 0 |
| Reports (5) | PARTIAL | 2.5 |
| Documentation & presentation (5) | PARTIAL | 2.5 |
| **Total** | | **37.5 / 85 ≈ 44 %** (static, unverified at runtime) |

Because PASS rows still carry "runtime UNVERIFIED", the real figure could be lower. Percent-of-endpoints would be far more flattering and is deliberately not used.

### 1.4 Counts

| Measure | Count | Source |
|---|---|---|
| Manual requirement rows assessed | 158 + 15 criterion roll-ups | `evidence/agent1-manual-compliance.md` |
| PASS / PARTIAL / MISSING / FAIL / OUT OF SCOPE / UNVERIFIED (rows) | 59 / 44 / 36 / 13 / 4 / 2 | same |
| P0 requirement gaps (graded criterion blocked) | 5 | same |
| Demo-blocking or money-integrity defects confirmed **by code reading** | 9 (CL-01, CL-02, CL-03, CL-05, CL-07, FD-01, FD-02, FD-04, FD-15) | Report 2 |
| Defects confirmed by **runtime execution** | 5 (D-02, D-05, D-18, D-03 under injected fault, D-41 timezone) — Appendix R of Report 2 | `evidence/agent12-runtime-tests.md` |
| Expected-but-absent features (not in manual) | CASA transfer; forex *conversion*; loan repayment; loan applicant financials | agents 3, 5 |

### 1.5 Strongest parts

1. Authentication: bcrypt, HS512 JWT with `userId`/`roles`/`tokenVersion`, lockout after 5 failures, optional TOTP MFA, hard-wired `ROLE_CUSTOMER` on signup (no self-registration as ADMIN), `@EnableMethodSecurity` on in all services (security audit, E-01/E-02).
2. Ownership checks: savings, FD, loan and forex endpoints are scoped by the JWT `userId`; no customer-to-customer IDOR found by static review.
3. FD **booking and funding** path: ownership, active savings, currency, balance, `SELECT … FOR UPDATE`, idempotency key, journal, atomic `@Transactional` (CL path #4).
4. Money types: `DECIMAL(24,8)`, explicit `BigDecimal` rounding, DB `CHECK sa_balance >= 0`, unique idempotency scope/actor/key.
5. KYC is the best-tested area (including a real two-thread race test).
6. Architecture direction (one DB per service, local reference replicas fed by Kafka outbox) is sound and fits the manual's "microservice decomposition" future-work note.

### 1.6 Most important gaps

1. **FD customer premature withdrawal cannot complete from the UI** (customer button calls a staff-only endpoint → 403; no staff screen exists) — FD-01/UI-01.
2. **No batch scheduler** in the active stack; no statements, no alerts, no file extract, no Python/CSV report — A1-001/004/005/006.
3. **FD calculator lacks the manual's central rule** (combined category cap, multiple categories) and has **no UI** — A1-002/003.
4. **No CASA transfer and no forex conversion** in the active stack (neither is in the manual) — CL-08, FK-01/02.
5. **Ledger is partial**: only FD operations post journals; deposits, withdrawals, demo credits and loan disbursement do not; premature closure leaves the FD liability account negative — CL-02/03.
6. **Unbacked money creation**: demo opening credit enabled in compose with no cap or journal — CL-01/SE-004.
7. **Deployment story missing**: no k8s/Azure/HTTPS/CI; compose + frontend Dockerfiles are uncommitted — AR-01/AR-09, A1-013.
8. **Documentation set missing** (no diagrams, no test plan, stale Postman/Swagger) — A1-012.

---

## 2. Requirements-to-Implementation Matrix

Status key (as requested): **PASS** implemented *and* verified by meaningful execution/test evidence; **PARTIAL**; **FAIL** confirmed not to meet; **MISSING**; **UNVERIFIED**; **OUT OF SCOPE**.

> **Important:** because the backend could not be built or run with the original toolchain, almost nothing can honestly be called PASS under the strict definition. Per your rule ("do not use PASS for an endpoint that merely exists"), the table below uses **PASS\*** to mean *"code satisfies the requirement by static review; runtime UNVERIFIED"*. The full 158-row version with line numbers is `evidence/agent1-manual-compliance.md`. Runtime outcomes from Appendix R may upgrade or downgrade individual rows.

| ID | Manual Requirement | Evidence in Repository | Implementation Status | Verification Evidence | Gap / Required Action | Priority |
|---|---|---|---|---|---|---|
| T-04 | Python for reports (§3, L6, L11) | no `*.py` anywhere | MISSING | repo-wide search | Add Python report script + trigger, or document deviation | P1 |
| T-06 | Kubernetes (§3) | none | MISSING | file search | Add k8s manifests or justify compose-only | P1 |
| T-07 | Azure App Service/ACR/Azure SQL, scaling (§3, L23) | none | MISSING | file search | Deployment guide + artefacts | P1 |
| T-09 | Readiness/liveness probes | actuator `health,info`; no probe groups, no healthchecks on Java containers (AR-10) | PARTIAL | read | Enable probes, compose healthchecks | P1 |
| T-10 | Elastic scaling | none; outbox pollers not multi-replica safe (AR-05) | MISSING | read | HPA / replica design | P1 |
| T-11 | Email/SMS alerts | none in FD (password-reset mail only) | MISSING | read | Notification service + `notifications_log` | P1 |
| T-12 | Event publishing | Outbox + Kafka (`FdEventPublisher`, `product.catalog`, `user.snapshot`) | PASS\* | read; runtime UNVERIFIED | Verify with Kafka | P3 |
| T-13 | File extracts | none | MISSING | search | CSV/extract endpoint | P1 |
| L1-01 | `POST /api/register` (username, password, email) | `IDC/AuthController.java:51` `/api/v1/auth/register` | PASS\* | read | Path versioned; contract differs (token, not `{status,message}`) | P3 |
| L1-02 | Bcrypt with salt | `IDC/SecurityConfiguration.java:27` | PASS\* | read | — | — |
| L1-03 | Unique username → 409 | check-then-insert, no catch for race | PARTIAL | read | Handle `DuplicateKeyException` | P3 |
| L2-01..05 | Login, bcrypt verify, JWT, masked password, interceptor | `AuthController.java:72-83`, `login.html:15`, `auth.interceptor.ts` | PASS\* | UI screenshot confirms masked field | 401 handler doesn't redirect (UI-05) | P2 |
| L3/L4 | RBAC ADMIN/BANK_OFFICER/CUSTOMER, `@PreAuthorize`, 403 | `@EnableMethodSecurity` in all 3 services | PASS\* | read | 401-vs-403 behaviour UNVERIFIED (A1-023) | P2 |
| L3-06 | Token expiry, invalidate when user disabled | revocation only in Identity; Product/FD accept revoked tokens ≤ 24 h | PARTIAL | read (A1-011, SE-001) | Enforce `tokenVersion`/status in all services | P1 |
| Eval-3 | Customer PII masking | proof-of-ID masked; ID/DOB stored plaintext; staff API omits email/phone | PARTIAL | read (A1-015, KY-03) | Mask/encrypt at rest; define masking policy | P1 |
| L5-01/03 | Product create/maintain | `/api/v1/products`, `/api/v1/fd-products`; rate *bands* instead of `termLimit`/`rateLimit` | PARTIAL | read | Document deviation or add fields | P2 |
| L5-02 | `GET /api/product/search?productType&startDate&endDate` | only `status`/`lang` filters | MISSING | read | Add filter endpoint | P1 |
| L5-07 | Solution doc with API samples | READMEs only | MISSING | — | Write doc | P1 |
| L9-03 | Audit user on product changes | literal `'API'` stored | FAIL | read (A1-021) | Record principal | P2 |
| L6-02/03 | Simple & compound formulas | `FdCalculatorController.java:35-47`; `FdCompounding` | PASS\* (formula) | **Executed** `FdCompounding` (JDK11 scratch copy) + Python cross-check agree | Calculator uses `double` pow; unknown basis → QUARTERLY (QA-11/12) | P2 |
| L6-04/05 | Category add-ons, **combined cap** (e.g. 2 %), multiple categories | boosts hard-coded +0.50/+0.75; single category; **no cap** | FAIL / MISSING | read (A1-002) | Implement cap + categories from rate matrix | **P0** |
| L6-07 | UI: category dropdown, add-on %, cap | no calculator component | MISSING | grep | Build calculator screen | **P0** |
| L10-01 | Calculator CUSTOMER-only, personalised | no `@PreAuthorize`; no customer-category source | FAIL | read | Restrict + data source | P1 |
| L11-01 | Python CSV report from calculator | none | MISSING | — | — | P1 |
| L12/L13 | FD created by BANK_OFFICER only; account no. `[3][6][1]`; product/rate/term validation | customer self-service booking; `FD-`+16 hex; product/term validation present | PARTIAL | read (A1-007/008) | Decide: document self-service deviation or add officer path | P1 |
| L16 | create-with-txn: FD + initial DEPOSIT in one DB txn | booking PENDING; `FD_FUNDING` txn at funding (atomic there) | PARTIAL | read (A1-019) | Document or add endpoint | P2 |
| L14 | Interest calculation engine, capitalization/payout | `FdBatchService.accrueOne` daily compounding, capitalised at maturity; payout frequency stored but never executed (CL-11) | PARTIAL | formula executed; batch UNVERIFIED | Implement payout or remove option | P1 |
| L14/L17/L18 | `@Scheduled` batches, logs, per-account failure isolation, idempotent | no scheduler; `runDaily` self-invocation bypasses transaction; `accountsFailed` hard-coded 0 | **FAIL** | read (A1-001, FD-04, CL-05) | Add scheduler + per-account `REQUIRES_NEW` | **P0** |
| L17 | `fd_statements` generated | table only; no writer | MISSING | grep | Statement generator | P1 |
| L15 | Premature withdrawal: accrued interest, product penalty, close, payout, audit | two-step request/approve; UI wired to staff endpoint; penalty NULL → 409; penalty formula differs (rate points on simple interest) | **FAIL** (end-to-end via UI) | read (FD-01/02/08); runtime check in Appendix R | Fix wiring; seed penalty; decide formula | **P0** |
| L15 | withdrawal window, `transferAccount`, officer-initiated | no date/transferAccount input; owner-only | PARTIAL | read | Document deviation | P2 |
| L18/L19 | Maturity batch → pay out → CLOSED; MATURITY_PAYOUT | batch sets MATURED only; customer must click Close | PARTIAL | read (FD-13, A1-009) | Auto-settle or surface step | P1 |
| L19 | Manual close by BANK_OFFICER/ADMIN; no duplicate processing | owner-only close; duplicate guards (status, journal key) present | PARTIAL | read | Staff close | P2 |
| L19 | GL entries for debit/credit | partial `fd_journals`; no chart of accounts; non-FD flows unjournaled | PARTIAL | read (CL-02/03) | See Report 2 ledger plan | P1 |
| L18 | Notifications (email/WhatsApp), `notifications_log` | none | MISSING | grep (A1-004) | — | P1 |
| L20 | E2E lifecycle + role validation | `scripts/verify-kafka-demo.ps1` (Windows only, unrun) | PARTIAL | not executed | Port script to bash | P2 |
| L21 | `/report/fd-summary`, `/report/customer-portfolio`, CSV | `/api/reports/portfolio`, `/operational` (JSON, per currency); no UI | PARTIAL | read (A1-018) | Alias + product grouping + CSV + UI | P1 |
| L22 | Architecture/ER/deployment diagrams, Swagger/Postman, test plan, versioning notes | none for active stack; legacy Postman/OpenAPI stale | MISSING | search (A1-012) | Create | P1 |
| L23 | Azure App Service deployment, HTTPS, scaling | none | MISSING | search | — | P1 |
| L24/L25 | Final run, presentation | in-lab | OUT OF SCOPE | — | — | — |

---

## 3. Module-by-Module Assessment

Legend for "works": all UNVERIFIED at runtime unless Appendix R says otherwise.

### 3.1 Authentication and authorization
- **Required:** L1–L4, L9, L10; evaluation 10 + 5 marks.
- **Current:** `AuthController` (register/login/logout/refresh/MFA/lockout), JWT HS512, method security, token-version revocation in Identity.
- **Works (static):** bcrypt, no role escalation on signup, uniform login error, ownership scoping.
- **Broken/incomplete:** revocation ignored by Product & FD (SE-001/A1-011); MFA and password reset have **no UI** (UI-04, KY-08/09); no rate limiting at gateway (SE-009); 24 h token with unlimited refresh (SE-008); login discloses account state (SE-010); token in `localStorage`, role read from a second `localStorage` key (UI-06, SE-017).
- **Missing deps:** none. **Remaining:** apply shared token-version check or short expiry; add redirect-on-401.

### 3.2 Customer onboarding and identity
- **Required:** L3/L4 integration with Customer module; PII masking (5 marks).
- **Current:** `KycService`, `CustomerAdminController`, signup + KYC UI.
- **Works:** masked proof-ID in API/UI; lockout; best test coverage in repo.
- **Broken:** signup collects address/city/postal/home-branch that the DTO and DB drop (KY-06); no duplicate prevention for email/phone/government ID (KY-04); ID number and uploaded document stored unencrypted and unreadable by staff (KY-03); any officer can set arbitrary KYC/risk strings with no audit (KY-02).
- **Remaining:** see KYC plan in Report 2 §7.

### 3.3 Product and Pricing
- **Required:** L5, L9; 10 marks.
- **Current:** `ProductCatalogController`, `FdOfferController`, `ProductConfigurationController` (builder), outbox to FD.
- **Works:** CRUD, categories, currencies, rate bands, charges, activation, role protection.
- **Gaps:** no `/api/product/search` with productType/date range (A1-014); audit user recorded as `'API'` (A1-021); customers can list DRAFT/INACTIVE products (SE-005b); offer API hard-codes `QUARTERLY`/`AT_MATURITY` (FD-12); no solution document or API samples (L5-07).

### 3.4 FD Calculator
- **Required:** L6, L10, L11; 10 marks.
- **Current:** `POST /api/calculator/estimate` (`FdCalculatorController`).
- **Works:** simple and compound formulas; the compounding helper behaves per its unit tests (executed).
- **Broken/missing:** no combined cap, single category, hard-coded boosts; rate entered as percent while products use fractions (FD-11); not CUSTOMER-restricted; no UI; no Python/CSV; uses `double`.

### 3.5 CASA and account management
- **Required by manual:** none directly (savings account is the payout target in L15/L19).
- **Current:** `SavingsAccountController`, `StaffAccountController` (open, list, demo-opening, staff deposit/withdraw).
- **Works:** `FOR UPDATE` row locks, non-negative CHECK, idempotency, KYC gate on FD funding.
- **Broken:** unlimited accounts per holder/currency, no KYC on opening (CL-06); demo credit enabled in compose (CL-01); staff deposit/withdraw unlimited, no actor in ledger, no journal (CL-02/14, SE-003); no freeze/close lifecycle (CL-15).

### 3.6 Account-to-account transfers
- **Required by manual:** no. **Expected by team:** yes.
- **Status: MISSING in the active system.** No controller, gateway route or UI call. The legacy monolith had it (`SavingsTransferService`, V31) — a *regression from legacy*, not a manual gap (CL-08). A fully specified atomic/idempotent design is in Report 2.

### 3.7 Fixed Deposit creation and funding
- **Required:** L12, L13, L16; part of 35 marks.
- **Works (static):** booking validates product/tenure/amount against the Kafka-synced snapshot; funding is a single atomic transaction with journal and idempotency; KYC and risk gate.
- **Gaps:** customer self-service instead of BANK_OFFICER; account number `FD-<16 hex>`; no `create-with-txn`/initial DEPOSIT; no booking-time category adjustment (A1-020); **seeded products/currencies never reach fd-account-service until a write triggers a Kafka snapshot** (AR-03); concurrent duplicate idempotency key may 500 (FD-14, UNVERIFIED).

### 3.8 Interest accrual and payout
- **Works:** daily compounding on balance with 8-dp amounts, per-date idempotency, force-capitalisation on last period; accrual vs `FdCompounding` agree on hand check.
- **Gaps:** daily accruals are never journaled (GL only at maturity); payout frequency (monthly/quarterly) stored but never executed; `fd_interest_payout` unused (CL-11); `businessDate` parameter unbounded for officers (FD-05/CL-07).

### 3.9 Premature withdrawal and penalties — **primary reported failure**
- See Report 2 §4B for the 12-point trace. Summary: endpoints exist and are routed; **the customer UI calls the staff-only approve endpoint (403)**; products without a `CLOSURE_PENALTY` rule return 409; penalty is *rate points on simple interest* (not manual's "% of accrued interest"); quote is frozen at request time and paid unchanged; **no interest-expense journal** so FIXED_DEPOSIT goes negative; PENDING requests cannot be rejected/cancelled. Duplicate-payout protection is good (idempotency + status + row lock + journal key).

### 3.10 Maturity processing
- Selection, double-processing guards (status, `AND fa_status='ACTIVE'`, journal key) are good. But the batch only sets MATURED; payout waits for the customer's Close; `runDaily` loses its transaction through self-invocation so a mid-batch failure can leave a MATURED FD with no journal that **can never be closed** (FD-04/CL-05).

### 3.11 Batch jobs and scheduling
- **FAIL.** No `@Scheduled` anywhere in active fd-service (only the outbox poller). Triggers: admin demo-clock "advance" or manual officer POST. No run logging; no per-account failure isolation. Legacy `FdBatchScheduler` (00:00/01:00 IST) not ported.

### 3.12 Transaction history and ledger consistency
- `savings_transactions` (single-sided statement), `fd_transactions`, `fd_journals` + `fd_journal_lines` (balanced two-line journals). **Partial journal model, not a complete double-entry ledger.** Detailed assessment in Report 2 §5A and Appendix L (below).

### 3.13 Forex
- **Beyond manual.** `LoanForexController` stores a *request* with a configured markup. No conversion, no rate source, no source/destination account, no ledger, nothing processes or cancels a request. The UI is honest ("Indicative requests only"). Only one pair/direction is tested (FK-01…06).

### 3.14 KYC
- **Beyond manual** (the manual only requires PII masking). Format-only auto-verify instantly sets VERIFIED and unlocks FD/loan/forex (KY-01/SE-002).

### 3.15 Loans
- **Beyond manual.** Application captures no occupation/employer/income/obligations/documents (LN-02); disbursement credits savings with no receivable, schedule, repayment or journal (LN-01); approve/disburse don't re-check KYC (LN-03); same admin can approve and disburse (LN-04).

### 3.16 Reporting and exports
- `FdReportController`: portfolio, per-user portfolio, operational. Customer isolation OK. Paths differ from L21; grouped by currency not product; no interest-history or maturity-summary report; no CSV/PDF/Python; no UI.

### 3.17 Alerts and notifications
- **MISSING.** No email/SMS/WhatsApp, no `notifications_log`; tables `fd_notices`, `fd_maturity_instructions` unused; Kafka `fd.lifecycle` events have no consumer.

### 3.18 API documentation and testing
- No Swagger/OpenAPI on active services; Postman collection and OpenAPI are legacy-only and reference dead routes (`/fd/v1/accounts`, QA-06). Tests: 43 tests/9 classes in services, all integration tests Docker-gated, none run here; key FD money scenarios untested (QA-03/04); frontend specs unrunnable (QA-05).

### 3.19 Microservices integration
- Sound pattern (outbox → Kafka → local reference replicas) but fragile at first start (AR-03), no dead-letter handling (AR-04), outbox pollers not multi-instance safe (AR-05), build-time dependency on `BACKEND/` (AR-02), no service-level healthchecks (AR-10), dependent services honour revoked tokens (SE-001). Gateway: every UI call has a routed controller (no dead calls — UI agent), but the **frontend's own nginx proxies `/fd/` to the gateway**, colliding with SPA routes (UI-03) and the production environment hard-codes `http://localhost:8080` (UI-13).

### 3.20 UI/UX and responsiveness
- Builds with warnings; no calculator, reports, batch trigger, MFA, password-reset screens; staff cannot approve premature closure; dashboard is a link hub; i18n check fails; unit tests cannot run; a11y partially good (87 aria attributes, skip-link) with `outline: none` on glass inputs; mobile (375 px) login card appears clipped (possibly a headless-Chrome minimum-width artefact — UNVERIFIED). See `evidence/agent6-frontend.md` UI-01…15.

### 3.21 Deployment and operational readiness
- Compose only. `docker-compose.yml` is **modified and the frontend/gateway Dockerfiles are untracked**; HEAD's compose has no frontend, so a fresh clone cannot build the stack (AR-01). `.env.example` JWT placeholder is not base64 though code requires it (AR-07); setup scripts are PowerShell only (AR-08). No CI, k8s, Azure, HTTPS, probes, scaling, correlation IDs, backup (AR-09/10/13/17). Published host ports include DBs, Kafka and an unauthenticated Kafka UI (AR-16, SE-006).

---

## 4. Required Deliverables Missing From the Repository

**Explicitly required by the manual (L22 and others):**

| Deliverable | Status |
|---|---|
| Solution documents with API signatures/samples for Product & Login (L5) and Calculator & FD (L6) | MISSING for the active stack (`FD_MODULE_IMPLEMENTATION.md` describes the legacy system) |
| Solution architecture diagram | MISSING |
| ER diagrams | MISSING (SQL exists: Flyway V1–V10 in `BACKEND/`; root `0x_moneypal_*.sql` are stale) |
| Deployment diagram (cloud) | MISSING |
| Module-wise Swagger/OpenAPI | MISSING (legacy-only, stale) |
| Postman collections | STALE (legacy FD only) |
| Test plan (unit, integration, E2E, security, batch) | MISSING |
| API versioning / backward-compat notes | MISSING (mixed `/api/v1`, `/api`, `/fd/v1`) |
| Python report script, CSV extracts | MISSING |
| Docker, Kubernetes, Azure deployment steps | Docker PARTIAL; K8s/Azure MISSING |
| Final presentation (L25 slide structure) | In-lab (OUT OF SCOPE) |

**Additional recommended (not manual-mandated):** service dependency diagram; configuration/env-variable reference without secrets; batch-processing runbook; error-handling/recovery runbook; known-limitations document; demo guide (macOS/Linux, not just PowerShell); CI pipeline; dev container; bash port of `verify-kafka-demo.ps1`.

---

## 5. Academic Compliance vs. Banking-Grade Improvements

### A. Required for the lab (directly affects assessment criteria)
1. Scheduler-driven interest + maturity batches with logging and per-account failure isolation (L14/L17/L18) — **P0**.
2. Calculator: multi-category add-on with combined cap, product-defined max, Customer-only, UI integration (L6/L10/L11) — **P0**.
3. Working premature withdrawal end to end with penalty and payout (L15) — **P0**.
4. Maturity payout/close to savings with `MATURITY_PAYOUT`-style record and manual officer close (L18/L19).
5. Alerts (at least mock SMTP/log-based) and `notifications_log` (L18/L20).
6. `fd_statements` generation (L17).
7. Reports per L21 incl. CSV, and Python-generated report (L6/L11/§3) with UI (L23 presentation).
8. Product search by type/date (L5).
9. PII masking evidence for customer module (Eval 5 marks).
10. Documentation set + test plan + Swagger/Postman for the active stack (L22).
11. Dockerised deployment, k8s manifests or documented equivalent, probes, scaling, Azure steps (L23).
12. Decision recorded on deviations: self-service FD booking vs BANK_OFFICER; account-number pattern; DECIMAL vs DOUBLE (improvement).

### B. Recommended for stronger banking realism (not required by the manual)
Complete double-entry ledger with chart of accounts and reconciliation job; CASA transfer; revocation checks in all services; dual control and limits on teller operations; KYC human review and encrypted PII; loan receivable/repayment; forex quote with timestamped *simulated* rates; reversal/correction journals; outbox leader-election; correlation IDs; backups; rate limiting; secret management; CI.

---

## 6. Final Compliance Verdict

- **Satisfied (static, runtime unverified):** registration/login/hash/JWT/masked password; RBAC mechanism; product CRUD & role protection; simple/compound formulas; FD booking+funding; idempotency and locking; event publishing design.
- **Partially satisfied:** token revocation, PII masking, product search, calculator, FD creation roles, transactions, interest, maturity, reports, ledger/GL mapping, documentation, health probes.
- **Failed or missing:** batch scheduler, calculator cap + UI, premature withdrawal end-to-end, statements, alerts, file extract, Python reports, k8s/Azure/HTTPS/scaling, diagrams/test plan/Swagger for active stack.
- **Unverified:** everything that needs a running stack (see §0.2 and Appendix R).

### Top ten gaps before the final demonstration
1. Fix premature-withdrawal wiring (customer confirm or staff approve screen) and seed/enforce product penalty config (FD-01/02).
2. Turn the demo-opening credit off by default; journal it (CL-01).
3. Add a scheduler (or an honestly documented admin-triggered batch) with per-account transactions and logs (A1-001, FD-04).
4. Post the missing interest journal on premature closure (FD-07/CL-03).
5. Calculator cap + multi-category + UI (A1-002/003).
6. Make a clean checkout runnable: commit compose/frontend/gateway Dockerfiles, base64 JWT placeholder, bash setup script (AR-01/07/08).
7. Seed/republish reference data on startup so a fresh stack can book an FD (AR-03).
8. Maturity auto-settlement or visible next step + a notification log (FD-13, A1-004).
9. Reports (L21 paths, CSV) with UI, plus statements.
10. Documentation + test plan + one full recorded E2E run from a clean environment.

---

## Appendix L — Double-entry ledger summary (full detail: `evidence/agent3-casa-ledger-db.md`)

| Question | Answer (static) |
|---|---|
| Is double-entry genuinely implemented? | **Partially.** `fd_journals` (header, `total_debit = total_credit` CHECK, unique `fj_key`) + `fd_journal_lines` (one side positive per line). Every journal the code writes is two lines with equal amounts. |
| Which operations post journals? | FD funding (`FUNDING:<id>`), FD maturity interest (`INTEREST:<id>:MATURITY`), FD settlement (`SETTLEMENT:<id>`). |
| Which bypass it? | Staff deposit/withdraw, demo opening, loan disbursement (balance + one-sided `savings_transactions` only); daily accruals (off-ledger until maturity); premature-closure interest (CL-03). Transfer, forex, fees, repayment do not exist. |
| Chart of accounts / GL map? | None. `jl_account_code` is free text (`SAVINGS`, `FIXED_DEPOSIT`, `INTEREST_EXPENSE`); `gl_code_mapping` exists in the product DB but is never consumed. No cash/suspense/receivable accounts. |
| Header = Σ lines enforced? | Only by app convention; no trigger/FK; admin ledger shows header totals, not line sums. |
| Do balances reconcile to ledger? | Not possible today: non-FD movement has no journal. Statement table (`savings_transactions`) should reconcile to `sa_balance` (reconciliation SQL in CL V3). |
| Duplicate posting risk | Low for FD paths (unique journal keys + idempotency + status guards). `settle` skips journal creation if key exists yet still credits savings (CL-12). No "two independent ledgers" found; `savings_transactions` and `fd_journals` are complementary, not contradictory, but only one side is balanced. |
| Float arithmetic | None in money paths except the calculator (`Math.pow` double) which is quote-only. |
| Corrections / reversals | None; journals mutable via SQL; no contra workflow. |
| Reconciliation report | None. |
