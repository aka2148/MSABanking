# Agents 5+6+7 audit: Forex, Customer onboarding/KYC, Loans (read-only)

Repo copy: `<scratchpad>/repo` (abbreviated `R/`). Static review only; JDK 11 and no MySQL, so every runtime claim is UNVERIFIED unless stated. Paths below are relative to `R/`.
Abbreviations: LFC = `services/fd-account-service/src/main/java/com/bank/fdservice/LoanForexController.java`; KS = `services/identity-customer-service/src/main/java/com/bank/identitycustomer/KycService.java`; CAC = `.../CustomerAdminController.java`; AC = `.../AuthController.java`.

Scope class: the lab manual (`evidence/lab-manual-extracted.txt`) has no forex, loan, KYC or MFA requirement (grep for KYC/MFA/TOTP returned no hits). All three modules are **beyond-manual / demo-scope**. They matter for quality and security, not manual compliance. Do not count them toward manual coverage; do count their defects, because they ship in the demo and touch the savings ledger.

## 1. Feature status tables

### 1.1 Forex
| Capability | Status | Evidence / note |
|---|---|---|
| Customer catalog of forex products | implemented | `CustomerOfferController.java:31-50`; UI `FRONTEND/src/app/features/forex/forex-page.*` |
| Actual currency conversion / balance movement | **missing (by design)** | LFC:156-183 only INSERTs `forex_requests`; no savings read or write. UI says "No live quote, currency trade, or funds transfer is performed" (`forex-page.html` line 5). Honest labelling. |
| Rate source | partial | There is no FX rate. The only number is an admin-configured per-destination-currency "markup" band keyed on funding amount (LFC:214-219, `CustomerOfferController.java:83`). Labelled "Indicative request only; no live exchange quote" (`CustomerOfferController.java:91`). Template text claims "using a supplied reference rate" (`product-templates.ts:126`), which is misleading (FK-04). |
| Direction / inverse | partial | Funding must equal product `baseCurrencyCode`; destination must differ (LFC:172-173). Inverse (USD to INR) works only if a separate product has USD as base. No tests for inverse. |
| Rounding / precision | partial | Amount uses `RoundingMode.UNNECESSARY` against the currency scale (LFC:206-212), so it is strict and safe. Column `fr_amount DECIMAL(18,4)` and `fr_markup_rate DECIMAL(9,8)` (`BACKEND/.../fd/V7__loan_forex_requests.sql`) cannot hold currencies with up to 8 decimals (allowed by `ReferenceDataController.validatedScale` 0..8). |
| Spread / fees | missing | `fr_fee` is hardcoded 0 (LFC:179). Offers with configured `charges` are hidden from customers (`CustomerOfferController.java:66`). |
| Stale / missing rate | n/a | No rate is fetched. A missing band returns 400 "No configured pricing band" (LFC:218). |
| Unsupported pairs | implemented | 400 "Select the configured funding and destination currencies" (LFC:173); the test covers INR to INR. |
| Source / destination account currency | missing | No source or destination savings account on a forex request. |
| Ledger / transaction records | missing | No `savings_transactions` and no `fd_journals` row. |
| Atomicity / idempotency | implemented | `@Transactional`, unique `(customer, idempotency key)`, replay comparison (LFC:161-169). The replay check does not compare `productId` alone. It does compare product, amount and currencies (OK). |
| Approval workflow | **missing** | Status check allows `REQUESTED`/`CANCELLED` only, but no endpoint cancels, approves or processes (grep of LFC: only GET and POST). Requests sit in REQUESTED forever; staff have no queue. |
| UI wiring | implemented | Routes `/forex*` (`app.routes.ts:76-79`); `productAccessBlocked` gate via `CustomerProductWarning`. |
| Tests | partial | One forward-pair happy path, idempotent replay, ownership 404, and KYC/risk gate (`LoanForexIntegrationTest.java`, Testcontainers, skipped without Docker). No inverse, rounding, band-edge, or stale tests. UNVERIFIED (not run). |

### 1.2 Loans
| Capability | Status | Evidence |
|---|---|---|
| Product catalog | implemented | `CustomerOfferController`, `loan-page.ts` mode `catalog` |
| Application (amount, tenure, purpose, currency, destination savings) | implemented | LFC:54-85 |
| Applicant financial profile (occupation, employer, income, obligations) | **missing** | No such field in the DTO, UI, or DB (see 2). Occupation exists only in KYC (`customer_details.cud_occupation`) and is not joined to loans. |
| Documents | missing | UI text: "Document upload is not available in this lab" (`loan-page.html`) |
| Status workflow | partial | PENDING_REVIEW to APPROVED/REJECTED to DISBURSED (LFC:99-143). Schema allows ACTIVE and CLOSED (`ck_loan_status`) but no code ever sets them. |
| Approval roles | partial | `hasRole('ADMIN')` only (LFC:87-123). BANK_OFFICER cannot review. No maker-checker, no amount limits, and the same admin can approve and disburse. |
| Approved amount | partial | Always equal to requested (LFC:105); there is no partial approval. |
| Disbursement | partial | Credits the savings balance and inserts a `savings_transactions` row (LFC:136-140). No `fd_journals` double-entry lines, unlike FD booking (`FixedDepositController.java:82-84`). |
| Repayment schedule / EMI / liability | **missing** | Rate is stored (`la_annual_rate`) and displayed, but nothing is computed. The customer receives funds with no obligation recorded. The Flyway migration `V15__align_loan_repayment_schedule_version` in BACKEND is a legacy no-op for the old monolith. |
| KYC gating | partial | Submission is gated (LFC:59). Approve and disburse re-validate customer ACTIVE, product, limits and account (`revalidate`, LFC:228-237) but **not** KYC or risk (LN-03). |
| Audit trail | implemented | `loan_application_events` with actor, status and reason (LFC:242) |
| Idempotency | implemented | Submit key is unique per customer. Approve, reject and disburse are state-idempotent under `SELECT ... FOR UPDATE`. |
| Admin UI | partial | `/staff/loans` is `adminGuard` only. It lists customer id, amount and tenure; it shows no income, KYC or credit information because none is captured. |

### 1.3 Customer onboarding / KYC / auth
| Capability | Status | Evidence |
|---|---|---|
| Signup | partial | AC:51-70 creates users, credentials, role, customer, details, email and phone in one transaction. Several UI fields are dropped (KY-06). |
| Role assignment at signup | implemented (safe) | Server hardcodes `ROLE_CUSTOMER`, user type `CUSTOMER` (AC:56,59-62). The client sends `userType: 'CUSTOMER'` but the DTO ignores it (`RegisterRequest`, AC:189). No escalation path found. |
| Admin bootstrap | implemented (safe) | `DemoAdminBootstrap.java:37-72`: needs env-provided credentials, 20+ char password, no default. It resets the stored hash to the env value on each start. |
| KYC data model | implemented | `customer_kyc_profile` (cycles, confirmed_fields, masked id, optional document blob) and `customer_kyc_events` (`customer/V4__kyc_profile.sql`) |
| Drafts | implemented | `PUT /api/v1/customers/me/kyc/draft`, partial field saves with per-field errors (KS:55-101) |
| Auto-verify | implemented, **rubber stamp** | When 9/9 fields pass format checks, `cus_kyc_status` becomes VERIFIED immediately (KS:91-99). No human review, no document requirement, no checksum or external lookup (KY-01). |
| Staff KYC review queue | **missing** | No endpoint lets staff view submitted KYC data or the stored document. No endpoint reads `document_bytes`. |
| Manual status override | implemented, unsafe | `PUT /customers/{id}` and `PATCH /customers/{id}/status` (CAC:73-96) (KY-02) |
| Re-KYC | implemented | KS:103-117; customer self-service with 10-500 char remarks |
| Duplicate prevention | partial | Username unique. Email, phone and government ID are not unique (KY-04). |
| User/customer linkage | implemented | `customers.cus_user_id` UNIQUE (`customer/V1`). |
| MFA/TOTP | partial | Backend is sound (AES-GCM secret, replay-protected steps, lockout, token versioning, AC:96-153). **No frontend** for enroll, enable or verify (grep `mfa` in FRONTEND/src returned no hits); login UI cannot complete an MFA challenge (KY-08). |
| Password reset | partial | Backend exists at `/auth/password/forgot|reset` (note: no `/api/v1` prefix) and is off by default. **No frontend route or page** (grep `forgot|reset-password` in FRONTEND/src empty) although `docker-compose.yml:110` points the email link at `/reset-password` (KY-09). |

## 2. Loan field mapping (form to DTO to validation to DB to response)
| Field | UI (loan-page) | DTO `LoanRequest` | Server validation | DB column | Response |
|---|---|---|---|---|---|
| Applicant | session identity (not a form field) | n/a, from JWT `userId` (LFC:245) | `requireVerified` + ACTIVE | `la_customer_id` | `customerId`, `customerName`, `customerNumber` |
| Occupation | **not captured** | none | none | none (KYC occupation not linked) | none |
| Employer | **not captured** | none | none | none | none |
| Income | **not captured** | none | none | none | none |
| Existing obligations | **not captured** | none | none | none | none |
| Amount | `amount` number (min 0.01) | `@DecimalMin("0.01")` | scale strict, product min/max per currency (LFC:206-212) | `la_requested_amount DECIMAL(18,4)`; `la_approved_amount` copy | `requestedAmount`, `approvedAmount` |
| Currency | select (product currencies) | `currencyCode @NotBlank` | must be in offer; savings currency must match | `la_currency_id` | `currencyCode` |
| Tenure | `tenureMonths` | `@Min(1) int` | must fall in a pricing band (LFC:214) | `la_tenure_months` | `tenureMonths` |
| Rate | shown "annual sample rate" (`rate*100`), not an input | none | server picks band | `la_annual_rate DECIMAL(9,8)` | `annualRate` (fraction) |
| Purpose | textarea max 500 | `@NotBlank @Size(500)` | trimmed | `la_purpose` | `purpose` |
| Destination account | select (active, same currency) | `destinationSavingsAccountId @Min(1)` | must be owned, ACTIVE, same currency (LFC:221-226) | `la_destination_savings_id` | `destinationSavingsAccountId`, name, masked number |
| Documents | "not available in this lab" | none | none | none | none |
| Idempotency key | `crypto.randomUUID()` header | header `@NotBlank` | unique | `la_idempotency_key` | not returned |
| Product name | n/a | derived from offer | | `la_product_name` (V8, default `'Loan product'`) | `productName` |
| Decision fields | admin only | `Rejection.reason` | | `la_decision_admin_id`, `la_decided_at`, `la_rejection_reason` | returned |

Fields shown but not saved: none in the loan form. Fields a real lender needs but that are neither shown nor saved: occupation, employer, income, obligations, documents, collateral, co-applicant. The rate is saved and shown but never used for any calculation.

## 3. KYC gating matrix (backend)
| Operation | Backend check | Evidence |
|---|---|---|
| FD booking | `requireVerified` (VERIFIED and risk != HIGH) | `FixedDepositController.java:42` |
| FD funding | `requireVerified` | `FixedDepositController.java:67` |
| Loan apply | `requireVerified` | LFC:59 |
| Loan approve / disburse | **no KYC or risk check** (only ACTIVE) | LFC:228-237 (LN-03) |
| Forex request | `requireVerified` | LFC:160 |
| Savings open (customer) | **none** | `SavingsAccountController.java:80-83` |
| Savings open (staff) | none (role only) | `SavingsAccountController.java:85` |
| Savings demo opening credit | none (feature flag only) | `SavingsAccountController.java:52-62` |
| Savings transfers / deposits / withdrawals | not found in the gate grep (`requireVerified` has only the 4 call sites above) | `grep requireVerified` over services |

The gate reads `fd_user_ref`, a Kafka-fed snapshot. It is eventually consistent: a customer who has just been verified or rejected sees the old status until the snapshot arrives (`ReferenceSnapshotConsumer`; the test shows older versions are ignored, correct). UNVERIFIED at runtime.

## 4. Findings

Severity P0 (critical) to P3 (minor). Confidence: H/M/L.

### KYC / identity
**KY-01 | P1 | Business logic | KYC auto-verify is a format-only rubber stamp**
- Evidence: KS:91-99 (`confirmed.size()==9` sets VERIFIED); `KycValidation.java:13-19` (regex only); document optional (`kyc.html` "Document (optional)").
- Expected: a bank KYC needs document evidence and a reviewer, or at least an identity-check hook. Actual: any well-formed fake PAN/Aadhaar plus a DOB 18+ years ago yields VERIFIED in seconds and unlocks FD, loan and forex. Aadhaar uses no Verhoeff checksum.
- Root cause: deliberate demo shortcut (commit "auto-verify") without a labelled demo flag. Confidence H.
- Fix: gate auto-verify behind `app.kyc.auto-verify` (default off outside demo profile), else set status `UNDER_REVIEW` and require staff approval; label the UI "demo auto-verification".
- Test: with flag off, completing 9 fields leaves PENDING/UNDER_REVIEW; with flag on, VERIFIED plus an AUTO_VERIFIED event.
- Effort: M.

**KY-02 | P1 | Security / authorization | Bank officers can set any KYC or risk value without validation or audit**
- Evidence: CAC:73-96 (`PUT /{id}`, `PATCH /{id}/status`) under class-level `hasAnyRole('BANK_OFFICER','ADMIN')` (CAC:19). `kycStatus` and `riskCategory` are free strings (`UpdateCustomer`, CAC:109); DB has no CHECK on `cus_kyc_status` (`customer/V1`, line 116). `PATCH` does not verify the customer exists before UPDATE. No `customer_kyc_events` row and no actor is recorded; the event table's CHECK only allows AUTO_VERIFIED and REKYC_REQUESTED.
- Expected: constrained enum, existence check, audited decision with actor and reason, maker-checker for VERIFIED. Actual: an officer can mark anyone VERIFIED with no evidence, with no trace; a typo such as "VERIFED" silently blocks the customer (the gate requires exact VERIFIED, `CustomerProductAccess.java:14`).
- Root cause: admin convenience endpoint predates the KYC module. Confidence H.
- Fix: validate against `{PENDING,VERIFIED,REJECTED}` and risk enum; write a `STAFF_DECISION` event with actor and mandatory remarks; add a DB CHECK.
- Test: PATCH with "FOO" returns 400; a valid change adds an event row with actor.
- Effort: S-M.

**KY-03 | P1 | Privacy / compliance | Government ID stored in plaintext; uploaded document unreadable by staff and stored unencrypted**
- Evidence: `customer_kyc_profile.proof_of_id_value VARCHAR(20)` and `document_bytes MEDIUMBLOB` (`customer/V4`); KS:89 writes the raw value. API output is masked: `saved.remove("proofOfIdValue")` (KS:167), mask `****`+last 4 (KS:85), and `DraftRequest.toString` is redacted (`KycController.java:21-23`). I did not find a log statement for the value in these files. Good: the API never returns the full ID, which satisfies masking for the API and UI. Gaps: no encryption at rest; no staff read path for the document (it can only be reviewed via direct DB), so nothing consumes it.
- Expected: field-level encryption (or hash plus last 4) for ID numbers; evidence viewable by authorised reviewers with access logging. Actual: raw ID in DB, and the blob is write-only.
- Root cause: draft-first implementation. Confidence H (static).
- Fix: store only a keyed hash (for duplicate detection, see KY-04) plus the masked value, or encrypt as `TotpService` does; add an officer-only, audited document download.
- Test: DB column contains no cleartext; an officer fetch writes an audit row.
- Effort: M.

**KY-04 | P2 | Data integrity | No duplicate prevention for email, phone or government ID**
- Evidence: `register` checks only username (AC:54-55). `customer_emails.cem_email_address` has no UNIQUE (`customer/V1:169`; the unique list at lines 29-127 does not include it). Phone has none either. `proof_of_id_value` is not unique or hashed.
- Expected: one customer per ID; unique primary email. Actual: unlimited accounts with the same PAN, each auto-verified; password reset joins on email and takes `users.get(0)` (`PasswordResetController.java:51-53`), which is ambiguous with duplicate emails.
- Root cause: schema inherited from the monolith. Confidence H.
- Fix: unique index on normalised primary email; keyed hash plus UNIQUE on ID type+value, rejecting with 409 "already registered".
- Test: second signup with same email returns 409.
- Effort: M.

**KY-05 | P2 | Business logic | Self-service re-KYC lets a customer reset status and re-verify without any review; there is also no re-KYC trigger from the bank side**
- Evidence: KS:103-117, `KycController.java:27`. Also a bank-set REJECTED correctly freezes editing (KS:43). Direction is safe (blocks products until completed) but meaningless given KY-01.
- Fix/test: covered by KY-01. Effort: S.

**KY-06 | P2 | Contract mismatch | Signup collects fields that are dropped**
- Evidence: UI sends `firstName,lastName,preferredLanguage,legalEntityType,homeBranchId,addressLine,city,postalCode,mobileNumber->phone,address,credentialType,userType` (`signup.ts:354-370`). `RegisterRequest` (AC:189) accepts only username, password, email, fullName, phone, address, legalEntityType. `address` is accepted but never inserted (AC:62-69); `homeBranchId`, `city`, `postalCode` and `preferredLanguage` are required in the UI form but discarded. Phone is not validated server side and `legalEntityType` is free text.
- Expected: either persist or do not collect. Actual: user supplies data that vanishes (looks like a captured address, but there is none).
- Root cause: "slim signup" commit left UI ahead of DTO. Confidence H.
- Fix: persist to existing `customer_addresses`/branch tables, or remove the fields; server-side `@Pattern` for phone and enum for entity type.
- Test: register then read back address.
- Effort: S-M.

**KY-07 | P2 | Security | Registration immediately issues a JWT; no email verification; no rate limiting; check-then-insert race**
- Evidence: AC:51-70 returns `response(...)` with a token; password policy is length 8-100 only (`RegisterRequest`); username uniqueness is checked with COUNT then INSERT, so a concurrent duplicate hits the UNIQUE index and likely yields a 500 (UNVERIFIED). The UI redirects to login anyway.
- Fix: catch `DuplicateKeyException` as 409; do not return a token on register (or require email verification); add throttling at the gateway.
- Test: parallel registers produce one 201 and one 409. Effort: S.

**KY-08 | P2 | UI wiring / security | MFA has no frontend; admins are not forced to use MFA**
- Evidence: no `mfa` references in `FRONTEND/src`; backend is complete (AC:84-153). A user who enabled MFA via API cannot log in through the UI (the login screen has no challenge step). MFA disable needs only a TOTP code (not the password). Enrolment via `/mfa/enroll` re-issues a new secret if MFA is not yet enabled (acceptable).
- Fix: add a login challenge step and a security settings page, or hide the endpoints; consider requiring MFA for ADMIN.
- Test: Playwright flow login with MFA. Effort: M.

**KY-09 | P3 | UI wiring | Password reset has no frontend and is disabled by default; endpoint enumeration signal**
- Evidence: `PasswordResetController.java:27,95` (needs flag and key); no route in `app.routes.ts`. `forgot` returns 503 only when the account exists and mail fails (lines 51-63), which leaks account existence when mail is misconfigured. Token handling is otherwise sound: 256-bit random, SHA-256 stored, 30-minute expiry, single use, token-version bump (lines 54-81).
- Fix: build `/forgot-password` and `/reset-password` pages; always return the generic 202 and log mail failures. Effort: M.

**KY-10 | P3 | Security | Login reveals account state before password check**
- Evidence: AC:77-79 return 403 "Account is not active" and 423 "temporarily locked" before verifying the password, so state is observable without credentials.
- Fix: verify the password first, or use generic errors. Effort: S.

### Loans
**LN-01 | P1 | Business logic | Disbursement creates money with no loan liability, repayment schedule or ledger journal**
- Evidence: LFC:123-143 credit the savings balance; no table or code for schedule, EMI, outstanding balance, or repayment; statuses ACTIVE/CLOSED are never set. FD flows write `fd_journals` and `fd_journal_lines` (`FixedDepositController.java:82-84`); loans do not, so the admin ledger report (`FdLedgerAdminController`) cannot reconcile loan credits.
- Expected: disbursement posts a balanced journal (Dr loan receivable, Cr savings) and generates a schedule; the UI says "Submitting an application does not move money", but disbursement does. Actual: free money, unbalanced ledger.
- Root cause: workflow scoped as "internal loan disbursement" demo (`st_remarks`). Confidence H.
- Fix: label the module demo-only in the UI and DB remarks, or add schedule and journal; minimum: write a balanced `fd_journals` row.
- Test: after disburse, journal debits equal credits and the savings balance changes by exactly the amount. UNVERIFIED (needs MySQL). Effort: L for full, S for the journal.

**LN-02 | P2 | Contract / completeness | Loan application lacks applicant financial data**
- Evidence: field map in section 2. No occupation, employer, income, obligations or documents anywhere; reviewers decide on amount and purpose only.
- Fix: add fields to `LoanRequest`, columns in a new migration, UI inputs, and show them on `/staff/loans/:id`. Effort: M.

**LN-03 | P2 | Security / business logic | Approve and disburse do not re-check KYC or risk**
- Evidence: `revalidate` (LFC:228-237) calls `requireCustomerActive`, offer, limits and savings only; `requireVerified` is called only at submit (LFC:59).
- Expected: a customer moved to REJECTED, PENDING (re-KYC) or HIGH risk after applying must not be funded. Actual: they can be approved and disbursed.
- Fix: call `CustomerProductAccess.requireVerified` in `revalidate`.
- Test: set the customer to REJECTED after submit; approve returns 403. Effort: S.

**LN-04 | P2 | Authorization | Single-role, no maker-checker, no limits on loan decisions**
- Evidence: LFC:87-123 `hasRole('ADMIN')`; `adminGuard` in routes; the same admin may approve and disburse; there is no amount threshold. BANK_OFFICER (the customer-facing staff role) cannot work the queue.
- Fix: allow BANK_OFFICER to approve below a threshold, require a different ADMIN for disbursement; record both actors (the disburse event already records the actor). Effort: M.

**LN-05 | P3 | Data integrity | Amount columns narrower than currency scale; error on large scales**
- Evidence: `DECIMAL(18,4)` for loan and forex amounts vs allowed currency scale up to 8 (`ReferenceDataController.validatedScale`). A currency with more than 4 decimals would be silently rounded or rejected under strict mode (UNVERIFIED).
- Fix: use DECIMAL(24,8) or cap decimal places at 4. Effort: S.

**LN-06 | P3 | Observability | Approved amount always equals the requested one; `la_approved_amount` is redundant**
- Evidence: LFC:105. Fix: accept an optional approved amount (<= requested) at approve. Effort: S.

**LN-07 | P3 | Test gap | Disbursement balance and ledger not asserted**
- Evidence: `LoanForexIntegrationTest.java` asserts one `savings_transactions` row per `LOAN:<id>` but not the resulting balance, the `st_balance_after`, concurrency, or revalidation failure paths; the whole class is skipped without Docker (`disabledWithoutDocker = true`).
- Fix: add balance assertions and a concurrent-disburse test. Effort: S.

### Forex
**FK-01 | P2 | Completeness | Forex is a request log that nothing ever processes**
- Evidence: LFC:145-183 (no cancel, approve or complete endpoint); status CHECK allows only `REQUESTED`/`CANCELLED`, and no code sets CANCELLED. No staff UI (no forex route under `/staff`). The UI is transparent: "Indicative requests only".
- Expected: either an explicit "demo, informational" label in the product docs, or a staff queue with fulfilment. Actual: customers file requests that no one can see or act on.
- Root cause: scope cut. Confidence H.
- Fix: add `GET /api/v1/admin/forex-requests` plus cancel; or remove the "request" language. Effort: M.

**FK-02 | P2 | Business logic | No rate or quote exists, so conversion cannot be computed; the persisted "markup" is the only price**
- Evidence: LFC:177 stores `fr_markup_rate`; no `fx_rate` table or source is used. `CustomerOfferController.java:91` and `forex-page.html` disclose "no live reference rate is supplied". The rate source is therefore admin-configured, not hardcoded, and labelled.
- Fix: add a rate table with timestamp and staleness check if conversion is ever wanted; otherwise keep the labels. Effort: L.

**FK-03 | P3 | Data | Forex row does not capture a source or destination savings account**
- Evidence: `forex_requests` columns (`fd/V7`). Fix with FK-01. Effort: S.

**FK-04 | P3 | Content | Template description promises "a supplied reference rate" that does not exist**
- Evidence: `FRONTEND/src/app/features/staff/product-builder/product-templates.ts:126`. Fix: reword. Effort: S.

**FK-05 | P3 | Usability | Products with charges or eligibility rules silently vanish from the customer catalog**
- Evidence: `CustomerOfferController.java:65-66` return null with a comment ("Request workflows do not execute configured charges"). An admin configuring a fee sees the product disappear without an explanation. Fix: surface a publish-time warning in Product Builder. Effort: S.

**FK-06 | P3 | Test gap | Only one pair and one direction tested**
- Evidence: test lines for `/forex-requests`. Add inverse (USD base), band-edge (valueFrom/valueTo inclusive both ends, so ranges that touch at boundaries are ambiguous: the first matching band wins, LFC:215-217), and 8-decimal currency cases. Effort: S.

### Schema drift
**KY-11 | P3 | Documentation / schema | Root SQL files omit loan, forex and KYC tables**
- Evidence: `grep -in 'loan|forex|kyc' 01_*.sql 02_*.sql 03_*.sql` matches only `cus_kyc_status` in `01_moneypal_customer.sql:119`. The real schema comes from Flyway under `BACKEND/src/main/resources/db/service-migration/**` (V7 fd, V4 customer). Anyone reviewing the DDL files alone would miss these tables and think the features are absent.
- Fix: regenerate the root SQL from the migrations or mark it legacy in the README. Effort: S.

## 5. What was checked and is sound
- Role escalation at signup: none. Role is fixed server side, DTO ignores client `userType`.
- Admin bootstrap: env-only, no baked-in credentials (`DemoAdminBootstrap.java`); `.env` is gitignored (`.gitignore:2`; contents not read).
- Passwords are BCrypt; the last 5 hashes are blocked; lockout after 5 failures (15 min); token version invalidates JWTs on password change, role change, MFA change and logout.
- TOTP: AES-GCM at rest, 6-digit code with +/-1 step drift, used steps recorded in `mfa_used_totp_steps` (replay-safe), MFA challenge token cannot act as an access token because the filter requires a `tokenVersion` claim (`JwtAuthenticationFilter.java:30-33`).
- KYC API never returns the full ID; draft request logging is redacted; uploads are sniffed by magic bytes, 5 MB cap, filename sanitised (KS:122-147).
- Loan/forex ownership checks and idempotency behave as the test describes (static reading; UNVERIFIED at runtime).

## 6. Verification steps for UNVERIFIED items
1. `docker` available: `cd services/fd-account-service && mvn -q test -Dtest=LoanForexIntegrationTest` and `cd services/identity-customer-service && mvn -q test -Dtest=Kyc*Test`.
2. LN-01: after disburse, `SELECT * FROM fd_journals WHERE fj_key LIKE 'LOAN%'` should be empty (confirms the gap).
3. KY-02: as BANK_OFFICER, `PATCH /api/v1/customers/{id}/status {"status":"VERIFIED"}`, then check that no row appears in `customer_kyc_events`.
4. KY-07: fire two concurrent `/auth/register` calls with one username and record the status codes.
5. KY-04: register twice with the same email and confirm both succeed.
