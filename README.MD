# Banking Lab

Banking Lab is a college demonstration application for customer identity, product setup, savings operations, and the fixed-deposit lifecycle. The active app is a set of Spring Boot services with one MySQL database per service, Kafka for service-to-service snapshots/events, an Nginx API gateway, and an Angular frontend.

## What runs

| Component | Purpose | Local address / port |
| --- | --- | --- |
| Angular frontend | Customer and staff web application | http://localhost:4200 |
| API gateway (Nginx) | Routes frontend API calls | http://localhost:8080 |
| Identity and Customer | Sign-in, roles, customers, staff, MFA | http://localhost:8081 |
| Product | Products, categories, currencies, FD offers, customer loan/forex offers | http://localhost:8082 |
| FD Account | Savings accounts, FD lifecycle, loan applications, forex requests, transactions | http://localhost:8083 |
| Kafka UI | Browse Kafka clusters, topics, and messages | http://localhost:8084 |
| Kafka | Service event broker | localhost:29092 |
| MySQL: customer | Identity/customer service database | localhost:3307 |
| MySQL: product | Product service database | localhost:3308 |
| MySQL: FD | FD account service database | localhost:3309 |

The three services own separate schemas. They share user and product snapshots through Kafka; a service should not query another service's database directly. The frontend is run separately from Compose.

`BACKEND/` is the retained legacy Spring Boot modular monolith. It is not part of the active Compose request path.

## Install

For the standard Docker setup, install:

1. Git.
2. Docker Desktop with its Linux container engine and Docker Compose v2 enabled.
3. Node.js 20 LTS (npm is included) for the Angular frontend.

The service images build their Java applications inside Docker. You do not need a local Java or Maven installation to start the Compose stack. Install JDK 17 and Maven only if you plan to build/run the Java services directly on your computer.

## Start the application on Windows

Open PowerShell in the repository folder. Start Docker Desktop and wait until its engine is ready, then run:

```powershell
.\scripts\initialize-demo-env.ps1
docker compose up --build -d
docker compose ps
```

The environment script creates a local, ignored `.env` with random database passwords, JWT/MFA secrets, and a demo administrator account. It preserves an existing `.env`. The Docker build compiles each Java service and starts MySQL, Kafka, the services, gateway, and Kafka UI. On the first run, image downloads and database migrations can take a few minutes.

In a second PowerShell window, install and start the Angular app:

```powershell
Set-Location .\FRONTEND
npm ci
npm start -- --host 0.0.0.0
```

Open [http://localhost:4200](http://localhost:4200). The development frontend sends API requests through the gateway at `http://localhost:8080`.

### First sign-in

The first Identity service startup creates the local administrator using `DEMO_ADMIN_USERNAME` and `DEMO_ADMIN_PASSWORD` in the root `.env`. The setup script generates these values; open `.env` locally to see them. Keep the file private and do not paste its contents into chat, issues, or source control. The password is only used when the configured administrator is first created. Changing the `.env` value later does not reset an already-created user's password.

## Verify and operate

```powershell
docker compose ps
docker compose logs -f identity-customer-service
docker compose logs -f product-service
docker compose logs -f fd-account-service
```

Use `Ctrl+C` to stop following logs. The Kafka dashboard is at [http://localhost:8084](http://localhost:8084); select the `banking-lab` cluster and inspect topics such as `user.snapshot`, `product.catalog`, and `fd.lifecycle` while exercising the app.

To stop the stack while preserving database and Kafka data:

```powershell
docker compose down
```

To delete all Compose database and Kafka data and start fresh, run `docker compose down -v`. This permanently removes the named volumes.

## Optional: use MySQL Server and MySQL Workbench

By default the app uses the three MySQL containers above. The root SQL files are for setting up the service schemas on a separate MySQL Server that you can inspect with Workbench; importing them does not connect the app automatically.

1. Install and start MySQL Server 8.4 (or MySQL 8.0.16 or later) on the host, plus MySQL Workbench. Ensure the server listens on port `3306` and `mysql.exe` is on `PATH`.
2. In Workbench, connect as a MySQL administrator and run each schema file against a fresh local MySQL instance. Each file creates and selects its own database:

   - `01_moneypal_customer.sql`
   - `02_moneypal_product.sql`
   - `03_moneypal_fd.sql`

3. From the repository root, initialize `.env` if needed, then run:

   ```powershell
   .\scripts\initialize-demo-env.ps1
   .\scripts\configure-workbench-mysql.ps1
   ```

   The second script prompts for the local MySQL administrator password. It creates a separate least-scope application user for each database and updates the ignored `.env` with `host.docker.internal` connection URLs. It also enables Flyway baseline mode so the services recognize the imported V1 schemas and apply later migrations. It does not write the administrator password to `.env`.

4. Start or recreate the services so they read the new database settings:

   ```powershell
   docker compose up --build -d
   ```

In this mode the app connects to the host MySQL databases on port `3306`; Workbench can inspect those same databases. Compose may also start its local database containers because they are part of the service dependencies, but the services use the host URLs saved in `.env`.

The SQL files are schema setup files for fresh databases, not application seed data. Do not import them into the default Compose databases; Flyway creates those schemas on first startup. Do not both import a schema manually and run Flyway V1 without the baseline configuration.

## Configuration and data

- `.env.example` documents the environment variables. Use `scripts/initialize-demo-env.ps1` to create a real local `.env`; do not commit `.env`.
- Compose stores MySQL and Kafka data in named volumes, so containers can be recreated without losing data.
- Password reset email delivery is disabled by default. Enabling it requires provider settings in the ignored `.env`.
- Signup email verification uses Brevo transactional email. Set `PASSWORD_RESET_API_URL` to `https://api.brevo.com/v3/smtp/email`, add a Brevo `PASSWORD_RESET_API_KEY`, and configure a verified `PASSWORD_RESET_FROM` sender in `.env`; recreate the identity service after changes.
- Workbench setup and Compose database setup are separate options; the SQL files are not mounted into the running containers.

## Customer loans and forex requests

Administrators create loan and forex offers in **Product configuration** using the existing Product Builder templates, customize the sample values, and activate the completed configuration. The customer catalog shows only currently effective, active Product Builder revisions with complete currency and pricing bands. Offers with eligibility rules needing customer facts that are not available to Product Service, or with charges this request workflow cannot collect, are withheld. Sample template rates are illustrative, not official bank rates. No product or customer IDs are hardcoded.

Customers can open multiple savings accounts. For a fixed deposit, choose an active account in the offer currency at booking. The booking stores that account; funding verifies ownership, status, currency, balance, deposit state, and idempotency before debiting it once. The funding transaction and history include the source savings-account ID. The selected account is also shown on the deposit page.

For a loan, browse **Loans**, enter an amount, tenure, currency, purpose, and an active savings account in the same currency. Submission records `PENDING_REVIEW`; it does not credit funds. A `ROLE_ADMIN` user opens **Loan review** to approve or reject with a reason. After approval, a separate **Disburse** action credits the selected savings account once and records its transaction ID. The destination is fixed for the application. The customer can inspect status and history under **My applications**. KYC details remain in the existing customer administration screen; document uploads and repayment collection are outside this lab workflow. The demonstrated loan lifecycle ends at `DISBURSED`; repayment, `ACTIVE`, and `CLOSED` loan servicing states are not implemented.

Forex uses an indicative **request** flow. Customers choose an active product, destination currency, and funding amount, then inspect the configured markup and submitted request history. The request records no debit, exchange, destination credit, or live rate. Because no reference rate service exists, the app does not quote a destination amount or execute a trade. Forex request recording has no fee; it displays the configured markup and the recorded zero fee.

| API path | Purpose |
| --- | --- |
| `GET /api/v1/customer-products/LOAN` and `/LOAN/{id}` | Current complete loan offers |
| `GET /api/v1/customer-products/FOREX` and `/FOREX/{id}` | Current complete forex offers |
| `POST /api/v1/loans`, `GET /api/v1/loans`, `GET /api/v1/loans/{id}`, `GET /api/v1/loans/{id}/history` | Customer applications and status |
| `GET /api/v1/admin/loans`, `GET /api/v1/admin/loans/{id}`, `POST /api/v1/admin/loans/{id}/approve`, `/reject`, `/disburse` | Admin decisions and credit |
| `POST /api/v1/forex-requests`, `GET /api/v1/forex-requests`, `GET /api/v1/forex-requests/{id}` | Customer indicative requests |

Loan and forex `POST` requests use an `Idempotency-Key` header. Customer identity comes from the bearer token. The FD service owns the new `V7__loan_forex_requests.sql` and `V8__request_product_names.sql` Flyway migrations; Product Service continues to own product configuration. After changing code, run `docker compose up --build -d`, then `npm run build` in `FRONTEND`. The configured demo administrator can review loans, and a registered customer can create their own accounts and applications. For a funded FD demo, the one-time savings demo opening credit may be enabled in Compose.

## Troubleshooting

- **Docker pipe/engine unavailable:** Open Docker Desktop and wait for “Engine running,” then retry `docker compose up --build -d`.
- **Port already in use:** Stop the other app using the conflicting port, or change the corresponding Compose port mapping and frontend API URL.
- **Frontend cannot reach the API:** Keep the Compose stack running and verify `http://localhost:8080/gateway/health` responds with `UP`.
- **Service reports a database connection or migration error:** Check `docker compose ps` and that each database is healthy, then inspect that service's logs. In Workbench mode, confirm MySQL is listening on `3306` and the configured `.env` URLs use `host.docker.internal`.
- **Login fails after changing demo admin settings:** The bootstrap only creates the account if it does not already exist. Existing account credentials are not overwritten on restart.

## Useful files

- `docker-compose.yml` — local service topology, ports, and named volumes.
- `.env.example` — names and purpose of configurable environment values.
- `scripts/initialize-demo-env.ps1` — generates local secrets and initial admin credentials.
- `scripts/configure-workbench-mysql.ps1` — creates service-specific MySQL users and configures Workbench database URLs.
- `01_moneypal_customer.sql`, `02_moneypal_product.sql`, `03_moneypal_fd.sql` — fresh Workbench schema definitions.
- `services/*/src/main/resources/db/service-migration/` — Flyway migrations owned by each active service.
