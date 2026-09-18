# Technology Stack

**Analysis Date:** 2026-09-18

## Languages

**Primary:**
- Java 17 - Spring Boot backend services, REST controllers, security, persistence, scheduled fixed-deposit jobs; declared in `BACKEND/pom.xml` and bootstrapped by `BACKEND/src/main/java/com/bank/MoneyPalApplication.java`.
- TypeScript 5.5.4 - Angular frontend application; locked in `FRONTEND/package-lock.json` and configured by `FRONTEND/tsconfig.json`.

**Secondary:**
- SQL (PostgreSQL dialect) - Flyway schema and migration scripts in `BACKEND/src/main/resources/db/migration/`.
- SCSS - Angular component and global styling in `FRONTEND/src/**/*.scss`, compiled through `FRONTEND/angular.json`.
- YAML - Spring configuration and OpenAPI documents in `BACKEND/src/main/resources/application.yml` and `BACKEND/src/main/resources/static/openapi/`.

## Runtime

**Environment:**
- JVM / Java 17 - backend runtime requirement from `BACKEND/pom.xml`.
- Node.js - frontend development/build runtime; Angular 18's locked toolchain accepts Node 18.19+, 20.11+, or newer as recorded in `FRONTEND/package-lock.json`. No repository Node version file is present.

**Package Manager:**
- Maven - backend dependency and build management through `BACKEND/pom.xml`; no Maven wrapper is present.
- npm - frontend dependency and script management through `FRONTEND/package.json`.
- Lockfile: `FRONTEND/package-lock.json` is present with lockfile version 3; no backend dependency lockfile is present.

## Frameworks

**Core:**
- Spring Boot 3.5.5 - backend application framework and executable service parent, declared in `BACKEND/pom.xml`.
- Spring MVC / Spring Web - REST HTTP endpoints under `BACKEND/src/main/java/com/bank/*/controller/`.
- Spring Data JPA with Hibernate - ORM and repositories under `BACKEND/src/main/java/com/bank/*/repository/` and `BACKEND/src/main/java/com/bank/*/entity/`.
- Spring Security 6.x via Spring Boot - stateless security filter chain, BCrypt credentials, method authorization, and JWT filtering in `BACKEND/src/main/java/com/bank/security/config/SecurityConfig.java`.
- Angular 18.2.14 - standalone frontend application and lazy-loaded routes in `FRONTEND/src/app/`.

**Testing:**
- Spring Boot Test and Spring Security Test - backend test support from `BACKEND/pom.xml`.
- H2 - in-memory PostgreSQL-compatible test database configured in `BACKEND/src/test/resources/application-test.yml`.
- Frontend spec configuration references Vitest globals in `FRONTEND/tsconfig.spec.json`, but `FRONTEND/package.json` has no test script or explicit Vitest dependency.

**Build/Dev:**
- Maven Spring Boot plugin - backend packaging and `spring-boot:run` workflow in `BACKEND/pom.xml` and `BACKEND/README.MD`.
- Angular CLI / `@angular/build` 18.2.21 - frontend build and dev server in `FRONTEND/angular.json` and `FRONTEND/package-lock.json`.
- Vite is an Angular CLI transitive build dependency, not a direct application dependency; its locked version is recorded in `FRONTEND/package-lock.json`.

## Key Dependencies

**Critical:**
- `org.postgresql:postgresql` - PostgreSQL JDBC connectivity for the Supabase database, declared in `BACKEND/pom.xml`.
- `org.flywaydb:flyway-core` and `org.flywaydb:flyway-database-postgresql` - versioned schema migrations in `BACKEND/src/main/resources/db/migration/`.
- `io.jsonwebtoken:jjwt-api`, `jjwt-impl`, and `jjwt-jackson` 0.11.5 - HS512 JWT creation and validation in `BACKEND/src/main/java/com/bank/security/jwt/JwtTokenProvider.java`.
- `org.springdoc:springdoc-openapi-starter-webmvc-ui` 2.8.13 - generated API documentation and Swagger UI configuration in `BACKEND/src/main/resources/application.yml`.
- `@angular/common`, `@angular/core`, `@angular/router`, `@angular/forms` 18.2.x - frontend framework, routing, and forms in `FRONTEND/package.json`.
- `rxjs` 7.8.2, `zone.js` 0.14.10, and `tslib` 2.8.1 - Angular reactive APIs, runtime change detection support, and TypeScript helpers locked in `FRONTEND/package-lock.json`.
- `three` 0.185.1 - WebGL globe visualization in `FRONTEND/src/app/features/landing/components/globe-hero/globe-hero.ts`.

**Infrastructure:**
- `spring-boot-starter-actuator` - health, liveness/readiness, info, and Prometheus endpoints configured in `BACKEND/src/main/resources/application.yml`.
- `spring-boot-starter-validation` - Jakarta Bean Validation on request DTOs and controllers in `BACKEND/src/main/java/com/bank/`.
- `spring-boot-starter-web` - JSON REST transport and embedded Spring Boot web runtime in `BACKEND/pom.xml`.
- `spring-boot-starter-data-jpa` - repository abstraction and transaction support in `BACKEND/pom.xml`.

## Configuration

**Environment:**
- Backend runtime configuration is centralized in `BACKEND/src/main/resources/application.yml`; database connection variables are `SUPABASE_DB_URL`, `SUPABASE_DB_USERNAME`, and `SUPABASE_DB_PASSWORD`.
- Internal User Service connection and resilience settings use `USER_SERVICE_URL`, `USER_SERVICE_SERVICE_TOKEN`, `USER_SERVICE_TIMEOUT_MS`, `USER_SERVICE_MAX_RETRIES`, `USER_SERVICE_FAILURE_THRESHOLD`, and `USER_SERVICE_OPEN_DURATION_MS` as referenced by `BACKEND/src/main/resources/application.yml`.
- JWT settings are read by `BACKEND/src/main/java/com/bank/security/jwt/JwtTokenProvider.java` from the `app.jwt` configuration namespace. Secret material is not documented here.
- A `.env` file exists at `FRONTEND/app/.env`; its contents were not read. The active frontend source under `FRONTEND/src/` has no environment-loader module and currently keeps its API base URL in `FRONTEND/src/app/core/services/auth.service.ts`.

**Build:**
- Backend build and dependency configuration: `BACKEND/pom.xml`.
- Frontend build targets, asset copying, SCSS entrypoint, production budgets, and dev server: `FRONTEND/angular.json`.
- Frontend TypeScript strictness and Angular compiler settings: `FRONTEND/tsconfig.json`, `FRONTEND/tsconfig.app.json`, and `FRONTEND/tsconfig.spec.json`.
- Frontend dependency resolution: `FRONTEND/package.json` and `FRONTEND/package-lock.json`.

## Platform Requirements

**Development:**
- Java 17 and Maven for the Spring Boot service; the documented launch command is `mvn spring-boot:run` in `BACKEND/README.MD`.
- Node.js compatible with Angular 18 and npm for the frontend; the documented commands are `npm install`, `npm start`, and `npm run build` in `FRONTEND/README.md`.
- A PostgreSQL-compatible Supabase connection is required for normal backend startup because `spring.datasource.url` is supplied by `SUPABASE_DB_URL` in `BACKEND/src/main/resources/application.yml`.

**Production:**
- The backend is an executable Spring Boot service listening on port 8080 by default, configured in `BACKEND/src/main/resources/application.yml`.
- The frontend produces a browser bundle under `dist/frontend` through `FRONTEND/angular.json`.
- No Docker, Kubernetes, cloud-hosting, or CI/CD deployment configuration is detected in the repository.

---

*Stack analysis: 2026-09-18*
