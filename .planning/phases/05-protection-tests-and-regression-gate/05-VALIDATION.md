---
phase: 5
slug: 05-protection-tests-and-regression-gate
status: draft
nyquist_compliant: false
wave_0_complete: false
created: 2026-09-22
---

# Phase 5 — Validation Strategy

> Per-phase validation contract for feedback sampling during execution.

---

## Test Infrastructure

| Property | Value |
|----------|-------|
| **Framework** | JUnit 5 + AssertJ/Mockito via spring-boot-starter-test; MockMvc + spring-security-test; Testcontainers postgresql via spring-boot-testcontainers |
| **Config file** | `BACKEND/pom.xml` (all test dependencies already present — no install needed) |
| **Quick run command** | `mvn -q -Dtest='Fd*Test' test` (from `BACKEND/`) |
| **Full suite command** | `mvn test` (from `BACKEND/`) |
| **Estimated runtime** | ~300 seconds (full suite; Testcontainers pulls PostgreSQL 16 Alpine on first run) |

---

## Sampling Rate

- **After every task commit:** Run `mvn -q -Dtest='<TouchedTestClass>' test`
- **After every plan wave:** Run `mvn test`
- **Before `/gsd-verify-work`:** Full suite must be green
- **Max feedback latency:** 300 seconds

---

## Per-Task Verification Map

| Task ID | Plan | Wave | Requirement | Threat Ref | Secure Behavior | Test Type | Automated Command | File Exists | Status |
|---------|------|------|-------------|------------|-----------------|-----------|-------------------|-------------|--------|
| 5-01-01 | 01 | 1 | AUTH-01, AUTH-02 | T-5-01 / T-5-02 | Cross-customer access → 404; staff bypass by role | unit | `mvn -q -Dtest='FdAccessPolicyTest' test` | ❌ W0 | ⬜ pending |
| 5-01-02 | 01 | 1 | FUND-02, HIST-02 | T-5-03 | Replay returns existing; hash mismatch → 409 | unit | `mvn -q -Dtest='FdIdempotencyGuardTest' test` | ❌ W0 | ⬜ pending |
| 5-01-03 | 01 | 1 | AUTH-02 | T-5-02 | 404/409/422 envelope mapping | unit | `mvn -q -Dtest='GlobalExceptionHandlerTest' test` | ❌ W0 | ⬜ pending |
| 5-02-01 | 02 | 2 | FUND-02 | T-5-03 | Funding replay safe; key-less repeat → 409 | integration | `mvn -q -Dtest='FdFundingIdempotencyIT' test` | ❌ W0 | ⬜ pending |
| 5-02-02 | 02 | 2 | AUTH-01, AUTH-02 | T-5-01 / T-5-02 | Actor-derived identity; 404 probe | integration | `mvn -q -Dtest='FdOwnershipContractTest' test` | ❌ W0 | ⬜ pending |
| 5-03-01 | 03 | 2 | HIST-02 | T-5-03 | Closure/accrual/maturity replay safe | integration | `mvn -q -Dtest='FdLifecycleIdempotencyIT' test` | ❌ W0 | ⬜ pending |
| 5-03-02 | 03 | 2 | AUTH-01, AUTH-02 | T-5-02 | History/premature-closure ownership + staff read | integration | `mvn -q -Dtest='FdOwnershipContractTest' test` | ❌ W0 | ⬜ pending |
| 5-04-01 | 04 | 2 | TEST-01 | — | Decimal/lifecycle/validation boundaries | unit | `mvn -q -Dtest='Fd*Test' test` | ✅ | ⬜ pending |
| 5-05-01 | 05 | 3 | TEST-02 | T-5-01 / T-5-03 | PG-backed flows, history, duplicates | integration | `mvn -q -Dtest='FdPhase5PostgresIT' test` | ❌ W0 | ⬜ pending |
| 5-05-02 | 05 | 3 | TEST-03 | — | Full existing suite green | regression | `mvn test` | ✅ | ⬜ pending |

*Status: ⬜ pending · ✅ green · ❌ red · ⚠️ flaky*

---

## Wave 0 Requirements

- [ ] `BACKEND/src/test/java/com/bank/fd/application/FdIdempotencyGuardTest.java` — stubs for FUND-02, HIST-02 guard semantics
- [ ] `BACKEND/src/test/java/com/bank/fd/application/FdAccessPolicyTest.java` — stubs for AUTH-01, AUTH-02 ownership matrix
- [ ] `BACKEND/src/test/java/com/bank/common/exception/GlobalExceptionHandlerTest.java` — stubs for D-11 status mapping

*Framework install: none — `BACKEND/pom.xml` already provides JUnit 5, MockMvc, spring-security-test, Testcontainers.*

---

## Manual-Only Verifications

| Behavior | Requirement | Why Manual | Test Instructions |
|----------|-------------|------------|-------------------|
| Live retry demo (same Idempotency-Key twice returns identical body) | FUND-02 | Human demo confidence | Book + fund via Swagger against local boot; replay fund with same key; compare bodies byte-for-byte |
| Live ownership probe (customer B → 404; staff → 200) | AUTH-02 | Human demo confidence | Fetch another customer's FD id as customer B (expect 404), then as BANK_OFFICER (expect 200) |

*All other phase behaviors have automated verification.*

---

## Validation Sign-Off

- [ ] All tasks have `<automated>` verify or Wave 0 dependencies
- [ ] Sampling continuity: no 3 consecutive tasks without automated verify
- [ ] Wave 0 covers all MISSING references
- [ ] No watch-mode flags
- [ ] Feedback latency < 300s
- [ ] `nyquist_compliant: true` set in frontmatter

**Approval:** pending
