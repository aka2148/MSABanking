# Phase 5: Protection, Tests, and Regression Gate - Context

**Gathered:** 2026-09-22
**Status:** Ready for planning

<domain>
## Phase Boundary

Harden the complete FD workflow (book → fund → accrue → mature → close/history) so it is safe for ordinary retries and basic customer/staff use, with test evidence that the existing college lab application did not regress. New capabilities (renewal, partial withdrawals, payment rails, Angular FD screens) belong in other phases.

</domain>

<decisions>
## Implementation Decisions

### Duplicate-action handling
- **D-01:** Repeating booking, funding, accrual, maturity, or closure for the same operation returns the existing result (same success body), not a second record. Retries are safe by default.
- **D-02:** All five operations (booking, funding, accrual, maturity, closure — including both premature-closure steps) honor the Phase 1 durable operation identity (`Idempotency-Key` or equivalent operation identity). A key-less repeat of the same logical action is rejected as a 409 conflict referencing the existing record.
- **D-03:** No duplicate FD, principal transaction, accrual row, settlement row, or history entry may result from a replay. Planner must reuse `FdOperationIdempotency` / `FdOperationIdempotencyRepository` rather than inventing a parallel mechanism.

### Ownership and staff roles
- **D-04:** Every FD endpoint derives customer identity from the authenticated actor (local JWT username → `fd_user_ref` via the existing `FdActorResolver` pattern). Caller-supplied customer/holder identifiers are never authoritative.
- **D-05:** A customer requesting another customer's FD by changing an identifier gets `404 Not Found` (not 403), so existence is not leaked through ID enumeration.
- **D-06:** Permitted staff/admin operations keep working through the existing role rules (`SecurityConfig`): staff/admin can read any FD and perform permitted mutations; customers are self-service only. Do not redesign roles — preserve and verify current behavior.

### Test depth and evidence
- **D-07:** Three-layer split: unit tests for decimal math/lifecycle/validation, MockMvc tests for REST flows + ownership failures, Testcontainers-backed PostgreSQL tests for Flyway persistence, history, and duplicate-action protection (following the existing `Fd*SchemaTest` / `FdBookingFundingIntegrationTest` pattern; skip only when Docker is unavailable).
- **D-08:** Unit scope (TEST-01): interest, maturity, penalty/unsupported-premature cases, validation, lifecycle boundaries, invalid transitions, plus boundary/invalid cases.
- **D-09:** Integration scope (TEST-02): Flyway/PostgreSQL persistence, REST booking→funding→lifecycle flows, ownership failures (404 on cross-customer access), transaction history, and duplicate-action protection for all five operations.
- **D-10:** Regression gate (TEST-03): the full existing backend suite (authentication, customer, product, calculator, reporting, migration, non-FD behavior) must remain passing after the FD changes — not just FD/auth-scoped tests.

### Error contract consistency
- **D-11:** One error envelope (`ApiErrorResponse` / `GlobalExceptionHandler` style) across booking through closure/history: invalid input → 400, unauthenticated → 401, cross-customer access → 404 (per D-05), invalid transitions and key-less duplicate repeats → 409 with a reference to the existing record, unsupported premature-closure rule → 422 business error without closing the FD.
- **D-12:** Error bodies stay truthful internal records: never claim an external payment rail succeeded; duplicate replays reference the already-recorded result.

### Agent's Discretion
- Exact header name (`Idempotency-Key` vs existing operation-identity field), conflict/error-code strings where the OpenAPI contract does not fix them, and test class/file layout — provided D-01 through D-12 hold.
- Exact staff-role names and matcher wiring — reuse whatever `SecurityConfig` already enforces.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project and phase scope
- `.planning/PROJECT.md` — Project vision, academic constraints, API-first decision, non-negotiables.
- `.planning/REQUIREMENTS.md` — FUND-02, HIST-02, AUTH-01, AUTH-02, TEST-01 through TEST-03 acceptance requirements; v2/infra boundaries.
- `.planning/ROADMAP.md` — Phase 5 goal, dependencies, and success criteria.
- `.planning/STATE.md` — Locked policy, actor, persistence, and calculation decisions from Phases 1–4.
- `.planning/phases/02-product-booking-and-funding/02-CONTEXT.md` — API-surface, server-authoritative, truthful-response, and no-Angular decisions carried forward.
- `.planning/phases/04-closure-and-demonstrable-api-workflow/04-CONTEXT.md` — Penalty semantics, two-step self-service premature closure, unified history, Postman demo surface.

### Prior phase foundations
- `.planning/phases/01-existing-fd-baseline/01-01-SUMMARY.md` — Lifecycle state machine and money/date policy.
- `.planning/phases/01-existing-fd-baseline/01-02-SUMMARY.md` — V6 schema, pending default, locking/versioning, durable operation identity.
- `.planning/phases/03-interest-and-maturity-processing/03-01-SUMMARY.md` — Shared `FdInterestCalculator` and V8 transaction codes.

### API, persistence, and security contract
- `BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml` — Existing `/fd/v1` paths, operation IDs, and error schema.
- `BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java` — Versioned FD boundary where ownership/duplicate guards attach.
- `BACKEND/src/main/java/com/bank/fd/api/dto/ApiErrorResponse.java` — Existing error envelope to extend consistently.
- `BACKEND/src/main/java/com/bank/fd/entity/FdOperationIdempotency.java` — Durable operation-identity row to reuse.
- `BACKEND/src/main/java/com/bank/fd/repository/FdOperationIdempotencyRepository.java` — Existing identity lookup boundary.
- `BACKEND/src/main/java/com/bank/fd/domain/FdLifecycleStateMachine.java` — Explicit transitions the duplicate/transition errors reference.
- `BACKEND/src/main/java/com/bank/fd/domain/FdInterestCalculator.java` — Shared BigDecimal math under unit-test scope.
- `BACKEND/src/main/java/com/bank/fd/application/FdActorResolver.java` — Authenticated-actor → `fd_user_ref` resolution to enforce everywhere.
- `BACKEND/src/main/java/com/bank/security/config/SecurityConfig.java` — Existing authenticated-route and role rules to preserve.
- `BACKEND/src/main/java/com/bank/common/exception/GlobalExceptionHandler.java` — Existing exception-to-status mapping to extend.

### Existing test patterns
- `BACKEND/src/test/java/com/bank/SecurityFlowTest.java` — MockMvc authentication/security flow pattern.
- `BACKEND/src/test/java/com/bank/fd/FdBookingFundingIntegrationTest.java` — Booking/funding integration pattern to extend.
- `BACKEND/src/test/java/com/bank/fd/FdPostgresSchemaTest.java` — Testcontainers PostgreSQL gate pattern (skip when Docker unavailable).
- `BACKEND/src/test/java/com/bank/fd/api/FdV1ClosureHistoryContractTest.java` — Contract-test style for closure/history.
- `BACKEND/src/test/java/com/bank/fd/domain/FdInterestCalculatorTest.java` — Unit-test style for decimal math.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `FdOperationIdempotency` + `FdOperationIdempotencyRepository`: durable operation identity from Phase 1 — the planner's duplicate-safety base, no new infrastructure.
- `FdActorResolver` / `FdActorContext`: authenticated-username → `fd_user_ref` resolution used by booking/funding — extend to accrual/maturity/closure/history reads.
- `FdLifecycleStateMachine` + `FdLifecycleCommand`: explicit transitions (`FUND`, `CLOSE`, `PREMATURE_CLOSE`) that already reject invalid moves.
- `ApiErrorResponse` + `GlobalExceptionHandler`: existing error envelope/mapping to extend for 404-vs-403 and 409/422 cases.
- `FdV1BookingContractTest`, `FdV1ClosureHistoryContractTest`, `FdBookingFundingIntegrationTest`, `FdPostgresSchemaTest`: established MockMvc/Testcontainers patterns to copy.

### Established Patterns
- Controller → application-service → repository layering; pessimistic lock + holder match + state-machine transition + persist (FundingService/MaturityService pattern).
- Explicit response DTOs, never serialized entities; truthful internal-record wording in responses.
- Flyway forward-only additive migrations; Hibernate validates mappings; PostgreSQL 16 Alpine Testcontainers gate with generated credentials.
- `BigDecimal` academic-v1 policy (Actual/365, UTC, scale 2, HALF_UP) shared via `FdInterestCalculator`.

### Integration Points
- Attach ownership checks (holder match → 404) and idempotency lookups inside the application services behind `FdV1ContractController` (`AccountBookingService`, `FundingService`, `AccrualService`, `MaturityService`, `ClosureService`, `PrematureClosureService`, `HistoryService`).
- Extend `GlobalExceptionHandler` for the 404/409/422 mappings; keep unrelated 501 placeholders untouched.
- Add tests beside existing suites under `BACKEND/src/test/java/com/bank/fd/...`; keep `FRONTEND/` unchanged.

</code_context>

<specifics>
## Specific Ideas

- Retry story for the demo: booking twice with the same key returns the same pending FD; funding twice does not double the principal; closing twice returns the same settlement.
- Ownership probe for the demo: customer B fetching customer A's FD id gets 404; staff role can still read it.
- Full `mvn test` (or the repo's backend suite) green is the exit gate — not just the new FD tests.

</specifics>

<deferred>
## Deferred Ideas

None — discussion stayed within phase scope.

</deferred>

---

*Phase: 05-protection-tests-and-regression-gate*
*Context gathered: 2026-09-22*
