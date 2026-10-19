# FD and Account Service

Standalone FD and savings-account service owning only `moneypal_fd`. It uses port 8083 by default and applies the V1 baseline plus V2–V5 Flyway migrations.

## Configure and run

Set `FD_DB_URL`, `FD_DB_USERNAME`, `FD_DB_PASSWORD`, and the shared Base64 `JWT_SECRET`, then run `mvn spring-boot:run`. Set `KAFKA_ENABLED=true` and `KAFKA_BOOTSTRAP_SERVERS` to consume versioned `user.snapshot` and `product.catalog` events and publish `fd.lifecycle` events. The service applies only newer user/product versions and stores product terms locally; it never queries the other service databases.

FD money columns use eight fractional digits so supported 0–8 decimal currencies can be stored without losing precision. Product currency schedules may specify `prematurePenaltyPoints` (percentage points) to enable premature closure.

## API coverage

- `GET/POST /api/v1/savings-accounts` lists or opens the authenticated customer's savings account. `/api/v1/savings-accounts/admin` opens one for a customer and requires staff role.
- `GET /api/v1/savings-accounts/{id}/transactions` shows the owner's savings ledger.
- `GET/POST /api/v1/fixed-deposits` lists the customer's FDs or books one with an `Idempotency-Key` and linked savings account.
- `GET /api/reports/portfolio` returns the signed-in customer's FD portfolio; staff may use `/api/reports/portfolio/{username}` and `/api/reports/operational` for bank-level reporting. Report balances are grouped by currency to avoid adding unlike currencies.
- `POST /api/calculator/estimate` preserves the legacy estimate request/response fields and category rate boosts. The estimate keeps the legacy two-decimal output because this API has no currency field.
- Premature-closure approvals retain the approving Identity user ID for audit without requiring the staff account to be mirrored as a customer in the FD database.
- `POST /api/v1/fixed-deposits/{id}/fund` funds a pending FD from its linked savings account; this also requires an `Idempotency-Key`.
- `POST /api/v1/admin/fixed-deposits/accrual-batch?businessDate=YYYY-MM-DD` accrues each active FD through the given UTC date, capped at maturity. Repeating a date does not duplicate daily accrual rows.
- `POST /api/v1/admin/fixed-deposits/maturity-batch?businessDate=YYYY-MM-DD` matures eligible FDs once the business date reaches maturity.
- `POST /api/v1/fixed-deposits/{id}/close` closes a matured FD to its linked savings account; requires an `Idempotency-Key`.
- `POST /api/v1/fixed-deposits/{id}/premature-closure` creates a penalty quote for an active FD; staff approves it at `/api/v1/admin/fixed-deposits/{id}/premature-closure/{closureId}/approve` with an `Idempotency-Key`.
- `GET /api/v1/fixed-deposits/{id}/transactions` returns the owner's accrual and FD transaction history.

Flyway V2 adds local product terms; V3 widens financial columns and adds optional premature penalty points plus transaction types; V4 adds username to the FD's local user reference; V5 removes a foreign key that incorrectly required staff approvers to be customers. For a Workbench-created schema, baseline at V1 so Flyway can apply later migrations. The active Angular auth, customer, product, savings, and FD routes use the gateway and their MySQL owner services.
