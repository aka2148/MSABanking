# Codebase Structure

**Analysis Date:** 2026-09-18

## Directory Layout

```
BankingTechnology/
├── BACKEND/
│   ├── pom.xml                         # Spring Boot/Maven project definition
│   ├── src/main/java/com/bank/         # Backend application and domain packages
│   │   ├── MoneyPalApplication.java    # Spring Boot entry point
│   │   ├── common/exception/           # Shared exception types and REST advice
│   │   ├── security/                   # Auth controllers, JWT, sessions, OTP, user-service port
│   │   ├── customer/                   # Customer controllers, services, DTOs, repositories, entities
│   │   ├── fd/                         # Fixed-deposit API, account domain, repositories, scheduler
│   │   ├── product/                    # FD product controller/service/entity/repository
│   │   ├── calculator/                 # FD estimation controller/service/DTOs
│   │   └── reports/                    # Portfolio/operational report controller/service/DTOs
│   ├── src/main/resources/
│   │   ├── application.yml             # Runtime, datasource, Flyway, security, actuator settings
│   │   ├── db/migration/                # Flyway schema migrations V1 through V5
│   │   └── static/openapi/              # Login and FD service OpenAPI contracts
│   └── src/test/                        # Spring Boot and service tests
│       ├── java/com/bank/               # Java test packages
│       └── resources/application-test.yml
├── FRONTEND/
│   ├── package.json                     # Angular scripts and dependencies
│   ├── angular.json                     # Angular CLI build/serve configuration
│   ├── tsconfig*.json                   # TypeScript and Angular compiler settings
│   ├── src/
│   │   ├── index.html                   # Browser document shell
│   │   ├── main.ts                      # Angular bootstrap entry point
│   │   ├── styles.scss                  # Global Sass entry point
│   │   └── app/
│   │       ├── app.ts                   # Standalone root component
│   │       ├── app.html                 # Root router outlet
│   │       ├── app.config.ts            # Router, HTTP, and animation providers
│   │       ├── app.routes.ts            # Lazy route definitions
│   │       ├── core/                    # Cross-feature services and models
│   │       ├── features/                # Route-oriented business/UI features
│   │       └── shared/                  # Reusable controls and style partials
│   └── public/                          # Static images and favicon assets
├── README.MD                           # Root project summary
└── CONTEXT.md                          # Project context and workflow notes
```

## Directory Purposes

**`BACKEND/src/main/java/com/bank/`:**
- Purpose: Root package for the Spring Boot application launched by `BACKEND/src/main/java/com/bank/MoneyPalApplication.java`.
- Contains: Business-domain packages and the cross-cutting `common` and `security` packages.
- Key files: `BACKEND/src/main/java/com/bank/MoneyPalApplication.java` and `BACKEND/src/main/java/com/bank/common/exception/GlobalExceptionHandler.java`.

**`BACKEND/src/main/java/com/bank/security/`:**
- Purpose: Authentication, authorization integration, session issuance, and security configuration.
- Contains: `controller/`, `config/`, `dto/`, `jwt/`, `service/`, and `userservice/` subpackages.
- Key files: `BACKEND/src/main/java/com/bank/security/config/SecurityConfig.java`, `BACKEND/src/main/java/com/bank/security/controller/AuthController.java`, `BACKEND/src/main/java/com/bank/security/controller/LoginController.java`, and `BACKEND/src/main/java/com/bank/security/userservice/UserServiceClientAdapter.java`.

**`BACKEND/src/main/java/com/bank/customer/`:**
- Purpose: Represent users, customer profiles, contact/detail children, and role assignments.
- Contains: `CustomerController`, `CustomerService`, profile DTOs, Spring Data repositories, and JPA entities mapped to `public.users`, `public.customers`, and related tables.
- Key files: `BACKEND/src/main/java/com/bank/customer/entity/User.java`, `BACKEND/src/main/java/com/bank/customer/entity/CustomerProfile.java`, and `BACKEND/src/main/java/com/bank/customer/service/CustomerService.java`.

**`BACKEND/src/main/java/com/bank/fd/`:**
- Purpose: Fixed-deposit account and transaction concerns, including the normalized FD API contract and scheduled lifecycle hooks.
- Contains: `api/`, `controller/`, `dto/`, `entity/`, `repository/`, `scheduler/`, and `service/`.
- Key files: `BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java`, `BACKEND/src/main/java/com/bank/fd/entity/FdAccount.java`, `BACKEND/src/main/java/com/bank/fd/repository/FdAccountRepository.java`, and `BACKEND/src/main/java/com/bank/fd/scheduler/FdBatchScheduler.java`.

**`BACKEND/src/main/java/com/bank/product/`, `calculator/`, and `reports/`:**
- Purpose: Isolate product administration, interest estimation, and reporting use cases.
- Contains: Each domain follows the available controller/service/repository/entity or DTO pattern.
- Key files: `BACKEND/src/main/java/com/bank/product/controller/FdProductController.java`, `BACKEND/src/main/java/com/bank/calculator/service/FdCalculatorService.java`, and `BACKEND/src/main/java/com/bank/reports/service/ReportService.java`.

**`BACKEND/src/main/resources/db/migration/`:**
- Purpose: Version and apply database schema changes through Flyway.
- Contains: `V1__bankingtech_public_schema.sql` for the normalized schema and `V2__align_entity_id_types.sql` through `V5__align_customer_and_role_id_types.sql` for follow-on alignment.
- Key files: `BACKEND/src/main/resources/db/migration/V1__bankingtech_public_schema.sql` and `BACKEND/src/main/resources/db/migration/V3__create_refresh_tokens.sql`.

**`BACKEND/src/main/resources/static/openapi/`:**
- Purpose: Store API contracts served as static resources and used by Swagger UI configuration.
- Contains: `login-service-openapi.yaml` and `fd-service-openapi.yaml`.
- Key files: `BACKEND/src/main/resources/static/openapi/login-service-openapi.yaml` and `BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml`.

**`FRONTEND/src/app/core/`:**
- Purpose: Hold cross-feature application services and shared TypeScript models.
- Contains: `FRONTEND/src/app/core/services/auth.service.ts` and `FRONTEND/src/app/core/models/auth.models.ts`.
- Key files: `FRONTEND/src/app/core/services/auth.service.ts` for `/api/auth` calls and `FRONTEND/src/app/core/models/auth.models.ts` for auth payloads and request context.

**`FRONTEND/src/app/features/`:**
- Purpose: Organize route-level screens and feature-specific child components.
- Contains: `landing/`, `auth/login/`, `auth/signup/`, and `dashboard/`.
- Key files: `FRONTEND/src/app/features/landing/landing.ts`, `FRONTEND/src/app/features/auth/login/login.ts`, `FRONTEND/src/app/features/auth/signup/signup.ts`, and `FRONTEND/src/app/features/dashboard/dashboard-stub.ts`.

**`FRONTEND/src/app/features/landing/components/`:**
- Purpose: Keep landing-page visuals below their owning feature rather than promoting them to global shared components.
- Contains: `globe-hero/` and `team-chart/`, each with TypeScript, template, and SCSS files.
- Key files: `FRONTEND/src/app/features/landing/components/globe-hero/globe-hero.ts` and `FRONTEND/src/app/features/landing/components/team-chart/team-chart.ts`.

**`FRONTEND/src/app/shared/`:**
- Purpose: Provide reusable UI components and Sass resources used by more than one feature.
- Contains: `components/glass-shell/`, `components/glass-select/`, and `styles/_tokens.scss`/`styles/_glass.scss`.
- Key files: `FRONTEND/src/app/shared/components/glass-shell/glass-shell.ts` and `FRONTEND/src/app/shared/components/glass-select/glass-select.ts`.

**`FRONTEND/public/`:**
- Purpose: Supply static assets copied by the Angular build.
- Contains: `world-map.png`, `world-map2.png`, and favicon files.
- Key files: `FRONTEND/public/world-map.png`, which is referenced by `GlobeHero` and `GlassShell`.

## Key File Locations

**Entry Points:**
- `BACKEND/src/main/java/com/bank/MoneyPalApplication.java`: Starts the Spring Boot backend and enables scheduling.
- `FRONTEND/src/main.ts`: Bootstraps the standalone Angular application.
- `FRONTEND/src/app/app.ts`: Root component rendered by Angular bootstrap.

**Configuration:**
- `BACKEND/pom.xml`: Defines the Java 17 Spring Boot build and runtime dependencies.
- `BACKEND/src/main/resources/application.yml`: Defines server port, datasource, JPA validation, Flyway, user-service client settings, JWT settings, actuator exposure, and Swagger paths.
- `FRONTEND/angular.json`: Defines source root, application builder, assets, global styles, output path, and build budgets.
- `FRONTEND/tsconfig.json`: Enables strict TypeScript and strict Angular template/injection checks.
- `FRONTEND/src/app/app.config.ts`: Registers router, HTTP client, and animations.

**Core Logic:**
- `BACKEND/src/main/java/com/bank/security/controller/`: Authentication API entry points.
- `BACKEND/src/main/java/com/bank/fd/service/FdAccountService.java`: FD account lifecycle boundary used by both API and scheduler paths.
- `BACKEND/src/main/java/com/bank/calculator/service/FdCalculatorService.java`: Interest and payout calculations.
- `BACKEND/src/main/java/com/bank/customer/service/CustomerService.java`: Customer profile retrieval/update mapping.
- `FRONTEND/src/app/core/services/auth.service.ts`: Frontend authentication API boundary.
- `FRONTEND/src/app/features/auth/`: Login and signup workflow implementations.

**Testing:**
- `BACKEND/src/test/java/com/bank/SecurityFlowTest.java`: Spring Boot MockMvc authentication/security flow.
- `BACKEND/src/test/java/com/bank/calculator/service/FdCalculatorServiceTest.java`: Calculator service tests.
- `BACKEND/src/test/java/com/bank/fd/service/AccountNumberGeneratorTest.java`: FD account-number generation tests.
- `FRONTEND/src/app/**/*.spec.ts`: Co-located Angular tests for app, auth pages, auth service, and shared shell.

## Naming Conventions

**Files:**
- Java types use PascalCase class names with descriptive suffixes such as `Controller`, `Service`, `Repository`, `Request`, `Response`, and `Exception`; examples include `BACKEND/src/main/java/com/bank/fd/controller/FdAccountController.java` and `BACKEND/src/main/java/com/bank/fd/dto/CreateFdRequest.java`.
- Angular source files use kebab-case directories and feature filenames such as `FRONTEND/src/app/features/auth/login/login.ts` and `FRONTEND/src/app/shared/components/glass-select/glass-select.ts`.
- Angular tests use the `.spec.ts` suffix beside the implementation, for example `FRONTEND/src/app/features/auth/login/login.spec.ts`.
- Component templates and styles share the implementation basename, for example `FRONTEND/src/app/features/landing/landing.ts`, `landing.html`, and `landing.scss`.
- Flyway migrations use `V<number>__description.sql`, as shown by `BACKEND/src/main/resources/db/migration/V1__bankingtech_public_schema.sql`.

**Directories:**
- Java packages use lowercase singular domain names (`security`, `customer`, `fd`, `product`, `calculator`, `reports`) with technical subpackages such as `controller`, `service`, `repository`, `entity`, and `dto`.
- Angular feature directories use kebab-case route or component names (`auth/login`, `features/landing`, `shared/components/glass-shell`).
- API path versioning is represented in the Java package `BACKEND/src/main/java/com/bank/fd/api/` and route prefix `/fd/v1`.

## Where to Add New Code

**New Backend Feature:**
- Primary code: Add a domain package under `BACKEND/src/main/java/com/bank/<feature>/`, then place HTTP endpoints in `controller/`, orchestration in `service/`, persistence mappings in `entity/` and `repository/`, and transport types in `dto/`.
- Database changes: Add a numbered migration under `BACKEND/src/main/resources/db/migration/`; keep entity column names aligned with the migration schema.
- API contract: Add or update the matching document under `BACKEND/src/main/resources/static/openapi/` and, for versioned FD routes, keep operation ids aligned with `BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java`.
- Tests: Add unit/service tests under `BACKEND/src/test/java/com/bank/<matching-package>/` and full HTTP/security flows beside `BACKEND/src/test/java/com/bank/SecurityFlowTest.java`.

**New Frontend Feature/Page:**
- Primary code: Add a route-owned folder under `FRONTEND/src/app/features/<feature>/` with a standalone `.ts` component and adjacent `.html`/`.scss` files.
- Routing: Register the page in `FRONTEND/src/app/app.routes.ts` using `loadComponent` for route-level lazy loading.
- Cross-feature API/model code: Put reusable services in `FRONTEND/src/app/core/services/` and shared interfaces/types in `FRONTEND/src/app/core/models/`.
- Reusable UI: Put components used by multiple features under `FRONTEND/src/app/shared/components/`; keep feature-only visuals under the owning feature, as with `FRONTEND/src/app/features/landing/components/`.
- Tests: Place `.spec.ts` files next to the component or service they exercise, matching `FRONTEND/src/app/features/auth/login/login.spec.ts` and `FRONTEND/src/app/shared/components/glass-shell/glass-shell.spec.ts`.

**New Shared Styling or Asset:**
- Sass tokens and reusable visual mixins: `FRONTEND/src/app/shared/styles/`.
- Global styles: `FRONTEND/src/styles.scss`.
- Component-local styles: The component folder beside its `.ts` and template.
- Static assets: `FRONTEND/public/` so `FRONTEND/angular.json` copies them into the build output.

## Special Directories

**`BACKEND/target/`:**
- Purpose: Maven compilation output, test classes, reports, and copied resources.
- Generated: Yes, by Maven.
- Committed: Treat as build output; source of truth remains under `BACKEND/src/` and `BACKEND/pom.xml`.

**`FRONTEND/dist/`:**
- Purpose: Angular production build output.
- Generated: Yes, by the `frontend` application builder configured in `FRONTEND/angular.json`.
- Committed: Treat as build output; source of truth remains under `FRONTEND/src/`, `FRONTEND/public/`, and Angular configuration files.

**`FRONTEND/node_modules/`:**
- Purpose: Installed npm dependency tree.
- Generated: Yes, from `FRONTEND/package.json` and `FRONTEND/package-lock.json`.
- Committed: No; add application dependencies through `FRONTEND/package.json` and regenerate the lockfile.

**`FRONTEND/public/`:**
- Purpose: Source-controlled static assets available at root-relative URLs.
- Generated: No.
- Committed: Yes as application asset source; references include `/world-map.png` in `FRONTEND/src/app/shared/components/glass-shell/glass-shell.ts` and `FRONTEND/src/app/features/landing/components/globe-hero/globe-hero.ts`.

---

*Structure analysis: 2026-09-18*
