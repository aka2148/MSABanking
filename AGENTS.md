# Banking Lab Project Guide

## Project

Banking Lab is an existing college lab banking application. Continue the current Spring Boot/Angular system rather than replacing working modules.

## Current Milestone

The active milestone is a backend/API-first fixed-deposit demonstration:

`authenticate -> view eligible products -> book -> fund -> ACTIVE -> calculate/accrue interest -> MATURED -> close -> inspect transaction history`

## Working Rules

- Read `.planning/PROJECT.md`, `.planning/REQUIREMENTS.md`, and the relevant phase artifacts before changing code.
- Preserve existing authentication, customer, product, calculator, reporting, legacy FD, migration, and API behavior unless a change is required by the FD workflow.
- Reuse existing services, entities, repositories, calculators, authentication mechanisms, Flyway migrations, and project conventions before adding replacements.
- Keep the implementation understandable for a college project: simple service-layer logic, clear domain models, minimal dependencies, and focused tests.
- Use `BigDecimal` with explicit scale and rounding for monetary calculations.
- Validate lifecycle transitions, customer association, ownership, role access, and duplicate actions at the appropriate service/API boundaries.
- Use PostgreSQL/Flyway persistence and add only necessary schema changes and constraints.
- Prefer REST/Swagger/Postman as the v1 demonstration surface; Angular FD screens are deferred unless existing functionality requires them.
- Do not introduce distributed locks, workflow engines, event/outbox infrastructure, real payment rails, elaborate reconciliation, or a major authentication rewrite for this milestone.
- Do not expose secrets, credentials, or sensitive account data in source, documentation, logs, or tests.

## GSD Workflow

- Project context: `.planning/PROJECT.md`
- Requirements: `.planning/REQUIREMENTS.md`
- Roadmap: `.planning/ROADMAP.md`
- State: `.planning/STATE.md`
- Current phase: Phase 1, Existing FD Baseline
- Plan phases before implementation and verify each phase against its success criteria.
