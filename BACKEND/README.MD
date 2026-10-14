# MoneyPal Backend

## Database

The backend now targets the supplied BankingTech PostgreSQL public schema. Its Flyway baseline is [V1__bankingtech_public_schema.sql](src/main/resources/db/migration/V1__bankingtech_public_schema.sql), copied unchanged from the supplied SQL file.

Set the Supabase database connection details before startup. The frontend anon key is not a PostgreSQL password and must not be used here. In the Supabase dashboard, open **Connect** and copy the PostgreSQL connection details; use the database password from that dialog.

```powershell
$env:SUPABASE_DB_URL = "jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres?sslmode=require"
$env:SUPABASE_DB_USERNAME = "postgres"
$env:SUPABASE_DB_PASSWORD = "your-supabase-database-password"
# Base64-encoded 64-byte value required by the HS512 JWT signer.
$env:JWT_SECRET = [Convert]::ToBase64String((1..64 | ForEach-Object { Get-Random -Maximum 256 }))
```

For an IPv4-only network, use the Supabase Session Pooler host and port shown in the same **Connect** dialog instead of the direct `db.<project-ref>.supabase.co` host.

Flyway applies the schema and Hibernate validates the entity mappings; it will not create or alter tables. The former MySQL `data.sql` seed was removed because it referenced the retired `fd_products` table.

## FD v1 API contract

The source OpenAPI document is [openapi/fd-service-openapi.yaml](src/main/resources/static/openapi/fd-service-openapi.yaml). It is the contract to import into SwaggerHub or another API-management tool.

The staff product-setup and batch contract is [openapi/fd-product-admin-openapi.yaml](src/main/resources/static/openapi/fd-product-admin-openapi.yaml). Both documents are listed in Swagger UI at `http://localhost:8080/swagger-ui.html` and are individually available under `/openapi/`.

An authenticated customer browses active offers at `GET /fd/v1/products`, books with `POST /fd/v1/accounts`, funds the exact principal, and uses the account detail endpoints to accrue, mature, close, and inspect history. The selected currency/tenure rate is resolved server-side and stored with the account.

## FD product setup and batch processing

USD and INR currency references and a `FIXED_DEPOSIT` category are provisioned by Flyway. Use `GET /api/v1/product-categories` to obtain the category ID, then use the staff-only `POST /api/v1/fd-products` endpoint in the FD Product Administration Swagger document. Supply one USD and one INR schedule. Each currency schedule has amount bounds and consecutive, non-overlapping tenure bands; rates are decimal fractions (`0.0650` means 6.50% annually). The initial policy compounds quarterly and pays at maturity. Activate the returned `fd_product_id` using `POST /api/v1/fd-products/{fdProductId}/activate`; only active products appear in customer offers.

Set up illustrative rates that suit the lab demonstration; no rate bands or percentages are hard-coded as a market recommendation. Only `BANK_OFFICER` and `ADMIN` roles can configure products or invoke `POST /api/admin/batch/run`. The scheduler accrues active accounts daily and processes eligible maturities. Batch runs are repeat-safe per account/date and report per-account failures without stopping other accounts.

For an on-demand EOD demonstration, an `ADMIN` can inspect the effective time with `GET /api/admin/clock` and advance it with `POST /api/admin/clock/advance` using `{"days":1}`. Advancing the clock automatically runs the accrual and due-maturity batch for the resulting business date; the response includes a `batch_run` summary. `POST /api/admin/clock/reset` or an application restart clears the in-memory time offset, while persisted accrual and transaction records remain. In normal operation, the scheduler runs accrual at 00:00 IST and maturity processing at 01:00 IST, using the Asia/Kolkata business timezone; the manual batch endpoint remains available for authorized testing or recovery.

## Login Service API contract

The Login Module OpenAPI document is [openapi/login-service-openapi.yaml](src/main/resources/static/openapi/login-service-openapi.yaml). Import this file directly into SwaggerHub, Swagger Editor, or another API-management platform.

When the backend is running, Swagger UI loads the Login Module contract at `http://localhost:8080/swagger-ui.html`, and the raw document is available at `http://localhost:8080/openapi/login-service-openapi.yaml`.

The specification covers the public `/auth` endpoints for password login, MFA, OTP login, sessions, token refresh, logout, account status, and password operations. User Service endpoints are listed under the non-standard `x-internal-user-service` extension for integration reference only; they are not public Login Service routes.

Configure the User Service connection before deployment:

```powershell
$env:USER_SERVICE_URL = "https://user-service.internal"
$env:USER_SERVICE_SERVICE_TOKEN = "signed-service-token"
```

The Login Service must use a secured service-to-service channel. Do not add a direct `user_db` datasource to this module.

## One-time first staff account bootstrap

Public registration creates customers only. When a new development database has no ADMIN to assign staff roles, an opt-in startup bootstrap can create one `BANK_OFFICER` employee. It is disabled by default, stores only a BCrypt password hash, and refuses to promote an existing non-staff username.

Stop the backend, set these values in the same PowerShell session that has the database connection settings, then restart it. Enter the password at the prompt; it is not placed in the command history or committed to source:

```powershell
$env:APP_STAFF_BOOTSTRAP_ENABLED = "true"
$env:APP_STAFF_BOOTSTRAP_USERNAME = "staff.username"
$env:APP_STAFF_BOOTSTRAP_FULL_NAME = "Staff Member"
$env:APP_STAFF_BOOTSTRAP_EMAIL = "your-staff-email@example.com"
$securePassword = Read-Host "Bootstrap password" -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
try {
    $env:APP_STAFF_BOOTSTRAP_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    mvn spring-boot:run
}
finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    $securePassword.Dispose()
    Remove-Item Env:APP_STAFF_BOOTSTRAP_PASSWORD -ErrorAction SilentlyContinue
    Remove-Item Env:APP_STAFF_BOOTSTRAP_ENABLED -ErrorAction SilentlyContinue
    Remove-Item Env:APP_STAFF_BOOTSTRAP_USERNAME -ErrorAction SilentlyContinue
    Remove-Item Env:APP_STAFF_BOOTSTRAP_FULL_NAME -ErrorAction SilentlyContinue
    Remove-Item Env:APP_STAFF_BOOTSTRAP_EMAIL -ErrorAction SilentlyContinue
}
```

The bootstrap runs after Flyway and creates a `ROLE_BANK_OFFICER` login plus an employee profile. Once startup completes, stop the app and start it normally; the bootstrap flag defaults to disabled.


## Savings integration and double-entry settlement

This feature extends the existing services on feature/fd-savings-settlement.
V26 adds savings accounts and the FD original-funding account reference.
V27 adds immutable journal headers/lines with unique operation keys, balanced
header checks and valid debit/credit-side constraints. V1–V25 are unchanged.
Neither migration seeds balances or debits historical FDs.

Zero opening balances are intentional for the current basic savings milestone.
Existing Supabase users, administrators and products are kept; local demo rows are
not part of Git changes. Review migration versions and the shared schema before
applying V26-V29 to the team's database.

Savings creation accepts only display_name and currency_code and derives the
owner from the authenticated customer. It starts at zero. New Angular bookings
select an owned, active savings account in the same currency. Older API bookings
can omit funding_savings_account_id, but cannot fund without an original source.
No endpoint replaces an already funded FD's source or directly edits a balance.
Existing funded FDs without verified source/funding journals fail settlement
with a reconciliation-required error. Do not guess or backfill their funding.

Accounting entries:
- Funding: debit savings liability; credit FD principal liability.
- Maturity: debit interest expense; credit accrued interest payable for the
  final booked calculation. Existing daily/cumulative calculation rows are not
  summed as separate journal postings. Quarterly compounding calculations remain
  unchanged, and the principal liability stays separate from interest payable.
- Normal closure: debit principal liability and interest payable; credit savings
  liability. The FD history transaction reports a remaining FD balance of zero.

Maturity still changes ACTIVE to MATURED. Existing EOD processing, the simulated
clock and its per-account transactions remain in place. The existing close
endpoint performs the payout and changes MATURED to CLOSED in the same transaction.
Savings balances, journal lines, FD history, lifecycle and idempotency receipts
commit together or roll back together. All records are in one database; no
cross-service settlement or distributed infrastructure is introduced.

FD row locks serialize funding/maturity/closure. Savings row locks protect shared
funding sources. Unique journal operation keys prevent second financial postings
even when clients use another request key. Replayed closure uses the original
settlement date. A SETTLEMENT journal is the permanent payout receipt, including
the savings account, currency, payout and savings balance after the transfer.

PrematureClosureService and PrematurePenalty business rules are unchanged.
Its existing workflow remains history-only for now; it does not invoke the new
savings settlement. FdAccountingService is the integration boundary for that
future change, which will also need to reconcile any interest/penalty adjustment.
Do not describe premature closure as a completed savings payout in this version.

API additions:
- POST /fd/v1/savings-accounts (Idempotency-Key supported).
- GET /fd/v1/savings-accounts and /fd/v1/savings-accounts/{id}.
- GET /fd/v1/accounts/{id}/journals (existing FD ownership/staff access policy).
- Original close/fund/mature API paths remain unchanged.

### Isolated Angular demonstration

Do not start the application with shared Supabase settings for this demonstration.
Create a separate local PostgreSQL database banking_demo and a banking_demo user
with schema permissions. Set DEMO_DB_PASSWORD and a local JWT_SECRET, then start
the backend with the local-demo Spring profile. application-local-demo.yml pins
the datasource URL to jdbc:postgresql://localhost:5432/banking_demo. Confirm no
environment or command-line datasource override points to shared Supabase.
Flyway will apply the complete migration chain to the empty local database.

Use the existing customer signup/login and product configuration workflow to
prepare a customer and an eligible FD offer in the isolated database. Start the
Angular frontend using npm start and follow:
1. Open Savings accounts from the customer dashboard or FD home.
2. Create an account in the FD currency. Its initial balance is zero.
3. In local-demo only, Add demo opening funds provisions 100000 currency units
   once per account, backed by debit DEMO_CASH / credit SAVINGS_LIABILITY.
   Retrying never adds another opening credit. This endpoint is absent in normal
   profiles and does not represent real external money.
4. Select an FD offer, book it and choose the original funding savings account.
5. Fund the FD. Verify the savings balance decreases by principal and the FD is ACTIVE.
6. As admin, advance the existing simulated clock through maturity and run EOD.
   The FD becomes MATURED. Savings is unchanged; the interest journal is visible.
7. As the customer, Close at maturity. Savings increases by principal plus interest,
   the FD is CLOSED, and the account page shows settlement receipt, history and journals.
8. Retry closure or rerun EOD to demonstrate that no second payout occurs.

The savings form reuses the existing glass shell, form controls and FD-booking
styles. It contains no FD tenure, rate or maturity inputs. Balance changes are
owned by financial services, leaving account metadata management for future work.

### Verification

FdSavingsSettlementIntegrationTest uses real Spring transactions/JPA with an
explicit H2 in-memory datasource. It covers funding, maturity/closure, ledger
balance, insufficient funds, retries, original receipt dates, concurrent closure,
EOD repetition and a journal failure after a savings credit to prove rollback.
SavingsAccountSchemaTest executes V26/V27 against isolated H2 to check foreign
keys, balances, journal totals and duplicate operation constraints.
FdSavingsPostgresSchemaTest applies the full migration chain to an isolated
PostgreSQL Testcontainer and skips when Docker is unavailable.
No verification requires or uses the team's shared Supabase database.
