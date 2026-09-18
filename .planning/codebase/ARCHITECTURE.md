# Architecture

**Analysis Date:** 2026-09-18

## Pattern Overview

**Overall:** Two-application monorepo with a Spring Boot modular monolith backend and a standalone Angular feature-based frontend. The backend uses package-by-business-domain organization layered as REST controllers, services, Spring Data repositories, and JPA entities. The frontend uses standalone Angular components, lazy route-level loading, feature folders, and a small `core`/`shared` split.

**Key Characteristics:**
- `BACKEND/src/main/java/com/bank/MoneyPalApplication.java` is the single Spring Boot process entry point; component scanning covers the `com.bank` package tree.
- Backend domains are separated by packages such as `security`, `customer`, `fd`, `product`, `calculator`, `reports`, and `common`, but they share one application context and one PostgreSQL/Supabase datasource configured in `BACKEND/src/main/resources/application.yml`.
- `FRONTEND/src/main.ts` bootstraps a standalone Angular root component; `FRONTEND/src/app/app.routes.ts` lazy-loads feature components rather than using NgModules.
- The backend exposes both implemented `/api/*` endpoints and contract-shaped `/auth/*` and `/fd/v1/*` surfaces; `BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java` is an HTTP contract skeleton whose operations return `501 Not Implemented`.
- Persistence is schema-first: Flyway migrations in `BACKEND/src/main/resources/db/migration/` create and evolve tables, while JPA is configured with `ddl-auto: validate`.

## Layers

**Frontend Composition and Routing:**
- Purpose: Select a page component for each URL and provide Angular platform services.
- Location: `FRONTEND/src/main.ts`, `FRONTEND/src/app/app.ts`, `FRONTEND/src/app/app.config.ts`, `FRONTEND/src/app/app.routes.ts`
- Contains: Browser bootstrap, `RouterOutlet`, router configuration, `HttpClient`, and animation providers.
- Depends on: Angular platform, router, and feature components.
- Used by: The browser entry point and all routed pages.

**Frontend Features:**
- Purpose: Own page-level user journeys and their local UI state.
- Location: `FRONTEND/src/app/features/`
- Contains: `landing`, `auth/login`, `auth/signup`, and `dashboard` components with paired HTML/SCSS files where applicable.
- Depends on: `FRONTEND/src/app/core/services/auth.service.ts`, `FRONTEND/src/app/core/models/auth.models.ts`, and reusable components under `FRONTEND/src/app/shared/components/`.
- Used by: Lazy routes declared in `FRONTEND/src/app/app.routes.ts`.

**Frontend Core Services and Models:**
- Purpose: Centralize cross-feature API access and request/response types.
- Location: `FRONTEND/src/app/core/`
- Contains: `AuthService` and authentication/request-context interfaces.
- Depends on: Angular `HttpClient` and RxJS `Observable`.
- Used by: `FRONTEND/src/app/features/auth/login/login.ts` and `FRONTEND/src/app/features/auth/signup/signup.ts`.

**Frontend Shared Presentation:**
- Purpose: Provide reusable visual and form controls without owning a business workflow.
- Location: `FRONTEND/src/app/shared/components/`, `FRONTEND/src/app/shared/styles/`
- Contains: `GlassShell`, `GlassSelect`, landing visual subcomponents, and Sass tokens/glass styles.
- Depends on: Angular component lifecycle, forms APIs, browser events, and Three.js for `FRONTEND/src/app/features/landing/components/globe-hero/globe-hero.ts`.
- Used by: Authentication and landing feature components.

**Backend HTTP/API Layer:**
- Purpose: Translate HTTP requests into domain service calls, apply endpoint authorization, and serialize DTOs/entities.
- Location: `BACKEND/src/main/java/com/bank/**/controller/`
- Contains: Authentication (`AuthController`, `LoginController`), customer, product, calculator, reports, FD legacy routes, batch triggers, and the FD v1 contract controller.
- Depends on: Domain services, repositories, Spring Security context, validation, and request DTOs.
- Used by: The Angular client and external/internal API consumers.

**Backend Application/Domain Services:**
- Purpose: Coordinate use cases and business calculations between controllers and persistence or external systems.
- Location: `BACKEND/src/main/java/com/bank/**/service/`
- Contains: `CustomerService`, `FdProductService`, `FdCalculatorService`, `FdAccountService`, `ReportService`, session/OTP/login-attempt services, and the `UserServiceClient` adapter boundary.
- Depends on: Repositories, JPA entities, security helpers, and external user-service HTTP calls where applicable.
- Used by: Controllers, the scheduled FD batch component, and security configuration.

**Backend Persistence:**
- Purpose: Store and query relational domain data through Spring Data JPA.
- Location: `BACKEND/src/main/java/com/bank/**/repository/`, `BACKEND/src/main/java/com/bank/**/entity/`
- Contains: Repository interfaces for users, customer profiles, roles, products, FD accounts, and FD transactions; JPA mappings for customer, security, product, and FD tables.
- Depends on: PostgreSQL-compatible schema objects created by `BACKEND/src/main/resources/db/migration/`.
- Used by: Services and, in a few flows, controllers such as `FdAccountController` and `AuthController`.

**Backend Infrastructure and Cross-Cutting Concerns:**
- Purpose: Configure security, external clients, error translation, scheduling, and application-wide runtime behavior.
- Location: `BACKEND/src/main/java/com/bank/security/`, `BACKEND/src/main/java/com/bank/common/exception/`, `BACKEND/src/main/java/com/bank/fd/scheduler/`
- Contains: JWT filter/provider, stateless Spring Security filter chain, user-service `RestClient`, retry/circuit-breaker-like state, global exception advice, and scheduled FD jobs.
- Depends on: `BACKEND/src/main/resources/application.yml` properties and Spring Boot infrastructure.
- Used by: Every protected HTTP request, authentication flows, and daily batch operations.

## Data Flow

**Angular authentication flow:**

1. `FRONTEND/src/app/app.routes.ts` lazy-loads `Login` or `Signup` for `/login` or `/signup`.
2. Reactive forms in `FRONTEND/src/app/features/auth/login/login.ts` and `FRONTEND/src/app/features/auth/signup/signup.ts` validate user input and build feature-local state.
3. `FRONTEND/src/app/core/services/auth.service.ts` adds a `RequestContext` and sends requests through Angular `HttpClient` to `http://localhost:8080/api/auth`.
4. `BACKEND/src/main/java/com/bank/security/controller/AuthController.java` handles `/api/auth/register` and `/api/auth/login` using local JPA user/customer records, `BCryptPasswordEncoder`, `AuthenticationManager`, and `JwtTokenProvider`.
5. The login response is consumed by the feature component; a successful response navigates to `/dashboard`, which resolves to `FRONTEND/src/app/features/dashboard/dashboard-stub.ts`.

**Delegated login/session flow:**

1. Requests to `/auth/*` enter `BACKEND/src/main/java/com/bank/security/controller/LoginController.java`.
2. Credential, account-status, role, contact, and password operations cross the `UserServiceClient` interface in `BACKEND/src/main/java/com/bank/security/userservice/UserServiceClient.java`.
3. `BACKEND/src/main/java/com/bank/security/userservice/UserServiceClientAdapter.java` uses the configured `RestClient`, bounded retries, and an in-memory failure circuit.
4. Successful login issues an in-memory `SessionService.Session` and a JWT containing the session id, user id, username, and roles; MFA uses `OtpService` before session issuance.
5. `JwtAuthenticationFilter` validates bearer tokens before protected controller methods run, while `SessionService` checks that the referenced in-memory session is present and not revoked for session endpoints.

**Implemented FD/product/report flow:**

1. A request reaches a domain controller such as `BACKEND/src/main/java/com/bank/product/controller/FdProductController.java`, `BACKEND/src/main/java/com/bank/fd/controller/FdAccountController.java`, or `BACKEND/src/main/java/com/bank/reports/controller/ReportController.java`.
2. `@PreAuthorize` and ownership checks use the Spring Security context populated by `BACKEND/src/main/java/com/bank/security/jwt/JwtAuthenticationFilter.java`.
3. The controller calls a domain service such as `FdProductService`, `FdAccountService`, or `ReportService`.
4. Services query or save JPA entities through Spring Data repositories such as `BACKEND/src/main/java/com/bank/fd/repository/FdAccountRepository.java`.
5. Hibernate validates mappings against the PostgreSQL schema created by `BACKEND/src/main/resources/db/migration/V1__bankingtech_public_schema.sql` and follow-on migrations.

**FD v1 contract flow:**

1. The API contract is documented in `BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml` and mounted under `/fd/v1` by `BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java`.
2. The controller exposes account lifecycle, holders, transactions, accrual, payout, closure, notices, statements, event, and reference-data synchronization routes.
3. Most operations return an `ApiErrorResponse` with HTTP `501`; the internal sync methods return `204` without persistence work.
4. The normalized FD schema already contains tables for those concepts in `BACKEND/src/main/resources/db/migration/V1__bankingtech_public_schema.sql`, but the contract controller is not connected to corresponding application services.

**State Management:**
- `FRONTEND/src/app/features/auth/login/login.ts` and `FRONTEND/src/app/features/auth/signup/signup.ts` keep form, loading, error, and navigation state in component instances; no frontend store or signal-based global state service is present.
- `BACKEND/src/main/java/com/bank/security/service/SessionService.java` and `BACKEND/src/main/java/com/bank/security/service/OtpService.java` keep sessions, refresh-token mappings, and OTP challenges in process-local `ConcurrentHashMap` instances.
- Durable business state is held in the relational database through JPA entities and Flyway migrations, with request authorization represented by JWT claims and role checks.

## Key Abstractions

**Business-domain package modules:**
- Purpose: Keep endpoint, service, persistence, DTO, and entity code grouped by capability rather than by one global technical folder.
- Examples: `BACKEND/src/main/java/com/bank/customer/`, `BACKEND/src/main/java/com/bank/fd/`, `BACKEND/src/main/java/com/bank/product/`, `BACKEND/src/main/java/com/bank/calculator/`, and `BACKEND/src/main/java/com/bank/reports/`.
- Pattern: Prefer a controller → service → repository/entity path for implemented use cases; shared failures live under `BACKEND/src/main/java/com/bank/common/exception/`.

**JPA entity projections:**
- Purpose: Map the normalized public schema to Java objects while retaining compatibility accessors for older endpoint code.
- Examples: `BACKEND/src/main/java/com/bank/fd/entity/FdAccount.java`, `BACKEND/src/main/java/com/bank/customer/entity/User.java`, `BACKEND/src/main/java/com/bank/customer/entity/CustomerProfile.java`, and `BACKEND/src/main/java/com/bank/product/entity/FdProduct.java`.
- Pattern: Use explicit `@Table`/`@Column` names; use `@Transient` compatibility fields when the normalized table stores the data in related tables or upstream services.

**Repository interfaces:**
- Purpose: Encapsulate database access behind Spring Data query methods.
- Examples: `BACKEND/src/main/java/com/bank/customer/repository/UserRepository.java`, `BACKEND/src/main/java/com/bank/fd/repository/FdAccountRepository.java`, and `BACKEND/src/main/java/com/bank/product/repository/FdProductRepository.java`.
- Pattern: Extend `JpaRepository`, use derived finder names for simple queries, and use `@Query` only for specialized lookups such as account-number prefix generation.

**External user-service port and adapter:**
- Purpose: Keep the delegated login module independent of the remote user-service transport details.
- Examples: `BACKEND/src/main/java/com/bank/security/userservice/UserServiceClient.java` and `BACKEND/src/main/java/com/bank/security/userservice/UserServiceClientAdapter.java`.
- Pattern: Controllers and services depend on the interface; the adapter owns `RestClient`, service-token headers, timeout values, retry count, and failure state.

**OpenAPI contract controllers:**
- Purpose: Keep externally agreed route shapes and operation ids visible while implementation is built behind them.
- Examples: `BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java`, `BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml`, and `BACKEND/src/main/resources/static/openapi/login-service-openapi.yaml`.
- Pattern: Match controller method names to OpenAPI `operationId` values and return the shared contract error DTO in `BACKEND/src/main/java/com/bank/fd/api/dto/ApiErrorResponse.java`.

**Standalone Angular components:**
- Purpose: Make each routed page and reusable control self-contained through its `imports` metadata.
- Examples: `FRONTEND/src/app/features/auth/login/login.ts`, `FRONTEND/src/app/features/auth/signup/signup.ts`, and `FRONTEND/src/app/shared/components/glass-select/glass-select.ts`.
- Pattern: Use `standalone: true`, declare direct imports in the component, and place external template/styles beside the TypeScript file.

## Entry Points

**Spring Boot application:**
- Location: `BACKEND/src/main/java/com/bank/MoneyPalApplication.java`
- Triggers: JVM process startup through Maven/Spring Boot.
- Responsibilities: Start the Spring application context and enable scheduled tasks with `@EnableScheduling`.

**Angular browser application:**
- Location: `FRONTEND/src/main.ts`
- Triggers: Angular CLI build/serve entry configured by `FRONTEND/angular.json`.
- Responsibilities: Load `zone.js`, bootstrap `App`, and install `appConfig` providers.

**Root Angular component:**
- Location: `FRONTEND/src/app/app.ts`
- Triggers: `bootstrapApplication` in `FRONTEND/src/main.ts`.
- Responsibilities: Render the `RouterOutlet` defined by `FRONTEND/src/app/app.html`.

**HTTP route groups:**
- Location: `BACKEND/src/main/java/com/bank/**/controller/`
- Triggers: HTTP requests to `/api/auth`, `/auth`, `/api/customer`, `/api/product`, `/api/calculator`, `/api/fd`, `/api/reports`, `/api/admin/batch`, or `/fd/v1`.
- Responsibilities: Validate input, enforce route/method authorization, delegate to services, and produce response bodies.

**Scheduled FD processing:**
- Location: `BACKEND/src/main/java/com/bank/fd/scheduler/FdBatchScheduler.java`
- Triggers: Cron schedules at midnight and 01:00 configured by `@Scheduled`.
- Responsibilities: Invoke interest accrual and maturity processing on `FdAccountService` and log failures without terminating the application.

## Error Handling

**Strategy:** Centralized REST exception translation for implemented backend endpoints, explicit authentication/authorization failures, and contract-level `501` responses for unimplemented FD v1 operations.

**Patterns:**
- `BACKEND/src/main/java/com/bank/common/exception/GlobalExceptionHandler.java` maps domain exceptions to structured timestamp/status/error/message responses.
- Validation failures from `@Valid` are translated into a `validationErrors` field by `GlobalExceptionHandler`.
- `SecurityConfig` in `BACKEND/src/main/java/com/bank/security/config/SecurityConfig.java` routes unauthorized requests through stateless Spring Security and method security; domain controllers add `@PreAuthorize` for role checks.
- `BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java` uses `ApiErrorResponse` and `HttpStatus.NOT_IMPLEMENTED` for contract placeholders.
- `FRONTEND/src/app/features/auth/login/login.ts` and `FRONTEND/src/app/features/auth/signup/signup.ts` convert failed HTTP requests into local user-facing error messages and reset their loading flags.

## Cross-Cutting Concerns

**Logging:** Backend security and scheduled processing use SLF4J through `JwtAuthenticationFilter`, `OtpService`, and `FdBatchScheduler`; frontend bootstrap errors use `console.error` in `FRONTEND/src/main.ts`.

**Validation:** Backend request DTOs and controller parameters use Jakarta validation annotations, for example `BACKEND/src/main/java/com/bank/security/dto/RegisterRequest.java` and `BACKEND/src/main/java/com/bank/calculator/dto/EstimationRequest.java`; Angular forms use reactive validators in `FRONTEND/src/app/features/auth/login/login.ts` and `FRONTEND/src/app/features/auth/signup/signup.ts`.

**Authentication:** `BACKEND/src/main/java/com/bank/security/config/SecurityConfig.java` configures stateless bearer authentication, while `BACKEND/src/main/java/com/bank/security/jwt/JwtAuthenticationFilter.java` populates the security context. The application contains both local JPA authentication in `AuthController` and delegated session authentication in `LoginController`.

**Authorization:** Route patterns are configured in `SecurityConfig`, role-level method guards use `@PreAuthorize` in controllers such as `FdAccountController` and `ReportController`, and FD account ownership is checked in `FdAccountController.validateOwnershipOrStaff`.

**Database evolution:** `BACKEND/src/main/resources/application.yml` enables Flyway and schema validation; versioned SQL migrations in `BACKEND/src/main/resources/db/migration/` own table creation and compatibility changes.

**API contracts:** OpenAPI documents under `BACKEND/src/main/resources/static/openapi/` describe login and FD service boundaries, while DTOs and controller mappings provide the Java implementation surface.

---

*Architecture analysis: 2026-09-18*
