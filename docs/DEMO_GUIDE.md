# MoneyPal lab demo guide

This guide uses the active local Compose stack. Use synthetic lab accounts and the configured local `.env`; never paste secrets into a report or chat.

## 1. Start and check the stack

From the repository root, with Docker Desktop running:

```powershell
docker compose up --build -d
docker compose ps
Invoke-RestMethod http://localhost:8080/gateway/health
```

Open the app at [http://localhost:4200](http://localhost:4200), the API gateway at [http://localhost:8080](http://localhost:8080), and Kafka UI at [http://localhost:8084](http://localhost:8084). Gateway health is ready only when the upstream services respond.

## 2. Sign in or create a customer

For the staff demo, use the local administrator account configured in the ignored `.env`. For a customer, use an existing synthetic account or register through the app/Postman. New registration requires email OTP verification before submitting the registration form. The email provider must be configured for the local environment.

Do not paste credentials or OTPs into checked-in files. A customer saves a KYC draft and submits it with `POST /api/v1/customers/me/kyc/submit`; the status remains `PENDING` and customer edits lock. A bank officer or administrator records the decision through `PATCH /api/v1/customers/{customerId}/status` with `{"status":"VERIFIED"}` or `{"status":"REJECTED"}`. Only verified customers pass current FD eligibility rules. The local Postman collection includes these steps.

## 3. Inspect configured products and estimate

Sign in as the customer. Browse **Fixed deposits** or call `GET /api/v1/fd-products/eligible` in the active Postman collection. Select an offer, currency, rate tier, term, and amount from the response. The calculation endpoint is `POST /api/calculator/estimate`; it accepts the terms to estimate and does not create an account.

## 4. Book and fund an FD

Create or select a same-currency savings account. Use an `Idempotency-Key` when opening it. If FD has not yet consumed the Kafka user snapshot, account opening returns retryable `503 USER_REFERENCE_NOT_READY` with `Retry-After`; retry with the same key. Replaying a successful opening with the same body and key returns the original account. The FD booking stores the selected product terms and begins in `PENDING`. Supply a unique `Idempotency-Key` to `POST /api/v1/fixed-deposits`. Funding uses `POST /api/v1/fixed-deposits/{id}/fund`, the same selected savings account, an idempotency key, and a valid MPIN proof when MPIN is enabled. Successful funding moves the FD to `ACTIVE` and records an internal savings debit and balanced journal.

Do not use a demo opening credit unless the local staff-only demo option is explicitly enabled. It is a synthetic accounting entry, not an external payment.

## 5. Inspect interest and maturity

Customers can inspect account accrual and transaction history in the FD page or via `/api/v1/fixed-deposits/{id}/accruals` and `/transactions`. Staff can use the **Operations** clock dashboard to advance the simulated business date, which runs the daily batch for that date, or use the explicit staff batch endpoints. Read the batch counts and account status afterward. Compose enables scheduled interest accrual at 00:00 and the maturity/statement catch-up batch at 01:00 Asia/Kolkata; set `FD_BATCH_SCHEDULE_ENABLED=false` to disable them. Advancing the demo clock remains an immediate full batch run.

After maturity processing changes an account to `MATURED`, close it through the supported close action and inspect the recorded internal settlement and transaction history. The demo does not send funds through an external payment rail.

## 6. Inspect reports and Kafka

Customer report screens are at `/reports`; staff screens are at `/staff/reports`. Customers see their own portfolio, interest history, statement records, and notice records. Staff can see portfolio and lifecycle summaries and download CSV. Status/maturity amounts stay separated by currency; stored maturity amount is presented as persisted data.

In Kafka UI, select cluster `banking-lab`. Current reference and lifecycle topics include `user.snapshot`, `product.catalog`, and `fd.lifecycle`. FD statement and notice reports read the FD database rows written by the existing lifecycle service.

The active API requests are in `docs/postman/MoneyPal-active-stack.postman_collection.json`; its variables are intentionally blank for product/account identifiers and product terms. Active endpoint documentation is `docs/openapi/moneypal-active.yaml`.

## 7. Stop the stack

```powershell
docker compose down
```

This stops containers and retains local named-volume data. Use `docker compose down -v` only when intentionally deleting local database and Kafka data.
