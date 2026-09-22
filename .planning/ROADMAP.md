# Roadmap: Banking Lab

## Overview

This milestone turns the existing Spring Boot/Angular Banking Lab into a small, understandable fixed-deposit demonstration without replacing working modules. The work stays REST/API-first, reuses the existing `/fd/v1`, legacy FD, authentication, entity, repository, calculator, Flyway, and PostgreSQL patterns, and delivers one demonstrable path: choose an eligible product, book and fund an FD, accrue and mature it, close it, and inspect the resulting history.

The roadmap deliberately excludes production banking infrastructure, real payment rails, workflow engines, distributed coordination, broad authentication rewrites, and a mandatory Angular FD interface.

## Phases

**Phase Numbering:**
- Integer phases (1, 2, 3): Planned milestone work
- Decimal phases (2.1, 2.2): Urgent insertions (marked with INSERTED)

- [x] **Phase 1: Existing FD Baseline** - Align the existing FD contract and persistence foundation around simple, explicit lifecycle rules.
- [ ] **Phase 2: Product Booking and Funding** - Let an authenticated customer select eligible terms, book an FD, fund it, and reach active status.
- [ ] **Phase 3: Interest and Maturity Processing** - Apply consistent decimal calculations, store accruals, and move eligible active FDs to matured.
- [ ] **Phase 4: Closure and Demonstrable API Workflow** - Complete normal/premature closure, settlement/history responses, and the end-to-end REST demonstration.
- [ ] **Phase 5: Protection, Tests, and Regression Gate** - Add basic ownership/role protection, duplicate safety, focused tests, and regression verification.

## Phase Details

### Phase 1: Existing FD Baseline
**Goal**: The existing application has an understandable FD foundation in which the current schema and API surfaces can support a small, explicit lifecycle without breaking established functionality.
**Depends on**: Nothing (first phase)
**Requirements**: LIFE-01, DATA-01, DATA-02
**Success Criteria** (what must be TRUE):
  1. FD accounts accept only the supported lifecycle transitions, including pending, active, matured, and closed, and invalid transitions are rejected.
  2. The existing Flyway/PostgreSQL schema starts and validates cleanly, with only necessary additive preparation for required relationships, statuses, amounts, and duplicate-sensitive records.
  3. FD DTOs and controller boundaries align with the existing `/fd/v1` and legacy endpoints while application services, entities, repositories, calculators, authentication, and migrations remain the reused implementation base.
  4. Existing authentication, customer, product, calculator, reporting, and other established API behavior remains available for the later FD work to build on.
**Plans**: 3 plans
Plans:
- [x] 01-01-PLAN.md — Establish the explicit FD lifecycle and versioned policy/time contracts
- [x] 01-02-PLAN.md — Add the forward-only PostgreSQL lifecycle baseline and durable operation identity
- [x] 01-03-PLAN.md — Type the `/fd/v1` account boundary and verify Flyway against PostgreSQL

### Phase 2: Product Booking and Funding
**Goal**: An authenticated customer can select an eligible FD offer, create a correctly associated pending account with locked terms, and fund it once to become active.
**Depends on**: Phase 1
**Requirements**: PROD-01, BOOK-01, BOOK-02, BOOK-03, FUND-01
**Success Criteria** (what must be TRUE):
  1. A customer can retrieve active, eligible products with supported terms, rates, amount limits, currency, and maturity information needed to choose an FD.
  2. A valid booking creates a customer-associated FD in pending/unfunded status and returns its principal, rate, term, dates, maturity estimate, and funding status.
  3. Invalid product, amount, currency, term, required-field, or customer-association combinations are rejected before an account is saved.
  4. The booked principal, rate, term, dates, and maturity estimate remain the agreed values even if the source product is later changed.
  5. One valid funding operation for the required principal records the funding and changes the FD from pending to active; an unfunded booking does not accrue or appear active.
**Plans**: 3 plans
Plans:
- [x] 02-01-PLAN.md — Establish authoritative product terms, booking DTOs, and reference persistence
- [x] 02-02-PLAN.md — Implement eligible offers and authenticated pending FD booking
- [x] 02-03-PLAN.md — Implement exact-principal funding and pending-to-active flow

### Phase 3: Interest and Maturity Processing
**Goal**: An active FD can be processed with one consistent, explainable money policy through interest accrual and maturity.
**Depends on**: Phase 2
**Requirements**: CALC-01, CALC-02, LIFE-02
**Success Criteria** (what must be TRUE):
  1. Interest and maturity amounts are reproducible with `BigDecimal`, explicit currency scale, and explicit rounding rather than binary floating-point arithmetic.
  2. The same documented calculation rules produce compatible estimates, accrual amounts, maturity values, and later closure inputs for the supported lab product.
  3. An eligible active FD can accrue for its supported business date(s), and the resulting accrual information can be read back for demonstration.
  4. When the stored maturity date is reached, processing brings the calculation through the required end date and transitions the FD to matured; pending or already-closed FDs are not matured.
**Plans**: 2 plans
Plans:
- [x] 03-01-PLAN.md — Shared BigDecimal interest calculator, accrual persistence, and V8 transaction codes
- [x] 03-02-PLAN.md — Accrual and maturity services with /fd/v1 endpoint wiring

### Phase 4: Closure and Demonstrable API Workflow
**Goal**: Students can demonstrate the complete FD lifecycle through the REST contract, including truthful normal or supported premature closure and its financial history.
**Depends on**: Phase 3
**Requirements**: LIFE-03, LIFE-04, HIST-01, API-01, API-02
**Success Criteria** (what must be TRUE):
  1. REST/Swagger/Postman users can follow product lookup, booking, funding, status, interest/maturity processing, closure, and history retrieval with understandable request and response DTOs.
  2. A matured FD can be closed normally after final calculation, with a settlement/transaction outcome recorded and visible through the history API.
  3. A premature-closure request either applies the existing available rule with `BigDecimal` interest/penalty and records the outcome, or returns a clear unsupported business error without closing the FD.
  4. Invalid input, missing resources, unauthorized access, invalid transitions, and unsupported premature closure produce consistent REST error responses rather than placeholder success.
  5. The account history demonstrates the funding, accrual, maturity, closure, and settlement records linked to the FD without claiming an external payment rail succeeded when only an internal record exists.
**Plans**: 4 plans
Plans:
- [x] 04-01-PLAN.md — V9 closure/history migration, premature-closure entity, and BigDecimal penalty policy
- [x] 04-02-PLAN.md — Normal closure, two-step premature-closure, and unified history services
- [x] 04-03-PLAN.md — Closure/status/history endpoint wiring, consistent errors, OpenAPI, contract tests
- [x] 04-04-PLAN.md — Postman end-to-end demo collection with contract-consistency test

### Phase 5: Protection, Tests, and Regression Gate
**Goal**: The demonstrable FD workflow is safe for ordinary retries and basic customer/staff use, and evidence exists that it did not regress the existing college lab application.
**Depends on**: Phase 4
**Requirements**: FUND-02, HIST-02, AUTH-01, AUTH-02, TEST-01, TEST-02, TEST-03
**Success Criteria** (what must be TRUE):
  1. Repeating booking, funding, accrual, maturity, or closure actions for the same operation returns the existing result or a clear conflict without creating duplicate FD, principal, accrual, settlement, or history effects.
  2. FD endpoints derive customer identity from the authenticated actor; a customer cannot read or mutate another customer's FD by changing an identifier, while permitted staff/admin operations still work through existing role rules.
  3. Focused unit tests cover decimal interest, maturity, penalty/unsupported-premature cases, validation, lifecycle boundaries, and invalid transitions.
  4. PostgreSQL-backed integration tests cover Flyway persistence, REST booking/funding/lifecycle flows, ownership failures, transaction history, and duplicate-action protection.
  5. Existing authentication, customer, product, calculator, reporting, migration, and relevant non-FD regression tests remain passing after the FD changes.
**Plans**: 5 plans
Plans:
- [ ] 05-01-PLAN.md — Guard foundation: idempotency guard, ownership policy, error mapping, CORS
- [ ] 05-02-PLAN.md — Wire duplicate safety and ownership into booking/funding/accrual/maturity
- [ ] 05-03-PLAN.md — Wire closure/premature/history plus /fd/v1 key threading and staff roles
- [ ] 05-04-PLAN.md — Unit tests for math, lifecycle, guard, policy, and error mapping
- [ ] 05-05-PLAN.md — PostgreSQL/MockMvc integration proof and full-suite regression gate

## Progress

**Execution Order:**
Phases execute in numeric order: 1 → 2 → 3 → 4 → 5

| Phase | Plans Complete | Status | Completed |
|-------|----------------|--------|-----------|
| 1. Existing FD Baseline | 3/3 | Complete | 2026-09-18 |
| 2. Product Booking and Funding | 3/3 | Complete | 2026-09-19 |
| 3. Interest and Maturity Processing | 2/2 | Complete | 2026-09-22 |
| 4. Closure and Demonstrable API Workflow | 4/4 | Complete | 2026-09-22 |
| 5. Protection, Tests, and Regression Gate | 0/TBD | Not started | - |

## Coverage

All 23 v1 requirements map to exactly one phase. No v1 requirements are orphaned or duplicated.
