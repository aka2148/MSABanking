# Phase 2: Product Booking and Funding - Discussion Log

> **Audit trail only.** Do not use as input to planning, research, or execution agents.
> Decisions are captured in `02-CONTEXT.md`; this log preserves the alternatives considered.

**Date:** 2026-09-19
**Phase:** 02-product-booking-and-funding
**Areas discussed:** API surface, funding, booking terms and offers, customer association, frontend boundary

---

## API Surface

| Option | Description | Selected |
|--------|-------------|----------|
| Existing `/fd/v1` | Implement the existing versioned contract and add only the missing funding operation; keeps the REST demonstration consistent with Phase 1. | Yes |
| Legacy `/api/fd` | Revive the older controller/service path, which currently reports that booking is unavailable after the schema migration. | |
| Both surfaces | Implement both APIs as compatibility paths, increasing duplicate behavior to maintain. | |

**User's choice:** Existing `/fd/v1`.

| Offer route | Description | Selected |
|-------------|-------------|----------|
| `GET /fd/v1/products` | Clear customer-facing offer endpoint; reserve `/ref/*` for internal reference data. | Yes |
| `GET /fd/v1/ref/products` | Reuse the existing placeholder reference route for customer offers. | |
| Both routes | Expose both paths for compatibility. | |

**User's choice:** `GET /fd/v1/products`.

---

## Funding

| Option | Description | Selected |
|--------|-------------|----------|
| Explicit fund endpoint | A pending booking is funded through a dedicated operation with the exact principal; success records funding and transitions it to ACTIVE. | Yes |
| Activate means funded | Existing `/accounts/{id}/activate` represents the funding action; no separate fund route. | |
| Staff-recorded funding | Customers book only; bank staff records funding and activates the account. | |

**User's choice:** Explicit fund endpoint.

| Fund amount | Description | Selected |
|-------------|-------------|----------|
| Exact booked principal | Funding must equal the locked principal; no partial or overfunded states. | Yes |
| Partial funding | Multiple funding steps before activation. | |
| Overfunding | Accept a positive amount and recalculate the account. | |

**User's choice:** Exact booked principal.

| Fund result | Description | Selected |
|-------------|-------------|----------|
| Internal record only | Create the funding transaction, mark the FD ACTIVE, and state that no external payment rail was called. | Yes |
| External-payment placeholder | Return a payment-pending result despite having no payment provider. | |
| No transaction yet | Change status without recording funding history. | |

**User's choice:** Internal record only.

---

## Booking Terms and Offers

| Option | Description | Selected |
|--------|-------------|----------|
| Months plus currency code | Expose supported tenure months and currency codes from server-owned terms; aligns with the normalized account model. | Yes |
| Days plus currency ID | Keep the legacy request shape and convert supported day values into months. | |
| Offer selection only | Booking accepts an offer identifier instead of separate term/rate fields. | |

**User's choice:** Months plus currency code.

| Rate model | Description | Selected |
|------------|-------------|----------|
| One rate per offer | Each offer exposes one server-owned annual rate with a supported tenure range; booking snapshots the selected rate. | Yes |
| Rate per tenure | Each supported month value has its own rate and requires a rate matrix. | |
| Client-provided rate | The request supplies the rate. | |

**User's choice:** One rate per offer.

| Offer fields | Description | Selected |
|--------------|-------------|----------|
| Complete booking choices | Product name/code, currency, amount range, month range, annual rate, compounding/payout terms, and maturity-estimate basis. | Yes |
| Minimal catalog | Product name, currency, and rate only. | |
| Raw database references | Expose internal product/rule/currency identifiers for the client to assemble. | |

**User's choice:** Complete booking choices.

---

## Customer Association

| Option | Description | Selected |
|--------|-------------|----------|
| Authenticated customer only | Derive identity from local authentication; staff/admin booking for another customer is deferred. | Yes |
| Customer and staff | Permit explicit staff target-customer booking. | |
| Staff only | Customers view offers but staff create and fund bookings. | |

**User's choice:** Authenticated customer only.

| FD customer reference | Description | Selected |
|-----------------------|-------------|----------|
| Resolve or create reference | Find or create the minimal FD user reference from the authenticated local customer; client supplies no holder ID. | Yes |
| Require existing reference | Reject booking until a separate synchronization has created the FD reference. | |
| Use customers directly | Redesign the FD account relationship to point directly at the customer table. | |

**User's choice:** Resolve or create reference.

---

## Frontend Boundary

The user confirmed continuation with the API-first approach. No Angular FD screens or frontend service changes are required for Phase 2; the existing shell remains unchanged and REST/Swagger/Postman is the acceptance surface.

## the agent's Discretion

- Exact class names and adapter implementation.
- Exact structured error details where the OpenAPI contract is not prescriptive.
- Whether the Phase 1 operation identity repository is used immediately for retry handling; full duplicate-action protection is assigned to Phase 5.

## Deferred Ideas

- Angular FD screens.
- Staff/admin booking on behalf of customers.
- Partial/overfunding and tenure-specific rate matrices.
- Interest, maturity, closure, settlement, and the Phase 5 security/regression gate.
