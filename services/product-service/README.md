# Product Service

Standalone Product Catalog service owning the `moneypal_product` MySQL database. It runs on port 8082 by default and packages the shared catalog V1 DDL plus Product V2 localization/rule migration.

## Configure and run

Provide `PRODUCT_DB_URL`, `PRODUCT_DB_USERNAME`, `PRODUCT_DB_PASSWORD`, and a Base64 `JWT_SECRET` of at least 64 bytes through environment configuration. The JWT key must match the Identity service. `SERVER_PORT` is optional. Then run `mvn spring-boot:run` from this directory. The service exposes only `/actuator/health` and `/actuator/info`.

If Workbench already created every table from the Product SQL file, `PRODUCT_FLYWAY_BASELINE=true` explicitly baselines that schema at V1 before applying V2. Compare the Workbench DDL first; for a fresh empty database, leave it false. No Workbench database is used by tests.

## API coverage

- Product catalog reads: `GET /api/product`, `GET /api/v1/products`, and product by ID/code; `lang` selects a translation with English/base-column fallback.
- Product management: create/update/status/deactivate and translation update under `/api/v1/products`.
- Category and currency management under `/api/v1/product-categories` and `/api/v1/currencies`. Currency decimal places are constrained to 0–8.
- FD offer authoring/activation under `/api/v1/fd-products`; offers configure amount bounds and contiguous monthly rate bands per currency.
- Each currency schedule may include optional `prematurePenaltyPoints` (percentage points deducted from the booked annual rate for premature closure).
- Write operations require `ROLE_BANK_OFFICER` or `ROLE_ADMIN` in the shared HS512 JWT. Read-only catalog routes are public for current client compatibility.

Activating an offer writes a versioned `product.catalog` snapshot, including supported currencies, decimal places, amount bounds, and rate bands, to the MySQL outbox in the same transaction. Set `KAFKA_ENABLED=true` and `KAFKA_BOOTSTRAP_SERVERS` to publish it; the outbox row is marked published only after Kafka acknowledges the send. Delivery is at least once, so consumers must be idempotent. The frontend still targets the existing backend until the complete lifecycle is verified through a routing layer.
