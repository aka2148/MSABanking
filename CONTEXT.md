# Banking Lab Project Context

## Project overview

Banking Lab is an existing college laboratory banking application. It uses a Spring Boot backend and an Angular frontend. Development should extend the current system and preserve working modules rather than replace them.

## Active milestone

The current milestone is a backend/API-first fixed-deposit (FD) demonstration. The intended customer journey is:

1. Authenticate.
2. View eligible FD products.
3. Book a deposit.
4. Fund the booking.
5. Confirm the deposit becomes `ACTIVE`.
6. Calculate and accrue interest.
7. Mark the deposit `MATURED`.
8. Close the deposit.
9. Inspect transaction history.

REST, Swagger, and Postman are the v1 demonstration surface. Angular FD screens are deferred unless existing functionality requires them.

## Existing system boundaries

Preserve existing authentication, customer, product, calculator, reporting, legacy FD, migration, and API behavior unless a change is needed for the FD journey. Before adding a replacement, look for existing services, entities, repositories, calculators, authentication mechanisms, Flyway migrations, and established conventions to reuse.

## Implementation conventions

- Keep service logic straightforward and suitable for a college project.
- Use `BigDecimal` with explicit scale and rounding for monetary calculations.
- Validate lifecycle transitions, customer association, ownership, role access, and duplicate actions at the appropriate service or API boundary.
- Persist with PostgreSQL and Flyway; add only necessary schema changes and constraints.
- Keep dependencies minimal and tests focused.
- Do not expose secrets, credentials, or sensitive account data in source, documentation, logs, or tests.

## Explicitly out of scope for this milestone

Do not introduce distributed locks, workflow engines, event/outbox infrastructure, real payment rails, elaborate reconciliation, or a major authentication rewrite.

## Planning and workflow

The project planning source of truth is under `.planning/`:

- `PROJECT.md` — project context
- `REQUIREMENTS.md` — requirements
- `ROADMAP.md` — milestone and phase breakdown
- `STATE.md` — current execution state

The current phase is Phase 1, **Existing FD Baseline**. Read the relevant phase artifacts before implementation. Plan phases before coding and verify each phase against its success criteria.

## Repository inspection note

This context file captures the project instructions supplied for this workspace. The command runner was unavailable during its creation, so the repository source, build files, and detailed planning artifacts still need direct inspection before making code-level assumptions.
