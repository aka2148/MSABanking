# Phase 2: Product Booking and Funding - Context

**Gathered:** 2026-09-19
**Status:** Ready for planning

<domain>
## Phase Boundary

Implement the REST/API-first product selection, FD booking, and funding flow. An authenticated customer can view eligible FD offers, create a pending FD with server-resolved and locked terms, fund it once for the exact booked principal, and reach `ACTIVE`. Interest accrual, maturity, closure, broad ownership hardening, duplicate-action coverage, and Angular FD screens remain outside this phase.

</domain>

<decisions>
## Implementation Decisions

### API Surface
- **D-01:** Implement Phase 2 on the existing versioned `/fd/v1` surface rather than reviving the legacy `/api/fd` booking path.
- **D-02:** Add a customer-facing `GET /fd/v1/products` eligible-offer endpoint. Keep `/fd/v1/ref/*` for internal/reference-data semantics rather than using the raw reference route for customer offers.
- **D-03:** Use the existing `/fd/v1/accounts` account-creation contract for booking and add an explicit account funding operation under the same versioned surface.
- **D-04:** Preserve the existing contract and error conventions; do not implement a second compatibility API in this phase.

### Booking Terms and Offers
- **D-05:** Customer booking accepts tenure in months and a currency code. Do not use the legacy days-based request as the authoritative Phase 2 contract.
- **D-06:** Eligible offers expose complete booking choices: active product name/code, currency, minimum and maximum principal, supported tenure-month range, annual rate, compounding/payout terms, and the maturity-estimate basis needed by the REST demonstration.
- **D-07:** Use one server-owned annual rate per offer across its supported tenure range for the academic scope. Do not introduce a tenure-to-rate matrix in this phase.
- **D-08:** Product terms, rate, currency eligibility, amount limits, term limits, maturity date, and maturity estimate are resolved or calculated server-side. Client-supplied rate, status, customer identity, and derived maturity values are not authoritative.
- **D-09:** On booking, snapshot the resolved principal, rate, term, currency, dates, maturity estimate, applied rate reference, and policy version into the FD account so later product changes do not rewrite the booking.

### Funding and Activation
- **D-10:** Funding is an explicit operation, separate from booking and separate from merely changing a status.
- **D-11:** The funding amount must equal the booked principal exactly. Partial funding, overfunding, and multiple funding stages are not part of this phase.
- **D-12:** A successful funding operation records an internal funding transaction, then transitions the FD from `PENDING` to `ACTIVE` using the Phase 1 lifecycle boundary.
- **D-13:** The response must describe the internal demonstration record truthfully; it must not imply that an external payment rail was called or that money moved outside the application.
- **D-14:** The unfunded booking remains `PENDING` and must not be treated as active or eligible for later processing.

### Customer Association and Access
- **D-15:** Phase 2 booking is available to authenticated customers creating an FD for themselves. Staff/admin booking on behalf of another customer is not added here.
- **D-16:** The customer/holder identity is derived from the local authenticated actor and is not accepted as a caller-controlled request field.
- **D-17:** If the authenticated local customer has no FD-specific reference row, the booking flow resolves or creates the minimal FD user reference required by the existing schema. The client does not manage that synchronization.

### Frontend Boundary
- **D-18:** No Angular FD screens, routes, services, or visual changes are required for Phase 2. REST, Swagger, and Postman remain the acceptance surface; the existing Angular landing/auth/dashboard shell is preserved.

### the agent's Discretion
- Exact Java DTO, service, adapter, and repository class names, provided they follow the existing domain-package conventions.
- The concrete mapping from normalized product/rule/currency tables into `ProductTermsPort`, provided it remains server-authoritative and does not replace existing product services.
- Exact HTTP status/error-code details where the existing OpenAPI contract does not already fix them, provided validation, missing-resource, unsupported-term/currency, and invalid-transition failures remain structured and truthful.
- The exact idempotency implementation details may use the Phase 1 durable operation identity boundary; Phase 5 owns the full duplicate-action test and protection gate.

</decisions>

<canonical_refs>
## Canonical References

**Downstream agents MUST read these before planning or implementing.**

### Project and phase scope
- `.planning/PROJECT.md` - Project vision, academic constraints, API-first decision, and non-negotiables.
- `.planning/REQUIREMENTS.md` - PROD-01, BOOK-01 through BOOK-03, and FUND-01 acceptance requirements; v2/frontend boundaries.
- `.planning/ROADMAP.md` - Phase 2 goal, dependencies, and success criteria.
- `.planning/STATE.md` - Locked Phase 1 policy, actor, persistence, and schema decisions carried into Phase 2.

### Phase 1 foundation
- `.planning/phases/01-existing-fd-baseline/01-01-SUMMARY.md` - Lifecycle, money/date policy, actor, and product-terms boundary decisions.
- `.planning/phases/01-existing-fd-baseline/01-02-SUMMARY.md` - V6 schema, pending default, locking/versioning, and durable operation identity.
- `.planning/phases/01-existing-fd-baseline/01-03-SUMMARY.md` - Typed/authenticated `/fd/v1` boundary and PostgreSQL verification gate.
- `BACKEND/src/main/resources/db/migration/V6__fd_lifecycle_baseline.sql` - Existing additive lifecycle and idempotency schema baseline.

### API and persistence contract
- `BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml` - Existing `/fd/v1` paths, operation IDs, lifecycle statuses, and response contract.
- `BACKEND/src/main/resources/db/migration/V1__bankingtech_public_schema.sql` - Normalized product, currency, FD user reference, account, and transaction tables.
- `BACKEND/src/main/java/com/bank/fd/application/ProductTermsPort.java` - Server-authoritative product-term adapter boundary.
- `BACKEND/src/main/java/com/bank/fd/application/ProductTerms.java` - Validated resolved terms snapshot shape.
- `BACKEND/src/main/java/com/bank/fd/application/ProductTermsQuery.java` - Validated product-term lookup inputs.

### Existing implementation patterns
- `BACKEND/src/main/java/com/bank/product/entity/FdProduct.java` - Existing local product projection and limitation that rule values are transient.
- `BACKEND/src/main/java/com/bank/product/service/FdProductService.java` - Existing active-product service behavior to reuse or adapt.
- `BACKEND/src/main/java/com/bank/product/repository/FdProductRepository.java` - Existing product repository contract.
- `BACKEND/src/main/java/com/bank/fd/entity/FdAccount.java` - Pending-safe FD account mapping and lifecycle fields.
- `BACKEND/src/main/java/com/bank/fd/repository/FdAccountRepository.java` - Phase 1 account locking and owner-scoped repository contracts.
- `BACKEND/src/main/java/com/bank/fd/repository/FdOperationIdempotencyRepository.java` - Durable operation identity boundary.
- `BACKEND/src/main/java/com/bank/customer/service/CustomerService.java` - Local authenticated username to customer-profile resolution.

### Frontend boundary
- `FRONTEND/src/app/app.routes.ts` - Existing lazy Angular route surface preserved without Phase 2 FD routes.
- `FRONTEND/src/app/core/services/auth.service.ts` - Existing frontend authentication/API pattern; no FD service is required in this phase.

</canonical_refs>

<code_context>
## Existing Code Insights

### Reusable Assets
- `ProductTermsPort`, `ProductTermsQuery`, and `ProductTerms` provide the intended server-owned terms boundary, but no concrete normalized-table adapter exists yet.
- `FdProductService` and `FdProductRepository` already list active local FD product projections, but `FdProduct` stores amount, term, and rate fields as transient values; the planner must trace the normalized product/rule tables rather than trusting those fields blindly.
- `FdAccount` already maps the pending-safe V6 account schema, including principal, tenure months, currency, rate, maturity fields, row version, and primary holder reference.
- `CustomerService.getProfileEntityByUsername` resolves the authenticated local username to the customer profile used by current ownership code.
- `FdOperationIdempotencyRepository` and the V6 operation table provide a durable place for operation identity without adding distributed infrastructure.

### Established Patterns
- Backend code follows controller -> service -> repository/entity layering inside business-domain packages.
- Spring Security populates the authenticated username and roles from the local JWT; existing FD v1 routes are class-level authenticated.
- Flyway owns schema evolution and Hibernate validates mappings; new schema changes must be additive and PostgreSQL-compatible.
- Monetary values use `BigDecimal`; Phase 1 fixes the policy to Actual/365, UTC, two-decimal `HALF_UP`, and `academic-v1`.
- The existing legacy `/api/fd` service intentionally throws clear unavailable errors and should not become a second booking implementation.

### Integration Points
- Implement business behavior behind `FdV1ContractController` and its existing `/fd/v1/accounts` route rather than creating a replacement API family.
- Connect product lookup and booking to the normalized `fd_product_ref`, currency, rule, and outcome data through a concrete `ProductTermsPort` adapter.
- Resolve/create the authenticated customer's `fd_user_ref` before persisting `FdAccount.primaryHolderId`.
- Persist booking and funding through the existing FD account and transaction repositories, using the Phase 1 lifecycle state machine and row locking where needed.
- Keep `FRONTEND/` unchanged unless an API contract incompatibility is discovered; frontend FD delivery is explicitly deferred.

</code_context>

<specifics>
## Specific Ideas

- The intended demonstration path is REST/Swagger/Postman: list eligible offers, book, fund, and inspect the returned pending/active account state.
- Funding represents an internal academic demonstration record only; responses must not claim external settlement.
- The customer should never have to supply a holder/customer identifier when booking for themselves.

</specifics>

<deferred>
## Deferred Ideas

- Angular FD screens and dashboard integration remain deferred unless a later phase proves the API cannot be demonstrated adequately without them.
- Staff/admin booking on behalf of customers is deferred to the Phase 5 authorization work.
- Partial funding, overfunding, recurring funding, and tenure-specific rate matrices are outside the academic Phase 2 scope.
- Interest accrual, maturity processing, closure, settlement/history completion, and full duplicate/ownership test gates remain in Phases 3-5.

</deferred>

---

*Phase: 02-product-booking-and-funding*
*Context gathered: 2026-09-19*
