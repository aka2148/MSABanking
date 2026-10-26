# MoneyPal active stack test plan and evidence

This plan follows the active microservices, not the legacy `BACKEND/` monolith. Tests that could change financial, security, schema, or cross-service behavior require a reviewed plan before implementation. Integration testing uses disposable Testcontainers or the isolated `moneypal-i2-check` Compose project only.

## Required release checks

| Area | Check | Evidence to retain |
| --- | --- | --- |
| Build | Maven service tests and Angular production build | Command, exit code, executed/failed/skipped counts |
| Persistence | Clean MySQL migration from empty schema and migration validation | Migration versions and container/test database identity |
| Readiness | Compose health for databases, Kafka, services, gateway, frontend | `docker compose ps` and gateway response |
| Gateway | Identity, Product, FD routes and frontend SPA deep links | HTTP status, body shape, and route response content type |
| Authentication | OTP registration, login, current KYC eligibility, logout/revocation | HTTP result and synthetic database row checks |
| Kafka | User and product initial snapshots reach FD reference tables | topic/consumer evidence and reference row version |
| Financial flow | Product offer, estimate, FD booking/funding, accrual/maturity or premature closure, settlement | persisted account state, transaction IDs, balanced journal totals |
| Reports | Customer ownership, staff role filters, currency grouping, CSV content | API response, CSV parse, denial for cross-owner/unauthorized calls |
| Frontend | Customer/staff report screens, 401/403 behavior, direct deep-link refresh | production build and browser/HTTP evidence |
| Regression | Existing Identity/Product/FD integration suites and critical workflow tests | Maven summaries with zero skipped for claimed pass |

## Verification evidence (2026-10-26)

- Final full service verification after the KYC and Kafka/account-opening changes: `mvn -q -f services/identity-customer-service/pom.xml test` **24 passed**, `mvn -q -f services/product-service/pom.xml test` **24 passed**, and `mvn -q -f services/fd-account-service/pom.xml test` **45 passed**; all three had 0 failures, 0 errors, and 0 skipped. Total: **93 passed, zero skipped**. Testcontainers used disposable MySQL 8.4. Identity migration reached V11, Product V8, and FD V14 from empty schemas.
- Focused current-turn tests: `mvn -q -f services/identity-customer-service/pom.xml "-Dtest=KycIntegrationTest,IdentityCustomerMigrationTest" test` passed 16 tests, 0 failures, 0 errors, 0 skipped; clean Testcontainers MySQL applied Identity migrations through V11. `mvn -q -f services/fd-account-service/pom.xml "-Dtest=FdFlowIntegrationTest" test` passed 1 test, 0 failures, 0 errors, 0 skipped; clean Testcontainers MySQL applied all 14 FD migrations. Current-turn focused total: 17 passed, 0 failed/errors/skipped. The earlier full service run of 92 tests predates these final KYC/Kafka changes and is historical evidence, not a current full-suite pass.
- `npm run check:i18n` in `FRONTEND`: passed, 884 messages across six languages, including placeholders, switching, persistence, RTL, fallback, and blocked storage checks.
- `npm run build` in `FRONTEND`: passed. Current initial bundle is 915.04 kB versus the 512 kB budget; `fd-account-detail.scss` (6.17 kB) and `product-builder.scss` (8.19 kB) still exceed the 4.10 kB style budgets. These are warnings, not build failures.
- `git diff --check`: passed with line-ending normalization warnings. OpenAPI and Postman syntax checks are recorded above; current active API examples include the KYC review and account-opening idempotency contracts.
- Fresh isolated Compose project `moneypal-i5-20261026` used unique volumes, alternate localhost ports, a local OTP mail mock, and dynamic product terms read from the live Product API. It was removed after the run; the pre-existing `moneypal-i2-check` project and its volumes were not altered.
- Gateway E2E result: `E2E_GATEWAY_API=PASS`. HTTP: health 200; register 201; KYC submit/staff review 200; offers/calculator 200; savings open/replay 201; FD book 201; fund/batch/mature/close/transfer 200; reports and CSV 200; customer access to staff summary 403; logout 204; revoked-token request 401; `/reports` SPA path 200 `text/html`. The first savings open received retryable 503 until the Kafka user snapshot arrived; bounded retry succeeded on attempt 2 and replay returned the same account ID.
- Persisted synthetic readbacks: FD `CLOSED`, INR principal 1000.00 selected from the live configured offer, 30 accrual rows, 33 FD history rows, one statement, two notices, and two staff summary groups. Admin reconciliation returned zero journal-header mismatches, zero history-sequence mismatches, and zero per-savings-account journal/balance mismatches. No live/shared database was queried or modified.

## Gateway journey verified; browser journey remains unverified

The synthetic gateway journey above completed in one HTTP session through clock-driven maturity, settlement, transfer, reports, and journal reconciliation. Browser-driven Angular customer/staff interactions, expired-token navigation, Angular component tests, concurrent/idempotency stress, injected rollback, and full invalid-ID/cross-owner security matrix remain UNVERIFIED; Angular has no configured unit-test target. No external email was sent, and no Azure/Kubernetes deployment was attempted. KYC now remains PENDING until an authorized staff decision; reviewer identity is recorded in the existing KYC event remarks field.

## Full integration sequence

1. Start the isolated Compose stack and confirm all health checks.
2. Register/login a synthetic customer and verify email/KYC through the supported demo setup.
3. Confirm `user.snapshot` and `product.catalog` references are available in FD before booking.
4. Select an actually configured offer and currency; do not invent a rate or amount limit.
5. Create a savings account, fund it only through the configured demo mechanism, then book and fund an FD with unique idempotency keys.
6. Exercise either premature-closure request/decision or advance the business clock through accrual and maturity; close matured deposits and inspect transaction/journal records.
7. Exercise a savings transfer with a unique idempotency key and valid MPIN proof.
8. Verify customer and staff reports, statements/notices, CSV grouping, ownership denial, and frontend route/error handling.
9. Reconcile journals and balances, inspect rollback/retry behavior, and record every skipped or unrun check as UNVERIFIED.

This plan does not turn skipped tests into passes and does not make production/cloud readiness claims. See `FINAL_MONEYPAL_RELEASE_AUDIT.md` for the prioritized finding status and release decision.
