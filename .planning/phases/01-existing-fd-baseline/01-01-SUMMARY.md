---
phase: 01-existing-fd-baseline
plan: 01
subsystem: domain/api
tags: [fixed-deposit, lifecycle, BigDecimal, Actual-365, PostgreSQL, policy-contracts]

# Dependency graph
requires:
  - phase: none
    provides: existing FD status enum, Spring Boot backend, and normalized Flyway schema
provides:
  - exhaustive pure FD lifecycle transition contract
  - immutable versioned academic money/date policy
  - injected business-clock, actor, and authoritative product-terms boundaries
affects: [02-product-booking-and-funding, 03-interest-and-maturity-processing, 04-closure-and-api]

# Tech tracking
tech-stack:
  added: []
  patterns: [pure transition policy, immutable Java value types, injected Clock and ZoneId, server-authoritative ports]

key-files:
  created:
    - BACKEND/src/main/java/com/bank/fd/domain/FdLifecycleCommand.java
    - BACKEND/src/main/java/com/bank/fd/domain/FdLifecycleStateMachine.java
    - BACKEND/src/main/java/com/bank/fd/domain/FdTransition.java
    - BACKEND/src/main/java/com/bank/fd/exception/InvalidFdTransitionException.java
    - BACKEND/src/main/java/com/bank/fd/domain/FdCalculationPolicy.java
    - BACKEND/src/main/java/com/bank/fd/domain/FdTimePolicy.java
    - BACKEND/src/main/java/com/bank/fd/application/FdActorContext.java
    - BACKEND/src/main/java/com/bank/fd/application/ProductTermsPort.java
    - BACKEND/src/main/java/com/bank/fd/application/ProductTermsQuery.java
    - BACKEND/src/main/java/com/bank/fd/application/ProductTerms.java
    - BACKEND/src/test/java/com/bank/fd/domain/FdLifecycleStateMachineTest.java
    - BACKEND/src/test/java/com/bank/fd/domain/FdCalculationPolicyTest.java
  modified:
    - BACKEND/src/main/resources/application.yml

key-decisions:
  - "Policy authority: academic fixed policy, not a general production policy engine."
  - "Rate unit: decimal fraction (0.0650 represents 6.50% annually)."
  - "Day count: Actual/365."
  - "Date interval: opening date included, maturity date excluded."
  - "Business timezone: UTC."
  - "Currency/display scale: 2 decimals."
  - "Rounding: HALF_UP."
  - "Actor authority: local /api/auth identity."
  - "Product/rate authority: existing normalized server-owned product/rule tables."
  - "Policy version: academic-v1; later calculations must preserve the booking version."

patterns-established:
  - "Lifecycle changes enter only through FUND, MATURE, CLOSE, or PREMATURE_CLOSE and return a typed transition."
  - "Financial policy requires explicit version, enum values, positive scales, and rounding mode; time requires an injected clock and zone."
  - "FD application code consumes typed actor and product terms contracts rather than persistence entities or security context globals."

requirements-completed: [LIFE-01, DATA-02]

# Metrics
duration: 12min
completed: 2026-09-18
---

# Phase 1 Plan 1: Existing FD Baseline Summary

**Explicit FD lifecycle transitions and versioned academic Actual/365 policy contracts backed by injected UTC time, local auth identity, and normalized product/rule authority.**

## Performance

- **Duration:** 12 min for the resumed execution
- **Started:** 2026-09-18T09:44:30Z
- **Completed:** 2026-09-18T09:56:22Z
- **Tasks:** 3/3
- **Files modified:** 13 application/test/config files, plus this summary

## Accomplishments

- Verified the prior TDD lifecycle commits and retained the exhaustive five-status/four-command transition matrix.
- Recorded the decision checkpoint exactly: academic fixed policy, decimal-fraction rate, Actual/365, half-open opening-inclusive/maturity-exclusive interval, UTC, two-decimal HALF_UP money/display, local `/api/auth` actor, and normalized server-owned product/rule terms.
- Added immutable calculation/time contracts, typed actor and product-terms boundaries, and concrete `fd.policy`/`fd.integration` configuration without changing the existing calculator, database configuration, migrations, or API behavior.

## Task Commits

Each implementation task was committed atomically; the resumed plan includes the prior lifecycle commits:

1. **Task 1: Implement the explicit FD lifecycle state machine** - `63eb82a` (test RED), `f6987b6` (feat GREEN)
2. **Task 2: Select the authoritative money and business-date policy** - decision recorded in this summary and configuration
3. **Task 3: Encode the selected policy and injected time boundary** - `d12ae57` (test RED), `83ea8f3` (feat GREEN)

**Plan metadata:** final docs commit contains this summary plus the updated planning state, roadmap, and requirements traceability.

## Files Created/Modified

- `BACKEND/src/main/java/com/bank/fd/domain/FdLifecycleCommand.java` - closed four-command lifecycle vocabulary.
- `BACKEND/src/main/java/com/bank/fd/domain/FdLifecycleStateMachine.java` - pure allowlist for the four supported transitions.
- `BACKEND/src/main/java/com/bank/fd/domain/FdTransition.java` - immutable source/target result.
- `BACKEND/src/main/java/com/bank/fd/exception/InvalidFdTransitionException.java` - rejects invalid source/command pairs with both values in the message.
- `BACKEND/src/main/java/com/bank/fd/domain/FdCalculationPolicy.java` - validated `academic-v1` rate, day-count, scale, boundary, and rounding contract.
- `BACKEND/src/main/java/com/bank/fd/domain/FdTimePolicy.java` - derives instants and business dates from injected `Clock`/`ZoneId`.
- `BACKEND/src/main/java/com/bank/fd/application/FdActorContext.java` - immutable local-auth actor contract with defensive role copying.
- `BACKEND/src/main/java/com/bank/fd/application/ProductTermsPort.java` - server-authoritative active terms boundary.
- `BACKEND/src/main/java/com/bank/fd/application/ProductTermsQuery.java` and `ProductTerms.java` - validated immutable product/rule request and resolved snapshot values.
- `BACKEND/src/test/java/com/bank/fd/domain/FdCalculationPolicyTest.java` - policy validation, exact values, injected time, and typed contract coverage.
- `BACKEND/src/main/resources/application.yml` - selected `fd.policy` and `fd.integration` values.

## Decisions Made

All checkpoint decisions were recorded exactly as requested in the frontmatter and configuration. The implementation uses `academic-v1` as the immutable policy version; it is intentionally not a configurable production rule engine.

## Verification

- `mvn -q -Dtest=FdLifecycleStateMachineTest test` — PASS.
- `mvn -q -Dtest=FdCalculationPolicyTest test` — PASS.
- `mvn -q test` from `BACKEND` — PASS; existing H2-backed security, calculator, and account-number tests also remained passing.
- Acceptance scans confirmed all eight policy fields, no direct `LocalDate.now()`/`Instant.now()` in `FdTimePolicy`, all required YAML keys/values, and no repository/controller/security-context/FdProduct imports in the new contracts.
- The repository's existing Supabase/PostgreSQL datasource configuration and Flyway migrations were preserved; no database or schema replacement was introduced.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 1 - Bug] Normalize missing rounding mode to the contract's validation error**
- **Found during:** Task 3 focused policy test
- **Issue:** A missing rounding mode was rejected by `Objects.requireNonNull` as `NullPointerException`, while invalid/missing policy values are required to fail as a policy validation error.
- **Fix:** Added explicit `IllegalArgumentException` validation for null enum and rounding-mode values.
- **Files modified:** `BACKEND/src/main/java/com/bank/fd/domain/FdCalculationPolicy.java`
- **Verification:** `mvn -q -Dtest=FdCalculationPolicyTest test` — PASS.
- **Committed in:** `83ea8f3` (Task 3 GREEN commit)

---

**Total deviations:** 1 auto-fixed (Rule 1: 1).
**Impact on plan:** Required validation behavior was clarified without changing architecture, schema, or existing runtime behavior.

## Issues Encountered

- Maven test execution regenerated existing tracked `BACKEND/target` build artifacts; those generated changes were restored/removed and were not included in task commits.
- The SDK roadmap/requirements handlers did not match this repository's existing Phase 1 roadmap row and split requirement bold markers while marking requirements. The affected roadmap and requirement entries were repaired manually and verified.

## Known Stubs

None in files created or modified by this plan.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

The lifecycle and policy boundaries are ready for Plan 01-02 and later booking/calculation consumers. Product terms still require a concrete adapter in the later booking phase; this plan intentionally created only the server-owned port and did not claim booking or calculation functionality.

## Self-Check: PASSED

- Summary file exists at the required phase path.
- Prior lifecycle RED/GREEN commits, Task 3 RED/GREEN commits, and the initial metadata commit are present in git history.
- Focused lifecycle, focused policy, full backend tests, acceptance scans, and stub scan completed successfully.

---
*Phase: 01-existing-fd-baseline*
*Completed: 2026-09-18*
