# Phase 1: Existing FD Baseline — Research

**Researched:** 2026-09-18
**Status:** Ready for planning

## Scope and Goal

Phase 1 establishes a safe foundation for the existing fixed-deposit model. It does not book, fund, accrue, mature, or close customer deposits; those behaviors belong to the later roadmap phases. It must make the lifecycle rules, persistence constraints, and `/fd/v1` boundary explicit enough that later phases can implement them without replacing the existing schema, security, product, calculator, or API modules.

## Repository Findings

- `BACKEND/src/main/java/com/bank/fd/entity/FdStatus.java` already declares `PENDING`, `ACTIVE`, `MATURED`, `CLOSED`, and `PREMATURELY_CLOSED`, but `FdAccount` initializes new entities to `ACTIVE` and exposes a public arbitrary status setter.
- `FdAccount` converts tenure months to days using `months * 30` and converts days back with integer division. These compatibility accessors must not become the authoritative lifecycle model.
- `FdAccountService` and `FdV1ContractController` contain explicit unsupported paths. The controller accepts untyped maps and returns `501`; internal sync routes currently return `204` without persistence. Phase 1 should preserve truthful unsupported responses while establishing typed boundaries, not claim business success before the owning phase implements it.
- `fd_accounts` has a database default of `ACTIVE`, nullable financial terms, no numeric row-version column, and no status/amount/tenure checks. `fd_interest_accrual` has no natural account/date uniqueness key. The existing migrations V1–V5 must remain immutable; baseline changes belong in a new forward migration.
- PostgreSQL is the authoritative persistence engine. The current test profile disables Flyway and uses H2 `create-drop`, so a separate PostgreSQL migration test is required for schema evidence.
- The existing calculator uses `doubleValue()`, `Math.pow`, and final-only rounding. Phase 1 should define the policy and lifecycle contracts without silently reusing this as a persisted-money authority; calculation replacement is planned for Phase 3.

## Planning Constraints

1. Use the existing Java 17/Spring Boot 3.5.5/JPA/Flyway/PostgreSQL modular-monolith structure.
2. Use a pure explicit transition policy rather than Spring Statemachine or a workflow engine.
3. Additive migration only: do not edit V1–V5, drop existing data, or make a broad schema rewrite.
4. Keep unsupported operations truthful with the existing `ApiErrorResponse`; do not convert them to success responses.
5. Financial policy values that are not established by the repository must be selected at the execution decision checkpoint and persisted as an explicit versioned policy contract rather than guessed in services.

## Validation Architecture

- Pure lifecycle state-machine tests cover every allowed edge and every rejected source/command pair.
- A PostgreSQL Testcontainers test applies all Flyway migrations, checks the new default/check/unique structures through `information_schema` and `pg_catalog`, and verifies a fresh FD account insert defaults to `PENDING`.
- Reflection/resource contract tests verify that the account boundary uses typed request DTOs, that arbitrary status updates are absent from the OpenAPI update schema, and that existing unsupported routes retain the stable error shape.
- The existing Maven test suite remains a regression gate; H2 tests continue to provide fast non-PostgreSQL coverage but are not treated as schema proof.

## Sources Read

- `.planning/PROJECT.md`
- `.planning/REQUIREMENTS.md`
- `.planning/ROADMAP.md`
- `.planning/STATE.md`
- `.planning/research/SUMMARY.md`
- `.planning/research/ARCHITECTURE.md`
- `.planning/research/FEATURES.md`
- `.planning/research/PITFALLS.md`
- `.planning/research/STACK.md`
- `.planning/codebase/ARCHITECTURE.md`
- `.planning/codebase/CONCERNS.md`
- `BACKEND/src/main/resources/db/migration/V1__bankingtech_public_schema.sql` through `V5__*.sql`
- `BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml`
- Existing FD entities, repositories, services, controllers, scheduler, calculator, tests, `pom.xml`, and `application.yml`

## Out of This Phase

Product eligibility, booking, funding, interest calculation, accrual, maturity processing, normal closure, premature closure, ownership hardening, duplicate-operation behavior, and the full REST demonstration remain assigned to their roadmap phases. No plan may implement a reduced substitute for those capabilities in this baseline.
