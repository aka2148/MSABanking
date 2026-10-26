# Final MoneyPal Release Audit

**Audit date:** 2026-10-26  
**Repository:** `aka2148/MSABanking`  
**Current HEAD:** `a1c705dfa24ddf525fc206e00521110e2e56dc2e` (`main`)  
**Decision:** **CONDITIONAL GO for a supervised, synthetic-data local lab demo. NO-GO for production or a fully integrated release.**

The active stack is Identity/Customer, Product, FD Account, Kafka, three MySQL databases, API gateway, Angular/Nginx, and Kafka UI. Legacy `BACKEND/` Java is not an active request path; its `service-migration` directories remain the migration source used by active services.

## Audit basis and working state

The checkout contains `audit-reports/REPORT_1_DELIVERABLES_COMPLIANCE_AUDIT.md` and its companion `REPORT_2_CRITICAL_DEFECTS_AND_ACTION_PLAN.md`, plus their evidence files. The companion report contains findings D-01 through D-43 and identifies a Claude scratch environment. No separately titled OpenCode report is present. I therefore assessed every D-ID supplied in the request against current HEAD, but cannot independently attribute the overlapping IDs to an OpenCode-specific report.

The audit reports assessed an older extracted copy at `8dce24e`; their findings were rechecked against current HEAD rather than carried forward as facts. I also read the accepted Shaunak, Sudhiksha, and Krishang handoffs and regression reports, and the later-changes handoff. Current HEAD includes their accepted work through `a1c705d`.

The working tree contains the prior I2 changes, the I3 reporting/frontend/docs changes, and the focused I4 storage-range validation. These changes are uncommitted. Nothing was committed, pushed, merged, or deployed.

## Verification results

| Workstream | Status | Evidence / remaining limit |
| --- | --- | --- |
| I1 — integrated audit | Complete | HEAD/branch, active stack ownership, current audits, accepted handoffs, API/migration/test surfaces reviewed. Audit finding labels below are checked against current working-tree code. |
| I2 — platform integration | Implemented and verified | Fresh isolated stack, real gateway OTP/KYC review, Kafka reference retry, eligible product, FD booking/funding and current-token behavior passed. No shared/live DB touched. |
| I3 — reports/frontend/docs | Implemented; browser checks unverified | Existing statement/notice data, customer/staff reports, role filtering, currency grouping, CSV, Angular screens, OpenAPI/Postman and demo docs updated. Python L6 report scope remains unresolved. |
| I4 — integrated API workflow | Passed | Synthetic gateway flow reached CLOSED FD, transfer, reports and zero reconciliation mismatches. No commit, merge, or deployment. |
| T1 — independent platform check | Passed for tested scope | Three current Maven suites and isolated Compose gateway E2E passed; current service totals below. Browser automation is unavailable in the project. |
| T2 — release regression | Conditional / incomplete | Financial workflow and selected authorization/idempotency/revocation checks pass. Full hostile-ID, cross-owner, concurrency, injected rollback, currency/timezone boundary and Angular browser matrix is not fully evidenced; see D-26/D-36/D-37. |

| Check | Result |
| --- | --- |
| Identity service | `mvn -q -f services/identity-customer-service/pom.xml test` — **24 passed, 0 failed, 0 errors, 0 skipped**; clean Testcontainers migration through Customer V11. |
| Product service | `mvn -q -f services/product-service/pom.xml test` — **24 passed, 0 failed, 0 errors, 0 skipped**; clean Testcontainers migration through Product V8. |
| FD Account service | `mvn -q -f services/fd-account-service/pom.xml test` — **45 passed, 0 failed, 0 errors, 0 skipped**; clean Testcontainers migration through FD V14. |
| Focused D-42 regression | `LedgerInvariantIntegrationTest#tellerOperationsAreJournaledAndOpeningDefaultsOff` — **1 passed, 0 failed, 0 errors, 0 skipped**; this is an extra run of a test included in the 45-test FD suite |
| Focused monthly statement regression | `FdBatchSafetyIntegrationTest#dailyRunIsAtomicPerAccountResumableAndTimezoneStable` — **1 passed, 0 failed, 0 errors, 0 skipped**; verifies month-boundary backfill after clock jumps and idempotent reruns |
| Aggregate current service suites | **93 tests, all passed, zero skipped** (Identity 24 + Product 24 + FD 45). |
| Angular production build | Passed. Existing warnings remain: initial bundle 915.04 kB vs 512 kB budget; `fd-account-detail.scss` 6.17 kB and `product-builder.scss` 8.19 kB vs 4.10 kB each. |
| MPIN source check | `npm run check:mpin` passed. |
| Translation checker | `npm run check:i18n` passed: **884 messages across six languages**, including placeholders, switching, persistence, RTL, fallback, and blocked-storage checks. |
| OpenAPI/Postman syntax | OpenAPI YAML parsed with 43 paths; Postman JSON parsed with group sizes 8/18/10. |
| Whitespace | `git diff --check` passed; only LF/CRLF normalization warnings were printed. |
| Isolated Compose rebuild | `docker compose -p moneypal-i2-check up --build -d` passed after the D-42 change. The named volumes were preserved. |
| Focused KYC/Kafka regressions | Identity KYC/migration tests **16 passed, 0 failed/errors/skipped**; FD flow test **1 passed, 0 failed/errors/skipped**. These targeted runs were followed by the full current 93-test service run above. |
| Fresh integrated gateway flow | **Passed** on disposable project `moneypal-i5-20261026`; exact results below. |

### Final isolated gateway run

- Fresh project used unique `moneypal-i5-20261026` volumes, alternate localhost ports, and a local-only OTP mail mock. It was removed after verification; the pre-existing `moneypal-i2-check` project and its volumes were not changed.
- Gateway health **200**; registration **201**; KYC submit **200/PENDING**; staff review **200/VERIFIED**; offers/calculator **200**.
- Savings open initially returned retryable **503 USER_REFERENCE_NOT_READY**; bounded retry succeeded on attempt 2. Replaying the same idempotency key returned the same account ID.
- FD booking **201**; funding, batch, maturity, close, transfer, reports and CSV **200**; customer request for staff summary **403**; logout **204**; revoked-token reuse **401**; `/reports` SPA deep link **200 text/html**.
- Synthetic readbacks: FD `CLOSED`, INR principal 1000.00 selected from live Product terms, 30 accrual rows, 33 FD history rows, one statement, two notices, and two staff summary groups. Reconciliation reported zero journal-header, history-sequence, and savings journal/balance mismatches.
- Testcontainers used disposable MySQL 8.4 databases and applied Identity through V11 and FD through V14 from empty schemas. No shared/live database was queried or modified. No secrets, OTPs, or access tokens were printed.

The earlier I2 runtime evidence on `moneypal-i2-check` covered OTP signup/login through local mail capture (no external email), current Identity session/KYC checks, Kafka user/product references, eligible Product offer lookup, FD booking/readback, and revoked-token denial. A second fresh-stack run completed the full synthetic gateway API flow described below. Neither run contacted the configured external email provider.

## Prioritized audit findings against current code

Status labels follow the requested set: `FIXED+TESTED`, `IMPLEMENTED-UNVERIFIED`, `OPEN`, and `OUT-OF-SCOPE`.

| Finding | Status | Current evidence and remaining issue |
| --- | --- | --- |
| D-06 — premature-closure penalty configuration | **FIXED+TESTED** | The approved default FD template includes the previously accepted 1.0000 percentage-point penalty. Booked terms are frozen; missing legacy terms fail closed with a clear error. No new rate or penalty was invented here. |
| D-07 — incomplete money journal coverage | **FIXED+TESTED** | Shared posting and chart-of-accounts paths cover active savings/teller/demo/transfer movements; FD funding, interest, and settlement journal paths have service integration coverage. Loan disbursement remains disabled until receivable accounting exists. Historical entries were not rewritten. |
| D-08 — savings transfer absent | **FIXED+TESTED** | The active FD service implements an atomic, idempotent transfer with stable lock order and balanced entries; transfer concurrency, replay, rollback, and reconciliation are covered in the FD suite. |
| D-09 — startup references fail to reach FD | **FIXED+TESTED** | FD returns retryable 503 with `Retry-After` and a stable code until the Kafka user snapshot arrives; the client performs bounded retries. Fresh gateway E2E observed the 503, succeeded on retry 2, and verified idempotent replay returned the same savings account. |
| D-10 — clean-clone readiness blocked by uncommitted files | **OPEN** | The current dirty working tree builds and runs, but a clean clone cannot contain uncommitted I2–I4 files. No commit was made because the instruction prohibits committing without authorization. |
| D-11 — Flyway source lives under `BACKEND/service-migration` | **OUT-OF-SCOPE** | Active services load these migration resources and clean MySQL tests apply them successfully. The legacy Java monolith is not an active service; migration-source relocation is unnecessary for this focused release work. |
| D-12 — local environment template/setup | **IMPLEMENTED-UNVERIFIED** | `.env.example` now directs developers to the random local credential generator instead of copying placeholders. The current `.env` successfully starts Compose and authenticates the local demo admin. A fresh-directory generator run and non-Windows setup remain unverified. |
| D-16 — maturity payout/closure path | **FIXED+TESTED** | Batch records maturity and recognized interest; the explicit close step posts settlement to savings. The FD integration workflow covers maturity, closure, and idempotency. The batch intentionally does not automatically close the account; the demo workflow exposes the separate close action. |
| D-17 — logout/revocation on Product and FD | **FIXED+TESTED** | Product and FD validate the current Identity session; the isolated runtime denied a revoked token on both services. |
| D-18 — KYC form completion auto-verifies | **FIXED+TESTED** | Customer submission persists PENDING and locks edits. The existing authorized staff status route accepts VERIFIED/REJECTED decisions only after submission; it records the decision and reviewer ID in existing event remarks and publishes the updated snapshot. V11 expands only the event type constraint. Focused Identity tests and fresh gateway staff-review flow pass. |
| D-23 — reports/API/CSV/UI/Python | **OPEN** | Requested I3 customer/staff reports, owner/role filtering, currency grouping, CSV, and Angular report screens are implemented and verified through gateway/build checks. The separate Python-based calculator report described by L6 remains absent; whether it is required for this active milestone needs a lab-scope decision. |
| D-24 — statements and notifications | **OPEN** | The gateway flow persisted one monthly statement and two in-app notices. FD batch processing now backfills statement boundaries crossed by a simulated clock jump, with idempotent rerun coverage. There is no email/WhatsApp delivery or external notification-log sink. |
| D-25 — Azure/Kubernetes/production DevOps | **OUT-OF-SCOPE** | Only local Docker Compose was exercised. Architecture/deployment docs state that no cloud deployment was run. |
| D-26 — incomplete regression infrastructure | **OPEN** | Current Identity/Product/FD suites: 93 passed, 0 failed/errors/skipped; one full synthetic gateway journey passed. Angular has no configured unit-test target; browser-driven journeys and the broader security/concurrency/rollback matrix remain unverified. |
| D-34 — Kafka/outbox resilience | **OPEN** | Initial reference snapshots work in the isolated flow. No dead-letter queue or complete poison-event/outbox recovery mechanism was added or tested. |
| D-35 — health/readiness/correlation | **IMPLEMENTED-UNVERIFIED** | Compose healthchecks and gateway readiness return healthy in this run. Correlation IDs remain absent, and Kafka UI itself has no healthcheck. |
| D-36 — broader security hardening | **OPEN** | Current-token validation is fixed. The audit's rate-limiting and network-exposure concerns remain; Docker-published ports bind on all host interfaces in this Compose profile. MFA challenge-token and inactive-product negative paths were not rerun in the integrated stack. |
| D-37 — frontend integration | **IMPLEMENTED-UNVERIFIED** | The `/fd/` SPA collision is fixed, production API base is relative, report/account deep links return the SPA, production build and 884-message translation check pass, and the interceptor handles 401/403. Browser-level journeys, expiry navigation, and large KYC upload behavior remain unverified. |
| D-41 — timezone-sensitive accrual dates | **FIXED+TESTED** | FD batch/accrual tests pass under the machine's IST timezone; business dates use the UTC demo clock and date values round-trip in the integration suite. |
| D-42 — oversized money input becomes database 500 | **FIXED+TESTED** | Teller and demo-opening request DTOs now validate the exact `DECIMAL(24,8)` storage shape (16 integer digits, 8 fractional). The regression request returns 400 and leaves balance, savings transactions, and journals unchanged; no business cap or migration was added. |
| D-43 — registration 500 and `/auth/me` uncertainty | **FIXED+TESTED** | Identity migration tests cover verified-email signup, customer token, `/api/v1/auth/me`, and session data; earlier I2 runtime exercised OTP registration/login. |
| Related D-40 — translations and 401/403 distinction | **FIXED+TESTED** | Translation checks pass for 884 messages in six languages. Integrated E2E verified role denial as 403 and revoked-token rejection as 401. |

## I3 deliverables and I4 evidence

Implemented report APIs use the existing FD statement and notice records rather than adding duplicate writers. Customer endpoints are self-scoped; staff endpoints are role-protected. FD and maturity summaries group by product, currency, and status/date, use currency scale, and export CSV. Angular customer/staff report screens include loading, empty, access-denied, and error states. Nginx SPA deep links and report gateway routes were smoke-tested. OpenAPI, Postman, architecture/deployment, test-plan, and demo-guide artifacts were updated.

The I4 gateway API sequence completed in one synthetic session: OTP registration → KYC submission and staff review → product/calculator → savings account/funding → FD booking/funding → simulated clock/accrual/maturity → settlement/closure → savings transfer → reports/CSV/statements/notices → ledger reconciliation → logout/revocation. Savings creation first returned the planned retryable 503; bounded retry succeeded on attempt 2 and idempotent replay returned the original account. The FD closed with INR 1000.00 principal; readbacks showed 30 accrual rows, 33 FD history rows, one statement, two notices, two staff-summary groups, and zero journal/history/balance mismatches. Staff-summary denial returned 403; revoked-token reuse returned 401. Browser Angular journeys, Angular unit tests, Python L6 report, external email delivery, cloud deployment, and the broader security/concurrency/rollback matrix remain unverified/open.

The statement-boundary regression now covers advancing the simulated clock across multiple month starts and confirms reruns do not duplicate statements. During development, an initial separate test method left an account in the class-shared Testcontainers database and caused one failure in a 46-test FD run; the assertion was merged into the existing cleanup-safe test, after which the final FD suite passed 45/45 with zero skips. An initial focused fixture also used an account number longer than the schema allows; the fixture was corrected before final verification. MySQL/Flyway logs include compatibility/deprecation warnings. The i18n failure and the remaining unverified browser/security checks are unresolved release gaps.

## Exact task files in the working tree

These are the I2–I4 application/documentation paths currently modified or added. The user-supplied audit inputs (`REPORT_1_*`, `REPORT_2_*`, and `audit-reports/`) are not listed as task edits.

### Configuration and entry points

- `.env.example`
- `docker-compose.yml`
- `README.MD`
- `scripts/run-backend.ps1`
- `BACKEND/src/main/resources/db/service-migration/customer/V10__verified_email_registry.sql`
- `BACKEND/src/main/resources/db/service-migration/customer/V11__kyc_staff_review_events.sql`
- `FRONTEND/nginx.conf`
- `services/api-gateway/nginx.conf`

### Angular

- `FRONTEND/src/app/app.routes.ts`
- `FRONTEND/src/app/core/i18n/translations.ts`
- `FRONTEND/src/app/core/i18n/workflow-messages.ts`
- `FRONTEND/src/app/core/i18n/reports-messages.ts`
- `FRONTEND/src/app/core/interceptors/auth.interceptor.ts`
- `FRONTEND/src/app/core/services/reports.service.ts`
- `FRONTEND/src/app/features/dashboard/dashboard-stub.ts`
- `FRONTEND/src/app/features/kyc/kyc.html`
- `FRONTEND/src/app/features/reports/reports.ts`
- `FRONTEND/src/app/features/reports/reports.html`
- `FRONTEND/src/app/features/reports/reports.scss`
- `FRONTEND/src/app/shared/components/workspace-shell/workspace-shell.html`

### FD Account service and tests

- `services/fd-account-service/src/main/java/com/bank/fdservice/CustomerProductAccess.java`
- `services/fd-account-service/src/main/java/com/bank/fdservice/FdBatchAccountProcessor.java`
- `services/fd-account-service/src/main/java/com/bank/fdservice/FdReportController.java`
- `services/fd-account-service/src/main/java/com/bank/fdservice/FixedDepositController.java`
- `services/fd-account-service/src/main/java/com/bank/fdservice/IdentitySessionClient.java`
- `services/fd-account-service/src/main/java/com/bank/fdservice/JwtFilter.java`
- `services/fd-account-service/src/main/java/com/bank/fdservice/LoanForexController.java`
- `services/fd-account-service/src/main/java/com/bank/fdservice/SavingsAccountController.java`
- `services/fd-account-service/src/main/java/com/bank/fdservice/SavingsTransferController.java`
- `services/fd-account-service/src/main/java/com/bank/fdservice/SavingsTransferService.java`
- `services/fd-account-service/src/main/java/com/bank/fdservice/StaffAccountController.java`
- `services/fd-account-service/src/main/resources/application.yml`
- `services/fd-account-service/src/test/java/com/bank/fdservice/CustomerProductAccessTest.java`
- `services/fd-account-service/src/test/java/com/bank/fdservice/FdAccountMigrationTest.java`
- `services/fd-account-service/src/test/java/com/bank/fdservice/FdBookingAndCalculatorFinancialTest.java`
- `services/fd-account-service/src/test/java/com/bank/fdservice/FdBatchSafetyIntegrationTest.java`
- `services/fd-account-service/src/test/java/com/bank/fdservice/FdFlowIntegrationTest.java`
- `services/fd-account-service/src/test/java/com/bank/fdservice/FdReportIntegrationTest.java`
- `services/fd-account-service/src/test/java/com/bank/fdservice/IdentitySessionClientTest.java`
- `services/fd-account-service/src/test/java/com/bank/fdservice/IdentitySessionTestSupport.java`
- `services/fd-account-service/src/test/java/com/bank/fdservice/LedgerInvariantIntegrationTest.java`
- `services/fd-account-service/src/test/java/com/bank/fdservice/LoanForexIntegrationTest.java`

### Identity and Product services

- `services/identity-customer-service/pom.xml`
- `services/identity-customer-service/src/main/java/com/bank/identitycustomer/AuthController.java`
- `services/identity-customer-service/src/main/java/com/bank/identitycustomer/CustomerAdminController.java`
- `services/identity-customer-service/src/main/java/com/bank/identitycustomer/JwtAuthenticationFilter.java`
- `services/identity-customer-service/src/main/java/com/bank/identitycustomer/KycService.java`
- `services/identity-customer-service/src/main/java/com/bank/identitycustomer/PasswordPolicy.java`
- `services/identity-customer-service/src/main/java/com/bank/identitycustomer/PasswordResetController.java`
- `services/identity-customer-service/src/main/java/com/bank/identitycustomer/UserReferenceSnapshotBootstrap.java`
- `services/identity-customer-service/src/main/java/com/bank/identitycustomer/UserSnapshots.java`
- `services/identity-customer-service/src/test/java/com/bank/identitycustomer/IdentityCustomerMigrationTest.java`
- `services/identity-customer-service/src/test/java/com/bank/identitycustomer/KycIntegrationTest.java`
- `services/identity-customer-service/src/test/java/com/bank/identitycustomer/PasswordPolicyTest.java`
- `services/product-service/src/main/java/com/bank/productservice/FdOfferController.java`
- `services/product-service/src/main/java/com/bank/productservice/FdProductTemplateBootstrap.java`
- `services/product-service/src/main/java/com/bank/productservice/IdentitySessionClient.java`
- `services/product-service/src/main/java/com/bank/productservice/JwtFilter.java`
- `services/product-service/src/main/resources/application.yml`
- `services/product-service/src/test/java/com/bank/productservice/IdentitySessionClientTest.java`
- `services/product-service/src/test/java/com/bank/productservice/ProductIdentitySessionTestSupport.java`
- `services/product-service/src/test/java/com/bank/productservice/ProductServiceIntegrationTest.java`

### Documentation and release audit

- `docs/ARCHITECTURE_AND_DEPLOYMENT.md`
- `docs/DEMO_GUIDE.md`
- `docs/TEST_PLAN.md`
- `docs/openapi/moneypal-active.yaml`
- `docs/postman/MoneyPal-active-stack.postman_collection.json`
- `FINAL_MONEYPAL_RELEASE_AUDIT.md`

## Next dependency

For a supervised local lab demo, use synthetic data and follow `docs/DEMO_GUIDE.md`. Remaining release dependencies are browser-driven Angular validation, the broader security/concurrency/rollback matrix, a decision on Python L6 report scope, and separate authorization to commit. No commit, merge, or deployment is authorized by this audit; cloud deployment and external email delivery were not performed.
