# Requirements: Banking Lab

**Defined:** 2026-09-18
**Core Value:** Students must be able to demonstrate a complete, understandable fixed-deposit workflow from product selection through booking, funding, interest calculation, maturity, and closure using the existing application.

## v1 Requirements

Requirements for the academic FD demonstration. Each maps to exactly one roadmap phase.

### Product and Booking

- [x] **PROD-01**: An authenticated customer can view active FD products and the supported term, rate, amount, and maturity information needed to choose one.
- [x] **BOOK-01
**: An authenticated customer can book an FD associated with their customer/account identity using a valid product, principal, currency, and term.
- [x] **BOOK-02**: The server validates product eligibility, positive amount, supported term/currency, required fields, and customer association before saving a booking.
- [x] **BOOK-03
**: The booking stores the agreed principal, rate, term, open date, maturity date, and maturity estimate so later product changes do not unexpectedly rewrite the demonstration account.

### Funding and Lifecycle

- [x] **FUND-01
**: A newly booked FD starts in a pending/unfunded status and becomes active only after one valid funding operation for the required principal.
- [ ] **FUND-02**: Repeating a booking or funding request does not create a second FD or duplicate principal transaction for the same operation.
- [x] **LIFE-01**: The FD uses clear, validated lifecycle transitions for pending, active, matured, and closed states, and rejects invalid transitions.
- [ ] **LIFE-02**: The system can process maturity for an eligible active FD and transition it to matured using the stored term and maturity date.
- [ ] **LIFE-03**: The system supports normal closure of an eligible matured FD and records the resulting settlement/transaction outcome.
- [ ] **LIFE-04**: The system supports premature closure where the existing business rule is available, calculates the applicable interest/penalty with `BigDecimal`, and records the outcome; otherwise it returns a clear business error.

### Interest and History

- [ ] **CALC-01**: Interest and maturity calculations use `BigDecimal`/decimal arithmetic with explicit scale and rounding rather than binary floating-point calculations.
- [ ] **CALC-02**: The same calculation rules are reused consistently for estimates, accrual, maturity, and closure within the supported academic product scope.
- [ ] **HIST-01**: The system records funding, interest/accrual, maturity, closure, and settlement transaction/history records linked to the FD account.
- [ ] **HIST-02**: Repeating an accrual, maturity, or closure operation does not create an incorrect duplicate financial/history record.

### REST API and Persistence

- [ ] **API-01**: REST endpoints expose product lookup, booking, funding, account status, interest/maturity processing, closure, and transaction/history retrieval with understandable request and response DTOs.
- [ ] **API-02**: Invalid input, missing resources, unauthorized access, invalid lifecycle transitions, and unsupported premature-closure rules return consistent error responses.
- [x] **DATA-01**: The implementation reuses and extends the existing Flyway/PostgreSQL schema only where necessary, with database constraints for required relationships, valid amounts, statuses, and duplicate prevention.
- [x] **DATA-02**: Existing repositories, entities, services, calculators, authentication mechanisms, and API paths are reused or adapted before new replacements are introduced.

### Authentication, Authorization, and Tests

- [ ] **AUTH-01**: FD endpoints require authentication where appropriate and derive the customer identity from the authenticated user rather than trusting arbitrary request identifiers.
- [ ] **AUTH-02**: A customer cannot read or mutate another customer's FD by changing an account identifier, while permitted staff/admin operations remain available through existing role rules.
- [ ] **TEST-01**: Unit tests cover interest, maturity, penalty, validation, and lifecycle transition rules, including important boundary and invalid cases.
- [ ] **TEST-02**: Integration tests cover Flyway/PostgreSQL persistence, REST booking/funding/lifecycle flows, ownership authorization, transaction history, and duplicate-action protection.
- [ ] **TEST-03**: Existing authentication, customer, product, calculator, reporting, migration, and other relevant behavior remains passing after the FD changes.

## v2 Requirements

Deferred until the core academic workflow is stable.

### Extended Banking Features

- **EXT-01**: Recurring interest payouts, partial withdrawals, FD splits, auto-renewal, and sweep transfers.
- **EXT-02**: Full joint-holder, nominee, deceased-claim, and mandate workflows.
- **EXT-03**: Statements, notices, downloadable documents, external events, and broad reference-data synchronization.
- **EXT-04**: A complete Angular FD user interface beyond the existing authentication/dashboard shell, if REST/Swagger demonstration is sufficient for v1.

### Production Infrastructure

- **INFRA-01**: Real payment-rail integration with reversals, returns, provider reconciliation, and external settlement guarantees.
- **INFRA-02**: Distributed locks, workflow engines, event/outbox infrastructure, elaborate audit/reconciliation engines, and multi-instance operational orchestration.
- **INFRA-03**: Jurisdiction-specific tax reporting, regulatory certificates, multi-currency/non-resident product variants, and enterprise compliance workflows.

## Out of Scope

Explicitly excluded from this college lab milestone.

| Feature | Reason |
|---------|--------|
| Major rewrite of working authentication or banking modules | The goal is continuation and reuse of the existing application. |
| Enterprise-grade financial infrastructure | Distributed systems, payment rails, workflow engines, and complex reconciliation are disproportionate to the lab objective. |
| Generic configurable rule engine | A small, clear set of existing/product-specific rules is easier to implement, explain, and test. |
| Client-controlled customer, rate, status, penalty, or settlement values | These would undermine correctness and basic ownership/security requirements. |
| Replacing the current database/API model without a demonstrated need | Existing schema, migrations, and API behavior are project assets to preserve. |

## Traceability

Which phases cover which requirements. Populated during roadmap creation.

| Requirement | Phase | Status |
|-------------|-------|--------|
| PROD-01 | Phase 2 | Complete |
| BOOK-01 | Phase 2 | Pending |
| BOOK-02 | Phase 2 | Complete |
| BOOK-03 | Phase 2 | Pending |
| FUND-01 | Phase 2 | Complete |
| FUND-02 | Phase 5 | Pending |
| LIFE-01 | Phase 1 | Complete |
| LIFE-02 | Phase 3 | Pending |
| LIFE-03 | Phase 4 | Pending |
| LIFE-04 | Phase 4 | Pending |
| CALC-01 | Phase 3 | Pending |
| CALC-02 | Phase 3 | Pending |
| HIST-01 | Phase 4 | Pending |
| HIST-02 | Phase 5 | Pending |
| API-01 | Phase 4 | Pending |
| API-02 | Phase 4 | Pending |
| DATA-01 | Phase 1 | Complete |
| DATA-02 | Phase 1 | Complete |
| AUTH-01 | Phase 5 | Pending |
| AUTH-02 | Phase 5 | Pending |
| TEST-01 | Phase 5 | Pending |
| TEST-02 | Phase 5 | Pending |
| TEST-03 | Phase 5 | Pending |

**Coverage:**
- v1 requirements: 23 total
- Mapped to phases: 23
- Unmapped: 0 ✓

---
*Requirements defined: 2026-09-18*
*Last updated: 2026-09-18 after academic-scope clarification*
