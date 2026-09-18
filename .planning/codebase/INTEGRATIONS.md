# External Integrations

**Analysis Date:** 2026-09-18

## APIs & External Services

**User identity and account service:**
- User Service - the login module delegates credential validation, account status, login-attempt reporting, role lookup, contact lookup, unlock, password change, and password reset over internal HTTP calls from `BACKEND/src/main/java/com/bank/security/userservice/UserServiceClientAdapter.java`.
  - SDK/Client: Spring `RestClient`, configured in `BACKEND/src/main/java/com/bank/security/userservice/UserServiceClientConfig.java`.
  - Auth: bearer service token from `USER_SERVICE_SERVICE_TOKEN` via `BACKEND/src/main/resources/application.yml`.
  - Base URL: `USER_SERVICE_URL`, defaulting to a local HTTPS placeholder in `BACKEND/src/main/resources/application.yml`.
  - Resilience: configurable timeout, retry count, consecutive-failure threshold, and circuit-open duration in `BACKEND/src/main/java/com/bank/security/userservice/UserServiceClientAdapter.java`.
  - Paths: `/internal/users/validate-credentials`, `/internal/users/{id}/account-status`, `/internal/users/{id}/login-attempts`, `/internal/users/{id}/roles`, `/internal/users/{id}/contacts`, `/internal/users/{id}/unlock`, `/internal/users/{id}/password/change`, `/internal/password/forgot`, and `/internal/password/reset`.

**Frontend-to-backend REST API:**
- MoneyPal backend - the Angular auth service sends JSON login and registration requests to the local backend from `FRONTEND/src/app/core/services/auth.service.ts`.
  - SDK/Client: Angular `HttpClient`, provided in `FRONTEND/src/app/app.config.ts`.
  - Auth: backend-issued responses are modeled by `FRONTEND/src/app/core/models/auth.models.ts`; no frontend token interceptor is detected.
  - Endpoints used: `/api/auth/login` and `/api/auth/register`.
  - Backend controllers also expose `/auth/**` session/MFA/password/OTP endpoints from `BACKEND/src/main/java/com/bank/security/controller/LoginController.java` and `/api/auth/**` legacy login/register endpoints from `BACKEND/src/main/java/com/bank/security/controller/AuthController.java`.

**API contracts and documentation:**
- OpenAPI 3 contracts - static Login and Fixed Deposit API specifications are served by Springdoc/Spring MVC from `BACKEND/src/main/resources/static/openapi/login-service-openapi.yaml` and `BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml`.
  - Runtime documentation: `/swagger-ui.html`, `/api-docs`, and `/openapi/**` are configured in `BACKEND/src/main/resources/application.yml`.
  - The example production and sandbox server URLs in the YAML files are contract metadata; no client code connects to them.

## Data Storage

**Databases:**
- PostgreSQL via Supabase - primary persistent store for users, credentials, customers, roles, fixed-deposit products, accounts, transactions, notices, statements, and related reference data.
  - Connection: `SUPABASE_DB_URL`, `SUPABASE_DB_USERNAME`, and `SUPABASE_DB_PASSWORD` in `BACKEND/src/main/resources/application.yml`.
  - Client: PostgreSQL JDBC driver, Spring Data JPA, Hibernate, and Hikari-backed Spring Boot datasource declared in `BACKEND/pom.xml`.
  - Schema: Flyway applies `BACKEND/src/main/resources/db/migration/V1__bankingtech_public_schema.sql` through `V5__align_customer_and_role_id_types.sql`; Hibernate is configured to validate rather than generate schema.
  - PostgreSQL extension: the baseline migration enables `pgcrypto` for UUID defaults in `BACKEND/src/main/resources/db/migration/V1__bankingtech_public_schema.sql`.
- H2 - test-only in-memory database configured by `BACKEND/src/test/resources/application-test.yml`; Flyway is disabled for the test profile.

**File Storage:**
- Local/static assets only - frontend images are packaged from `FRONTEND/public/` and loaded by `FRONTEND/src/app/features/landing/components/globe-hero/globe-hero.ts`.
- No object-storage SDK, document-upload backend, or external file-storage integration is detected. The database includes document-reference fields, but no storage client is present in application source.

**Caching:**
- None detected. Login sessions, refresh-token mappings, and OTP challenges are held in process memory by `BACKEND/src/main/java/com/bank/security/service/SessionService.java` and `BACKEND/src/main/java/com/bank/security/service/OtpService.java`.

## Authentication & Identity

**Auth Provider:**
- Custom application authentication backed by the User Service and PostgreSQL-backed customer entities.
  - Password hashing: BCrypt through `BACKEND/src/main/java/com/bank/security/config/SecurityConfig.java`.
  - Access tokens: HS512 JWTs created and validated by `BACKEND/src/main/java/com/bank/security/jwt/JwtTokenProvider.java`.
  - Sessions: stateless Spring Security request filtering plus in-memory session and refresh-token rotation in `BACKEND/src/main/java/com/bank/security/service/SessionService.java`.
  - MFA/OTP: six-digit codes with five-minute expiry held in memory by `BACKEND/src/main/java/com/bank/security/service/OtpService.java`; no email or SMS delivery provider is wired, and the service currently logs the destination metadata.
  - CORS: local Angular origins `http://localhost:4200` and `http://127.0.0.1:4200` are allowed in `BACKEND/src/main/java/com/bank/security/config/SecurityConfig.java`.
- No Supabase Auth SDK, OAuth provider, Firebase Auth, Stripe Identity, or other managed identity client is detected in `BACKEND/src/main` or `FRONTEND/src`.

## Monitoring & Observability

**Error Tracking:**
- None detected. No Sentry, Application Insights, or equivalent client is declared in `BACKEND/pom.xml` or `FRONTEND/package.json`.

**Logs:**
- SLF4J/Logback through Spring Boot; scheduled fixed-deposit jobs log start, completion, and failures in `BACKEND/src/main/java/com/bank/fd/scheduler/FdBatchScheduler.java`.
- Actuator exposes health, info, and Prometheus endpoints according to `BACKEND/src/main/resources/application.yml`.
- Authentication and JWT failures are logged by `BACKEND/src/main/java/com/bank/security/jwt/JwtTokenProvider.java`; OTP creation is logged by `BACKEND/src/main/java/com/bank/security/service/OtpService.java`.

## CI/CD & Deployment

**Hosting:**
- Not detected. `BACKEND/README.MD` documents a local Spring Boot process and `FRONTEND/README.md` documents the Angular dev server; no hosting provider configuration is present.

**CI Pipeline:**
- Not detected. No CI workflow, container definition, infrastructure-as-code, or deployment manifest is present in the repository.

## Environment Configuration

**Required env vars:**
- `SUPABASE_DB_URL` and `SUPABASE_DB_PASSWORD` are required by `BACKEND/src/main/resources/application.yml`; `SUPABASE_DB_USERNAME` defaults to `postgres`.
- `USER_SERVICE_SERVICE_TOKEN` is required for secured User Service calls; `USER_SERVICE_URL` and resilience variables are configurable in `BACKEND/src/main/resources/application.yml`.
- JWT secret material is configured under `app.jwt` and is intentionally omitted from this document.
- A `.env` file exists at `FRONTEND/app/.env`; its contents were not read or reproduced. The active frontend source currently uses the hardcoded backend URL in `FRONTEND/src/app/core/services/auth.service.ts` rather than a detected runtime environment adapter.

**Secrets location:**
- Runtime secrets are expected through Spring property placeholders/environment variables in `BACKEND/src/main/resources/application.yml` and local environment setup described in `BACKEND/README.MD`.
- The repository also contains configured JWT secret material in application configuration; the value is intentionally not reproduced here.

## Webhooks & Callbacks

**Incoming:**
- None detected. The REST routes in `BACKEND/src/main/java/com/bank/` are application APIs, not provider webhook handlers, and no webhook signature verification is present.

**Outgoing:**
- User Service internal REST calls are the only detected outbound application integration, implemented by `BACKEND/src/main/java/com/bank/security/userservice/UserServiceClientAdapter.java`.
- No payment gateway, notification provider, event broker, webhook callback, or external banking-network connector is detected.

---

*Integration audit: 2026-09-18*
