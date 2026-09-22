# Phase 4: Closure and Demonstrable API Workflow - Context

**Gathered:** 2026-09-22
**Status:** Ready for planning

<domain>
## Phase Boundary

Complete the FD lifecycle through the REST contract: normal closure of matured FDs, two-step premature closure applying the product's penalty rule (or a clear unsupported error), settlement/history records, and a runnable end-to-end demonstration. Duplicate-action hardening, broad ownership/role protection, and regression gates remain in Phase 5.

</domain>

<decisions>
## Implementation Decisions

### Premature-Closure Penalty
- **D-01:** The product's `CLOSURE_PENALTY` rule value is interpreted as a rate reduction in percentage points: premature payout interest = `FdInterestCalculator.interestForPeriod` over elapsed days at (contracted annual rate − penalty points), floored at zero. This mirrors the legacy 1% semantics in BigDecimal without touching the double-based legacy calculator.
- **D-02:** A product with no `CLOSURE_PENALTY` rule cannot be closed prematurely: the request is rejected with a clear unsupported-business-rule error and the FD stays open (never closed, no records written).

### Approval Flow
- **D-03:** Premature closure is two-step, reusing the existing controller shape: `POST .../premature-closure` creates a pending closure request, `POST .../premature-closure/{closureId}/approve` finalizes it (applies penalty, writes records, transitions to `PREMATURELY_CLOSED`).
- **D-04:** Both steps are performed by the same authenticated owning customer (self-service, matching booking/funding). No staff/admin approval role is added in this phase.

### History Shape
- **D-05:** One unified timeline endpoint returns time-ordered entries merging `fd_transactions` rows (funding, maturity, closure, settlement, penalty) with `fd_interest_accrual` rows, each entry carrying type, amount, date, and running balance where known.
- **D-06:** Full detail per entry (amounts, dates, types, running balance) so the demo can narrate funding through closure in a single read.

### Demo Artifact
- **D-07:** Deliver an importable Postman collection covering the full path (products → book → fund → accrue → mature → close/history → premature-closure variant) with example payloads and expected responses.

### Agent's Discretion
- Exact Java DTO, service, and repository class names, following existing domain-package conventions.
- Settlement breakdown fields and transaction-code choices, provided responses stay truthful internal records.
- Exact HTTP status/error-code details where the OpenAPI contract does not already fix them, provided invalid input, missing resources, invalid transitions, and unsupported premature closure remain structured and consistent.
- The Postman collection layout and variable scheme.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project and phase scope
- `.planning/PROJECT.md` - Project vision, academic constraints, API-first decision, and non-negotiables.
- `.planning/REQUIREMENTS.md` - LIFE-03, LIFE-04, HIST-01, API-01, API-02 acceptance requirements; v2/infra boundaries.
- `.planning/ROADMAP.md` - Phase 4 goal, dependencies, and success criteria.
- `.planning/STATE.md` - Locked policy, actor, persistence, and calculation decisions from Phases 1-3.
- `.planning/phases/02-product-booking-and-funding/02-CONTEXT.md` - API-surface, server-authoritative, truthful-response, and no-Angular decisions carried forward.

### Prior phase foundations
- `.planning/phases/01-existing-fd-baseline/01-01-SUMMARY.md` - Lifecycle state machine (CLOSE, PREMATURE_CLOSE) and money/date policy.
- `.planning/phases/03-interest-and-maturity-processing/03-01-SUMMARY.md` - Shared `FdInterestCalculator` and V8 transaction codes.
- `.planning/phases/03-interest-and-maturity-processing/03-02-SUMMARY.md` - Accrual/maturity services, `AccrualResponse` style, endpoint wiring pattern.
- `.planning/phases/03-interest-and-maturity-processing/03-REVIEW.md` - Advisory findings routed to Phase 4 (error-shape 400-vs-409, cumulative semantics).

### API and persistence contract
- `BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml` - Existing `/fd/v1` paths, operation IDs, and error schema.
- `BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java` - Placeholders this phase replaces: `closeAccount`, `requestPrematureClosure`, `getPrematureClosure`, `approvePrematureClosure`.
- `BACKEND/src/main/java/com/bank/fd/application/ProductTerms.java` - Server-owned terms snapshot including required `closurePenaltyRule`.
- `BACKEND/src/main/java/com/bank/fd/application/NormalizedProductTermsAdapter.java` - How `CLOSURE_PENALTY` rule outcomes resolve (missing/ambiguous handling).
- `BACKEND/src/main/java/com/bank/fd/entity/TransactionType.java` - Existing `PENALTY` code.
- `BACKEND/src/main/java/com/bank/fd/domain/FdInterestCalculator.java` - Shared BigDecimal math to reuse (never re-derive).
- `BACKEND/src/main/java/com/bank/calculator/service/FdCalculatorService.java` - Legacy double-based calculator: reference for penalty semantics ONLY, do not use for computation.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `FdInterestCalculator.interestForPeriod/maturityAmount` provides the single BigDecimal policy; premature interest reuses it with the penalized rate.
- `FdLifecycleStateMachine` already encodes `MATURED + CLOSE → CLOSED` and `ACTIVE + PREMATURE_CLOSE → PREMATURELY_CLOSED`.
- `TransactionType.PENALTY` exists; V8 established the idempotent transaction-code provisioning pattern.
- `ProductTerms.closurePenaltyRule()` carries the required server-owned penalty rule from normalized product/rule tables.

### Established Patterns
- Controller → service → repository layering; pessimistic lock + holder match + state-machine transition + persist + save (FundingService/MaturityService pattern).
- Explicit response DTOs, never serialized entities; truthful internal-record wording in responses.
- Flyway forward-only additive migrations; Testcontainers tests skip when Docker is unavailable.

### Integration Points
- Replace the four closure placeholder bodies in `FdV1ContractController` (keep paths/signatures); unrelated placeholders (payouts, notices, statements, events, sync) stay 501 for later phases.
- Persist closure/settlement/penalty rows through `fd_transactions` (+ accrual rows as needed) and expose them via the unified history read.

</code>

<specifics>
## Specific Ideas

- The demo path extends the Phase 2/3 flow: products → book → fund → accrue → mature → close → history, plus a premature-closure variant showing penalty math and the unsupported-rule error.
- Premature payout example to verify in tests: penalized rate applied to elapsed days, never negative interest, settlement = principal + penalized interest.

</specifics>

<deferred>
## Deferred Ideas

- Staff/admin approval of premature closures — deferred to Phase 5 authorization work (self-service only in this phase).
- General duplicate-action/idempotency test gate — Phase 5 owns it.
- Angular FD screens — remain deferred; Postman collection is the demo surface.

</deferred>

---

*Phase: 04-closure-and-demonstrable-api-workflow*
*Context gathered: 2026-09-22*
