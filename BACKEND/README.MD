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
