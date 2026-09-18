# Banking Lab

## What This Is

Banking Lab is an existing college lab banking system with a Spring Boot backend and Angular frontend in a single repository. The backend provides authentication, customer, fixed-deposit product, calculator, reporting, and scheduled-processing capabilities over PostgreSQL/Supabase persistence; the frontend provides the current landing, authentication, and dashboard shell. This project continues development of that existing system into an understandable, functional FD demonstration without breaking established functionality.

## Core Value

Students must be able to demonstrate a complete, understandable fixed-deposit workflow from product selection through booking, funding, interest calculation, maturity, and closure using the existing application.

## Requirements

### Validated

- ✓ Spring Boot modular-monolith backend starts from `BACKEND/src/main/java/com/bank/MoneyPalApplication.java` — existing
- ✓ Angular standalone frontend with lazy-loaded landing, authentication, and dashboard routes — existing
- ✓ Flyway-managed PostgreSQL-compatible schema with Hibernate validation — existing
- ✓ Existing authentication, customer, product, calculator, reporting, and API contract surfaces — existing
- ✓ Fixed-deposit domain entities, repositories, calculations, scheduled jobs, and `/fd/v1` contract surface exist — existing, though lifecycle behavior is incomplete

### Active

- [ ] Implement a demonstrable FD lifecycle against the existing Flyway schema and REST contract.
- [ ] Allow an authenticated customer to view eligible products/terms, associate an account, book an FD, and fund it.
- [ ] Transition an FD through clear statuses such as pending, active, matured, and closed.
- [ ] Calculate interest and maturity amounts with `BigDecimal` and persist the resulting transaction/history records.
- [ ] Support normal maturity closure and a straightforward premature-closure path with the applicable existing penalty/rule behavior.
- [ ] Preserve existing authentication, product, calculator, customer, reporting, legacy FD, migration, and other API behavior while the FD work is added.
- [ ] Add focused unit and integration tests for the core business rules, PostgreSQL persistence, validation, basic ownership/role authorization, and duplicate-action protection.

### Out of Scope

- Advanced deposit/withdrawal variants, recurring payouts, auto-renewal, and complex holder/nominee workflows — beyond the core lab demonstration.
- Real payment-rail integration, tax/regulatory reporting, distributed locking, event/outbox infrastructure, and enterprise reconciliation — not needed for the academic scope.
- A major authentication rewrite or broad rule-engine redesign — reuse the existing mechanisms and add only what the FD flow needs.
- Replacing the existing Flyway schema or redesigning the REST contract unnecessarily — compatibility with the current model is required.
- Removing existing API surfaces before replacement behavior is verified — preserve current functionality during the work.

## Context

- The repository is a brownfield monorepo. Its existing codebase map is stored in `.planning/codebase/`.
- The backend uses Java 17, Spring Boot 3.5.5, Spring Data JPA/Hibernate, Spring Security, Flyway, PostgreSQL, and Maven from `BACKEND/pom.xml`.
- The frontend uses Angular 18 standalone components and TypeScript from `FRONTEND/package.json`, but frontend delivery is not the focus of this milestone.
- The normalized FD schema is defined by migrations under `BACKEND/src/main/resources/db/migration/`, with the API contract in `BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml`.
- `BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java` currently exposes many placeholder operations returning `501 NOT_IMPLEMENTED`, while legacy FD services and scheduled jobs also contain intentionally incomplete paths.
- Existing codebase concerns identify split authentication contracts, tracked build output, static JWT configuration, in-memory session/OTP state, incomplete reports, frontend contract drift, and limited automated coverage. The FD work should address only the directly relevant correctness and access-control gaps.

## Constraints

- **Academic scope**: Prefer simple, explainable service-layer code and minimal dependencies over production-scale infrastructure.
- **Compatibility**: Treat the existing Flyway schema and REST/OpenAPI contract as the starting point — current functionality and migrations must remain supportable.
- **Security**: Enforce authenticated customer ownership and basic staff/role authorization, including protection against direct-object-reference access.
- **Financial correctness**: Use `BigDecimal`, persisted monetary values, validation, and explicit lifecycle transitions.
- **Persistence**: Validate migrations and mappings against PostgreSQL behavior while reusing existing repositories and entities where practical.
- **Duplicate safety**: Prevent repeated booking, funding, accrual, maturity, or closure actions from creating incorrect duplicate records without introducing distributed infrastructure.
- **Regression safety**: Existing authentication, customer, product, calculator, reporting, and other established APIs must continue to pass relevant tests and compatibility checks.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Make the core FD workflow the next milestone priority | The existing FD implementation is incomplete, while the lifecycle is the central academic demonstration | — Pending |
| Reuse existing services, entities, repositories, calculators, authentication, and schema | The project is an existing college application and should remain understandable | — Pending |
| Use `BigDecimal` and focused lifecycle rules | Monetary correctness matters without introducing a production policy engine | — Pending |
| Deliver REST/API-first end-to-end functionality | It provides a clear demonstrable workflow while keeping the milestone achievable | — Pending |
| Use focused unit/integration/security/regression tests | The project needs evidence of core behavior, not enterprise infrastructure | — Pending |

## Evolution

This document evolves at phase transitions and milestone boundaries.

**After each phase transition** (via `/gsd-transition`):
1. Requirements invalidated? → Move to Out of Scope with reason
2. Requirements validated? → Move to Validated with phase reference
3. New requirements emerged? → Add to Active
4. Decisions to log? → Add to Key Decisions
5. "What This Is" still accurate? → Update if drifted

**After each milestone** (via `/gsd-complete-milestone`):
1. Full review of all sections
2. Core Value check — still the right priority?
3. Audit Out of Scope — reasons still valid?
4. Update Context with current state

---
*Last updated: 2026-09-18 after academic-scope clarification*
