# Agent 11 - QA / Test Automation Audit

Scope: read-only audit of the extracted repo copy at
`/private/tmp/claude-501/-Users-yashralhan-Projects-PokerThen-/e0312acd-4510-4ec5-98db-ebead3794720/scratchpad/repo`
(paths below are relative to that root). Scratch work: `.../scratchpad/qa/` (outside the repo).

## 0. Headline

- **No Spring, MySQL, Testcontainers, Maven or npm test was executed.** The environment has JDK 11 only (services need 17), no Maven, no Docker, no MySQL, and the frontend has no test runner configured. Nothing here claims a Spring/DB test passed.
- What I did execute: (a) the real `FdCompounding.java` logic compiled with JDK 11 from a scratch copy, run on boundary inputs; (b) an independent Python/Decimal re-implementation of the same algorithm, cross-checked against the Java output and the numbers hard-coded in `FdCompoundingTest`; (c) the manual closed-form formula; (d) static analysis of Postman, gateway and frontend specs.
- Biggest finding: the **active** stack (3 services + nginx gateway + Angular) has **no CASA-to-CASA transfer and no forex conversion** implemented. `SavingsTransferService` exists only in the legacy `BACKEND/` monolith, which the README says is "not part of the active Compose request path". The ~45 BACKEND test files mostly test non-deployed code, while the active `services/` have only 9 test classes (6 Docker-gated).

## 1. Test inventory

### 1.1 Environment / execution facts

| Fact | Evidence |
|---|---|
| Every service integration test is `@Testcontainers(disabledWithoutDocker = true)` + `@SpringBootTest` with `MySQLContainer<>("mysql:8.4")`. Without Docker they are **silently SKIPPED, not failed**; a CI box without Docker reports green with almost no integration assertions executed. | `services/*/src/test/java/**` (6 classes), `docker-java.properties` (`api.version=1.44`) in each service |
| BACKEND `MySqlServiceSchemaMigrationTest` uses `Assumptions.assumeTrue(isDockerAvailable())` (also skip-not-fail); 5 more BACKEND classes use `disabledWithoutDocker`. | `BACKEND/src/test/java/com/bank/migrations/MySqlServiceSchemaMigrationTest.java:23`; `FdPhase5ProtectionIT`, `Fd*Postgres*`, `FdBookingFundingIntegrationTest` |
| `app.kafka.enabled=false` in FD integration tests; Kafka publish/consume (`OutboxPublisher`, `FdEventPublisher`, listener wiring of `ReferenceSnapshotConsumer`) never exercised end to end. | `LoanForexIntegrationTest.java` DynamicPropertySource |
| No CI definition in repo (no `.github/`, no pipeline file). | `ls -a repo` |
| Stale historical evidence: `BACKEND/target/` holds only `classes/` (58 `.class` files) and `test-classes/`. **No surefire-reports / TEST-*.xml anywhere**, so there is no historical pass/fail record to cite. | `find BACKEND/target -name 'TEST-*.xml'` -> none |
| `BACKEND/src/test/resources/application-test.yml` needs `${JWT_SECRET}` env var with no default (tests fail to boot if unset); also uses H2 `MODE=PostgreSQL`, not the production dialect. | file |

### 1.2 Active services (`services/*/src/test`)

Docker = needs Docker/Testcontainers. Persist = asserts DB state.

| Test class | Verifies | Docker | Persist | Mocks / stubs | Weak assertions / order dependence |
|---|---|---|---|---|---|
| `fd-account-service/.../FdCompoundingTest` (3) | Cap-date anchoring (Jan-31 clamp, leap); known maturity 10616.77 / 10613.64 / 10600.00; quarterly/null/UNKNOWN bit-for-bit equal to an in-test copy of the "previous algorithm" | No (pure) | n/a | none | Test 3 re-implements the production algorithm inside the test (self-referential). Known numbers have no independent oracle in repo (I supplied one, section 3). |
| `.../CustomerProductAccessTest` (3) | KYC gate `requireVerified`: VERIFIED + LOW/MEDIUM/null ok; PENDING/REJECTED/null/missing snapshot -> 403 "bank officer"; HIGH risk -> 403 | No | No | Mockito `JdbcTemplate`/`ResultSet` | Asserts message substrings; mock mirrors implementation query shape. |
| `.../FdAccountMigrationTest` (3) | (1) Flyway applies. (2) E2E: open savings, demo-opening (123.456 -> 400, 123.45 ok, second -> 409, stranger -> 404), book FD (PENDING), fund wrong account 409, fund -> ACTIVE, fund replay same key -> 200 and exactly 1 savings txn, savings balance 900.00000000, accrual + maturity batch -> MATURED, close -> CLOSED, 3 journals, second FD premature request -> approve -> PREMATURELY_CLOSED, FD_PENALTY row exists. (3) accrual per stored compounding basis; rerun creates 0 rows | Yes | Yes (partial) | real DB; JWT minted in-test | **One ~130-line mega-test**, steps depend on earlier ones. `penalty.signum()>0` only; exact penalty/net/savings credit not asserted; post-close savings balance not asserted; `>= 28` accrual rows weak; uses `LocalDate.now()`; seeds fixed IDs (savings 1, product 11). No negative booking cases, no IDOR on FD, no admin-batch 403, no maturity rerun, no close-twice. |
| `.../LoanForexIntegrationTest` (2) | Loan: PENDING_REVIEW, replay same id/1 row, stranger GET 404, other user's dest account 400, currency mismatch 400, over-limit 400, same key different payload 409, customer approve 403, admin approve/disburse (idempotent: exactly 1 savings txn `LOAN:id`), blank reject reason 400, reject idempotent, disburse-rejected 409. Forex: REQUESTED + markup 0.01, same-currency 400, replay, stranger 404. Test 2: KYC snapshot gate -> 403 on FD book/fund/loan/forex for null/PENDING/REJECTED/VERIFIED+HIGH; older `sourceVersion` ignored | Yes | Yes | Product service replaced by in-JVM `HttpServer` stub (ignores Authorization); Kafka consumer called directly with mock `Acknowledgment` | Test 1 is a single ~50-step sequence. Forex only proves a request row exists (no conversion/rate/rounding/debit). Disbursed balance not asserted. Loan missing-field cases not tested. |
| `identity-customer-service/.../IdentityCustomerMigrationTest` (6) | Flyway V1 + tables + role seed; register -> 201 `ROLE_CUSTOMER` (client-sent `"role":"ADMIN"` ignored) + `/me`; 4x401 then 423, correct password still 423; change-password; customer GET `/customers/{id}` 403 vs officer 200; officer create/search/update/KYC status -> outbox snapshot v3 | Yes | Yes (outbox row) | none | Lockout never verified to expire; no duplicate user, weak password, tampered/expired JWT, logout/refresh, MFA tests. |
| `.../KycIntegrationTest` (7) | Lazy profile/masking/partial errors; 9 floor fields -> progress -> auto-verify + risk + snapshot; re-KYC; **concurrent final save -> {200,409} with exactly 1 AUTO_VERIFIED event** (real 2-thread race); document magic bytes/replace/remove/lock; customer-only/staff blocked; invalid fields never persist | Yes | Yes | none | Strongest suite. Race test runs once, 20 s timeout. |
| `.../KycValidationTest` (3) | Date/enum/proof-format/safe-text validators | No | n/a | none | Pure. |
| `product-service/.../ProductServiceIntegrationTest` (6) | Migrations + localisation; published-only loan catalog; builder draft validate/create; multi-currency FD offer create/activate + outbox; generated codes / identity errors; writes require staff role; category duplicate message | Yes | Yes (outbox) | none | Large methods; message assertions. Rate-band overlap / min>max validation coverage unconfirmed. |
| `.../ProductTextRulesTest` (4) | Prefix/code/name/description validators | No | n/a | none | Pure. |

### 1.3 Legacy `BACKEND/src/test` (modular monolith, NOT in the Compose request path)

| Group (files) | Verifies | Docker | Notes |
|---|---|---|---|
| Domain unit: `FdInterestCalculatorTest`, `FdCompoundCalculatorTest`, `FdCalculationPolicyTest`, `FdPhase5BoundaryTest`, `FdTimePolicyTest`, `PrematurePenaltyTest`, `FdLifecycleStateMachineTest`, `FdCalculatorServiceTest`, `AccountNumberGeneratorTest`, `FdDepositLimitPolicyTest` | Interest, penalty, boundaries, state transitions | No | Test legacy classes, not the active `FdCompounding`/`FdBatchService`/`FdLifecycleController`. |
| Service unit (Mockito): `AccountBookingServiceTest`, `AccrualMaturityServiceTest`, `ClosureServiceTest`, `PrematureClosureServiceTest`, `FundingServiceTest`, `HistoryServiceTest`, `FdIdempotencyGuardTest`, `FdAccessPolicyTest`, `FdBatchProcessingServiceTest`, `NormalizedProductTermsAdapterTest`, `SavingsAccountServiceTest`, **`SavingsTransferServiceTest`** | e.g. transfer posts linked DEBIT/CREDIT, balances 999.75 / 600.25, rejects FD destination, insufficient funds, inactive, non-owner source, self-transfer, currency mismatch, `never().saveAndFlush` on failure | No | Repositories mocked: asserts in-memory entity state and calls, **not persisted state, no real locking, no concurrency**. |
| API/MockMvc: `FdV1*ContractTest`, `FdOwnershipContractTest` (431 lines), `FdAccountingControllerTest`, `AuthCustomerEndpointContractTest`, `SecurityFlowTest`, `BatchTriggerControllerTest`, `FdClockControllerTest`, `GlobalExceptionHandlerTest`, `StaffBootstrapServiceTest`, `FdBatchSchedulerTest`, `PostmanCollectionConsistencyTest` | Contracts, ownership, errors | mixed (H2 or mocks) | Legacy routes (`/fd/v1/accounts/...`). |
| IT/schema: `FdBookingFundingIntegrationTest`, `FdSavingsSettlementIntegrationTest`, `FdPhase5ProtectionIT` (`@TestMethodOrder(OrderAnnotation)`, `PER_CLASS` -> **order dependent**), `Fd*SchemaTest`, `FdPostgresSchemaTest`, `FdSavingsPostgresSchemaTest`, `SavingsAccountSchemaTest`, `MySqlServiceSchemaMigrationTest`, `FdPersistenceContractTest` | Real-DB lifecycle, EOD batch duplicate-safety, schema | Yes (most) | Postgres Testcontainers for legacy. |
| Product: `ProductConfigurationFlowTest`, `ProductConfigurationRegressionTest`, `FdOfferAdminFlowTest` | Legacy product admin | mixed | Legacy. |

### 1.4 Frontend (`FRONTEND/src/**/*.spec.ts`, 12 files)

| Finding | Evidence |
|---|---|
| **No test runner.** `package.json` scripts: `start`, `build`, `check:i18n` only; devDependencies have no karma/jasmine/jest/vitest; `angular.json` architect targets are `['build','serve']` (no `test`). Specs use Jasmine APIs but cannot be run by `ng test`. No e2e framework. `node_modules` absent. | `FRONTEND/package.json`, `FRONTEND/angular.json` (python-parsed) |
| `app.spec.ts` expects `h1` to contain "Hello, frontend" (scaffold leftover; would fail). | `FRONTEND/src/app/app.spec.ts:21` |
| `auth.spec.ts`, `login.spec.ts`, `glass-shell.spec.ts` only `toBeTruthy()`. | files |
| `fd.service.spec.ts` meaningful (bearer header, Idempotency-Key reuse on retry/rotation after success, changed payload blocked while uncertain) but uses legacy `'/accounts'` while service calls `/fixed-deposits`. | spec L83 vs `fd.service.ts:99,107` |
| `app.routes.spec.ts` (guards), `fd-account-detail.spec.ts` (UI action gating only), `fd-booking/fd-home/my-deposits` specs: component state only, no HTTP/ledger. | files |

### 1.5 Postman (`BACKEND/postman/fd-lifecycle.postman_collection.json`)

- 15 requests, 4 folders; each request has only a status-code assertion plus id chaining. **No body, balance or ledger assertions.**
- `baseUrl = http://localhost:8080/fd/v1`, paths `/products`, `/accounts/...`. The gateway (`services/api-gateway/nginx.conf`) routes only `/fd/v1/staff*` to the FD service; every other `/fd/v1/*` falls to `location /` -> 404. Active endpoints are `/api/v1/fixed-deposits...` with mandatory `Idempotency-Key` and camelCase bodies (`savingsAccountId`, `principal`, `tenureMonths`), whereas the collection posts `product_id/principal_amount/tenure_months` with no key. **The collection cannot run against the active stack.**
- Collection says premature approval is "Self-service: the same owning customer approves"; active code returns 403 "Approver must be a different staff user" and requires `BANK_OFFICER/ADMIN`.
- Legacy `PostmanCollectionConsistencyTest` validates only against legacy controllers, masking the drift.

## 2. Scenario coverage matrix (active stack unless noted)

COVERED = automated test asserts it against the active implementation; WEAK = partial/indirect/mocked/status-only; NOT COVERED; N/I = not implemented in active stack.

### 2.1 CASA transfer

| Scenario | Status | Evidence / note |
|---|---|---|
| A debited / B credited | **N/I (active)**; legacy WEAK | No transfer endpoint in `services/*/src/main`. Legacy `BACKEND/src/test/java/com/bank/fd/application/SavingsTransferServiceTest.java:65-86` uses Mockito repos. |
| Insufficient funds | legacy WEAK (`:114-125`); active staff withdraw 409 "Insufficient funds" (`StaffAccountController.transact`) and FD funding 409 "Insufficient savings balance" (`FixedDepositController.fund`) **NOT COVERED** | |
| Same-account | legacy WEAK (`:128-136`); active N/I | |
| Concurrent debits | **NOT COVERED** (only concurrency test in repo is KYC). Code uses `SELECT ... FOR UPDATE`, unproven. | |
| Idempotent retry | FD funding replay COVERED (`FdAccountMigrationTest` ~L123-131). Staff deposit/withdraw replay NOT COVERED. | |
| Failure mid-way / rollback | **NOT COVERED** | |

### 2.2 Fixed deposits

| Scenario | Status | Evidence / note |
|---|---|---|
| Open (book) | COVERED happy path | `FdAccountMigrationTest`; persisted terms/maturity amount not asserted |
| Invalid tenure / principal / product / currency | **NOT COVERED**. Validation exists (`@Min(1)`, `@DecimalMin("0.01")`, `setScale(UNNECESSARY)`, offer lookup -> 400) but no test hits it | `FixedDepositController.book` |
| Booking replay / key reuse with other payload (409) / other user's savings (404) | NOT COVERED | |
| Interest known inputs | COVERED for `FdCompounding.maturity` (+ my independent check, section 3). Calculator `/api/calculator/estimate` NOT COVERED. No simple-interest test | |
| Premature close + penalty + payout | WEAK: `penalty>0`, status flip, `FD_PENALTY` row only. Exact penalty (8.22 for 10000 @6%, 1pt, 30d), net, savings credit, journal lines, floor-at-zero, no-config 409, duplicate pending 409, approver==owner 403, approve replay NOT COVERED | |
| Closed FD rejects further operations | NOT COVERED | |
| Maturity | WEAK: batch -> MATURED, accrual rows >= 28, close -> CLOSED, 3 journals. Payout equals `fa_maturity_amount`, savings balance after close, boundary day NOT asserted | |
| Maturity/close twice, no duplicate payout | WEAK: accrual rerun = 0 rows only. Maturity-batch rerun, close replay/new key not tested (code idempotent by design: `status='ACTIVE'` filter, `fj_key` check, `FOR UPDATE`) | |
| Customer cannot call admin batch (403); other customer cannot read FD (404) | NOT COVERED (loan/savings stranger 404 covered) | |

### 2.3 Forex

| Scenario | Status | Evidence / note |
|---|---|---|
| Supported pair | WEAK: REQUESTED row with markup 0.01 only | `LoanForexIntegrationTest` |
| Unsupported pair | WEAK: same currency -> 400; currency not configured on product NOT COVERED | |
| Rate direction / conversion / rounding | **N/I**: `LoanForexController.forexRequest` stores `fr_markup_rate` and `fr_fee=0` only; no FX rate source, converted amount or account movement | |

### 2.4 Identity / KYC / loans

| Scenario | Status | Evidence |
|---|---|---|
| Register / authenticate / lockout | COVERED | `IdentityCustomerMigrationTest` (role injection ignored, 423 lockout) |
| Ownership / 403 / IDOR | COVERED for customer admin API, KYC, loans, forex, savings; NOT COVERED for FD detail/transactions/accruals/premature GETs and savings transactions list |
| JWT tamper / expiry / wrong key / `alg=none` / missing `userId` | NOT COVERED |
| KYC flow and gate | COVERED (Docker-gated) | `KycIntegrationTest`, `CustomerProductAccessTest`, `LoanForexIntegrationTest#snapshotsGate...`; gate on `close`/`premature-closure`/savings opening untested |
| Loan mandatory fields | WEAK: amount limits and destination covered; missing purpose/tenure/product, tenure outside band NOT COVERED |

### 2.5 Integration

| Scenario | Status | Evidence |
|---|---|---|
| Gateway routing | **NOT COVERED** (no test of `nginx.conf`: routes, CORS regex, 404 default, 6 MB KYC upload limit) |
| JWT propagation | WEAK: loan/forex forward `Authorization` to Product; stub ignores it. Shared-secret mismatch between services untested |
| Migrations | COVERED (Docker-gated) per service; upgrade-with-data NOT COVERED |
| Kafka | NOT COVERED end to end (disabled). Older-version snapshot ignored: COVERED at unit level by direct call. Duplicates/poison/ordering NOT COVERED. `scripts/verify-kafka-demo.ps1` is manual |
| Batch | WEAK: happy path via admin endpoints only; scheduler wiring, per-account failure isolation (all in one `@Transactional`), crash/rerun NOT COVERED |
| Reports `/api/reports/*`, calculator, clock, ledger admin | NOT COVERED |
| Frontend E2E | NOT COVERED |

## 3. Execution log (what was actually run)

| # | Command / action | Result |
|---|---|---|
| 1 | `java -version` | Corretto 11.0.24. Services target 17 -> Spring tests cannot compile here. Maven, Docker, MySQL absent. |
| 2 | Scratch copy of `services/fd-account-service/.../FdCompounding.java` to `.../scratchpad/qa/fdc/`. Two edits only: Java-14 `switch` expression in `basis()` replaced by equivalent ternary (JDK 11), class/methods made `public`. Logic of `months`, `capitalizationDate`, `maturity` untouched. `javac` + `java Run`. | Compiled and ran. Output in 3.1. |
| 3 | `python3 -I .../scratchpad/qa/fd.py` (independent `Decimal` re-implementation + manual formula + simple interest + premature penalty) | Output in 3.1; agrees with Java. |
| 4 | Python/grep static analysis of Postman JSON, nginx.conf, angular.json, package.json | Sections 1.4 / 1.5. |
| 5 | **Not run**: `mvn`, `docker`, `ng test`, `npm`. Tools/runtime absent. | |

### 3.1 Results

Real Java output of `FdCompounding.maturity` (JDK 11 scratch copy), P=10000, r=6%, start 2025-01-31, 1 year:

| Case | Java | Python independent | Manual `M=P(1+r/n)^nt` | `FdCompoundingTest` expects |
|---|---|---|---|---|
| MONTHLY | 10616.77 | 10616.77 | 10616.78 | 10616.77 |
| QUARTERLY | 10613.64 | 10613.64 | 10613.64 | 10613.64 |
| YEARLY | 10600.00 | 10600.00 | 10600.00 | 10600.00 |
| basis `null`/`"garbage"` | 10613.64 (silently quarterly) | | | test asserts null/UNKNOWN == quarterly |

Simple interest `I=P*r*t/100` = 10000*6*1/100 = **600** (no active booking path uses simple interest; only the calculator `SIMPLE` branch, days/365).

Java boundary results: principal 0 -> 0.00; rate 0 -> 10000.00; start==end -> 10000.00; end<start -> 10000.00 (silent no-op); 1 day -> 10001.64; scale 0 (1,000,000 @0.1%, 1 month) -> 1000077; scale 3 (5000 @4%, 6m quarterly) -> 5099.670; 10y 100000 @7.5% monthly from 2024-02-29 -> 211292.50; 999,999,999,999.99 @7% quarterly -> 1071858902750.07; **negative rate -5% -> 9511.31 (no guard in helper)**; leap year 2024-01-01 to 2025-01-01 YEARLY -> **10601.64** (not 10600.00); `capitalizationDate`(Jan-31 start): 2025-02-28 MONTHLY true, 2025-03-31 true, 2025-02-27 false.

Premature penalty (Python mirroring `requestPremature`): 10000 @6%, 1 point, 30 days -> penalised rate 0.05, paid interest 41.10, full interest 49.32, penalty 8.22, net 10041.10; 8 points on 6% -> rate floored at 0.

Conclusion: the Java test constants are consistent with an independent implementation; the MONTHLY 1-paisa difference from the closed form is per-period rounding.

## 4. Findings

| ID | Sev | Finding | Evidence |
|---|---|---|---|
| QA-01 | P1 | No CASA transfer and no forex conversion in the active stack; the only transfer code and tests are in the non-deployed legacy monolith. If the brief expects them, it is a missing feature with zero coverage; otherwise ~45 BACKEND test files give false assurance. | README legacy statement; `services/*/src/main` has no transfer; `SavingsTransferServiceTest`; `LoanForexController.forexRequest` |
| QA-02 | P1 | All active integration tests skip silently without Docker (`disabledWithoutDocker=true`); no CI config. A green run may execute only 3 small pure classes. | grep; no `.github` |
| QA-03 | P1 | Money-movement invariants under-asserted: FD funding insufficient funds, exact premature penalty/net/credit, maturity payout and post-close balance, double close, maturity rerun, mid-way rollback, concurrent debits. | `FdAccountMigrationTest`; section 2 |
| QA-04 | P1 | No negative-input tests for FD booking; no IDOR tests for FD GET/transactions/accruals/premature endpoints; no 403 test for customer on `/api/v1/admin/fixed-deposits/*-batch`. | `FixedDepositController`, `FdLifecycleController` vs tests |
| QA-05 | P2 | Frontend specs cannot run (no runner/devDeps/test target); `app.spec.ts` asserts scaffold text; `fd.service.spec.ts` uses legacy path; no E2E. | `package.json`, `angular.json` |
| QA-06 | P2 | Postman collection is dead against the active stack (`/fd/v1/accounts` 404 at gateway, wrong body, no Idempotency-Key, status-only asserts, documents self-approval the code forbids). Consistency test only checks legacy. | collection, `nginx.conf`, `FdLifecycleController.approvePremature` |
| QA-07 | P2 | Mega-tests with sequential dependence and wall-clock/fixed-ID fixtures; one failure masks later checks; legacy `FdPhase5ProtectionIT` is `@Order`-dependent. | test files |
| QA-08 | P2 | Kafka disabled in all integration tests; outbox -> broker -> consumer -> KYC gate (which gates every customer write) never tested end to end. | `app.kafka.enabled=false` |
| QA-09 | P2 | Gateway has zero tests (routes, CORS, default 404, upload limit). | `services/api-gateway/` |
| QA-10 | P2 | No JWT-hardening tests (expired, tampered, wrong key, `alg=none`, missing `userId`). | tests |
| QA-11 | P3 | `FdCompounding` quirks: unknown basis silently QUARTERLY; end<start silent no-op; negative rate accepted by helper; leap-year 1-year YEARLY FD yields 10601.64; MONTHLY 1 paisa under the closed form. Document or assert as intended. | section 3.1 |
| QA-12 | P3 | Calculator estimate (`Math.pow` doubles, termDays/365, +0.50/+0.75 boosts) uses a different algorithm from booking; quote can differ from booked maturity by paise; no comparison test. | `FdCalculatorController.estimate` vs `FdCompounding` |
| QA-13 | P3 | `FdCompoundingTest` test 3 copies the production algorithm (self-referential oracle). | file L26-41 |
| QA-14 | P3 | Weak assertions: `penalty.signum()>0`, `>= 28` rows, message substrings, create-only specs. | listed files |
| QA-15 | P3 | No stale surefire reports exist to corroborate past runs. | `BACKEND/target` |

## 5. Test plan (JUnit 5 + MockMvc + Testcontainers MySQL 8.4 unless noted)

Common fixtures: currencies INR/USD (scale 2), JPY (scale 0); `fd_user_ref`: 701 VERIFIED/LOW, 702 VERIFIED/LOW, 703 PENDING, 704 VERIFIED/HIGH; product P with `fd_product_currency_ref` (min 100, max 100000, `premature_penalty_points`=1) and `fd_product_rate_ref` (rate 0.06, tenure 1..60, MONTHLY); savings owned by 701 (5000.00 INR) and 702 (1000.00). JWT claims `userId`, `roles`. Inject a fixed `DemoClockService` date (never `now()`). Each test creates its own rows. After each test assert conservation (sum of savings + active FD principal constant unless interest paid) and every `fd_journals` row has total_debit = total_credit.

### 5.1 CASA transfer (blocked: not implemented in active stack - decide scope first)
Proposed `POST /api/v1/savings-accounts/{id}/transfers` with `Idempotency-Key`:

| Case | Preconditions | Input | HTTP | Expected DB |
|---|---|---|---|---|
| Happy | A=5000.00, B=1000.00 same currency | 250.25 A->B | 200 | A=4749.75, B=1250.25; 2 `savings_transactions` (DEBIT A, CREDIT B) sharing reference, correct `st_balance_after`; idempotency COMPLETED |
| Insufficient | A=100 | 250 | 409 | no change, 0 txns |
| Same account / currency mismatch / FD destination / inactive | | | 400/409 | no change |
| Non-owner source | caller 702 uses A | | 404 | no change |
| Retry | after happy | same key+body | 200 same body | still 2 txns; same key other body 409 |
| Concurrent | A=100, two threads 80 each | | one 200, one 409 | A=20, one pair of txns |
| Mid-way failure | force CREDIT insert to fail | | 500 | full rollback: balances unchanged, 0 txns, no COMPLETED idempotency row |

Existing staff API (`/fd/v1/staff/savings-accounts/{id}/withdraw|deposit`): withdraw > balance -> 409 "Insufficient funds"; scale violation -> 400; same key replay -> unchanged balance; customer role -> 403; concurrent withdrawals never overdraw.

### 5.2 FD open / fund
| Case | Input | HTTP | DB |
|---|---|---|---|
| Open | 701, product P, savings S(5000), principal 1000.00 INR, tenure 12, key k1 | 201 PENDING | 1 `fd_accounts` row, rate 0.06, maturity date = open + 12m, `fa_maturity_amount` = oracle (FdCompounding), event BOOKED v1, savings unchanged |
| Replay k1 same body | | 201 same `account_id` | still 1 row |
| k1 other body | principal 2000 | 409 | no new row |
| No Idempotency-Key | | 400 | none |
| principal 0 / -5 / 100.001 | | 400 | none |
| tenure 0 / 61 / outside band | | 400 | none |
| unknown or inactive product, unknown currency, currency not on product | | 400 | none |
| savings of 702 / wrong currency / inactive | | 404 | none |
| KYC PENDING / HIGH (703/704) | | 403 | none |
| missing/expired/tampered JWT | | 401 | none |
| Fund | pending FD, S=5000 | 200 ACTIVE | S=4000.00; DEBIT 1000 txn; `fd_transactions` FD_FUNDING; journal `FUNDING:{id}` SAVINGS dr 1000 / FIXED_DEPOSIT cr 1000; event FUNDED v2 |
| Fund again (new key) | | 409 | no change |
| Fund with S=500 | | 409 "Insufficient savings balance" | FD PENDING, S=500 |
| Fund by 702 | | 404 | none |
| Two parallel funds, different keys | | one 200, one 409 | S debited once |

### 5.3 Premature withdrawal
Setup: ACTIVE FD 10000.00 @0.06, penalty 1 point, business date open + 30 d.

| Case | Expected |
|---|---|
| Request | 200 PENDING; `penalty_amount=8.22`, `net_payable_amount=10041.10`; closure row PENDING; FD still ACTIVE; savings unchanged |
| Request replay (same key) | same closure id, one row |
| Second request while PENDING | 409 |
| Product without penalty config | 409 |
| Penalty points >= rate (8) | paid interest 0, net = principal |
| Approve by owner or customer role | 403 |
| Approve by officer/admin | 200 PREMATURELY_CLOSED; savings credited exactly net (4000.00 -> 14041.10 in the 10000 case); closure APPROVED with `fpc_approved_by`; FD_PENALTY 8.22 and FD_CLOSURE 10041.10 transactions; balanced `SETTLEMENT:{id}` journal |
| Approve replay same key | 200, no second credit; new key -> 409; balance unchanged |
| Request/close on CLOSED, MATURED, PENDING FD | 409 |
| Other customer requests or reads | 404 |

### 5.4 Maturity + rerun idempotency
Setup: ACTIVE FD 10000 @0.06 MONTHLY, open 2025-01-31, 12 months, business date 2026-01-31.

| Step | Expected |
|---|---|
| Batch endpoints as customer | 403; officer 200 |
| Batch day before maturity | stays ACTIVE; accrual rows = elapsed days |
| `maturity-batch` on maturity date | MATURED; `fa_maturity_amount`=10616.77 (Java oracle); 365 accrual rows, last capitalised; journal `INTEREST:{id}:MATURITY` 616.77 INTEREST_EXPENSE -> FIXED_DEPOSIT; FD_MATURITY txn; savings not yet credited |
| Rerun both batches | `accountsProcessed=0`; accrual, journal, transaction, event counts unchanged |
| `close` as owner | 200 CLOSED; savings credited exactly maturity amount once; `SETTLEMENT:{id}` journal once |
| `close` replay same key | 200 same body, balance unchanged; new key -> 409; other customer -> 404 |
| Close while ACTIVE | 409 |
| Two concurrent `maturity-batch` runs | one maturity record, no duplicate journals |
| One bad account in a batch | decide expected behaviour (currently one `@Transactional` rolls back all) and test it |

### 5.5 Forex
Current (request-only) behaviour: INR->USD 2000 -> 201 REQUESTED, markup from band, fee 0, 1 row; replay same key -> same id; same key other payload -> 409; same currency, non-base funding currency, currency not on product, amount below min/above max, scale violation -> 400; stranger GET 404; KYC gate 403. If conversion is required: direction (base->foreign divides by rate, reverse multiplies), HALF_UP rounding to currency scale, atomic debit/credit with ledger rows, unsupported pair 400/422, stale rate rejected.

### 5.6 KYC gate
Matrix: kyc in {null, PENDING, REJECTED, VERIFIED+HIGH, VERIFIED+LOW} x {book FD, fund, close, premature-closure, loan, forex, savings creation}: 403 except VERIFIED+LOW (2xx); GETs stay 200. Snapshot ordering: version n+1 applies, n ignored, duplicate idempotent. Kafka Testcontainer end to end: identity status VERIFIED -> outbox -> broker -> `fd_user_ref.fur_kyc_status` within N seconds -> FD booking 201.

### 5.7 Auth / IDOR
Register (201, role injection ignored; duplicate username/email 409; weak password 400), login (200 / 401 / 5th failure 423, lock expiry), JWT matrix on every protected route (none, expired, bad signature, `alg=none`, missing `userId`, deleted/disabled user -> 401), role matrix across all `@PreAuthorize` routes (admin loans ADMIN-only, batches officer/admin, journals ADMIN, clock/ledger ADMIN, reports per endpoint, staff API officer/admin), IDOR sweep: for every `{id}` route create as 701, call as 702, expect 404 and no leakage in list/pagination.

### 5.8 Integration / non-functional
- Gateway: nginx container test per location -> correct upstream, unknown path 404 JSON, OPTIONS 204 with CORS only for localhost origins, 6 MB KYC upload accepted / larger rejected 413.
- Rewrite Postman to `/api/v1/*` with Idempotency-Key and body/ledger assertions; run with Newman in CI against compose.
- Migrations: previous Flyway version with seeded data -> latest; data preserved.
- Batch: scheduler trigger, failure isolation, crash/rerun. Reports: own vs `/portfolio/{username}` (officer only), totals reconcile to ledger.
- Frontend: add Karma+Jasmine (or Vitest) and a `test` target; fix stale specs; Playwright flows (signup/login, book + fund FD, KYC-blocked message, premature request, staff approval, language switch).

## 6. Release gates

1. CI runs `mvn -B verify` for all 3 services on JDK 17 with Docker available; build fails if any `*IntegrationTest`/`*MigrationTest` is reported skipped.
2. Zero failing tests; frontend `ng test --watch=false` (runner added), `ng build`, `npm run check:i18n` pass.
3. Money-invariant suite (5.1-5.4) green: conservation of funds, balanced journals, no duplicate payout on replay/rerun/concurrency, rollback on injected failure.
4. Negative-input and IDOR sweep (5.2, 5.7) green; every route in an auto-generated inventory has an authorization test.
5. KYC gate matrix (5.6) green on every customer write, including Kafka end to end.
6. Interest oracle table (leap year, month-end, scale 0/3) approved; calculator vs booking divergence documented or bounded to 0.01.
7. Gateway route test and updated Postman/Newman run green against the compose stack.
8. Recorded decision on transfers and forex conversion (implement + test, or descope and retire legacy BACKEND tests so they do not imply coverage).
9. Test hygiene: no shared fixed IDs, injected clock, no sequential mega-tests, no `>0`/`>=N` assertions on money.
10. Repo root contains a `.env` file; I did not read or print it. Ensure it is git-ignored and not shipped.
