# Banking Lab — Codebase Context

Last reviewed: 2026-10-10. This is a working orientation grounded in the repository. The planning source of truth is .planning/PROJECT.md, REQUIREMENTS.md, ROADMAP.md, STATE.md, and phase artifacts.

## What this app is

Banking Lab (backend artifact name MoneyPal) is a college banking application in a single repository. Its current focus is an end-to-end fixed-deposit demonstration: a customer signs in, browses eligible server-configured offers, books and funds an FD, accrues interest, processes maturity, closes it, and reads transaction history. Existing domains also include authentication, customers, general banking products/configuration, calculators, reports, staff FD product administration, and legacy FD code. Extend the current system and preserve working behavior.

The planning state records the v1 milestone complete (six phases, 21 plans). The supplied AGENTS.md says Phase 1 is current, which conflicts with .planning/STATE.md; consult planning state and relevant phase artifacts before new milestone work.

## Repository map

- BACKEND/: Java 17 Spring Boot 3.5.5 modular monolith; Maven project com.bank:moneypal.
- FRONTEND/: Angular 18 standalone app; source is under FRONTEND/src/.
- .planning/: project intent, requirements, roadmap/state, phase artifacts, research, and codebase maps.
- BACKEND/src/main/resources/db/migration/: ordered Flyway migrations V1–V21. Hibernate validates schema; Flyway owns changes.
- BACKEND/src/main/resources/static/openapi/: FD, FD product administration, and login API specifications.
- BACKEND/postman/: REST demonstration collection(s).

## Architecture

### Backend

One Spring application starts at com.bank.MoneyPalApplication. Packages are grouped by domain and use controller → service → Spring Data repository → JPA entity layers. Key domains include security, customer, fd, product, calculator, reports, and common.

Stack: Spring MVC, Spring Data JPA/Hibernate, Spring Security, validation, JWT (jjwt), PostgreSQL/Supabase, Flyway, springdoc OpenAPI, Maven. Configuration lives in BACKEND/src/main/resources/application.yml. Database credentials and JWT signing secrets are supplied through environment variables; never copy them into source or docs.

The primary FD demonstration API is /fd/v1, implemented at fd/api/controller/FdV1ContractController.java with focused services and domain types under fd/. Implemented core paths include offers, booking, funding, account reads, accrual, maturity, closure, premature-closure request/approval, and unified history. The controller also preserves a wider schema-shaped contract; many extended operations intentionally return 501 NOT_IMPLEMENTED. Check each endpoint and service before assuming it works. Staff product administration is separate under /api/v1/fd-products. Auth APIs include /api/v1/auth and existing /auth contract surfaces.

FD policy decisions recorded in STATE.md: rates are decimal fractions (0.0650 means 6.50% annually), Actual/365, opening date included and maturity date excluded, UTC business timezone, currency scale 2, HALF_UP rounding. Terms/rates are server-owned. Funding records an internal academic transaction and does not claim external payment settlement. Idempotency and ownership/role checks protect the demonstration workflow.

Legacy FD code remains for compatibility, but some write/batch paths explicitly direct users to /fd/v1. Do not assume same-named legacy endpoints implement the primary lifecycle.

### Frontend

Standalone Angular 18 with lazy-loaded routes, feature folders, RxJS, and a small core/shared split. FRONTEND/src/main.ts bootstraps the root component; routes are in FRONTEND/src/app/app.routes.ts. Features include landing, login/signup, dashboard, staff FD product administration, and customer FD browsing, booking, lifecycle, and history. Shared GlassShell and tokens/styles are under shared/.

AuthService calls the backend auth API; auth interceptor and guards protect routes. FdService is the typed /fd/v1 transport and attaches idempotency keys on writes. Its code default is http://localhost:8080/fd/v1; confirm runtime configuration before changing API origins.

## Useful entry points

- Backend startup: BACKEND/src/main/java/com/bank/MoneyPalApplication.java
- FD REST boundary: BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java
- FD lifecycle implementation: BACKEND/src/main/java/com/bank/fd/
- Security: BACKEND/src/main/java/com/bank/security/config/SecurityConfig.java
- FD product administration: BACKEND/src/main/java/com/bank/product/controller/FdProductController.java and FdOfferAdminController.java
- FD API contract: BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml
- Frontend routes: FRONTEND/src/app/app.routes.ts
- Frontend auth and FD transport: FRONTEND/src/app/core/services/auth.service.ts and fd.service.ts
- Backend setup/API notes: BACKEND/README.MD
- Planning: .planning/PROJECT.md, REQUIREMENTS.md, ROADMAP.md, STATE.md

## Local development

- Backend: from BACKEND/, run mvn spring-boot:run after configuring PostgreSQL connection variables and JWT_SECRET. See BACKEND/README.MD for setup and safe staff bootstrap details.
- Frontend: from FRONTEND/, npm start runs Angular dev server and npm run build creates the build.
- Keep DB passwords, JWT secrets, access tokens, and customer data out of context files, tests, logs, and committed config.

## Working conventions

Read project/requirements and relevant phase plans before FD changes; plan phases before implementation and verify against success criteria. Reuse existing services, entities, repositories, calculators, auth, Flyway, and API contracts. Prefer additive schema changes and simple service rules. Use BigDecimal with explicit scale/rounding for money. Validate transitions, ownership, roles, and duplicate behavior. Preserve established non-FD behavior and keep academic scope: no real payment rails, distributed locks, workflow engines, event/outbox infrastructure, or broad auth rewrite.

## Caveats

- The old root CONTEXT.md described an unrelated Angular 21/Supabase-only placeholder and was stale; this file replaces that overview.
- Root README.MD remains minimal; use BACKEND/README.MD and .planning/ for useful operational and milestone details.
- The /fd/v1 controller contains both working workflows and intentionally unimplemented contract operations; inspect implementation rather than inferring from route/spec presence.
- Supabase is the PostgreSQL hosting option described by backend docs, not a browser-side Supabase SDK architecture.
