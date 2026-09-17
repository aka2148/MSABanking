# MoneyPal Backend

## Database

The backend now targets the supplied BankingTech PostgreSQL public schema. Its Flyway baseline is [V1__bankingtech_public_schema.sql](src/main/resources/db/migration/V1__bankingtech_public_schema.sql), copied unchanged from the supplied SQL file.

Set the Supabase database connection details before startup. The frontend anon key is not a PostgreSQL password and must not be used here. In the Supabase dashboard, open **Connect** and copy the PostgreSQL connection details; use the database password from that dialog.

```powershell
$env:SUPABASE_DB_URL = "jdbc:postgresql://db.<project-ref>.supabase.co:5432/postgres?sslmode=require"
$env:SUPABASE_DB_USERNAME = "postgres"
$env:SUPABASE_DB_PASSWORD = "your-supabase-database-password"
```

For an IPv4-only network, use the Supabase Session Pooler host and port shown in the same **Connect** dialog instead of the direct `db.<project-ref>.supabase.co` host.

Flyway applies the schema and Hibernate validates the entity mappings; it will not create or alter tables. The former MySQL `data.sql` seed was removed because it referenced the retired `fd_products` table.

## FD v1 API contract

The source OpenAPI document is [openapi/fd-service-openapi.yaml](src/main/resources/static/openapi/fd-service-openapi.yaml). It is the contract to import into SwaggerHub or another API-management tool.

Run the application with `mvn spring-boot:run`, then browse the interactive documentation at `http://localhost:8080/swagger-ui.html`. The raw document is available at `http://localhost:8080/openapi/fd-service-openapi.yaml`.

The `/fd/v1` endpoint mappings are deliberately scaffolding only. They do not call services or persist data. Most return `501 Not Implemented`; internal reference-data sync endpoints return their contract-defined `204 No Content` response.

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
