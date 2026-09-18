---
phase: 1
slug: existing-fd-baseline
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-09-18
---

# Phase 1 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit Jupiter, Spring Boot test dependencies, Testcontainers PostgreSQL |
| **Config file** | `BACKEND/pom.xml`; PostgreSQL test uses programmatic container properties |
| **Quick run command** | `mvn -q -Dtest=FdLifecycleStateMachineTest test` from `BACKEND` |
| **Full suite command** | `mvn -q test` from `BACKEND` |
| **Estimated runtime** | ~60 seconds with Docker available |

## Sampling Rate

- **After every task commit:** Run `mvn -q -Dtest=FdLifecycleStateMachineTest test` or the task-specific test command.
- **After every plan wave:** Run `mvn -q test` from `BACKEND`.
- **Before `/gsd-verify-work`:** Full suite must be green, including the PostgreSQL container test.
- **Max feedback latency:** 60 seconds for unit tests; container startup may exceed this on a cold Docker cache.

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 01-01-01 | 01 | 1 | LIFE-01 | T-01-01 | Only named lifecycle edges are accepted | unit | `mvn -q -Dtest=FdLifecycleStateMachineTest test` | ✅ W0 | ⬜ pending |
| 01-01-03 | 01 | 1 | LIFE-01 | T-01-01 | Policy contract rejects incomplete configuration | unit | `mvn -q -Dtest=FdCalculationPolicyTest test` | ✅ W0 | ⬜ pending |
| 01-02-01 | 02 | 1 | DATA-01 | T-01-02 | Schema rejects invalid FD state/amount/tenure and defaults new rows to `PENDING` | integration | `mvn -q -Dtest=FdPostgresSchemaTest test` | ✅ W0 | ⬜ pending |
| 01-03-01 | 03 | 2 | DATA-02 | T-01-03 | Contract boundary is typed and does not accept arbitrary status mutation | contract | `mvn -q -Dtest=FdV1ContractBoundaryTest test` | ✅ W0 | ⬜ pending |

## Wave 0 Requirements

Existing Maven/JUnit infrastructure plus the tests named in the map cover the phase requirements; no separate Wave 0 installation task is required.

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Policy decision checkpoint | LIFE-01 | The repository does not establish the authoritative product money/date policy | Select one policy option during Plan 01 Task 2 and confirm the selected version/values are recorded in `FdCalculationPolicy` and configuration. |

## Validation Sign-Off

- [ ] All tasks have an automated verification command.
- [ ] Sampling continuity has no three-task gap without an automated check.
- [ ] No watch-mode flags are used.
- [ ] `nyquist_compliant: true` is set after all commands pass.
- [ ] Approval: pending
