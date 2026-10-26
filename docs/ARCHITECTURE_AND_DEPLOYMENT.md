# MoneyPal active architecture and deployment

This document describes the active Spring Boot/Angular microservice stack in `docker-compose.yml`. `BACKEND/` contains retained legacy monolith code and migration sources; it is not an active request path.

## Request and event flow

```mermaid
flowchart LR
  Browser[Browser / Angular] -->|HTTP localhost:4200| Web[Nginx frontend]
  Web -->|/api and /auth| Gateway[Nginx API gateway :8080]
  Gateway --> Identity[Identity and Customer :8081]
  Gateway --> Product[Product Service :8082]
  Gateway --> FD[FD Account Service :8083]
  Identity --> IdentityDB[(MySQL customer DB)]
  Product --> ProductDB[(MySQL product DB)]
  FD --> FDDB[(MySQL FD DB)]
  Identity -->|user.snapshot| Kafka[Kafka :9092]
  Product -->|product.catalog| Kafka
  Kafka -->|snapshots| FD
  FD -->|fd.lifecycle| Kafka
  KafkaUI[Kafka UI :8084] --> Kafka
  FD -->|read lifecycle records| FDDB
```

Identity owns customer credentials, profile, KYC, roles, and verified email registry. Product owns product catalog, offer terms, currencies, and rate bands. FD Account owns savings/FD accounts, accruals, lifecycle statements/notices, transactions, and journals. Cross-service customer/product reference data reaches FD through `user.snapshot` and `product.catalog`; there is no shared application database.

The FD lifecycle writes statement and notice metadata through its existing lifecycle record service. The report API reads those rows and does not add a second writer. FD summary values come from persisted account and accrual data. They are grouped by currency and decimal scale; stored maturity amounts are labeled as stored values, not recalculated estimates.

## Local Compose deployment

The supported deployment exercised in this repository is Docker Compose on a developer machine. It includes three MySQL containers, Kafka, the three application services, API gateway, Kafka UI, and the frontend Nginx server. The React-like SPA is Angular, built in the frontend image and served at `http://localhost:4200`; the API gateway is `http://localhost:8080`.

```mermaid
flowchart TB
  subgraph DeveloperMachine[Developer PC - Docker Desktop]
    Compose[Docker Compose network]
    Compose --> Web[Frontend Nginx]
    Compose --> Gateway[API gateway]
    Compose --> Identity[Identity service]
    Compose --> Product[Product service]
    Compose --> FD[FD Account service]
    Compose --> IDB[(customer-db volume)]
    Compose --> PDB[(product-db volume)]
    Compose --> FDB[(fd-db volume)]
    Compose --> K[(kafka-data volume)]
  end
  User[Browser] -->|4200| Web
  User -->|8084| KUI[Kafka UI]
  User -->|8080| Gateway
```

Start with an existing ignored `.env` and the Linux container engine:

```powershell
docker compose up --build -d
docker compose ps
Invoke-RestMethod http://localhost:8080/gateway/health
```

The Compose environment is configured with persistent named local volumes. `docker compose down` stops the stack but retains data. `docker compose down -v` deletes those volumes; only use it when intentionally discarding local demo data. The isolated audit stack is separate and uses its own Compose project name/volumes.

FD batch scheduling is disabled by default in Compose (`FD_BATCH_SCHEDULE_ENABLED` defaults to false). The default scheduler code uses a 00:00 UTC cron when enabled. In the lab demo, staff can advance the simulated business clock or run explicit batch endpoints. The staff clock uses the configured FD business timezone; the current service default is UTC.

## Deployment evidence and limits

No Azure, Kubernetes, or other cloud deployment has been executed or verified in this work. The diagrams above describe the local Compose topology only. Cloud infrastructure, production TLS/secret management, broker authentication, backup/restore, high availability, and production operations are not represented as completed deployment work.

The project planning pages contain historical descriptions of a PostgreSQL baseline; the active Compose manifests and service configuration currently use separate MySQL 8.4 databases. For the current runnable stack, follow this document and the root README.
