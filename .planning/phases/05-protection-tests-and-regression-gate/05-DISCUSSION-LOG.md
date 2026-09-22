# Phase 5: Protection, Tests, and Regression Gate - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in CONTEXT.md — this log preserves the alternatives considered.

**Date:** 2026-09-22
**Phase:** 5-protection-tests-and-regression-gate
**Areas discussed:** Duplicate-action handling, Ownership and staff roles, Test depth and evidence, Error contract consistency

---

## Duplicate-action handling

| Option | Description | Selected |
|--------|-------------|----------|
| Return existing result | Replay returns the original success response; safe retries, no duplicates | ✓ |
| 409 Conflict on replay | First succeeds, repeats get a clear conflict error pointing at the existing record | |
| You decide | Pick whichever fits the existing idempotency table best | |

**User's choice:** Return existing result
**Notes:** Safe-retry semantics; key-less repeat of same logical action still a 409 referencing the existing record.

| Option | Description | Selected |
|--------|-------------|----------|
| All five gated | Booking, funding, accrual, maturity, closure all honor the key; key-less repeat is a 409 | ✓ |
| Money-moving only | Only booking/funding/closure need keys; accrual/maturity recompute safely | |
| You decide | Match whatever the Phase 1 identity table already supports | |

**User's choice:** All five gated

---

## Ownership and staff roles

| Option | Description | Selected |
|--------|-------------|----------|
| 404 Not Found | Hides existence — safer against ID enumeration | ✓ |
| 403 Forbidden | Reveals existence but denies access; clearer for debugging | |
| You decide | Follow whatever existing customer endpoints already do | |

**User's choice:** 404 Not Found

| Option | Description | Selected |
|--------|-------------|----------|
| Read + mutate via roles | Staff/admin can read any FD and perform permitted ops; customers self-service only | ✓ |
| Read-only for staff | Staff can view any FD but only the owning customer can mutate | |
| You decide | Keep whatever SecurityConfig already permits | |

**User's choice:** Read + mutate via roles

---

## Test depth and evidence

| Option | Description | Selected |
|--------|-------------|----------|
| Unit + MockMvc + PG Testcontainers | Unit for math/lifecycle, MockMvc for REST+ownership, Testcontainers PG for Flyway/persistence/duplicates | ✓ |
| Unit + MockMvc only | Skip live-PG tests; rely on faster harness | |
| You decide | Fit the split to what already runs green | |

**User's choice:** Unit + MockMvc + PG Testcontainers

| Option | Description | Selected |
|--------|-------------|----------|
| Full backend suite green | All existing auth/customer/product/calculator/reporting/migration tests pass plus new FD tests | ✓ |
| FD + auth scope only | Only FD, auth, and touched-module tests must pass | |
| You decide | Gate on whatever CI already enforces | |

**User's choice:** Full backend suite green

---

## Error contract consistency

| Option | Description | Selected |
|--------|-------------|----------|
| Distinct codes, one envelope | Same error body; duplicates→409 with ref, bad transitions→409/422, unsupported premature→422 | ✓ |
| You decide | Match the existing GlobalExceptionHandler style | |
| Minimal change | Keep per-endpoint errors; only add conflict handling where missing | |

**User's choice:** Distinct codes, one envelope

---

## Agent's Discretion

None — user picked concrete options for every question. Header names, exact code strings, and test file layout left to planner within the locked decisions.

## Deferred Ideas

None — discussion stayed within phase scope.
