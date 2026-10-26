# Agent 2+12: Architecture, DevOps and Release Readiness

Scope: repo copy at `<scratchpad>/repo` (paths below are repo-relative). Read-only static audit. No service could be run (JDK11 only, no Maven/Docker/MySQL), so every runtime claim is labelled UNVERIFIED with a verification step. Secret values were not printed; `.env` is untracked (`git ls-files` shows only `.env.example`).

## 1. Service inventory

| Component | Tech / port | Build | Notes |
|---|---|---|---|
| kafka | apache/kafka:4.1.0 KRaft, 9092 internal / 29092 host | image | auto-create topics ON, single broker, healthcheck present (docker-compose.yml:3-37) |
| customer-db / product-db / fd-db | mysql:8.4, host 3307/3308/3309 | image | one schema per service, healthchecks |
| identity-customer-service | Spring Boot 3.5.5, JDBC (no JPA), 8081 | services/identity-customer-service/Dockerfile (maven build stage, JRE17) | auth, MFA, customers, KYC, users/roles/branches/employees, password reset; producer of `user.snapshot` |
| product-service | 8082 | same pattern | products, builder, currencies, FD offers, loan/forex offers; producer of `product.catalog` |
| fd-account-service | 8083 | same pattern | savings, FD lifecycle, loans/forex requests, ledger, demo clock, reports; consumer of both topics; producer of `fd.lifecycle` |
| api-gateway | nginx 1.27-alpine, 8080 | services/api-gateway/Dockerfile (UNTRACKED) | conf also bind-mounted over the baked-in copy |
| frontend | Angular 18 -> nginx 1.27, host 4200 | FRONTEND/Dockerfile (UNTRACKED) | new in compose (uncommitted) |
| kafka-ui | provectuslabs/kafka-ui 0.7.2, 8084 | image | |
| BACKEND/ (legacy monolith) | Spring Boot + JPA + Flyway, Postgres-era | not in compose | still holds the ONLY copy of the service Flyway scripts (see AR-02) |

Java source: 56 files (identity 14, product 15, fd 18 + others). Dense one-line style, no tests run here (src/test exists in all three).

## 2. Service dependency map

```
Browser :4200 -> frontend nginx --(/api /auth /fd)--> api-gateway nginx :8080
                                                          |-> identity :8081 -> customer-db
                                                          |-> product  :8082 -> product-db
                                                          |-> fd       :8083 -> fd-db
identity --outbox--> Kafka user.snapshot ----------------> fd (fd_user_ref)
product  --outbox--> Kafka product.catalog --------------> fd (fd_product_ref, fd_currency_ref, fd_product_currency_ref, fd_product_rate_ref)
fd       --fd_account_events poller--> Kafka fd.lifecycle (no consumer found in repo)
fd -> product  REST  GET /api/v1/customer-products/{family}/{id}  (LoanForexController.java:186-188), forwards caller's Authorization header
JWT: HS512 minted by identity (AuthController.java:87); validated independently in all 3 services with the same JWT_SECRET (base64-decoded).
```
Only synchronous inter-service call: fd -> product (loan/forex offer lookup). No timeouts configured on `RestClient.create(url)` (LoanForexController.java:37) -> default JDK client, effectively unbounded read wait (probable).

## 3. DB ownership and data flow

| DB | Owner | Schema source | Replicated data |
|---|---|---|---|
| moneypal_customer | identity | Flyway `db/service-migration/customer` V1-V5 | outbox_event -> user.snapshot |
| moneypal_product | product | Flyway `.../product` V1-V7 | outbox_event -> product.catalog |
| moneypal_fd | fd | Flyway `.../fd` V1-V10 | fd_*_ref snapshot tables, fd_account_events |

How schema is created (answer to the IMPORTANT question): all three service poms add `${project.basedir}/../../BACKEND/src/main/resources/db/service-migration/<svc>` as a Maven resource with an explicit include list (e.g. services/fd-account-service/pom.xml:23-26), and each application.yml points Flyway at `classpath:db/service-migration/<svc>`. The scripts ARE tracked (22 files, `git ls-files | grep -c service-migration`) but live under BACKEND/, not under services/*/src/main/resources. No JPA/ddl-auto anywhere in services (grep empty). Root `01/02/03_moneypal_*.sql` are Workbench-only manual imports (their header says do not combine with Flyway) and are not mounted anywhere. Docker build context is the repo root (`context: .`) and each Dockerfile copies BACKEND/.../service-migration to `/workspace/BACKEND/...`, with WORKDIR `/workspace/services/<svc>` so `../../BACKEND` resolves. `.dockerignore` excludes .git/.env/.planning/BACKEND.zip/target/node_modules/dist only, so the migrations are included. Conclusion: a clean `docker compose up --build` CAN produce a working schema (static reasoning; UNVERIFIED at runtime). Fragility noted in AR-02/AR-03.

## 4. Gateway route-to-controller table (services/api-gateway/nginx.conf vs controllers)

All controller paths found in the 3 services are routed. Mismatches are about behaviour, not missing routes.

| Controller (service) | Base paths | Gateway rule | OK |
|---|---|---|---|
| AuthController (id) | /api/v1/auth/* | `= /api/v1/auth`, `^~ /api/v1/auth/` -> identity | yes |
| PasswordResetController (id) | /auth/password/{forgot,reset,change} | `^~ /auth/` -> identity | yes (legacy non-versioned path) |
| CustomerAdminController, KycController (id) | /api/v1/customers[/me/kyc...] | `^~ /api/v1/customers/`, KYC document exact w/ 6m body | yes |
| IdentityAdminController (id) | users, roles, branches, employees, customer-classification-types | one pair each | yes |
| ProductCatalogController (prod) | /api/product, /api/product/all, /api/v1/products/** | yes | yes |
| ProductConfigurationController, FdOfferController, EligibleFdOfferController, FdComplianceController, CustomerOfferController, ReferenceDataController (prod) | product-configurations, fd-products(/eligible), compliance/fd, customer-products, currencies, product-categories | yes (a redundant `= /api/v1/currencies/` rule exists, nginx.conf:46, harmless) | yes |
| SavingsAccountController, LoanForexController, FixedDepositController, FdLifecycleController, FdLedgerAdminController (fd) | savings-accounts, loans, admin/loans, forex-requests, fixed-deposits, admin/fixed-deposits/**, admin/ledger | yes | yes |
| FdClockController (fd) | /api/admin/clock/{advance,reset} | `= /api/admin/clock`, `^~ /api/admin/clock/` | yes |
| StaffAccountController (fd) | /fd/v1/staff/** | yes | yes |
| FdReportController / FdCalculatorController (fd) | /api/reports/portfolio[/{u}], /api/reports/operational, /api/calculator/estimate | exact rules; `/api/reports/operational/` and any new `/api/reports/*` or `/api/calculator/*` return gateway 404 | yes today, brittle |
| Spring actuator `/actuator/health` | all 3 | NOT routed (404 via gateway) | only reachable on host ports 8081-8083 |

Header/CORS/timeouts: `proxy_pass http://$var;` without URI forwards the original URI and, by nginx default, the Authorization header unchanged; no X-Forwarded-*/X-Request-Id set; no proxy_*_timeout (default 60s), no client_max_body_size except KYC (so default 1m elsewhere). CORS is handled only in nginx (no Spring CORS config found); OPTIONS returns 204 before auth.

## 5. Uncommitted / untracked diff against HEAD

- docker-compose.yml (+23/-1): api-gateway switches from `image: nginx:1.27-alpine` to a local build; new `frontend` service (host 4200->80, healthcheck). Requires the untracked files below, so a clean `git clone` of HEAD would NOT contain FRONTEND/Dockerfile and compose build would fail.
- services/api-gateway/nginx.conf (+2/-2): CORS origin regex widened from `http://localhost:420[01]` to any `https?://(localhost|127.0.0.1)(:port)?`; allowed headers add X-Requested-With, Accept. Safe (no credentials header, localhost only) but the regex allows any local port.
- Untracked: FRONTEND/Dockerfile (node:20 build -> nginx; output `dist/frontend/browser` matches angular.json outputPath `dist/frontend` + application builder; `npm ci` + production fileReplacements -> `apiBaseUrl: ''`), FRONTEND/nginx.conf, FRONTEND/.dockerignore, services/api-gateway/Dockerfile. Content is sane. Not committed = release blocker.

## 6. Findings

### AR-01 (P1, release/DevOps) Frontend + gateway container assets are uncommitted
Evidence: `git status` ` M docker-compose.yml`, ` M services/api-gateway/nginx.conf`, `?? FRONTEND/Dockerfile`, `?? FRONTEND/nginx.conf`, `?? FRONTEND/.dockerignore`, `?? services/api-gateway/Dockerfile`; docker-compose.yml:175-215.
Expected: demo stack reproducible from the repo. Actual: HEAD compose uses a stock nginx and no frontend; the working tree needs files not in git. Root cause: work in progress (confirmed). Fix: `git add` the six files and commit. Effort: 5 min.

### AR-02 (P1, DevOps) Active services depend on migration scripts stored under the legacy BACKEND/ tree
Evidence: services/*/pom.xml resource blocks (fd:23-26, product:29, identity:89); Dockerfiles line 5; README.MD last bullet claims `services/*/src/main/resources/db/service-migration/`, which does not exist.
Expected: each service self-contained. Actual: deleting/pruning BACKEND (the legacy duplicate) breaks all three builds with no compile error until Flyway finds no migrations at startup (Flyway with an empty location logs a warning and starts with NO schema -> every query fails). Also building outside Docker from any other checkout layout breaks. Root cause: migration scripts were not moved during the split (confirmed). Fix: move `db/service-migration/<svc>` into `services/<svc>/src/main/resources/db/service-migration` and drop the extra pom resources and Dockerfile COPY. Effort: 1 h plus rebuild test. Verify: `docker compose build identity-customer-service && docker compose up`, check `flyway_schema_history` in each DB.

### AR-03 (P1, Data) Seeded product catalog and currencies never reach fd-account-service (Kafka snapshot only on write)
Evidence: grep shows `INSERT INTO outbox_event` only in controller code (ProductConfigurationController.java:502, FdOfferController.java:122, ProductCatalogController.java:97, UserSnapshots.java:16), never in migrations or on startup; product V3/V4/V6 seed currencies/categories into product DB only. fd reads `fd_currency_ref`, `fd_product_ref`, `fd_user_ref` (SavingsAccountController.java:91; LoanForexController.java ~200 "Currency reference has not arrived from Product Service").
Expected: after `docker compose up` the demo flow (register -> open savings -> book FD) works. Actual: it works only after an officer creates/activates a product (emits product.catalog + its currencies) and after each user registers/updates (user.snapshot emitted on register AuthController.java:68). Any user or product that existed before the consumer/topic existed (volume restored, `docker compose down -v` of only the fd-db, Kafka volume wiped, topic retention) has no snapshot and cannot be backfilled: no replay/republish job. Root cause: event-carried state transfer with no initial sync/rebuild (confirmed by code reading). Fix (minimal): on product-service and identity startup (ApplicationRunner) re-insert outbox rows for all active products/users (idempotent because consumers are version-gated), or a one-off admin "republish snapshots" endpoint; document in demo runbook that products must be activated after a fresh start. Effort: 3-4 h. UNVERIFIED at runtime; verify: fresh up, register customer, GET savings-accounts/currencies and try to book an FD before creating any product.

### AR-04 (P1, Reliability) Poison-message / ordering behaviour of snapshot consumers
Evidence: ReferenceSnapshotConsumer.java (user/product listeners throw `IllegalArgumentException` without ack, manual ack mode, application.yml fd:15-21, no DLT, no custom ErrorHandler, `auto.create.topics` on).
Expected: bad message isolated. Actual (probable, Spring Kafka default DefaultErrorHandler = 10 attempts with 0 backoff, then logs and skips and commits): a malformed or order-dependent event (e.g. rate band referencing an unknown currency, `Rate band references an unknown currency`) is silently dropped after retries, leaving fd_product_ref stale/empty until the next product edit. product consumer deletes child rows before re-inserting inside @Transactional, so a mid-way failure rolls back correctly. Idempotency is good: user upsert and product are gated by source_version (`current>=version` skip, GREATEST). Fix: configure `DefaultErrorHandler` with `DeadLetterPublishingRecoverer` or fixed backoff + alert log; add a counter. Effort: 2 h. Verify: publish a bad payload to `product.catalog` via Kafka UI and watch fd logs/offsets.

### AR-05 (P2, Reliability) Outbox publishers: head-of-line blocking and no multi-instance safety
Evidence: identity OutboxPublisher.java:22-33, product OutboxPublisher.java:30-43, FdEventPublisher.java:23-34. Loop `return`s on first failure (blocks all later events behind one bad row; a permanently unserializable payload blocks the topic forever); `SELECT ... LIMIT 25` without `FOR UPDATE SKIP LOCKED` so two replicas (manual L-"elastic scaling") publish duplicates; publish-then-mark gives at-least-once (consumers are idempotent for snapshots, but fd.lifecycle has no consumer). Poll every 1 s with no backoff while Kafka is down -> a WARN stack trace per second per service. Also `KafkaTemplate.send(...).get(10s)` blocks scheduler thread. Confidence: confirmed by reading. Fix: add `FOR UPDATE SKIP LOCKED` in a transaction, per-row failure counter, log without stack at WARN. Effort: 2-3 h (only needed if scale-out is demonstrated).

### AR-06 (P2, Scope/docs) CONTEXT.md says outbox/event infrastructure is out of scope and PostgreSQL; code has both outbox and MySQL
Evidence: CONTEXT.md:32 (PostgreSQL and Flyway) and :38 (no event/outbox infrastructure) vs three OutboxPublisher-style classes and `mysql:8.4` in compose. Note the manual (L-event publishing) expects Kafka-style events, so the code matches the manual, not CONTEXT.md. Root cause: CONTEXT.md stale (confirmed). Fix: update CONTEXT.md (or state the exception) so the demo narrative is consistent. Effort: 15 min. `.planning/codebase/*.md` are also stale: ARCHITECTURE.md/STACK.md (analysis 2026-09-18) describe a single Spring Boot modular monolith on PostgreSQL/Supabase with JPA, port 8080, `SUPABASE_DB_URL`, User Service delegation; reality is three JDBC services on MySQL + nginx + Kafka. Treat those files as legacy-monolith docs; do not cite them for the current system.

### AR-07 (P1, Security/ops) JWT secret must be base64 but `.env.example` placeholder is not; and the only generator is PowerShell
Evidence: `Decoders.BASE64.decode(secret)` in identity AuthController.java:46, JwtAuthenticationFilter.java:26, product JwtFilter.java:27, fd JwtFilter.java:20; .env.example JWT_SECRET=`repl...` placeholder (non-base64 text; invalid characters -> `DecodingException`, or <256 bits -> `WeakKeyException`); only scripts/initialize-demo-env.ps1:16-24 generates a 64-byte base64 value. compose requires `JWT_SECRET` (identity `:?`) so a macOS/Linux demo host following the README's Windows-only flow has no documented way to make a valid secret. MFA_ENCRYPTION_KEY falls back to JWT_SECRET (docker-compose.yml:~108): key reuse. Also `DEMO_ADMIN_USERNAME/PASSWORD` are absent from `.env.example` but required (password 20-100 chars) for first admin (DemoAdminBootstrap.java). Confidence confirmed (static). Fix: document `openssl rand -base64 64` and the admin vars in .env.example; add `scripts/initialize-demo-env.sh`. Effort: 1 h.

### AR-08 (P2, DevOps) Windows-only scripts; README is Windows-only (macOS/Linux gap)
Evidence: scripts/*.ps1 (initialize-demo-env, configure-workbench-mysql, verify-kafka-demo; 399 lines) and README "Start the application on Windows". Fix: POSIX equivalents or a Makefile. Effort: 2-3 h. verify-kafka-demo.ps1 is the only automated smoke test; there is no CI.

### AR-09 (P2, DevOps) No CI/CD, no Kubernetes/Azure manifests, no container registry config
Evidence: no `.github/`, no Jenkinsfile/azure-pipelines, no `*.yaml` besides BACKEND OpenAPI docs, no `k8s/`, no Helm/Terraform/App Service config. Manual (Docker, Kubernetes, Azure App Service, readiness/liveness probes, elastic scaling, Azure SQL) expectations are only partially met: Docker yes; K8s/Azure/Azure SQL (MySQL driver only; Flyway MySQL dialect) no. Fix (minimal for a demo): a GitHub Actions workflow running `mvn -B verify` per service plus `docker compose build`, and a small `k8s/` with Deployment+Service+probes pointing at `/actuator/health/readiness`. Effort: 0.5-1 day. Risk if not done: manual rubric items unmet.

### AR-10 (P2, Operability) Health/readiness is only partly wired
Evidence: all three services expose `health,info` (application.yml) and permit `/actuator/health` in SecurityConfiguration; no probes enabled (`management.endpoint.health.probes.enabled` absent, though Boot auto-enables liveness/readiness groups only on Kubernetes); NO service has a compose `healthcheck`; JRE image has no curl/wget guaranteed; actuator is not routed through the gateway; gateway `/gateway/health` is a static `return 200 UP` (does not reflect backends). The gateway/frontend depends_on use `service_started` only, so frontend and gateway start before services are ready and the first requests return 502 during the ~30-60 s JVM + Flyway startup (UNVERIFIED). Frontend nginx also resolves `api-gateway` at config load (proxy_pass literal host), so a gateway container recreate leaves a stale IP (502 until frontend restart); the gateway itself correctly uses `resolver 127.0.0.11` with variables. Fix: add `management.endpoint.health.probes.enabled=true`, compose healthchecks (use `wget` via `eclipse-temurin` or a `java` HEALTHCHECK alternative), switch depends_on to `service_healthy`, add resolver+variable in frontend nginx. Effort: 2 h.

### AR-11 (P2, Reliability) Startup ordering and failure modes with Kafka/product down
- Kafka: services depend on kafka `service_healthy` (good). Spring Kafka listener and producer do not fail startup if broker later disappears; the fd consumer keeps retrying (default). New registrations still succeed because snapshot is written to the DB outbox in the same transaction (UserSnapshots.writeUserSnapshot inside @Transactional register). While Kafka is down the fd service cannot see new users -> `POST /savings-accounts/admin` fails with "owner" lookup (SavingsAccountController.java:91) and FD booking by a brand-new user fails or reads stale state; recovers automatically after Kafka returns (catch-up from offsets, `auto-offset-reset: earliest`). Eventual consistency window = poll 1 s + consume latency in normal operation.
- Product service down: fd Loan/forex offer lookups return 503 ("Product catalog could not validate this request", LoanForexController.java:191-193); FD booking itself uses the local fd_*_ref tables and is independent of product liveness (good). No circuit breaker/timeouts.
- Topics are auto-created on first use; a consumer subscribing before the topic exists logs `UNKNOWN_TOPIC_OR_PARTITION` warnings until the first publish (cosmetic, probable).
- fd.lifecycle events have no consumer in the repo: written for demonstration only (README/KAFKA-DEMO.md). If the manual requires consumers, flag it.
- Token validity: only identity checks the DB per request (JwtAuthenticationFilter takes JdbcTemplate); product/fd accept a valid signed token for up to 24 h (`JWT_EXPIRATION_MS` 86400000) even if the user was deactivated or role revoked (P2, confirmed by code; probable demo-visible if showing deactivation). Fix: shorten expiry or check `fd_user_ref.fur_user_status` in fd.

### AR-12 (P2, Gateway) Frontend nginx will reject large KYC uploads and has no tuned timeouts
Evidence: FRONTEND/nginx.conf has no `client_max_body_size` (default 1 MB) while gateway allows 6m and identity max-file-size 6MB (identity application.yml). In the Dockerised UI (port 4200, apiBaseUrl '') a KYC document >1 MB gets 413 from the frontend nginx before it reaches the gateway. Confidence: probable (UNVERIFIED; verify by uploading a 3 MB file via http://localhost:4200). Also no `proxy_read_timeout` (default 60 s) for batch endpoints (`accrual-batch`, `maturity-batch`) which can run long; no X-Request-Id propagation. Fix: add `client_max_body_size 6m;` on the frontend `/api/` location. Effort: 5 min.

### AR-13 (P2, Observability) No correlation IDs, metrics or structured logging
Evidence: grep for MDC/traceId/X-Request-Id returns nothing in services; only `info,health` exposed (no metrics/prometheus); no logging config in application.yml; default console format. Gateway adds no request id, so a request cannot be traced across gateway -> fd -> product. Fix: nginx `proxy_set_header X-Request-Id $request_id;` plus a servlet filter/MDC and `logging.pattern.level`. Effort: 2 h. Demo log inspection is limited to `docker compose logs`.

### AR-14 (P2, Time) Inconsistent clocks and timezones
Evidence: fd DemoClockService uses configurable `app.demo-clock.business-zone` (default Asia/Kolkata) with an offset stored in `fd_demo_clock`; product-service FdComplianceController.java:27,37,50 hardcodes Asia/Kolkata with real system date (no offset); identity KycValidation.java:28 uses UTC; FdReportController/FixedDepositController use ZoneOffset UTC; JDBC URL forces UTC (`connectionTimeZone=UTC`). JVM containers default to UTC. After advancing the demo clock, product compliance "effective date" checks (rate disclosure/effective date evaluation) still use real "today" (probable mismatch; verify via `GET /api/v1/compliance/fd/clock` after `POST /api/admin/clock/advance`). Around 18:30 UTC-00:00 IST the business date differs from the UTC date used in reports. No `@Scheduled` accrual/maturity jobs exist (only outbox pollers): accrual and maturity batches run only when an officer calls the admin endpoints, so the demo clock is the only driver; fine but must be stated. Fix: have product compliance call fd clock or accept the date from caller; document. Effort: 2 h.

### AR-15 (P3, Hygiene) Legacy BACKEND duplication and repo bloat
Evidence: BACKEND/ (506 tracked files), BACKEND.zip tracked (460 KB), BACKEND/db/migration V1-V31 (Postgres-era) vs service-migration (MySQL), two sets of docs. README states BACKEND is retained but unused. Risk: reviewers/graders read the wrong code; AR-02 coupling. Fix: after AR-02, archive BACKEND to a tag/branch and remove BACKEND.zip. Effort: 30 min.

### AR-16 (P3, Security/ops) Published ports and defaults expose internals
Evidence: compose publishes DB ports 3307-3309, service ports 8081-8083, Kafka 29092 and kafka-ui 8084 (unauthenticated, `DYNAMIC_CONFIG_ENABLED=false` but no auth) to the host; containers run as root; no resource limits; `restart: unless-stopped`; images use floating Maven/JRE tags (`eclipse-temurin:17-jre`). Direct hits on 8081-8083 bypass the gateway (JWT still enforced by Spring). Fine for a lab, flag for a "production-ish" narrative. Fix: bind to 127.0.0.1, add non-root USER. Effort: 1 h.

### AR-17 (P3, Backup/recovery) No backup or restore procedure
Evidence: only named volumes (kafka-data, customer-db-data, product-db-data, fd-db-data); no mysqldump job, no docs besides `docker compose down -v` (destroys data). Recovery of snapshots after fd-db loss requires AR-03 fix. Fix: document `docker compose exec <db> mysqldump` and a restore step. Effort: 30 min.

### AR-18 (P3, Gateway) Route table brittleness and dead routes
Evidence: nginx.conf:46 duplicate `= /api/v1/currencies/`; `= /api/reports/operational` and `= /api/calculator/estimate` exact-match only; `/auth` legacy routes exist for PasswordResetController only; product SecurityConfiguration `permitAll` for GET `/api/v1/fd-products/**` conflicts with FdOfferController class-level `@PreAuthorize(BANK_OFFICER|ADMIN)` (FdOfferController.java:22) so anonymous GET returns 401/403 at method level, only `/eligible` is intended public (probable). Fix: tidy rules; add a generated route-vs-controller test. Effort: 1 h.

### AR-19 (P3, Config) Docker build time and cache
Dockerfiles copy `src` before resolving dependencies and have no `mvn dependency:go-offline` layer, so every source change redownloads all Maven deps (3 services x full build); first `docker compose up --build` from cold may take 10+ minutes and needs internet (UNVERIFIED). Fix: copy pom first and run `mvn -B dependency:go-offline`, or use BuildKit cache mounts. Effort: 30 min. Also `-DskipTests` means tests are never run in the image build.

## 7. README accuracy vs reality

| README claim | Reality |
|---|---|
| "The frontend is run separately from Compose" | Working tree adds `frontend` service (uncommitted); README not updated (stale once committed). Also still says dev frontend uses :8080 and `npm start`; both still valid. |
| Flyway migrations "under services/*/src/main/resources/db/service-migration/" | Actually under BACKEND/src/main/resources/db/service-migration (AR-02) |
| Start only on Windows / PowerShell | See AR-07, AR-08 |
| "Do not import SQL files into the default Compose databases; Flyway creates schemas" | Correct and consistent with code |
| Ports table | Matches compose |
| Gateway "Routes frontend API calls" | Correct; also now adds CORS for any localhost origin (uncommitted) |
| Kafka topics `user.snapshot`, `product.catalog`, `fd.lifecycle` | Match code |

## 8. Demo-readiness view of startup reliability

Assumptions: Docker Desktop, valid `.env` generated, internet for first build. All UNVERIFIED; verification script: `docker compose up --build -d`, `docker compose ps`, `curl localhost:8080/gateway/health`, `docker compose logs <svc> | grep -i "flyway\|Started"`, then run scripts/verify-kafka-demo.ps1 (Windows) or the equivalent curl flow.

Likely startup sequence: kafka and 3 DBs become healthy (about 20-60 s) -> 3 Java services build/start in parallel (each runs Flyway, then Tomcat; roughly 30-60 s after image build) -> gateway and frontend start immediately (service_started only), so the first browser load within the first minute can see 502 on API calls (AR-10). Nothing in compose gates on service readiness, so use a manual wait or `curl` loop before the demo.

Risks to a clean demo, ranked:
1. Missing/invalid `.env` values (JWT_SECRET non-base64, DEMO_ADMIN_* absent) -> identity crash loop or no admin user (AR-07).
2. Uncommitted files (AR-01) if the demo is cloned fresh.
3. Empty product/currency/user snapshots in fd after a fresh start or volume reset -> FD booking and savings creation fail until an officer creates and activates a product, and any pre-existing user re-saves (AR-03). Pre-seed by activating a product and registering users AFTER the whole stack is healthy, and never reset only one volume.
4. Kafka partial outage, poison message (AR-04, AR-05, AR-11).
5. First-minute 502s (AR-10); KYC >1 MB upload via the Dockerised UI (AR-12).
6. Demo clock advance with product compliance mismatch (AR-14).
7. Cold build time and no CI regression gate (AR-09, AR-19).

Items positively verified by reading: every controller route is reachable through the gateway; Authorization header is forwarded; JWT is validated with one shared secret in all three services; DB-per-service ownership is respected (fd never queries another DB; only REST to product); Flyway scripts are in the Docker context; snapshots are version-gated and idempotent; Kafka healthcheck gates service startup; `.env` is gitignored and not tracked.
