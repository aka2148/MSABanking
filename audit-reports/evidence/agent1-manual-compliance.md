# Agent 1 - Lab Manual and Deliverables Compliance Audit

Scope: requirement-by-requirement matrix of Banking Technology Lab manual (CSF 3142, L1-L25 plus evaluation plan) against the ACTIVE microservices (`services/identity-customer-service`, `services/product-service`, `services/fd-account-service`, `services/api-gateway`, `FRONTEND`). Legacy `BACKEND/` is recorded separately.

Method: read-only static review of the extracted repo copy (`.../scratchpad/repo`). No build, no tests, no containers, no database. Toolchain had only JDK 11 (project needs JDK 17, Maven, Docker, MySQL). Every "it works at runtime" claim is therefore UNVERIFIED. An endpoint existing is not scored PASS unless the code path and its security/validation also match the manual requirement. No secret values were read or reproduced (`.env` was not opened; `application.yml` secrets are env placeholders).

Repo-relative paths are relative to the repo root. Line numbers are file line numbers.

Status key: PASS = code satisfies the manual requirement (runtime still UNVERIFIED unless stated); PARTIAL = some of it, material gap; FAIL = implemented but contradicts the manual; MISSING = not present; UNVERIFIED = cannot be judged statically; OUT OF SCOPE = human/in-lab deliverable (presentation, viva, record book). Priority: P0 = blocks a graded module / demo-breaking, P1 = clear marks loss against a graded criterion, P2 = moderate, P3 = polish.

Abbreviations: FDC = `services/fd-account-service/src/main/java/com/bank/fdservice`; IDC = `services/identity-customer-service/src/main/java/com/bank/identitycustomer`; PRD = `services/product-service/src/main/java/com/bank/productservice`; MIG = `BACKEND/src/main/resources/db/service-migration` (the active services build and run Flyway migrations out of this "legacy" folder).

---

## 0. Headline observations

1. **Largest functional gaps vs the manual are all in the 35-mark FD module**: no scheduled batches in the active stack (only manual/admin/demo-clock triggers), no notifications (email/SMS/WhatsApp, `notifications_log`), no statements (`fd_statements` table exists but no code writes it), no file extracts, no CSV/Python reports, no branch-based account number, FD creation is customer self-service instead of BANK_OFFICER, no staff manual-close endpoint, matured accounts are not auto-closed or paid out by the batch.
2. **FD calculator deviates from L6/L10/L11**: single category only, hard-coded +0.50 / +0.75 boosts, no combined-cap logic (the manual's central rule), not CUSTOMER-restricted, not tied to product rate, no UI.
3. **Documentation/deliverable set is largely absent for the active architecture**: no architecture/ER/deployment diagrams, no test plan, no solution document, no Swagger/OpenAPI on the microservices, Postman collection and OpenAPI exist only for the legacy monolith and describe `/fd/v1/...` routes that do not exist in the active stack.
4. **Cloud/DevOps items are absent**: no Kubernetes manifests, no Azure artefacts, no HPA/scaling config, no explicit liveness/readiness probe config, no HTTPS, no CI.
5. **Planning docs describe a different system** (PostgreSQL/Supabase, `/fd/v1`, Phase 1 "current") from the one that actually runs (MySQL, `/api/v1/fixed-deposits`, Kafka). See section 6.
6. Strong points: bcrypt, JWT, role method security, KYC with masked proof ID, DECIMAL money with explicit rounding, idempotency keys, transactional outbox + Kafka events, double-entry style journals, ownership checks (404-hiding), per-service databases.

---

## 1. Requirement matrix

### 1.1 Stack and cross-cutting (manual sections 1-4)

| ID | Manual ref | Requirement | Evidence (path:line) | Status | Gap | Priority |
|---|---|---|---|---|---|---|
| T-01 | S3 Backend | Java + Spring Boot REST APIs | `services/fd-account-service/pom.xml:4-6` (Boot 3.5.5, Java 17); controllers under FDC/IDC/PRD | PASS | - | - |
| T-02 | S3 Database | MySQL | `docker-compose.yml:48-52,64,80` (mysql:8.4 x3); `services/*/src/main/resources/application.yml` (`com.mysql.cj.jdbc.Driver`) | PASS | Active stack is MySQL. Legacy BACKEND and planning docs say PostgreSQL (see section 6). Manual itself is inconsistent (MySQL in S3, "Azure SQL DB" in L23). | P3 |
| T-03 | S3 Frontend | Angular, HTML5, CSS3 | `FRONTEND/angular.json`, `FRONTEND/src/app/**` | PASS | - | - |
| T-04 | S3 Report generation | Python for operational and customer reports | no `*.py` file anywhere in repo (`find . -name '*.py'` returned none); no `ProcessBuilder`/`Runtime.exec` in `services/` | MISSING | Manual lists Python as mandated reporting tech (also L6, L11). Reports are JSON-only Java SQL aggregations in `FDC/FdReportController.java`. | P1 |
| T-05 | S3 Containerization | Docker | `services/*/Dockerfile`, `docker-compose.yml`, `FRONTEND/Dockerfile` | PARTIAL | Images/compose exist; build never run here (UNVERIFIED). Java service containers have no HEALTHCHECK and compose uses `condition: service_started` for the gateway deps (`docker-compose.yml:~150-160`). Containers run as root. | P2 |
| T-06 | S3 Containerization | Kubernetes | no `k8s/`, `*.yaml` deployment, Helm chart in repo | MISSING | No Deployment/Service/Ingress/HPA/ConfigMap/Secret manifests. | P1 |
| T-07 | S3 Cloud deployment | Azure App Service (+ ACR, Azure SQL, scaling) | no Azure artefact (no `azure-pipelines.yml`, bicep, az scripts, App Service settings) | MISSING | Part of the 35-mark FD module criterion ("cloud deployment"). | P1 |
| T-08 | S3 Dev tools | Dev Containers (VS Code), Git, Postman | no `.devcontainer/`; Git present; Postman only in legacy `BACKEND/postman/` | PARTIAL | Dev container missing; Postman not for active API. | P3 |
| T-09 | S3 Monitoring / Obj. 6 | Readiness and liveness probes | actuator `health,info` exposed (`services/*/src/main/resources/application.yml` `management.endpoints...`); no `management.endpoint.health.probes.enabled`, no `/actuator/health/liveness|readiness` config, no k8s probes; compose healthchecks only for DB/Kafka/gateway/frontend (`docker-compose.yml`) | PARTIAL | Health endpoint exists; probes are not configured or demonstrated. Spring Boot only auto-enables probe groups when it detects Kubernetes. | P1 |
| T-10 | S3 Monitoring | Elastic scaling | no HPA/autoscale rule/replica config; services hold in-process state? (no) but `FdEventPublisher` polls a shared outbox with no lock (`FDC/FdEventPublisher.java:23-31`) so >1 replica would double-publish | MISSING | No scaling config; multi-replica safety of outbox pollers is not designed. | P1 |
| T-11 | S3 Other | Email/SMS alerts | FD service has none. Only email use: password-reset via Resend HTTP API, off by default (`docker-compose.yml` `PASSWORD_RESET_ENABLED:-false`; `IDC/PasswordResetController.java`) | MISSING | No FD alert (creation, interest, maturity). | P1 |
| T-12 | S3 Other / Obj. 7 | Event publishing | Transactional outbox + Kafka: `FDC/FdEventPublisher.java:23-31`, `PRD/OutboxPublisher.java`, `IDC/OutboxPublisher.java`; consumer `FDC/ReferenceSnapshotConsumer.java`; topics `fd.lifecycle`, `product.catalog`, `user.snapshot` | PASS | Code-level PASS; runtime UNVERIFIED. `scripts/verify-kafka-demo.ps1` is a PowerShell end-to-end check (not run). Pollers have no leader lock (see T-10). | P3 |
| T-13 | S3 Other / Obj. 7 | File extracts | no export/extract code; the only file handling is KYC document upload (`IDC/KycService.java:122-141`) | MISSING | Part of the FD 35-mark list ("file extract"). | P1 |
| T-14 | Obj. 8 | Apply caching | no `@Cacheable`, no cache dependency | MISSING | Learning outcome only; not in marks list. | P3 |
| T-15 | Obj. 8 / L22 | API versioning and container scaling | `/api/v1/**` used by most routes; unversioned: `/api/product`, `/api/calculator/estimate`, `/api/reports/*`, `/api/admin/clock`, `/fd/v1/staff` (`services/api-gateway/nginx.conf`) | PARTIAL | Mixed versioning; no compatibility/deprecation notes (L22 requires them). | P2 |
| T-16 | Obj. 4 | Distinguish admin vs end-user workflows | `@PreAuthorize` role split across IDC/PRD/FDC; Angular `staffGuard`/`adminGuard` (`FRONTEND/src/app/core/guards/*.ts`) | PASS | Server enforcement is real; UI guards read role from localStorage (cosmetic only). | - |
| T-17 | Obj. 6 / L17 | Batch jobs + their evaluation | see L14, L17, L18 | PARTIAL | Logic exists; no scheduler, no run logs. | P0 |
| T-18 | Manual DB types | Manual mandates DOUBLE for money (L12, L13, L14, L16 schemas) | `MIG/fd/V1__initial_schema.sql:22,25,31,55` use `DECIMAL(18,4)`, `DECIMAL(9,4)`, `DECIMAL(22,8)` | PASS | Deviation is an improvement (good practice, not manual-mandated). Note in report so a grader who diffs against the manual schema is not surprised. | P3 |
| T-19 | General instruction | Observation record needs API evidence | no collected evidence artefacts in repo | OUT OF SCOPE | In-lab/record-book deliverable. | - |

### 1.2 L1 - Registration API

| ID | Manual ref | Requirement | Evidence (path:line) | Status | Gap | Priority |
|---|---|---|---|---|---|---|
| L1-01 | L1 S3/5.4 | `POST /api/register` accepting username, password, email | `IDC/AuthController.java:51-53` `POST /api/v1/auth/register`; `RegisterRequest` at `:189` (`@Size(min=3,max=255)` username, `@Size(min=8,max=100)` password, `@Email`) | PASS | Path is versioned (`/api/v1/auth/register`), not `/api/register`. Also requires `fullName`. Gateway routes `/api/v1/auth/` to identity (`services/api-gateway/nginx.conf:~34`). | P3 |
| L1-02 | L1 S2/5.5 | Bcrypt password with salt | `IDC/SecurityConfiguration.java:27` `new BCryptPasswordEncoder()`; `AuthController.java:58` `passwords.encode(...)` | PASS | Default strength 10. Password never stored/returned in clear. | - |
| L1-03 | L1 S2/5.4 | Username uniqueness, 409 on duplicate | `AuthController.java:54-55` (count check then 409 "Username is already taken") | PARTIAL | Check-then-insert; a concurrent duplicate relies on DB unique key and would surface as an unhandled `DuplicateKeyException` (500) - `register` has no catch. Message text differs from manual ("Username already exists"). | P3 |
| L1-04 | L1 S4 | `users` table (id, username, password_hash, email, role, created_at) | `MIG/customer/V1__initial_schema.sql` (users, user_credentials, roles, user_roles, customers, customer_emails ...) | PASS | Normalised multi-table design replaces the manual's single table. Good practice, not manual-mandated. Column names differ (`ucr_password_hash` etc.). | P3 |
| L1-05 | L1 S7 | Response `{status:"success", message:"User registered successfully"}` / `{status:"error",...}` | `AuthController.java:52,69` returns 201 + `JwtResponse` (token, roles); errors via `ResponseStatusException` | PARTIAL | Different contract (auto-login token instead of status/message). Functionally fine; a grader running the manual's expected output will see a different JSON. | P3 |
| L1-06 | L1 S6 | Angular registration form | `FRONTEND/src/app/features/auth/signup/signup.ts` (390 lines) / `signup.html` | PASS | Runtime UNVERIFIED. | - |
| L1-07 | L1 S1 / L3 S1 | Role-ready; public registration cannot escalate | `AuthController.java:59-62` always `ROLE_CUSTOMER`; `IDC/DemoAdminBootstrap.java` seeds admin from env | PASS | Good. Admin password policy 20-100 chars enforced at bootstrap. | - |

### 1.3 L2 - Authentication and password masking

| ID | Manual ref | Requirement | Evidence | Status | Gap | Priority |
|---|---|---|---|---|---|---|
| L2-01 | L2 S4.1 | Login: fetch user, verify with bcrypt `matches`, 401 on failure | `AuthController.java:72-83` | PASS | Uniform "Invalid username or password" (no user enumeration). Extra: lockout after 5 failures for 15 min (`:81`), 423 for locked. | - |
| L2-02 | L2 S2 | JWT generated on success | `AuthController.java:182-187` HS512, claims `userId`, `roles`, `tokenVersion`; expiry from `app.jwt.expiration-ms` default 86 400 000 ms (24 h) (`IDC/.../application.yml`) | PASS | Manual example uses 10 h; configurable. Secret from env (`JWT_SECRET`), Base64 required (HS512 needs >= 64 bytes). | P3 |
| L2-03 | L2 S5 / Expected | Password masked in UI | `FRONTEND/src/app/features/auth/login/login.html:15` `<input type="password" ...>`; signup likewise | PASS | Runtime UNVERIFIED. | - |
| L2-04 | L2 S5 | Store JWT (optional) in local storage; show success/failure messages | `FRONTEND/src/app/core/services/auth.service.ts:80-86`; `login.html:6` error block | PASS | localStorage is exposed to XSS (acceptable per manual, flag as good-practice note). | P3 |
| L2-05 | L2 S5 / L7 | Angular `HttpClient` + attach token | `FRONTEND/src/app/core/interceptors/auth.interceptor.ts` adds Bearer, logs out on 401 | PASS | - | - |
| L2-06 | L2 (extra) | MFA / TOTP | `IDC/TotpService.java`, `AuthController.java:84-120,122-153` | PASS | Beyond manual. Secret encrypted at rest (`totp.encrypt`). `MFA_ENCRYPTION_KEY` falls back to `JWT_SECRET` (`docker-compose.yml`, `application.yml`) - key reuse. | P3 |

### 1.4 L3/L4 - Authorization and Customer module integration

| ID | Manual ref | Requirement | Evidence | Status | Gap | Priority |
|---|---|---|---|---|---|---|
| L3-01 | L3 S4.1, L4 S4 | Roles ADMIN, BANK_OFFICER, CUSTOMER stored | `IDC/AuthController.java:59-62` (CUSTOMER), `MIG/customer/V3__bank_officer_role.sql:2-3`, `DemoAdminBootstrap.java:68-72` (ADMIN) | PASS | Roles are `ROLE_*` prefixed codes in `roles` table (many-to-many). | - |
| L3-02 | L3 S4.2 | Stateless Spring Security, JWT filter before auth filter, `/auth/**` public, rest authenticated | `IDC/SecurityConfiguration.java:19-25`; `JwtAuthenticationFilter.java:27-40` | PASS | Uses Spring Security 6 `SecurityFilterChain` (manual shows deprecated `WebSecurityConfigurerAdapter`). Filter re-reads roles from DB per request and checks `tokenVersion` + user status. | - |
| L3-03 | L4 S5.1 | `@EnableGlobalMethodSecurity(prePostEnabled)` | `IDC/SecurityConfiguration.java:14` `@EnableMethodSecurity`; same in `FDC/SecurityConfiguration.java`, `PRD/SecurityConfiguration.java` | PASS | - | - |
| L4-01 | L4 S5.2/9 | `/api/customer/all` only BANK_OFFICER or ADMIN | `IDC/CustomerAdminController.java:17-19,24-42` (`GET /api/v1/customers`, class-level `@PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")`); search `:44-47` | PASS | Path differs (`/api/v1/customers`). Paged + filters. Covered by test `customerAdminApiRequiresOfficerOrAdminRole` (`IdentityCustomerMigrationTest`, needs Docker -> UNVERIFIED). | - |
| L4-02 | L4 S5.2/9 | `/api/customer/profile` only logged-in CUSTOMER | `IDC/AuthController.java:164-166` `GET /api/v1/auth/me` (authenticated, any role); KYC profile `IDC/KycController.java:14,18` (`hasRole('CUSTOMER')`) | PARTIAL | Own-profile identity is `/auth/me`, open to any authenticated role; the CUSTOMER-only profile is the KYC endpoint. No `Customer` profile endpoint with email/phone/address. Acceptable functionally; differs from manual. | P3 |
| L4-03 | L4 S9 | Unauthorized access returns 403 | `@PreAuthorize` -> `AccessDeniedException` -> 403 for authenticated users. Unauthenticated requests: no explicit `AuthenticationEntryPoint`, `httpBasic`/`formLogin` disabled (`IDC/SecurityConfiguration.java:23`) so Spring returns 403 (not 401) - UNVERIFIED | UNVERIFIED | Behaviour for anonymous (401 vs 403) not confirmed; frontend interceptor logs out only on 401 (`auth.interceptor.ts:17`). | P2 |
| L4-04 | L4 S2 | Service-layer filtering (customer only sees own data) | ownership by `authentication.getName()` / token `userId` in `KycService`, `FixedDepositController.userId()` (`FDC/FixedDepositController.java:97`) and `FdLifecycleController.fd()` owner checks returning 404 | PASS | Strong, consistent across FD APIs. | - |
| L4-05 | L4 S6, L3 S5 | Angular decodes token / hides UI by role | `FRONTEND/src/app/core/guards/staff.guard.ts`, `admin.guard.ts`, role from localStorage `roleKey` (`auth.service.ts:92-93`) | PARTIAL | Role is read from a separately stored value, not decoded from the JWT; manual says decode `role` claim. Server still enforces, so impact is cosmetic. | P3 |
| L4-06 | L3 S6 | Token expiration, logout, invalidate on disable | `AuthController.java:161-163` logout bumps `ucr_token_version`; `IDC/JwtAuthenticationFilter.java:33-34` checks version + ACTIVE; `IdentityAdminController.java:32-40` bumps version on suspend | PARTIAL | Revocation works only inside Identity. `PRD/JwtFilter.java` and `FDC/JwtFilter.java:22` verify signature/expiry only (no `tokenVersion`/status check), so a logged-out or suspended user's token keeps working against Product and FD APIs for up to 24 h. | P1 |
| L4-07 | Eval "Customer Module (PII Masking, Setup)" | PII masking | see section 1.12 | PARTIAL | - | P1 |
| L7-01 | L7 S2-4 | Token -> customer module reads username/role; customer sees own, officer/admin sees all | `IDC/CustomerAdminController` (staff list) + own data via `KycService`/`/auth/me` | PASS | Static review only. | - |

### 1.5 L5 - Product and Pricing

| ID | Manual ref | Requirement | Evidence | Status | Gap | Priority |
|---|---|---|---|---|---|---|
| L5-01 | L5 S4.1 | `POST /api/product` create product (code, name, type, currency, effectiveDate, termLimit, rateLimit) | `PRD/ProductCatalogController.java:36-47` `POST /api/v1/products` (productCode, productName, categoryId, baseCurrencyCode, effectiveFrom/To, description); FD-specific create with rate bands `PRD/FdOfferController.java:27-63`; builder `PRD/ProductConfigurationController.java:109` | PARTIAL | Path and payload shape differ. `/api/product` is GET-only (`ProductCatalogController.java:23,25`). `termLimit{min,max}` / `rateLimit{min,max}` do not exist as fields: replaced by per-currency tenure/rate **bands** (`FdOfferController.java:125-128`), which cover the intent. New products are created as DRAFT and need activation. | P2 |
| L5-02 | L5 S4.2 | `GET /api/product/search?productType&startDate&endDate` | `ProductCatalogController.java:28-29` only `status` and `lang` filters; no productType/date-range search | MISSING | Explicit manual endpoint and filter set absent. | P1 |
| L5-03 | L5 S4.3 | `PUT` maintain product (name, interest range) | `ProductCatalogController.java:49-56` (name/category/currency/dates/desc); `FdOfferController.java:67` `PUT /{productId}` (name/description only - `ProductUpdateRequest`) | PARTIAL | Interest range (bands) cannot be changed by the FD-offer PUT; builder PUT (`ProductConfigurationController.java:121`) can. | P2 |
| L5-04 | L5 S3 | Product rules: min/max terms, rate limits | `FdOfferController.java:112-116` validates min<=max amount, tenure inversion, rate scale; `RateBand` rate `@DecimalMin 0.0000 @DecimalMax 1.0000` (`:128`) | PASS | Stored as decimal fractions (0.0650), not percent as in the manual (6.5). Overlap/consecutive band check present (README of legacy describes; code at `:115`). | P3 |
| L5-05 | L5 S3 | Charges (interest credit, tax, withdrawal fees), roles (owner/co-owner/nominee) | charges: `PRD/ProductConfigurationController.java:323,376,447` (charge slabs, floor/cap); roles: no nominee/co-owner in `services/` | PARTIAL | Charges exist in the Product Builder; holder roles (owner/co-owner/nominee) not modelled in active services (`account_holder_roles` table only in `MIG/fd/V1__initial_schema.sql:255`, unused). | P3 |
| L5-06 | L5 S5 | Role protection: only BANK_OFFICER/ADMIN create/update | `ProductCatalogController.java:36,49,58,69,76`; `PRD/SecurityConfiguration.java` (builder paths `hasAnyAuthority`); `FdOfferController.java:22` class-level | PASS | DELETE is ADMIN only (`:69`). Public GET of active products without auth (`SecurityConfiguration` permitAll for `GET /api/product/**` etc.). | - |
| L5-07 | L5 S1/5 | Solution document: API specs, parameters, sample inputs/outputs | none for active services | MISSING | Doc deliverable of L5. `services/*/README.md` list endpoints only; no samples. | P1 |
| L5-08 | L9 S3 | Audit tracking of acting user | `ProductCatalogController.java:43,53,63` writes literal `'API'` into `pro_created_by` / `pro_updated_by` | FAIL | Authenticated username is not recorded; all product audit fields say "API". | P2 |
| L5-09 | L5 / L12 integration | Product events feed FD module | `PRD/ProductCatalogController.java:62,65,92-98` outbox `PRODUCT_CATALOG`; `FDC/ReferenceSnapshotConsumer.java` | PASS | Eventual consistency: FD uses local snapshot (`fd_product_ref`, `fd_product_rate_ref`). Runtime UNVERIFIED. | - |

### 1.6 L6/L10/L11 - FD Calculator

| ID | Manual ref | Requirement | Evidence | Status | Gap | Priority |
|---|---|---|---|---|---|---|
| L6-01 | L6 S2/3 | `POST /api/fd/calculate` | `FDC/FdCalculatorController.java:16-24` `POST /api/calculator/estimate`; gateway `nginx.conf` route `/api/calculator/estimate` | PARTIAL | Path differs. Payload: `{principal, baseInterestRate, termDays, category, compoundingFrequency}` vs manual `{principal, term{value,unit}, baseRate, categories[]}`. Response `{principal, effectiveInterestRate, totalInterest, maturityAmount, apy}` vs `{maturityAmount, effectiveRate, interestEarned}`. | P1 |
| L6-02 | L6 S2 | Simple interest `M = P + P*r*t/100` | `FdCalculatorController.java:35-38` factor `1 + r*days/365` | PASS | t in days/365 (not months/12). Example: P=100000, base 6.5 + SENIOR 0.50 = 7.00, 365 days -> maturity 107 000.00, interest 7 000.00 (matches manual's sample output). | - |
| L6-03 | L6 S2 | Compound `M = P(1+r/n)^(nt)` | `FdCalculatorController.java:42-47` `Math.pow(1+r/n, n*days/365)`; n from `FdCompounding.months` (`FdCompounding.java:10-14`) | PASS | Uses `double` for the power (monetary result rounded to BigDecimal). Unknown frequency strings silently become QUARTERLY (`FdCompounding.java:13`, `periods()` at `:55`) - no validation of `compoundingFrequency` (only `@NotBlank`). Example: 100000 @7% quarterly 365 d -> about 107 186.55 (computed by hand, UNVERIFIED). | P2 |
| L6-04 | L6 S2 | Category rates (e.g. Senior +1%) | `FdCalculatorController.java:19-20,25-29` SENIOR_CITIZEN +0.50, EMPLOYEE +0.75, else 0 (hard-coded); same constants in legacy | FAIL | Manual example says Senior +1%. Boosts are code constants, not product/rate-matrix driven; UI cannot fetch "additional % per category from rate matrix" (L6 S4). | P1 |
| L6-05 | L6 S2, L11 S2 | Combined add-on capped at configured cap (e.g. 2%) | no cap anywhere (`grep -i cap` in calculator and product services finds only charge-slab caps) | MISSING | Central rule of the lab; and request accepts one category only (`CustomerCategory category`, `:58-62`), so EMPLOYEE + SENIOR cannot even be expressed. Manual scenarios (6.5 + 2.0 = 8.5, "Capped? Yes"; 6.5 + 3.0 -> 8.5 capped) cannot be reproduced. | P0 |
| L6-06 | L6 S1 / L11 S1 | Integrate with Product module for base rates; enforce rate limits / product max | calculator takes `baseInterestRate` from caller; no call to product service or `fd_product_rate_ref` | MISSING | "Implement backend enforcement to cap effective rate based on product-defined maximums" (L11) not done. | P1 |
| L6-07 | L6 S4 | UI: category dropdown, auto-display add-on %, cap enforced before submit | `grep calculator|estimate FRONTEND/src` finds no component or service | MISSING | No FD calculator screen at all (graded criterion "FD Calculator (Simple & Compound)" 10 marks - the UI integration portion of L10/L14 presentations). | P0 |
| L10-01 | L10 S3/4 | Calculator restricted to CUSTOMER, 403 otherwise; categories fetched for logged-in user | `FDC/FdCalculatorController.java` has no `@PreAuthorize`; `FDC/SecurityConfiguration.java` only `anyRequest().authenticated()`; no customer category field exists in customer schema | FAIL | Any authenticated role may call; "personalised" category is user-supplied. No customer-category data source in Identity (no SENIOR/EMPLOYEE flag; only DOB in KYC). | P1 |
| L11-01 | L11 S3 | Python script report triggered from Java; CSV `Username,Principal,Term,BaseRate,CategoryAddOn,FinalRate,Maturity` | none | MISSING | Neither Python nor CSV. (Also: manual's `Runtime.exec("python3 ...")` pattern is injection-prone; if implemented, use fixed argv, no user-controlled strings - good-practice note.) | P1 |
| L11-02 | L11 S4 | Compound scenario validation tests | `FDC` test `FdCompoundingTest` (3 tests: date anchoring, known final balances) covers batch compounding, not the calculator endpoint | PARTIAL | No test of `FdCalculatorController`. | P2 |
| L6-08 | L14 S4 | Product-driven simple vs compound | product `compounding_basis` MONTHLY/QUARTERLY/YEARLY only (`FdCompounding.java`, `MIG/product/V7__fd_compounding_options.sql`) | PARTIAL | No SIMPLE-interest product option on the booking/batch path; "SIMPLE" exists only in the calculator. | P2 |

### 1.7 L8/L9 - Integration, product security

| ID | Manual ref | Requirement | Evidence | Status | Gap | Priority |
|---|---|---|---|---|---|---|
| L8-01 | L8 S2/3 | Postman collections for all endpoints | `BACKEND/postman/fd-lifecycle.postman_collection.json` (18 requests, legacy `/fd/v1/accounts...` routes) | PARTIAL | Does not target active services; covers FD only; no auth/product/customer/calculator/report requests. | P1 |
| L8-02 | L8 S3 | Backend logging (SLF4J/Logback) | `FDC/FdEventPublisher.java:15` only; no logger in `FdBatchService`, controllers | MISSING | Batch/audit logging essentially absent in active services (also needed for L17/L18 log requirements). | P1 |
| L8-03 | L8 S2/4 | Full flow Login -> Product -> Calculate -> Customer; invalid access tests; module interdependency doc; system diagram | `scripts/verify-kafka-demo.ps1` (E2E over gateway, not run); `services/KAFKA-DEMO.md`; no diagram | PARTIAL | Script is a good E2E artefact (register, create+activate FD product, savings, book, fund, batch, close) but does not call the calculator or reports and is PowerShell-only. | P2 |
| L9-01 | L9 S2-3 | Product APIs require JWT + role | see L5-06 | PASS | - | - |
| L9-02 | L9 S4 | `HttpInterceptor` attaches JWT; disable product UI for unauthorised | `auth.interceptor.ts`; routes `staff/fd-products`, `staff/product-builder` guarded by `staffGuard` (`app.routes.ts:40-50`) | PASS | Runtime UNVERIFIED. | - |

### 1.8 L12/L13/L16 - FD account creation

| ID | Manual ref | Requirement | Evidence | Status | Gap | Priority |
|---|---|---|---|---|---|---|
| L12-01 | L12 S2 | `fd_accounts` table with account no, product, customer, term, rate, amount, status, created | `MIG/fd/V1__initial_schema.sql:16-49` | PASS | Names `fa_*`; DECIMAL not DOUBLE; status vocabulary PENDING/ACTIVE/MATURED/CLOSED/PREMATURELY_CLOSED (manual: ACTIVE/CLOSED). Maturity date/amount extra. | - |
| L12-02 | L12 S2, L13 S5.1 | Account number pattern `[3-digit branch][6-digit seq][1-digit check]` | `FDC/FixedDepositController.java:57` `"FD-"+UUID.randomUUID()...substring(0,16).toUpperCase()` | FAIL | Random 16-hex UUID fragment: no branch, no sequence, no check digit, not numeric. (Legacy generator is `FD-BR<branch>-<8 digit seq>` - also not the manual pattern.) | P1 |
| L12-03 | L12 S2 | Pluggable `AccountNumberGenerator` interface | none in `services/`; legacy has a class `BACKEND/.../fd/service/AccountNumberGenerator.java` (not an interface) | MISSING | Account-number logic is inline in the controller. | P2 |
| L12-04 | L12 S4 / L13 S2 | Validate product code exists/active, term within bounds, rate within limits, min deposit | `FixedDepositController.java:51-53` joins local product snapshot, requires `p.fpr_status='ACTIVE'`, tenure band and amount range; picks highest matching `annual_rate` | PASS | Rate is taken from product band, never from client. Runtime UNVERIFIED (FD tests need Docker). | - |
| L12-05 | L12 S5, L13 S2/5.2, L16 S7 | Only BANK_OFFICER (and ADMIN in L13) can create FD accounts | `FixedDepositController.java:40-63` no `@PreAuthorize`; customer id taken from token (`userId(actor)`, `:97`); requires verified KYC (`CustomerProductAccess.requireVerified`, `FDC/CustomerProductAccess.java:11-18`) | FAIL | Opposite model: customer self-service booking. A BANK_OFFICER cannot create an FD for a customer (it would be booked for the officer's own userId). Manual expects 403 for non-officers. Defensible design, but contradicts the explicit mandatory role rule across L12, L13, L16, L20. | P1 |
| L12-06 | L12 S5 | Customer can view own FD records only | `FixedDepositController.java:34-38` (list/get by token userId), owner checks in `FdLifecycleController.java:76,99,106,113` | PASS | - | - |
| L13-01 | L13 S3/5.2 | `POST /api/fd/account/create` with productCode, termMonths, principalAmount, branchCode, customerId | `FixedDepositController.java:40-41` `POST /api/v1/fixed-deposits` + `Idempotency-Key`; `BookingRequest` (`:104`: productId, savingsAccountId, principal, currencyCode, tenureMonths) | PARTIAL | No branchCode, no customerId (token-derived), uses productId not productCode, requires a linked savings account and currency. | P2 |
| L13-02 | L13 S4 | `interestRate` = final rate after category adjustments | rate from product band only (`FixedDepositController.java:53`) | MISSING | No category add-on applied at booking (and no cap, see L6-05). | P1 |
| L13-03 | L13 S8 | Response carries the new FD account number; unique | `FdView` returned (`:62`); UNIQUE key `fd_accounts_fa_account_number_key` (`MIG/fd/V1__initial_schema.sql:47`) | PASS | - | - |
| L13-04 | L13 S7 | Angular creation form: product dropdown, term, principal, auto-fill rates | `FRONTEND/src/app/features/fd/booking/fd-booking.ts` (3-step wizard), `fd-home` lists eligible products (`PRD/EligibleFdOfferController.java:21`) | PASS | Customer-facing, matches the self-service model; no officer-side creation screen (`staff/fd` is a viewer/dashboard: `staff-account-dashboard.ts`). Runtime UNVERIFIED. | - |
| L16-01 | L16 S5.1 | `POST /fd/account/create-with-txn` | not present; booking `POST /api/v1/fixed-deposits` (`:40`) then `POST /{id}/fund` (`:65`) | FAIL | Two separate API calls. Booking creates PENDING FD only. | P1 |
| L16-02 | L16 S2/5.2 | Initial DEPOSIT transaction recorded at creation | At funding: `fd_transactions` row typed `FD_FUNDING` (`FixedDepositController.java:80-81`, `MIG/fd/V2__product_terms_reference.sql:31`); no `DEPOSIT` type | PARTIAL | Substance is satisfied (funding txn linked via account id with savings debit + journal), but name/timing differ and creation does not produce it. | P2 |
| L16-03 | L16 S3 / L16 `@Transactional` | Account + transaction in a single DB transaction | booking `@Transactional` (`:40`) inserts FD + BOOKED event only; funding `@Transactional` (`:65`) updates savings, inserts savings txn, FD txn, journal + lines, activates FD, event | PARTIAL | Each step is atomic with its own idempotency; the pair is not one transaction, so a PENDING unfunded FD is an observable state (by design). | P2 |
| L16-04 | L16 S6.2 | Response includes `initialTransactionId` | `FdView` (`:106`) has no txn id | MISSING | Frontend cannot show "Transaction ID" / link to history right after create (L16 S8). | P2 |
| L16-05 | L16 S7 | Validate principal >= minimum, term in limits, product active | `FixedDepositController.java:48,51-53` scale check + band match | PASS | - | - |
| L16-06 | L16 S1 | Audit metadata (who/when/amount) | `ft_created_by` = actor name (`:81`); events table `fae_*` | PASS | - | - |

### 1.9 L14/L17 - Interest, batch, statements

| ID | Manual ref | Requirement | Evidence | Status | Gap | Priority |
|---|---|---|---|---|---|---|
| L14-01 | L14 S5.1 / L17 S4 | `@Scheduled` daily (cron) batch for interest | active services: only `@Scheduled` is the outbox poller (`FDC/FdEventPublisher.java:23`); batches triggered by `POST /api/v1/admin/fixed-deposits/accrual-batch` and `/maturity-batch` (`FDC/FdLifecycleController.java:30-34`) and admin clock advance (`FDC/FdClockController.java:30-34`) | FAIL | No cron scheduler. Legacy BACKEND does have `FdBatchScheduler` (cron 00:00 and 01:00 IST: `BACKEND/src/main/java/com/bank/fd/scheduler/FdBatchScheduler.java`) but it is not on the active path. Graded "batch processing". | P0 |
| L14-02 | L14 S4 | Interest formulas simple/compound by product | accrual is daily Actual/365 on a compounding balance with period capitalisation (`FDC/FdBatchService.java:65-97`, `FdCompounding.java:22-32`) | PARTIAL | Compounding basis per product (monthly/quarterly/yearly) is honoured; manual's closed-form `P(1+r/1200)^n` is not what is used, results will differ slightly from manual examples (daily Actual/365 vs month-based). No simple-interest product option. Designed policy tag `academic-v2-*` (`FixedDepositController.java:55`). | P2 |
| L14-03 | L14 S3 | Capitalise vs queue payout; payout schedules | capitalisation: `FdBatchService.java:89-91`; payout frequency stored (`FixedDepositController.java:53`), table `fd_interest_payout` exists (`MIG/fd/V1__initial_schema.sql:70`) but no code reads/writes it | PARTIAL | Periodic payouts (monthly/quarterly) not implemented; all interest paid at maturity/closure. | P2 |
| L14-04 | L14 S9 | Idempotent on re-run | accrual resumes from last stored accrual date (`FdBatchService.java:74-84`: `if(!last.isBefore(end))return 0`); maturity filters `status='ACTIVE'` and journal key `INTEREST:<id>:MATURITY` (`:53-54`) | PASS | Logic-level PASS; runtime UNVERIFIED. Manual-check: `FdAccountMigrationTest.persistedAccrualUsesEachStoredCompoundingBasis` (Docker). No test for double-run. | - |
| L14-05 | L14 S9, L17 S8 | Log failing accounts and continue | `FdBatchService.accrue` (`:27-34`) loops with no try/catch inside a single `@Transactional` | FAIL | One bad account rolls back the whole batch (all-or-nothing). `DailyBatchResult.accountsFailed` is hard-coded 0 (`:24`). | P1 |
| L14-06 | L14 S9 | Manual triggers restricted to BANK_OFFICER/ADMIN | `FdLifecycleController.java:30,33` | PASS | Manual (L20) says ADMIN runs batches; officer also allowed. Demo clock is ADMIN only (`FdClockController.java:17`). | P3 |
| L14-07 | L14 S6 | `fd_interest_transactions` table | equivalent `fd_interest_accrual` (`MIG/fd/V1__initial_schema.sql:51-68`, DECIMAL(22,8)) | PASS | Renamed; includes compounding balance and capitalised amount. | - |
| L14-08 | L14 S7-8 | Admin dashboard of upcoming payouts, manual run UI, optional CSV interest summary, console log of run | UI: `FRONTEND/src/app/features/staff/operations/clock-dashboard.ts` (advance clock/run batch); no payout schedule UI; no CSV; no run log | PARTIAL | - | P2 |
| L17-01 | L17 S3-4 | Batch chains: interest -> statements -> interest transaction -> log | `FdBatchService.runDaily` (`:19-25`) chains accrue + mature only | PARTIAL | No statement step. | P1 |
| L17-02 | L17 S6-7, S10 | Generate daily/monthly `fd_statements` | table `fd_statements` in `MIG/fd/V1__initial_schema.sql:155-168`; no insert anywhere in `services/` | MISSING | Table exists, no code. Customer role "view statements" (L20 S7) unsupported. Monthly cron (`0 0 2 1 * ?`) absent. | P1 |
| L17-03 | L17 S10 | Each active FD has a daily interest record in `fd_transactions` | daily rows go to `fd_interest_accrual`; `fd_transactions` gets only FD_FUNDING / FD_MATURITY / FD_PENALTY / FD_CLOSURE; history API unions both (`FdLifecycleController.java:76`) | PARTIAL | Visible to the customer via `/transactions`, but not in `fd_transactions` as manual states. | P3 |
| L17-04 | L17 S8/12 | Batch start/finish log lines, processed counts | no logging in `FdBatchService` | MISSING | `BatchResult` returned to API caller only. | P1 |
| L17-05 | L17 S9 | Only the scheduler runs these (admin on-demand later) | n/a: no scheduler | FAIL | See L14-01. | P0 |

### 1.10 L15 - Premature withdrawal

| ID | Manual ref | Requirement | Evidence | Status | Gap | Priority |
|---|---|---|---|---|---|---|
| L15-01 | L15 S5 | `POST /api/fd/account/withdraw` body `{fdAccountNo, withdrawalDate, transferAccount}` | `POST /api/v1/fixed-deposits/{id}/premature-closure` (request, body `{reason}`) (`FDC/FdLifecycleController.java:46-61`) then staff `POST /api/v1/admin/fixed-deposits/{id}/premature-closure/{closureId}/approve` (`:63-73`) | FAIL | Two-step maker-checker flow; no `withdrawalDate`, no `transferAccount` input; response is a quote/`FdCloseResponse`, not `{status,message,withdrawalAmount,penaltyApplied}`. | P1 |
| L15-02 | L15 S3 / Intro | Accrued interest till withdrawal date | `FdLifecycleController.java:55-57` interest = principal x (penalised rate) x days(open -> min(today, maturity))/365, simple, using business date (demo clock) | PARTIAL | Simple Actual/365 on full principal; ignores compounded capitalisation already booked by the accrual batch, so the payout can differ from the accrued balance shown on the account. | P2 |
| L15-03 | L15 S4 | Penalty: reduce interest by 1% or configured; optional flat charge | penalty = full-rate interest minus interest at (rate - penalty points/100) (`:54,58`), points from product currency config (`fd_product_currency_ref.premature_penalty_points`); closure not allowed if not configured (`:52`) | PARTIAL | Penalty is expressed as rate points per product (acceptable "as configured"); no percentage-of-interest mode and no flat withdrawal fee (L15 S4 optional). Records `FD_PENALTY` txn (`:127`). | P3 |
| L15-04 | L15 S8 | Status must be ACTIVE; date between creation and maturity | `:49` requires ACTIVE; dates clamped (`:55`) rather than validated against a supplied date | PARTIAL | No explicit withdrawalDate validation; an ACTIVE FD past maturity date but before the maturity batch is closed prematurely with penalty (clamped to maturity). | P2 |
| L15-05 | L15 S3 | Atomic: all steps succeed or none | `@Transactional` on request/approve (`:46,63`) with `settle()` doing savings credit + txns + journal + status change in one transaction; row locks `FOR UPDATE` (`:120,136`) | PASS | Runtime UNVERIFIED. | - |
| L15-06 | L15 S6.1/8 | CUSTOMER or BANK_OFFICER may initiate; owner or officer only | request: owner only (`:49` owner check; no officer path); approve: BANK_OFFICER/ADMIN, approver != owner (`:69`) | PARTIAL | Officers cannot initiate on behalf of a customer; added segregation of duties (good practice beyond manual). | P3 |
| L15-07 | L15 S3 | Transfer proceeds to customer's savings account or payout | `settle()` credits the savings account linked at booking (`:119-124`) | PASS | Cannot choose another destination account (manual's `transferAccount`). | P3 |
| L15-08 | L15 S7 | DB: status CLOSED, closed_at; txns for final interest, withdrawal and penalty | `fa_status='PREMATURELY_CLOSED'`, `FD_CLOSURE` + `FD_PENALTY` txns, journal `SETTLEMENT` (`:125-132`) | PASS | Final interest transaction not separately recorded (no INTEREST txn). Different status label. | P3 |
| L15-09 | L15 S9 | UI: Withdraw button for ACTIVE only, confirm, show payout/penalty | `FRONTEND/src/app/features/fd/account-detail/fd-account-detail.ts:~150` (`requestPrematureClosure`, `approvePrematureClosure` in `fd.service.ts:150-158`) | PASS | Static; runtime UNVERIFIED. | - |
| L15-10 | L15/L22 | Test: premature withdrawal applies correct penalty | no premature-closure test in `services/fd-account-service/src/test` (`FdAccountMigrationTest` tests book-fund-accrue-mature-close only) | MISSING | Gap against L22 test-case table. | P2 |

### 1.11 L18/L19/L20 - Maturity, closure, lifecycle

| ID | Manual ref | Requirement | Evidence | Status | Gap | Priority |
|---|---|---|---|---|---|---|
| L18-01 | L18 S3/5 | Scheduled maturity batch (cron 4 AM) over ACTIVE accounts with maturity <= today | `FdBatchService.mature` (`:36-63`) query `status='ACTIVE' AND DATE(fa_maturity_date)<=?`; no cron | PARTIAL | Logic matches; no schedule (see L14-01). | P0 |
| L18-02 | L18 S4 | Maturity amount = principal + accrued interest | `:46-50` final accrual to maturity then balance from compounding ledger; maturity amount stored | PASS | Runtime UNVERIFIED. | - |
| L18-03 | L18 S3/5.2 | Create `MATURITY_PAYOUT` transaction and close the FD (CLOSED) | batch inserts `FD_MATURITY` ("Internal maturity record; no external payment rail was called") (`:59`) and sets status `MATURED` (`:51`); money moves only when customer calls `/close` (`FdLifecycleController.java:36-44`) | FAIL | Batch does not pay out and does not close; matured FDs stay MATURED until the customer acts (and staff cannot close for them, see L19-01). Contradicts manual "Matured FD accounts are closed automatically". | P1 |
| L18-04 | L18 S3/7 | Send email/WhatsApp maturity notification; `notifications_log` table | none; no `notifications_log` in `MIG/**` | MISSING | Also covers L20 Scenario 4 (creation/interest/maturity notifications). | P1 |
| L18-05 | L18 S6/9 | GL/FD transaction logs; interest finalised | journals `INTEREST` with lines `INTEREST_EXPENSE` / `FIXED_DEPOSIT` (`:52-58`); settlement journal `FIXED_DEPOSIT` -> `SAVINGS` (`FdLifecycleController.java:131`) | PASS | Double-entry journals are a strength. | - |
| L18-06 | L18 S10 | Skip CLOSED/withdrawn; failures logged and skipped | skip: `:43` status check; failures: none (single tx, no logging) | PARTIAL | See L14-05. | P1 |
| L18-07 | L18 S10 | API keys/templates in env vars; no plain PII in logs | n/a (no notification code); `IDC/KycController.java:21-23` redacts KYC draft `toString` | PARTIAL | Good PII-log hygiene in KYC; nothing to assess for notifications. | P3 |
| L19-01 | L19 S4.1 | `POST /fd/account/manual-close` for BANK_OFFICER/ADMIN | `FdLifecycleController.java:36-44` `POST /api/v1/fixed-deposits/{id}/close` - no `@PreAuthorize`; `owner==user` check (`:40`) so only the owning customer can close | FAIL | No staff manual-close path; role rule from manual inverted. | P1 |
| L19-02 | L19 S4.2 | Revalidate maturity date and interest before close | `verifyMaturityJournals` (`:85-91`) requires funding and interest journals to reconcile; status must be MATURED (`:41`) | PASS | Strong reconciliation. | - |
| L19-03 | L19 S4.2/7 | Transfer payout to customer savings (credit), `MATURITY_PAYOUT` txn | `settle()` (`:118-134`) credits savings, inserts `FD_CLOSURE` txn (not `MATURITY_PAYOUT`), journal DEBIT FIXED_DEPOSIT / CREDIT SAVINGS (matches manual's GL mapping) | PASS | Naming differs (`FD_CLOSURE`). | P3 |
| L19-04 | L19 S10 | No duplicate maturity processing | status check + `fd_operation_idempotency` claim (`:38`, `claim()` at `:140`) + unique journal key | PASS | Runtime UNVERIFIED. | - |
| L20-01 | L20 S3 step 1 | Create FD via create-with-txn as BANK_OFFICER | see L12-05, L16-01 | FAIL | - | P1 |
| L20-02 | L20 S3 steps 2,4,7 | `GET /fd/account/{id}`, `GET /fd/transactions`, verify closed + payout posted (CUSTOMER) | `FixedDepositController.java:37-38`; `FdLifecycleController.java:75-76` `GET /{id}/transactions`; `:97-109` accrual history | PASS | Paths versioned/different. | - |
| L20-03 | L20 S3 step 8 | `GET /savings/account/{id}` verify payout | `FDC/SavingsAccountController.java:39-48,73` | PASS | - | - |
| L20-04 | L20 S3 step 9 | `GET /report/fd-summary` as ADMIN | `GET /api/reports/operational` (`FDC/FdReportController.java:37-38` `@PreAuthorize BANK_OFFICER/ADMIN`) | PARTIAL | See L21-01. | P1 |
| L20-05 | L20 S5 | Scenario 3: withdraw on CLOSED account rejected, status unchanged | `FdLifecycleController.java:49` "Only an active FD can request premature closure" -> 409 | PASS | Runtime UNVERIFIED; no automated test of this path. | - |
| L20-06 | L20 S5 | Scenario 4: notifications on creation / interest / maturity | none | MISSING | See L18-04. | P1 |
| L20-07 | L20 S4 | Mock SMTP / WhatsApp sandbox | none (compose has Kafka UI only) | MISSING | - | P2 |
| L20-08 | L20 S7 | Role table: CUSTOMER view/withdraw/**statements**; OFFICER create/close/view all; ADMIN batch/reports | customers: view, premature request - statements MISSING; officer: view all `FDC/StaffAccountController.java:34` (`GET /fd/v1/staff/accounts`, masks account no via `mask()` `:120`), create/close FAIL (L12-05, L19-01); admin batch/reports PASS | PARTIAL | - | P1 |
| L20-09 | L20 S1 | End-to-end automated proof | `scripts/verify-kafka-demo.ps1` (245 lines); `FdAccountMigrationTest.customerCanBookFundAccrueMatureAndCloseWithIdempotentRequests` (Testcontainers) | UNVERIFIED | Neither could be run here (no Docker/MySQL/JDK17). | P1 |

### 1.12 Customer module - PII masking and setup (Eval 5 marks)

| ID | Manual ref | Requirement | Evidence | Status | Gap | Priority |
|---|---|---|---|---|---|---|
| PII-01 | Obj. 4, Eval | PII data protection / masking | Government ID returned only as masked `****<last4>` (`IDC/KycService.java:85,167`; test asserts `****234F`, `KycIntegrationTest.java:68`); ID value removed from API response (`:167`); KYC draft body logging redacted (`KycController.java:21-23`) | PASS | Masking is limited to the proof-of-ID. | - |
| PII-02 | Eval | Staff views mask PII | Staff customer list returns name, customer number, username, KYC/risk only (`IDC/CustomerAdminController.java:105-107`); FD/savings account numbers masked in staff dashboards (`FDC/StaffAccountController.java:46,61,120`, `FDC/FdLedgerAdminController.java:61,77`) | PARTIAL | PII is omitted rather than masked; staff cannot see masked email/phone/DOB at all, and no UI shows masked email/mobile. No explicit masking of email/phone/DOB anywhere (`grep -i mask` finds only ID/account numbers). A grader looking for "mask email/phone" finds nothing. | P1 |
| PII-03 | Obj. 4 | Encrypted data handling (at rest) | `proof_of_id_value VARCHAR(20)` plaintext (`MIG/customer/V4__kyc_profile.sql:12`); DOB plaintext; KYC document stored as `MEDIUMBLOB` (`:19`); MFA secret is encrypted (`TotpService`); passwords bcrypt | PARTIAL | Government ID and DOB stored unencrypted. Manual only says "encrypted credential storage" for L1 (met) and "encrypted data handling" as an objective. | P2 |
| PII-04 | Eval | Customer setup (onboarding data) | registration + KYC wizard (9 fields, auto-verify) `IDC/KycService.java:55-101`; staff create/update customer `CustomerAdminController.java:59-97`; `FRONTEND/src/app/features/kyc/kyc.ts` | PASS | Beyond manual. Staff can set KYC status directly (`PATCH /{id}/status` `:89-97`) with no audit trail of the actor. | P3 |
| PII-05 | L18 S10 | Sensitive customer info not logged in plain text | minimal logging overall | PASS | Positive by absence. | - |

### 1.13 L21 - Reports

| ID | Manual ref | Requirement | Evidence | Status | Gap | Priority |
|---|---|---|---|---|---|---|
| L21-01 | L21 S4.1 | `GET /report/fd-summary` per `product_code`: totalAccounts, totalPrincipal, totalInterestAccrued (BANK_OFFICER/ADMIN) | `FDC/FdReportController.java:37-45` `GET /api/reports/operational`: totals grouped by **currency** (count, principal, accrued, avg rate) + 30-day maturing-soon list; ACTIVE only | PARTIAL | Different path, not grouped by product, no `FDReportDto` shape. Currency grouping is a defensible improvement. | P1 |
| L21-02 | L21 S4.2 | `GET /report/customer-portfolio` (CUSTOMER self only) | `FdReportController.java:30-32` `GET /api/reports/portfolio` (CUSTOMER/OFFICER/ADMIN; uses `actor.getName()`), staff `GET /api/reports/portfolio/{username}` (`:34-36`) | PASS | Self-only enforced by token username; staff variant OFFICER/ADMIN. `fd_user_ref` must hold the username (event-fed). | - |
| L21-03 | L21 S1 | Interest transaction history report | per-FD only: `GET /api/v1/fixed-deposits/{id}/accruals[/cumulative]` (`FdLifecycleController.java:97-109`) | PARTIAL | No cross-account report. | P2 |
| L21-04 | L21 S1 | Maturity summary report | only `maturingSoonAccounts` list inside operational report | PARTIAL | - | P2 |
| L21-05 | L21 S6 | CSV (admin download), optional PDF | none (`grep -rn "csv"` in services returns nothing) | MISSING | Explicitly listed as supported format. | P1 |
| L21-06 | L21 S1-2 / S3 | Python-generated reports; report UI | no Python; no Angular reports page (`grep "api/reports" FRONTEND/src` finds nothing) | MISSING | Reports module (5 marks) and the "login page with Report Module integrated" presentation (L23) have no UI. | P1 |
| L21-07 | L21 S7 | 403 for unauthorised | `@PreAuthorize` on each method (`FdReportController`); nginx exposes `/api/reports/portfolio[/]` and `/api/reports/operational` (`nginx.conf` last block) | PASS | UNVERIFIED at runtime; no test for report endpoints in `fd-account-service/src/test`. | P2 |

### 1.14 L22 - Documentation and test plan

| ID | Manual ref | Requirement | Evidence | Status | Gap | Priority |
|---|---|---|---|---|---|---|
| L22-01 | L22 S2 | Solution architecture diagram | none (no image/mermaid/PlantUML; only `FRONTEND/public/*.png` map art). `.planning/codebase/ARCHITECTURE.md` is text and describes the legacy stack | MISSING | Graded documentation (5 marks). | P1 |
| L22-02 | L22 S2 | ER diagrams of all entities | none. Raw schema SQL: `01_moneypal_customer.sql`, `02_moneypal_product.sql`, `03_moneypal_fd.sql` | MISSING | SQL is a usable source for generating ERDs but no diagram. | P1 |
| L22-03 | L22 S2 | Deployment diagram (cloud infra) | none | MISSING | - | P1 |
| L22-04 | L22 S2/4 | Module-wise APIs via Swagger/OpenAPI | no `springdoc`/`swagger` dependency in `services/*/pom.xml`; OpenAPI YAMLs exist only in `BACKEND/src/main/resources/static/openapi/` (fd-service, fd-product-admin, login-service) | MISSING | Legacy specs describe routes absent from active stack (`/accounts/{id}/...`, `/auth/login/mfa/challenge`, `/api/admin/batch/run`, `/auth/otp/*`). Using them for the demo would mislead. | P1 |
| L22-05 | L22 S2/4 | Postman collections | `BACKEND/postman/fd-lifecycle.postman_collection.json` only | PARTIAL | Legacy routes; FD only. See L8-01. | P1 |
| L22-06 | L22 S2 | Test Plan (unit, integration, E2E, security, batch validation) | none (`.planning/phases/05-*/05-VALIDATION.md` is a GSD internal artefact for the legacy stack) | MISSING | - | P1 |
| L22-07 | L22 S2 | API versioning and backward-compat notes | none | MISSING | See T-15. | P2 |
| L22-08 | L22 S3-4 | JUnit/Mockito unit tests; listed test cases | 9 test classes, 43 `@Test` methods in `services/*/src/test`; Mockito not used; most are Testcontainers/MySQL integration tests (`docker-java.properties` present) | PARTIAL | Cases from L22 table: create FD (covered by `customerCanBookFund...`), unauthorised 403 (identity/product tests), maturity before date (no test), premature penalty (no test), batch validation (no test of double-run). All UNVERIFIED here. Coverage is thin for FD lifecycle, calculator, reports. | P1 |
| L22-09 | L22 S1 | Overall solution document | `README.MD` (ops guide), per-service READMEs, `services/KAFKA-DEMO.md`, `FD_MODULE_IMPLEMENTATION.md` (legacy FD notes) | PARTIAL | No solution document with API signatures + sample inputs/outputs (also L5/L6 requirement). | P1 |
| L22-10 | L22 S4 | MySQL Workbench schema visualisation | root `0x_moneypal_*.sql` + `scripts/configure-workbench-mysql.ps1` | PASS | Good fit; README documents Workbench mode. | - |
| L5-10 / L6-09 | L5, L6 | "Overall solution document for modules" (L5 two modules, L6 two modules) | none | MISSING | Duplicate of L22-09 as scheduled deliverables. | P1 |

### 1.15 L23/L24/L25 - Deployment, execution, presentation

| ID | Manual ref | Requirement | Evidence | Status | Gap | Priority |
|---|---|---|---|---|---|---|
| L23-01 | L23 S2 | Build Docker image per module | `services/*/Dockerfile` (multi-stage maven->jre17), `FRONTEND/Dockerfile`, gateway Dockerfile | PASS | Images not built here (UNVERIFIED). Services Dockerfile `COPY BACKEND/src/main/resources/db/service-migration ...` (e.g. `services/fd-account-service/Dockerfile:5`) and Maven `<resource>` from `../../BACKEND` (`fd pom.xml` build section): the "legacy" folder is a hard build dependency. | P2 |
| L23-02 | L23 S2 | Push to Azure Container Registry; deploy to App Service/AKS; env config; scaling rules | none | MISSING | - | P1 |
| L23-03 | L23 S3 | Azure SQL / managed DB, API Management, SendGrid/WhatsApp, Azure Monitor | none | MISSING | Manual conflicts with its own MySQL requirement. | P2 |
| L23-04 | L23 S4 | HTTPS access, verify REST APIs, DB connectivity, trigger batches via Azure CLI | none; nginx listens HTTP 8080 only (`services/api-gateway/nginx.conf:2`) | MISSING | - | P1 |
| L23-05 | L23 S1 | Presentation "login page with Report Module integrated" | no Angular report UI | MISSING | See L21-06. | P1 |
| L24-01 | L24 S2 | Final execution on cloud; create/close FD; run batch via Azure CLI; error handling | local only (compose + `scripts/verify-kafka-demo.ps1`) | MISSING | Cloud execution evidence absent. | P1 |
| L24-02 | L24 S3 | Troubleshooting matrix (env vars, cron, firewall, expired tokens/refresh) | `README.MD` Troubleshooting section (local Docker issues only) | PARTIAL | `POST /api/v1/auth/refresh` (`AuthController.java:155`) needs a still-valid access token; there is no refresh-token. Cron troubleshooting moot (no cron). | P3 |
| L25-01 | L25 S2 | Presentation (10 slides) | none in repo | OUT OF SCOPE | In-lab deliverable; `.planning` has none. | - |
| L25-02 | L25 S3 | Future enhancements | n/a (note: "microservice decomposition" already done) | OUT OF SCOPE | - | - |
| L7-02 / L14-09 / L21-08 / L24-03 | L7, L10, L14(pres.), L23, L24, L25 | Presentation and execution sessions | - | OUT OF SCOPE | Human/in-lab. Readiness depends on the functional gaps above. | - |

### 1.16 Evaluation plan roll-up (100 marks; 85 continuous + 15 viva)

| ID | Criterion (marks) | What the active stack provides | Status | Biggest gaps | Priority |
|---|---|---|---|---|---|
| E-01 | Login and UI integration (10) | Register/login/MFA/lockout, masked password input, JWT interceptor, route guards, landing, KYC UI (`FRONTEND/src/app/features/**`) | PASS | Frontend/backends UNVERIFIED at runtime; HTTPS missing; role from localStorage not JWT decode | P2 |
| E-02 | User auth, hashing, authorization (5) | Bcrypt, JWT HS512, method security, token-version revocation in Identity | PARTIAL | Revocation not enforced in Product/FD; 403-vs-401 unconfirmed; 24 h token, no refresh token | P1 |
| E-03 | Customer module: PII masking, setup (5) | KYC masked ID, staff customer admin, redaction | PARTIAL | Only government ID masked; staff API omits rather than masks email/phone; ID/DOB plaintext at rest | P1 |
| E-04 | Product and pricing (10) | Full CRUD, categories, currencies, rate bands, charges, activation, outbox to FD | PARTIAL | No `/api/product/search?productType&startDate&endDate`; no product-level term/rate "limit" fields as in manual; audit user not recorded; no Swagger/solution doc | P1 |
| E-05 | FD calculator simple and compound (10) | Working simple/compound endpoint, BigDecimal result rounding | PARTIAL | No combined category cap, single category, hard-coded boosts, not CUSTOMER-only, no UI, no Python/CSV | P0 |
| E-06 | FD module (35): creation | Self-service booking + funding with idempotency, KYC gate | PARTIAL | Not BANK_OFFICER-driven, no manual account-number pattern, no create-with-txn, no branch | P1 |
| E-07 | FD module: transactions | FD_FUNDING/FD_PENALTY/FD_CLOSURE txns + journals + accrual history | PARTIAL | Names differ (no DEPOSIT / MATURITY_PAYOUT); no statements | P2 |
| E-08 | FD module: batch | Accrual + maturity logic, idempotent per date, demo clock | FAIL | No scheduler, no logging, no per-account error isolation, no statements | P0 |
| E-09 | FD module: maturity | MATURED + customer-initiated close with payout and reconciliation | PARTIAL | Batch does not close/pay out; no staff manual-close; no notification | P1 |
| E-10 | FD module: alerts | none (password-reset mail only) | MISSING | Email/SMS/WhatsApp, `notifications_log` | P1 |
| E-11 | FD module: file extract | none | MISSING | - | P1 |
| E-12 | FD module: scaling / cloud deployment | compose only | MISSING | k8s, Azure, probes, HPA, HTTPS | P1 |
| E-13 | Reports (5) | JSON operational + portfolio reports with role checks | PARTIAL | No CSV/Python/UI; not per product | P1 |
| E-14 | Documentation and final presentation (5) | README(s), Kafka demo note, SQL schema files | PARTIAL | No diagrams, test plan, Swagger/Postman for active API, solution document | P1 |
| E-15 | Lab viva (15) | - | OUT OF SCOPE | - | - |

---

## 2. Legacy `BACKEND/` only features (not on the active compose path)

`BACKEND/` is a Spring Boot 3 modular monolith on PostgreSQL (per `BACKEND/README.md` "retains its legacy PostgreSQL schema"; `pom.xml` also lists MySQL driver and `MySqlServiceSchemaMigrationTest`). `docker-compose.yml` does not start it. Manual-relevant items that exist **only** there:

| Feature | Legacy location | Active status |
|---|---|---|
| `@Scheduled` daily accrual (00:00 IST) and maturity (01:00 IST) | `BACKEND/src/main/java/com/bank/fd/scheduler/FdBatchScheduler.java` | Not ported (active only has manual/clock triggers) |
| Swagger UI + OpenAPI specs (`springdoc-openapi-starter-webmvc-ui`) | `BACKEND/pom.xml:43-44`, `BACKEND/src/main/resources/static/openapi/*.yaml`, README references `/swagger-ui.html` | Not ported |
| Postman collection (18 requests, 4 folders) + consistency test | `BACKEND/postman/fd-lifecycle.postman_collection.json`, `BACKEND/src/test/java/com/bank/fd/api/PostmanCollectionConsistencyTest.java` | Not ported; routes differ |
| `AccountNumberGenerator` class (`FD-BR<branch>-<8 digit seq>`) | `BACKEND/src/main/java/com/bank/fd/service/AccountNumberGenerator.java` | Not ported (active uses random hex); neither matches the manual pattern |
| Calculator + customer-category boosts | `BACKEND/.../calculator/service/FdCalculatorService.java` (same +0.50 / +0.75 constants, no cap) | Ported 1:1 (inherits the same gaps) |
| Reports `portfolio` (was `UnsupportedOperationException`), `operational` | `BACKEND/.../reports/` | Active version is a real implementation |
| Wide FD OpenAPI surface (payouts, payout accounts, statements, notices, maturity instructions) | `fd-service-openapi.yaml` paths `/accounts/{id}/payouts`, `/statements`, `/notices`, ... ("37 mappings return 501 by design" per `FD_MODULE_IMPLEMENTATION.md:~294`) | Not implemented in either stack |
| Legacy login service contract (OTP, sessions, MFA challenge) | `login-service-openapi.yaml` | Active auth differs (no OTP/sessions endpoints) |

No Python script, CSV export, notification service, Kubernetes or Azure artefact exists in the legacy backend either (checked by file search). Hence these manual items are MISSING repo-wide, not just in the active path.

Repository hygiene (affects deliverable quality): `BACKEND/target/**` (compiled classes) is tracked in git; `BACKEND.zip` is tracked; both bloat the submission. `.env` is correctly git-ignored (`.gitignore`) and was not opened.

---

## 3. Manual-mandated vs good practice

**Manual-mandated items that are missing or contradicted** (a grader following the manual would look for these):
- Python-generated reports and CSV (S3, L6, L11, L21)
- Category add-ons with combined cap, applied to calculator and FD rate (L6, L11, L13)
- Only BANK_OFFICER creates FD; account number `[3][6][1]`; `/fd/account/create-with-txn` with DEPOSIT txn
- `@Scheduled` batches (daily interest/statements; maturity 4 AM), batch logging, per-account failure isolation
- `fd_statements` generation, `notifications_log`, email/WhatsApp/SMS alerts
- Staff manual-close, `MATURITY_PAYOUT`, auto-close at maturity
- `/report/fd-summary`, `/report/customer-portfolio`, CSV
- `/api/product/search` with productType/start/end dates
- Calculator restricted to CUSTOMER, categories from logged-in user
- Solution doc, architecture/ER/deployment diagrams, Swagger/Postman, test plan, versioning notes
- Docker + Kubernetes + Azure App Service/ACR, probes, elastic scaling, HTTPS

**Manual-mandated items where the repo deliberately deviates and is arguably better (document the deviation in the report):**
- DECIMAL instead of DOUBLE for money (manual schemas L12, L13, L14, L16)
- Normalised user/role tables instead of one `users` table
- `/api/v1` versioned paths instead of `/api/...`
- Spring Security 6 filter chain instead of `WebSecurityConfigurerAdapter`
- Premature closure as maker-checker (customer requests, staff approves) instead of direct withdrawal

**Good practice, not manual-mandated (credit, but do not count as compliance):**
- MFA/TOTP, lockout, password history, token-version revocation
- Idempotency keys on all FD writes; row locking; double-entry journals
- Transactional outbox + Kafka, per-service databases, API gateway
- KYC wizard with auto-verification; Flyway migrations; Testcontainers integration tests
- Ownership returns 404 (resource hiding)

---

## 4. Documentation artefacts: present vs missing

| Artefact (manual ref) | Present? | Where / note |
|---|---|---|
| Overall solution document, Product and Login modules + API signatures with samples (L5) | MISSING | READMEs list endpoints only |
| Overall solution document, FD Calculator and FD module + samples (L6) | MISSING | `FD_MODULE_IMPLEMENTATION.md` documents the legacy PostgreSQL `/fd/v1` implementation, not the active one |
| Solution architecture diagram (L22) | MISSING | text only |
| ER diagrams (L22) | MISSING | SQL schemas available: `01_moneypal_customer.sql`, `02_moneypal_product.sql`, `03_moneypal_fd.sql` |
| Deployment diagram (L22) | MISSING | - |
| Module-wise Swagger/OpenAPI (L22) | MISSING (active) / PRESENT-STALE (legacy) | `BACKEND/src/main/resources/static/openapi/*.yaml` |
| Postman collections (L8, L22) | PRESENT-STALE (legacy FD only) | `BACKEND/postman/` |
| Test plan (unit, integration, E2E, security, batch) (L22) | MISSING | - |
| API versioning and backward-compat notes (L22) | MISSING | - |
| Module interdependency notes (L8) | PARTIAL | `services/KAFKA-DEMO.md`, `README.MD` first sections |
| Lab record items: inputs/outputs, API evidence screenshots (general) | MISSING | not in repo (acceptable if kept in record book) |
| Final presentation (10 slides) (L25) | MISSING | not in repo (OUT OF SCOPE as in-lab) |
| Build/run instructions | PRESENT | `README.MD`, per-service READMEs (fd README is stale, see section 6) |
| Environment template | PRESENT | `.env.example` (no secrets) |
| E2E demo script | PRESENT (unrun) | `scripts/verify-kafka-demo.ps1`, `scripts/initialize-demo-env.ps1` |
| Dev container config | MISSING | - |
| CI pipeline | MISSING | no `.github/`, no pipeline file |

---

## 5. Status counts (matrix rows, section 1.1-1.15; section 1.16 roll-up counted separately)

158 rows in sections 1.1-1.15 (script count; PASS rows still carry UNVERIFIED runtime unless stated):

| Status | Count |
|---|---|
| PASS | 59 |
| PARTIAL | 44 |
| MISSING | 36 |
| FAIL | 13 |
| OUT OF SCOPE | 4 |
| UNVERIFIED | 2 |

Priority of non-passing rows: P0 = 5, P1 = 52 (includes PASS rows carrying '-' excluded), P2 = 29, P3 = 26. Section 1.16 adds 15 criterion-level roll-up rows (E-01..E-15).

---

## 6. Planning-doc claims not supported by the code

`.planning/` and the root context files describe the legacy monolith; they contradict the active stack.

| # | Claim (source) | Reality | Evidence |
|---|---|---|---|
| P-1 | "Persist with PostgreSQL and Flyway" / "Supabase-backed environment" (`AGENTS.md`, `CONTEXT.md`, `.planning/ROADMAP.md` Phase 1-6, `FD_MODULE_IMPLEMENTATION.md:90`) | Active stack is MySQL 8.4, one DB per service | `docker-compose.yml:48-80` |
| P-2 | "Current phase is Phase 1, Existing FD Baseline" (`AGENTS.md`, `CONTEXT.md`) | `.planning/STATE.md` says milestone complete (6/6 phases, 100%); the two statements conflict | `.planning/STATE.md:1-15` |
| P-3 | STATE.md: 21/21 plans, 100% complete; Phase 6 "Complete" | `.planning/ROADMAP.md` still lists Phases 2, 3, 4 unchecked; `.planning/REQUIREMENTS.md` leaves FUND-02, LIFE-02/03/04, CALC-01/02, HIST-01, API-01/02, AUTH-01/02, TEST-02/03 unchecked | `ROADMAP.md` phase list; `REQUIREMENTS.md` v1 list |
| P-4 | Verification files "status: human_needed" for phases 3, 4, 5 with live Supabase/Postman/PG IT runs outstanding | No live execution evidence exists; "128/134 tests, 0 failures" in `FD_MODULE_IMPLEMENTATION.md:~343` and `05-VERIFICATION.md` refer to the legacy suite | `04-VERIFICATION.md:8`, `05-VERIFICATION.md:8-12` |
| P-5 | FD REST surface `/fd/v1/accounts/**` (11 implemented, 37 return 501) (`FD_MODULE_IMPLEMENTATION.md:275-304`) | Active routes are `/api/v1/fixed-deposits/**`; no 501 placeholders; Postman and OpenAPI reference the old routes | `FDC/FixedDepositController.java:29`, `FDC/FdLifecycleController.java:30-111` |
| P-6 | Excluded on purpose: "payouts/statements/notices", "outbox/reconciliation" (`FD_MODULE_IMPLEMENTATION.md:~29`, `AGENTS.md`) | Outbox and Kafka are core to the active design; statements/notices still absent (conflicts with manual, see L17/L18) | `FDC/FdEventPublisher.java` |
| P-7 | "Reports portfolio still throws UnsupportedOperationException; accrued interest always 0" (`FD_MODULE_IMPLEMENTATION.md:306-322`) | Active portfolio works and sums accruals | `FDC/FdReportController.java:46-60` |
| P-8 | `README.MD` closing section: "`services/*/src/main/resources/db/service-migration/` - Flyway migrations owned by each active service" | No such directories; migrations live in `BACKEND/src/main/resources/db/service-migration/{customer,product,fd}` and are copied at build time | `services/fd-account-service/Dockerfile:5`, `pom.xml` resources |
| P-9 | `services/fd-account-service/README.md`: "applies the V1 baseline plus V2-V5 Flyway migrations" | Pom includes V1-V10 | `fd pom.xml` resource includes V1..V10 |
| P-10 | `.planning` phase 6: Angular `test` target missing, specs unrunnable (`FD_MODULE_IMPLEMENTATION.md` known gaps) | Frontend `*.spec.ts` files exist; whether `ng test` runs is UNVERIFIED | `FRONTEND/angular.json`, spec files |
| P-11 | Planning scope is "backend/API-first; Angular FD screens deferred" (`CONTEXT.md`, `AGENTS.md`) but Phase 6 delivered FD UI; and calculator/reports have no UI at all | Mismatch with manual's "login page with X integrated" presentations for calculator and reports | no calculator/report component in `FRONTEND/src/app` |

---

## 7. Findings (A1-xxx)

Severity: P0 blocks a graded criterion or demo; P1 clear marks loss; P2 moderate; P3 minor. Confidence: H = confirmed from code; M = code evidence but behaviour depends on config/runtime; L = inferred.

| ID | Sev | Finding | Evidence | Confidence |
|---|---|---|---|---|
| A1-001 | P0 | No scheduled batch in the active stack. Interest accrual and maturity run only when an ADMIN/OFFICER calls an endpoint or the demo clock is advanced; no `@Scheduled` cron, no run logging, no monthly job. Manual L14/L17/L18 and the "batch processing" part of the 35-mark FD module require cron-driven jobs. (Legacy has the scheduler.) | `FDC/FdLifecycleController.java:30-34`; `FDC/FdClockController.java:30-34`; only `@Scheduled` in `FDC/FdEventPublisher.java:23`; legacy `BACKEND/.../scheduler/FdBatchScheduler.java` | H |
| A1-002 | P0 | FD calculator lacks the manual's core rule: combined category add-on cap, multiple categories, product-defined max rate. Boosts hard-coded (+0.50, +0.75). Manual scenarios (EMPLOYEE+SENIOR capped to 8.5) cannot be produced. | `FDC/FdCalculatorController.java:19-20,25-30,58-62` | H |
| A1-003 | P0 | No Angular UI for the FD calculator or for reports, although the manual's presentations (L10/L14, L23) require "login page with Calculator/Report module integrated". Endpoints exist but are unused by the frontend. | `grep calculator|api/reports FRONTEND/src` -> no component/service; `nginx.conf` only exposes them | H |
| A1-004 | P1 | Alerts and notifications absent: no email/SMS/WhatsApp, no `notifications_log`, no mock SMTP. Maturity notification (L18) and Scenario 4 (L20) unmet. | no mail/SMS code in `services/`; no table in `MIG/fd/*.sql` | H |
| A1-005 | P1 | `fd_statements` is created but never written; no statement generation or download; customer "view statements" role action (L20) unsupported. | `MIG/fd/V1__initial_schema.sql:155-168`; no references in `services/**/*.java` | H |
| A1-006 | P1 | No file extract, no CSV report export, no Python report script. Manual lists all three. | repo-wide search: no `*.py`, no `text/csv`, no export endpoint | H |
| A1-007 | P1 | FD creation is customer self-service, not BANK_OFFICER; officer cannot book or manually close for a customer. Manual requires BANK_OFFICER (L12, L13, L16, L19, L20). | `FDC/FixedDepositController.java:40-63`; `FDC/FdLifecycleController.java:36-44` | H |
| A1-008 | P1 | Account number is `FD-` + 16 hex of a random UUID; not `[3 branch][6 seq][1 check]`; no branch concept in FD; no pluggable generator interface. | `FDC/FixedDepositController.java:57` | H |
| A1-009 | P1 | Maturity batch only moves FD to MATURED with an `FD_MATURITY` internal record; it neither pays out nor closes. Manual L18/L19 expect `MATURITY_PAYOUT`, CLOSED, and transfer to savings (can be done by customer `/close` afterwards). | `FDC/FdBatchService.java:51,59`; `FdLifecycleController.java:36-44` | H |
| A1-010 | P1 | Batch is a single `@Transactional` loop with no per-account try/catch and no logging; `accountsFailed` hard-coded to 0. Manual requires "log failure and continue". One poisoned account blocks all. | `FDC/FdBatchService.java:27-34,24` | H |
| A1-011 | P1 | Token revocation (logout, suspension, token version) enforced only in Identity; Product and FD JWT filters validate signature/expiry only, so revoked or suspended users keep API access up to JWT expiry (default 24 h). | `IDC/JwtAuthenticationFilter.java:33-34` vs `FDC/JwtFilter.java:22`, `PRD/JwtFilter.java` | H |
| A1-012 | P1 | Documentation deliverables for the active architecture are missing: architecture/ER/deployment diagrams, test plan, solution document, versioning notes. Swagger/OpenAPI and Postman exist only for the legacy monolith and document non-existent routes. | section 4 table; `BACKEND/postman/*`, `BACKEND/.../static/openapi/*` vs `services/api-gateway/nginx.conf` | H |
| A1-013 | P1 | Cloud/DevOps items missing: Kubernetes manifests, Azure App Service/ACR/SQL, HTTPS, elastic scaling, explicit liveness/readiness probes, CI. Java service containers lack HEALTHCHECK; outbox pollers are not safe for multiple replicas. | no such files; `docker-compose.yml`; `FDC/FdEventPublisher.java:23-31` | H |
| A1-014 | P1 | Product search per manual (`productType`, `startDate`, `endDate`) not implemented; product create/maintain payloads lack manual `termLimit`/`rateLimit` fields (replaced by rate bands). | `PRD/ProductCatalogController.java:23-29` | H |
| A1-015 | P1 | Customer PII masking limited to proof-of-ID; staff API omits email/phone/DOB; ID value, DOB stored in plaintext. Criterion "PII Masking" (5 marks) is thinly evidenced. | `IDC/KycService.java:85,167`; `MIG/customer/V4__kyc_profile.sql:12`; `IDC/CustomerAdminController.java:105-107` | H |
| A1-016 | P1 | Calculator not restricted to CUSTOMER and not personalised from the logged-in customer's category; no customer-category data exists in Identity. | `FDC/FdCalculatorController.java` (no `@PreAuthorize`); `FDC/SecurityConfiguration.java` | H |
| A1-017 | P1 | Premature closure deviates from L15: no `withdrawalDate`/`transferAccount`, two-step approval, interest computed as simple Actual/365 on principal (ignores capitalised balance), and no test for penalty arithmetic. | `FDC/FdLifecycleController.java:46-73,54-58`; tests dir | H |
| A1-018 | P1 | Reports do not match L21: operational report grouped by currency (not product), no interest-history or maturity-summary report, no CSV/PDF, no UI. Customer portfolio is fine. | `FDC/FdReportController.java:37-60` | H |
| A1-019 | P2 | Create-with-initial-DEPOSIT in a single DB transaction (L16) is split into booking (PENDING) and funding (`FD_FUNDING`); no `initialTransactionId` in the response. | `FDC/FixedDepositController.java:40-88,106` | H |
| A1-020 | P2 | Interest rate at booking ignores customer category adjustments; "final rate after category adjustments" (L13) unmet. | `FDC/FixedDepositController.java:51-53` | H |
| A1-021 | P2 | Product audit columns always hold literal `API`; acting username not recorded (L9 audit tracking). | `PRD/ProductCatalogController.java:43,53,63` | H |
| A1-022 | P2 | Unit/integration test coverage is thin and Docker-dependent: 43 tests in 9 classes; no tests for calculator, reports, batch re-run idempotency, maturity-before-date, premature penalty, staff role access on FD endpoints; Mockito absent. Nothing could be executed here. | `services/*/src/test/java/**` | M |
| A1-023 | P2 | 401 versus 403 behaviour for anonymous callers is unspecified (no `AuthenticationEntryPoint`); the manual expects 403 for unauthorised but Angular interceptor logs out only on 401. | `IDC/SecurityConfiguration.java:19-25`; `auth.interceptor.ts:17` | M |
| A1-024 | P2 | Build coupling: active services build Flyway migrations out of `BACKEND/` (Dockerfile COPY + Maven resource path). Deleting or archiving "legacy" BACKEND breaks the active build. README claims migrations live under each service. | `services/fd-account-service/Dockerfile:5`; `fd pom.xml` resources; `README.MD` "Useful files" | H |
| A1-025 | P2 | Planning/context docs describe PostgreSQL/Supabase, `/fd/v1`, and a "Phase 1" state; they will mislead a reviewer and contradict the running system (section 6). | `AGENTS.md`, `CONTEXT.md`, `.planning/**`, `FD_MODULE_IMPLEMENTATION.md` | H |
| A1-026 | P2 | Compose enables demo opening credit by default and exposes MySQL ports, Kafka and Kafka UI without auth on the host; acceptable for a lab demo, but the demo credit path (`/demo-opening`) is an unfunded money-creation endpoint if left enabled outside local use. | `docker-compose.yml` (`APP_DEMO_OPENING_BALANCE_ENABLED: "true"`, ports 3307-3309, 29092, 8084); `FDC/SavingsAccountController.java:52-64` | M |
| A1-027 | P2 | Calculator accepts arbitrary `compoundingFrequency` strings and silently treats unknown values as quarterly; compound power uses `double`. | `FDC/FdCalculatorController.java:42-47,54-56`; `FdCompounding.java:10-14` | H |
| A1-028 | P3 | Registration duplicate-username race not handled (500 instead of 409). | `IDC/AuthController.java:51-58` | M |
| A1-029 | P3 | Repository hygiene: compiled `BACKEND/target/**` and `BACKEND.zip` are tracked. | `git ls-files` shows 60 `target/` entries | H |
| A1-030 | P3 | `MFA_ENCRYPTION_KEY` defaults to `JWT_SECRET` (key reuse). | `docker-compose.yml`, `IDC/.../application.yml` | H |
| A1-031 | P3 | Role for UI guards read from a separately stored localStorage value rather than decoded from the JWT as the manual specifies; server enforcement unaffected. | `FRONTEND/src/app/core/services/auth.service.ts:80-93` | H |
| A1-032 | P3 | Manual itself contains inconsistencies a grader may probe: Senior +1% (L6) vs sample 7.0 effective (needs +0.5 with two categories), L11 table rows both end at 8.5 with a 2% cap, MySQL (S3) vs Azure SQL (L23), "Only BANK_OFFICER" (L12/L16) vs "BANK_OFFICER and ADMIN" (L13). The team should document which reading was implemented. | manual text lines 744, 868-877, 937, 996 | H |

---

## 8. Unverified-by-design list (could not be proven here)

- Compose build/start of any service or image; Flyway migrations applying to MySQL 8.4.
- Any JUnit or Testcontainers test result (JDK 17/Maven/Docker unavailable); the planning docs' "128 tests passing" refers to the legacy suite.
- Idempotent re-run of accrual/maturity; atomicity of funding and settlement under concurrency.
- Kafka event delivery and consumer snapshot application.
- Angular build and runtime behaviour; role-based menu visibility.
- 401/403 status for anonymous calls; nginx routing correctness.
- Numeric outputs quoted for the calculator (hand-computed from the formulas).

Recommended next verification (outside this read-only audit): run `docker compose up`, `scripts/verify-kafka-demo.ps1`, then add calls to `/api/calculator/estimate` and `/api/reports/*` with each role; run the three services' tests with Docker available.
