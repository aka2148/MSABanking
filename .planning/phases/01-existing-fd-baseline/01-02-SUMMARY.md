---
phase: 01-existing-fd-baseline
plan: 02
subsystem: database
tags: [fixed-deposit, PostgreSQL, Flyway, JPA, lifecycle, idempotency]

# Dependency graph
requires:
  - phase: 01-existing-fd-baseline
    provides: explicit FD lifecycle state machine, policy contracts, and normalized Flyway schema
provides:
  - forward-only V6 lifecycle and idempotency baseline migration
  - pending-safe FD account mapping with numeric optimistic versioning
  - locked account and deterministic transaction history repository contracts
  - durable actor-scoped FD operation identity mapping
affects: [02-product-booking-and-funding, 03-interest-and-maturity-processing, 04-closure-and-api, 05-protection-tests-regression]

# Tech tracking
tech-stack:
  added: []
  patterns: [diagnostic legacy-data preflight, named PostgreSQL checks/indexes, JPA row versioning, actor-scoped operation keys]

key-files:
  created:
    - BACKEND/src/main/resources/db/migration/V6__fd_lifecycle_baseline.sql
    - BACKEND/src/main/java/com/bank/fd/entity/FdOperationIdempotency.java
    - BACKEND/src/main/java/com/bank/fd/repository/FdOperationIdempotencyRepository.java
    - BACKEND/src/test/java/com/bank/fd/entity/FdPersistenceContractTest.java
  modified:
    - BACKEND/src/main/java/com/bank/fd/entity/FdAccount.java
    - BACKEND/src/main/java/com/bank/fd/repository/FdAccountRepository.java
    - BACKEND/src/main/java/com/bank/fd/repository/FdTransactionRepository.java

key-decisions:
  - "V6 is additive and forward-only; legacy violations raise named diagnostics before checks or unique indexes are added."
  - "The legacy fa_version string remains a compatibility field, while fa_row_version is the numeric JPA @Version column."
  - "Operation status remains a validated string because operation identity is not coupled to the FD lifecycle enum."

patterns-established:
  - "Database constraints and unique indexes enforce the minimum lifecycle and duplicate-sensitive invariants without changing V1-V5."
  - "Financial account mutations can acquire a pessimistic row lock, while owner-scoped reads and deterministic history ordering remain repository contracts."

requirements-completed: [DATA-01, DATA-02]

# Metrics
duration: 4min
completed: 2026-09-18
---

# Phase 1 Plan 2: Existing FD Baseline Summary

**Forward-only PostgreSQL FD baseline with diagnostic legacy preflight, pending-safe row versioning, deterministic history access, and durable actor-scoped operation idempotency.**

## Performance

- **Duration:** 4 min
- **Started:** 2026-09-18T10:01:00Z
- **Completed:** 2026-09-18T10:05:10Z
- **Tasks:** 3/3
- **Files modified:** 7 implementation/test files, plus this summary

## Accomplishments

- Added V6 without editing V1-V5. The migration validates existing principal, tenure, status, and resource UUID invariants before adding named checks, indexes, and a PENDING default.
- Added `fa_row_version` as a numeric JPA optimistic version while keeping the legacy `fa_version` compatibility mapping separate; new accounts now default to `PENDING` in Java and PostgreSQL.
- Added pessimistic account lookup, owner-scoped status lookup, deterministic transaction history ordering, and a durable three-part operation identity table/repository.

## Task Commits

Each implementation task was committed atomically:

1. **Task 1: Add the forward-only PostgreSQL FD baseline migration** - `179e224` (feat)
2. **Task 2: Align FD entities and repositories with the baseline schema** - `20c2a4f` (test RED), `367220c` (feat GREEN)
3. **Task 3: Map durable operation identity and verify its repository contract** - `d95354a` (feat)

## Files Created/Modified

- `BACKEND/src/main/resources/db/migration/V6__fd_lifecycle_baseline.sql` - Preflights legacy rows, adds lifecycle checks/default, numeric row version, scoped idempotency table, resource UUID uniqueness, and lookup indexes.
- `BACKEND/src/main/java/com/bank/fd/entity/FdAccount.java` - Uses a PENDING initializer and maps `fa_row_version` with `@Version`.
- `BACKEND/src/main/java/com/bank/fd/repository/FdAccountRepository.java` - Adds pessimistic-write and owner/status lookup contracts.
- `BACKEND/src/main/java/com/bank/fd/repository/FdTransactionRepository.java` - Adds deterministic account/date/id history ordering.
- `BACKEND/src/main/java/com/bank/fd/entity/FdOperationIdempotency.java` - Maps all `foi_*` operation identity columns without sensitive payload fields.
- `BACKEND/src/main/java/com/bank/fd/repository/FdOperationIdempotencyRepository.java` - Finds an operation by scope, actor key, and idempotency key.
- `BACKEND/src/test/java/com/bank/fd/entity/FdPersistenceContractTest.java` - Covers pending defaults, numeric version mapping, locking, and history contracts.

## Decisions Made

- V6 performs explicit diagnostic preflight checks and never silently coerces or deletes incompatible legacy rows.
- The existing `fa_version` string is retained for compatibility; lifecycle concurrency uses the new `fa_row_version` numeric field.
- Idempotency status is persisted as a constrained string rather than introducing a global enum for a future operation vocabulary.

## Verification

- `mvn -q -DskipTests compile` — PASS after each task.
- `mvn -q -Dtest=FdPersistenceContractTest test` — PASS after the GREEN implementation; the preceding RED run failed on the intentionally missing contracts.
- `mvn -q test` from `BACKEND` — PASS, including existing `SecurityFlowTest`, `FdCalculatorServiceTest`, and `AccountNumberGeneratorTest`.
- Static acceptance scans — PASS for V6 default/check/idempotency requirements, pending initializer, `fa_row_version` mapping, pessimistic lock, deterministic history finder, exact operation finder, and absence of sensitive operation fields.
- V1-V5 files remained unchanged; the existing Supabase/PostgreSQL configuration and data were not accessed or replaced.
- The real PostgreSQL/Testcontainers schema gate is introduced by Plan 01-03 and is ready to apply V6; no new database or external database mutation was performed in this plan.

## Deviations from Plan

### Auto-fixed Issues

**1. [Rule 3 - Blocking] Repair planning metadata after SDK marker mismatch**
- **Found during:** State and roadmap updates
- **Issue:** The roadmap handler did not recognize the repository's bold plan checkbox format, and the requirements handler split the `DATA-01` marker across lines while updating it.
- **Fix:** Manually restored the complete requirement marker, marked `DATA-01` complete, synchronized the Phase 1 plan checklist/progress to 2/3, and repaired the performance metrics table after the handler appended an orphan row.
- **Files modified:** `.planning/ROADMAP.md`, `.planning/REQUIREMENTS.md`, `.planning/STATE.md`
- **Verification:** Re-read all three artifacts; ROADMAP shows 01-01 and 01-02 checked with 2/3 progress, REQUIREMENTS shows complete `DATA-01`/`DATA-02` entries, and STATE reports two completed plans.
- **Committed in:** Final plan metadata commit.

---

**Total deviations:** 1 auto-fixed (Rule 3: 1).
**Impact on plan:** Planning metadata was corrected without changing application behavior or schema scope.

## Issues Encountered

None affecting implementation. Maven regenerated existing tracked `BACKEND/target` artifacts during verification; those build outputs were restored or removed and were not committed.

## Known Stubs

None in files created or modified by this plan.

## User Setup Required

None - no external service configuration required.

## Next Phase Readiness

Plan 01-03 can now run its Testcontainers PostgreSQL migration gate against V1-V6 and verify the catalog/rejection behavior. Later booking and processing phases have the pending-safe account mapping, row locking/versioning, deterministic history query, and durable operation-key boundary they require.

## Self-Check: PASSED

- All seven implementation/test files listed above exist.
- Task commits `179e224`, `20c2a4f`, `367220c`, and `d95354a` are present in git history.
- Compile, focused persistence contract test, full backend test suite, static acceptance scans, and V1-V5 immutability checks passed.

---
*Phase: 01-existing-fd-baseline*
*Completed: 2026-09-18*
