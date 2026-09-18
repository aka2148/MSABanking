# Banking Lab

## What This Is

Banking Lab is an existing banking system with a Spring Boot backend and Angular frontend in a single repository. The backend provides authentication, customer, fixed-deposit product, calculator, reporting, and scheduled-processing capabilities over PostgreSQL/Supabase persistence; the frontend provides the current landing, authentication, and dashboard shell. This project continues development of that existing system without breaking established functionality.

## Core Value

Customers and banking operations must be able to rely on a persisted, secure, and auditable fixed-deposit lifecycle from product selection through maturity or closure.

## Requirements

### Validated

- ✓ Spring Boot modular-monolith backend starts from `BACKEND/src/main/java/com/bank/MoneyPalApplication.java` — existing
- ✓ Angular standalone frontend with lazy-loaded landing, authentication, and dashboard routes — existing
- ✓ Flyway-managed PostgreSQL-compatible schema with Hibernate validation — existing
- ✓ Existing authentication, customer, product, calculator, reporting, and API contract surfaces — existing
- ✓ Fixed-deposit domain entities, repositories, calculations, scheduled jobs, and `/fd/v1` contract surface exist — existing, though lifecycle behavior is incomplete

### Active

- [ ] Implement the fixed-deposit lifecycle against the existing Flyway schema and `/fd/v1` contract.
- [ ] Support active product/rate selection, validated account booking and funding state, persisted product snapshots, and customer ownership checks.
- [ ] Persist idempotent interest accrual and maturity transitions, including safe scheduled processing.
- [ ] Support eligible account closure and recorded payout/settlement behavior.
- [ ] Preserve existing authentication, product, calculator, customer, reporting, and other API behavior while the FD implementation is added.
- [ ] Establish contract, service, PostgreSQL migration, security, scheduler, and regression test coverage for the delivered behavior.

### Out of Scope

- Customer deposit/withdrawal transaction variants beyond initial booking/funding — defer until the core lifecycle is stable.
- Statements, notices, event publishing, holders, and reference-data synchronization beyond what is required by the core lifecycle — defer to a later milestone.
- Angular fixed-deposit screens or end-to-end frontend integration — this milestone is backend API first.
- Replacing the authoritative Flyway schema or redesigning the `/fd/v1` contract — compatibility with the existing model is required.
- Removing existing API surfaces before replacement behavior and compatibility tests exist — preserve current functionality during migration.

## Context

- The repository is a brownfield monorepo. Its existing codebase map is stored in `.planning/codebase/`.
- The backend uses Java 17, Spring Boot 3.5.5, Spring Data JPA/Hibernate, Spring Security, Flyway, PostgreSQL, and Maven from `BACKEND/pom.xml`.
- The frontend uses Angular 18 standalone components and TypeScript from `FRONTEND/package.json`, but frontend delivery is not the focus of this milestone.
- The normalized FD schema is defined by migrations under `BACKEND/src/main/resources/db/migration/`, with the API contract in `BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml`.
- `BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java` currently exposes many placeholder operations returning `501 NOT_IMPLEMENTED`, while legacy FD services and scheduled jobs also contain intentionally incomplete paths.
- Existing codebase concerns identify split authentication contracts, tracked build output, static JWT configuration, in-memory session/OTP state, incomplete reports, frontend contract drift, and limited automated coverage. These must not be widened by the FD work.

## Constraints

- **Compatibility**: Treat the existing Flyway schema and `/fd/v1` OpenAPI contract as authoritative — downstream consumers and current migrations must remain supportable.
- **Security**: Enforce customer ownership, staff authorization, safe public boundaries, and idempotency for financial lifecycle operations.
- **Financial correctness**: Use persisted monetary and lifecycle state with deterministic, testable calculations and explicit transition rules.
- **Persistence**: Validate migrations and mappings against PostgreSQL-compatible behavior; do not rely only on H2 or mocks for schema-sensitive work.
- **Operational safety**: Accrual and maturity jobs must be idempotent, observable, and safe to rerun without duplicate financial effects.
- **Regression safety**: Existing authentication, customer, product, calculator, reporting, and other established APIs must continue to pass their current tests and compatibility checks.
- **Delivery order**: Build and verify backend APIs and services before adding frontend FD workflows.

## Key Decisions

| Decision | Rationale | Outcome |
|----------|-----------|---------|
| Make FD lifecycle the next milestone priority | The existing FD contract is broad but most operations are placeholders, blocking core banking value | — Pending |
| Implement backend API first | The lifecycle needs reliable domain, persistence, authorization, and scheduler behavior before UI integration | — Pending |
| Preserve the existing Flyway schema and `/fd/v1` contract | They are the repository's current integration boundary and authoritative model | — Pending |
| Deliver product/rates, booking/funding, accrual/maturity, and closure/payout first | This is the smallest complete core lifecycle; broader transactions and documents can follow | — Pending |
| Require contract, service, PostgreSQL migration, security, scheduler, and regression tests | Financial behavior and compatibility need verification beyond compilation | — Pending |

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
*Last updated: 2026-09-18 after project initialization questioning*
