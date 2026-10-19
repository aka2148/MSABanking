# Local Kafka and service demo

The root `docker-compose.yml` runs one Apache Kafka KRaft broker, three isolated MySQL databases, the Identity, Product, and FD services, and an Nginx API gateway. The databases do not share tables. The client entry point is `http://localhost:8080`; the gateway forwards auth/customer routes to Identity, catalog/reference routes to Product, and savings/FD routes to FD/Accounts. Kafka uses `kafka:9092` inside Compose and is exposed to host tools on `localhost:29092`.

## Start

1. Generate local credentials with `./scripts/initialize-demo-env.ps1`. The ignored `.env` file contains random local-only database, JWT, and demo admin credentials; do not commit it. Sign into the UI as the configured demo admin using the username and password in this local file.
2. Build the three services from the repository root:

   ```powershell
   Push-Location services/identity-customer-service; mvn -q -DskipTests package; Pop-Location
   Push-Location services/product-service; mvn -q -DskipTests package; Pop-Location
   Push-Location services/fd-account-service; mvn -q -DskipTests package; Pop-Location
   ```

3. Start the stack with `docker compose up -d --build`.

Service ports are gateway `8080`, Identity `8081`, Product `8082`, and FD/Accounts `8083`. MySQL is exposed on `3307`, `3308`, and `3309`; Kafka's host listener is `29092`. Angular's configured API base URL is `http://localhost:8080`. The gateway allows browser requests from `http://localhost:4200` and forwards bearer and `Idempotency-Key` headers.

## Verify event flow

Run the repeatable end-to-end check from the repository root. It sends API calls through the gateway:

```powershell
.\scripts\verify-kafka-demo.ps1
```

It registers a customer through Identity, creates and activates a fixed-deposit offer through Product, and then exercises savings-account opening, FD booking/funding, batch accrual, maturity, closure, and transaction history. Identity and Product publish their transactional outbox rows to `user.snapshot` and `product.catalog`; FD consumes those snapshots into its local reference tables. FD publishes the lifecycle events to `fd.lifecycle` after Kafka acknowledges each message, and the script checks the records for the test account.

The script uses the isolated demo databases. It logs into Identity as the configured demo admin rather than signing a privileged token itself, then registers a separate customer for the workflow. It records a local-only $100 opening credit in the newly created test savings account because external deposit rails are out of scope, and moves that test FD's dates into the past to run accrual and maturity immediately. It does not change Workbench databases.

Useful commands:

```powershell
docker compose ps
docker compose logs -f kafka identity-customer-service product-service fd-account-service
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 --list
docker compose down
```

`docker compose down` stops containers and preserves the local demo volumes. To reset this disposable demo database and Kafka data, use `docker compose down -v`.
