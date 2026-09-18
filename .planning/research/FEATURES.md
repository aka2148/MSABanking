# Feature Landscape

**Domain:** Fixed-deposit banking lifecycle (backend/API-first milestone)
**Researched:** 2026-09-18
**Overall confidence:** HIGH for the repository boundary; MEDIUM for jurisdiction-specific regulatory behavior

## Product Position

The smallest reliable fixed-deposit product is not a calculator or a CRUD account record. It is a controlled financial contract: an eligible customer selects an active offer, the bank accepts and funds a principal, the system preserves the contracted terms, accrues interest deterministically, moves the deposit through valid states, and records one auditable settlement at maturity or approved closure.

For this milestone, table stakes means behavior required for a trustworthy core lifecycle, not every feature exposed by the broad `/fd/v1` contract. The contract includes holders, notices, statements, reference-data synchronization, recurring payouts, renewal, events, and rule outcomes; those surfaces should not automatically become delivery commitments. Existing project scope explicitly defers statements, notices, event publishing, holders, and broader deposit/withdrawal variants.

The repository appears India-oriented (`IFSC`, `TDS`, and RBI-compatible terms), but the jurisdiction and licensed-bank operating model are not formally locked in the project context. RBI-specific notes below are therefore implementation constraints only if India is the applicable jurisdiction. Regulatory policy must be configuration- and policy-owned, never inferred from a customer request.

## Table Stakes

Missing any of these makes the core lifecycle unsafe or materially incomplete.

### 1. Product and rate selection

| Observable behavior | Complexity | Dependencies | Edge cases / correctness implication |
|---|---:|---|---|
| List only products that are active and effective for the requested booking date, currency, customer type, amount band, and tenure; do not offer `DRAFT`, expired, or future offers as bookable. | Medium | Product status/effective dates; currency and rule data; customer eligibility | The schema stores product effective dates as `VARCHAR`, so date parsing and invalid ranges need validation before they drive a financial contract. An inactive product must be rejected even if its numeric ID is valid. |
| Resolve the applicable rate/rule outcome server-side from the selected product, amount, tenure, currency, and eligibility. Return the selected rate, compounding basis, payout frequency, minimum/maximum amount, and maturity estimate for confirmation. | High | `product_rule`, `rule_condition`, `rule_outcome`, currency precision; calculator logic | A caller may send `interest_rate` and `applied_rate_outcome_id` in `CreateAccountRequest`, but these are claims to validate, not authority. Reject a mismatch rather than silently booking a different rate. |
| Show the customer the contract terms before booking: principal, currency, annual/simple rate representation, tenure, open date, maturity date, estimated maturity amount, compounding, payout, premature-withdrawal policy, and applicable charges/penalties. | Medium | Deterministic quote/calculator; product/rate snapshot model | Do not advertise only a compounded yield. RBI Directions require transparent disclosed rates and terms for applicable Indian banks. Exact tax and disclosure wording remains jurisdiction-specific. |
| Persist the selected product/rate terms as an immutable booking snapshot, including the applied outcome/rule version or equivalent. Later product edits must not change an existing deposit. | High | FD account schema/migration; audit trail | The current account table stores references and several terms but not a complete versioned snapshot. A follow-on migration or explicit snapshot representation is required; a live product join is not sufficient. |
| Reject invalid combinations before booking: unsupported currency, inactive product, tenure outside allowed range, amount outside limits, non-positive amount, excessive decimal scale, unavailable payout/compounding mode, or ineligible customer. | Medium | Product configuration; currency precision; customer/KYC/role status | Fail closed with a stable validation error. Never coerce a precision or round a principal silently. |

### 2. Booking and funding

| Observable behavior | Complexity | Dependencies | Edge cases / correctness implication |
|---|---:|---|---|
| Create a deposit in `PENDING` (or an equivalent funding state), with a unique account number/reference, primary holder, principal, currency, contractual terms, open date, and calculated maturity date. | Medium | `fd_accounts`; holder/customer reference; account-number allocator; state model | The schema default is `ACTIVE`, while the contract describes post-funding activation. The service/migration must prevent an unfunded account from becoming active by default. |
| Accept exactly one initial funding operation for the booked principal and activate only after funding succeeds. The funding record must be linked to the FD and carry the correct currency and amount. | High | Transaction types; funding/account ledger boundary; transaction persistence | Initial funding is in scope; arbitrary deposit/withdrawal variants are not. Reject underfunding, overfunding, wrong currency, duplicate funding, and funding after cancellation/closure. |
| Make account creation/funding safe to retry with an idempotency key or equivalent durable request identity. A client timeout followed by retry returns the original result and cannot create a second FD or second principal credit. | High | Durable idempotency record or unique business key; transaction boundary | JVM-local locks are insufficient across instances. Database uniqueness and a transaction are required. |
| Enforce ownership and staff authorization on every account, transaction, accrual, closure, and payout operation. A customer can read and operate only their own eligible FD; staff/system roles receive explicitly scoped operational access. | High | One authoritative authentication contract; holder/customer mapping; method security | Do not trust `holder_id`, `approved_by`, or caller-supplied customer IDs. Avoid account-existence leakage for unauthorized users by returning the project’s chosen not-found/forbidden behavior consistently. |
| Return a durable booking result containing the account number, status, principal, locked terms, dates, and funding/activation status. | Low | Account DTO and contract compatibility | A `201` must not imply the money is active if funding is still pending. Contract semantics should distinguish created, funded, and active. |
| Keep the ledger-like transaction history append-only for financial effects and calculate/store `balance_after` from server state. | High | `fd_transactions`; transaction type master data; concurrency control | Do not expose generic public transaction posting as a way to manufacture interest or payout credits. Financial transaction types must be allowlisted per operation. |

### 3. Accrual

| Observable behavior | Complexity | Dependencies | Edge cases / correctness implication |
|---|---:|---|---|
| Accrue only for an eligible active deposit and only for dates in the contractual accrual window. Do not accrue before activation, after closure, or twice for the same account/date. | High | Account state; accrual policy; unique account/date identity; scheduler/manual trigger | Add a database uniqueness guarantee for `(account_id, accrual_date)` or an equivalent idempotency key. The current table has no such unique constraint. |
| Persist the amount for each accrual date, cumulative accrued amount, currency, calculation inputs/version, and timestamps; repeated `runAccrual` returns the existing result rather than adding money again. | High | `fd_interest_accrual`; deterministic calculator; decimal arithmetic | OpenAPI uses `double`, but monetary implementation must use decimal/`BigDecimal` semantics and currency scale. Never use binary floating point as the authoritative amount. |
| Use one documented day-count, rounding, compounding, and date/time-zone convention for v1, and apply it consistently to quote, accrual, maturity, and premature closure. | High | Product policy; calculator; business calendar/time zone | The current codebase has an identified approximate 30-day month conversion and `double` risk. Calendar-month maturity and the selected accrual convention must replace implicit approximations. |
| Reconcile accrual history with the account’s cumulative accrued amount and the final maturity/closure calculation. Operational users can detect missing, duplicate, or out-of-order runs. | Medium | Read/query endpoints; audit events; invariant checks | A failed scheduled run must be retryable. Partial writes must roll back or leave an explicit recoverable status; never report success after only updating one side. |
| Support a safe catch-up run for missed dates, bounded to the account’s lifecycle and policy. Manual and scheduled processing use the same application service. | High | Batch query; locking/versioning; scheduler; observability | Avoid a scheduler-only implementation: the existing jobs currently call intentionally incomplete methods and fail by design. Concurrent scheduler/manual execution must not duplicate accrual. |

### 4. Maturity

| Observable behavior | Complexity | Dependencies | Edge cases / correctness implication |
|---|---:|---|---|
| Calculate maturity from the locked open date and tenure using calendar-aware rules, and persist/display the result before maturity processing. | Medium | Date policy; account snapshot; time zone | Month-end dates, leap days, daylight-saving boundaries, and a maturity date on a non-business day need explicit policy. Do not approximate every month as 30 days. |
| Transition `ACTIVE → MATURED` exactly once when the maturity date is reached, after bringing accrual through the contractual end date. A repeated manual trigger or scheduled job is a no-op returning the existing outcome. | High | State machine; accrual service; transaction/row locking; idempotency | Reject maturity for `PENDING`, `CLOSED`, or `PREMATURELY_CLOSED`; safely handle an already-matured account. |
| Persist the final maturity amount and the processing result, including whether the next action is payout, renewal, or an exception requiring operations review. | Medium | `fd_accounts`; maturity instruction; payout/renewal records | Never silently renew at a newly current rate. Renewal must create a new contractual account or an explicit versioned renewal record. |
| Process overdue matured deposits according to an explicit policy rather than leaving an ambiguous active account. | High | Maturity/overdue policy; payout/closure state | For India, RBI states that unpaid matured domestic term deposits attract the lower of savings rate or contracted rate; this must be encoded only if the product is subject to that rule. |

### 5. Closure

| Observable behavior | Complexity | Dependencies | Edge cases / correctness implication |
|---|---:|---|---|
| Allow normal closure only at/after maturity and only after the final interest calculation and settlement decision. | Medium | Matured state; payout/settlement; state transition rules | A close request racing with maturity must serialize to one valid result, not produce both normal and premature closures. |
| Provide a separate premature-closure request path that records reason, requested date, computed eligible rate, penalty, net payable, approver, and final status. | High | Premature-closure table; rule outcome; staff approval policy; payout | Never implement premature closure as a caller patch to `status`. In applicable India domestic deposits, RBI says interest is based on the amount and period actually held, not simply the contracted rate; penalty components must be disclosed at acceptance. |
| Enforce valid transitions: pending accounts cannot close as funded deposits, closed accounts cannot accrue or pay again, and rejected closure requests leave the FD active. | Medium | Central state machine; optimistic/pessimistic locking | All terminal transitions must be idempotent and auditable. |
| Record gross principal, gross interest, penalty/charge, tax/withholding treatment if applicable, net payable, and the settlement reference before/with closure. | High | Decimal calculation; policy/tax boundary; payout record; audit event | Do not silently omit tax or pretend a gross amount is net. The project has `tds_applicable` holder data but no complete withholding model; v1 should either implement a declared tax policy or explicitly mark tax as not calculated. |

### 6. Payout and settlement

| Observable behavior | Complexity | Dependencies | Edge cases / correctness implication |
|---|---:|---|---|
| Resolve one eligible payout destination belonging to the account holder, require verification/primary selection according to policy, and show the destination before initiating settlement. | High | Payout-account ownership/verification; holder mandate; secure masking | The OpenAPI model includes `is_verified`, but `fd_payout_accounts` in V1 does not. This is a schema gap, not permission to treat an unverified account as safe. |
| Create a durable payout/settlement record with amount, currency, payout date, destination, status (`PENDING`, `PROCESSED`, `FAILED`), and an external/idempotency reference. | High | `fd_interest_payout`; settlement boundary; durable idempotency | A failed bank-rail call must be retryable without a duplicate transfer. An account must not be terminally closed as successfully paid until the chosen settlement semantics say so. |
| At maturity or approved closure, settle principal plus eligible interest less approved deductions exactly once, and expose the payout result/history to authorized users and operations. | High | Final calculation; payout destination; transaction history; state machine | Amount and currency must match the FD. Do not pay accrued interest twice when periodic payout is later introduced. |
| For this milestone, support recorded settlement behavior behind the contract; do not claim that a local payout row means an actual external bank transfer succeeded. | Medium | Explicit adapter boundary; truthful status model | A real payment-rail integration, reconciliation, reversals, and returns are separate capabilities. `PROCESSED` should mean what the adapter contract defines, not merely “database insert succeeded.” |

## Differentiators

These add customer or operating value but are not required to prove a safe core lifecycle. They should follow the core state machine and ledger invariants rather than complicate it.

| Differentiator behavior | Value | Complexity | Recommended timing |
|---|---|---:|---|
| Rate quote with a short-lived lock/reservation and a clear expiry before booking. | Prevents surprise rate changes during a long confirmation flow. | High | Later, after snapshot and idempotent booking work. |
| Rich product comparison and recommendation across tenure, liquidity, payout frequency, effective yield, and maturity amount. | Improves selection without allowing the client to choose an invalid rate. | Medium | Later; calculator and product catalog APIs first. |
| Monthly/quarterly/annual interest payouts with discounted monthly payment rules and reconciliation to maturity principal. | Supports income-oriented customers. | High | Defer; v1 should prefer at-maturity settlement unless an existing product explicitly requires recurring payout. |
| Auto-renewal with a newly created renewal account, fresh eligible rate, explicit notice, and a configurable grace/overdue policy. | Reduces manual renewal work. | High | Defer until maturity and payout are stable. |
| Partial premature withdrawal or split FD while preserving the remainder’s original terms. | Adds liquidity without closing the whole deposit. | High | Defer; it changes principal, penalty, accrual, and account identity semantics. |
| Joint-holder operating mandates, nominee registration, deceased-depositor claims, and survivor-specific closure rules. | Matches real deposit operations and reduces claims friction. | High | Defer broad holder mutation, but do not bypass primary-holder ownership or mandate checks in v1. |
| Nomination capture, acknowledgement, cancellation, and display on the deposit record. | Important customer protection in jurisdictions with nomination obligations. | High | Phase after jurisdiction and holder/claims policy are locked. India’s 2025 nomination directions require explicit offering and record procedures. |
| Tax withholding calculation, certificates, exemptions, and tax-year reporting. | Makes the product production-ready for regulated tax reporting. | High | Defer unless the milestone has a declared tax mandate; never silently calculate zero tax. |
| Notices for booking, funding, maturity reminders, closure, and payout delivery through email/SMS/push/post. | Reduces missed maturity and improves trust. | Medium | Defer; persist internal lifecycle events first, then add delivery/outbox reliability. |
| Customer statements, downloadable TDR/receipts, and period statements. | Makes financial history self-service and auditable. | Medium/High | Defer per project scope; transaction and accrual history APIs are the prerequisite. |
| Sweep at maturity into another product or account based on threshold/multiple rules. | Automates cash management. | High | Defer; requires cross-product settlement and failure compensation. |
| Multi-currency and non-resident product variants with currency-specific day count, exchange, and regulatory rules. | Expands market coverage. | Very high | Defer; current contract/schema can carry currency IDs but do not provide the full policy boundary. |
| Operational dashboards for stuck funding, failed payout, overdue maturity, accrual gaps, and reconciliation breaks. | Makes batch processing supportable at scale. | Medium | Add immediately after core endpoints as an operational differentiator, even if customer UI is deferred. |

## Anti-Features

These are behaviors to explicitly avoid because they create financial, regulatory, or operational risk.

| Anti-feature | Why avoid it | Safer alternative |
|---|---|---|
| Trusting client-supplied interest rate, maturity amount, status, penalty, approver, or customer identity. | Enables mispricing, unauthorized closure, privilege escalation, and inconsistent financial records. | Resolve all values server-side from active policy, authenticated ownership, and persisted snapshots. |
| Activating an FD immediately when the create request is accepted, before principal funding is confirmed. | Creates deposits that appear to earn interest without money received and makes reconciliation ambiguous. | `PENDING → ACTIVE` only after one successful, idempotent funding operation. |
| Keeping a live product join as the only source of booked terms. | Later product/rate edits can retroactively change customer contracts. | Immutable per-account snapshot plus reference to the source product/rule version. |
| Using `double`, binary floating point, or implicit database casts for money. | Produces non-repeatable rounding and payout discrepancies. | Decimal arithmetic with currency-specific scale and one documented rounding policy. |
| Using approximate 30-day months for maturity or interest without a declared product policy. | Month-end and leap-year deposits mature or accrue on the wrong dates. | Calendar-aware date arithmetic and an explicit day-count convention tested at boundaries. |
| Allowing generic public `POST /transactions` to create any debit/credit. | A caller could manufacture principal, interest, or payout effects outside the lifecycle. | Internal, allowlisted financial commands that create transactions atomically with domain state. |
| Treating a scheduled job as the only way to progress accounts. | Missed jobs leave money stale; manual retries can duplicate effects. | Shared idempotent application services callable by scheduler and authorized operational endpoints. |
| Returning `204` for reference-data sync while doing no persistence or validation. | Consumers interpret a no-op as successful synchronization and later book against stale/missing data. | Persist an idempotent sync result or return an explicit unsupported/error response. |
| Marking payout `PROCESSED` because a local row was written. | Customers and operations may believe money moved when the external rail did not. | Distinguish accepted, submitted, processed, failed, returned, and reconciled states as supported by the adapter. |
| Changing payout destination after a payout is queued or hiding the destination from the customer. | Creates fraud and dispute risk. | Freeze destination at payout initiation, mask sensitive fields, and require a new verified destination for a new attempt. |
| Auto-renewing silently at a new rate or changing principal without a new contract. | Violates customer expectations and may conflict with disclosure rules. | Explicit maturity instruction, notice, new account/snapshot, and idempotent renewal. |
| Applying a penalty that was not disclosed or inventing a generic penalty when the product policy is absent. | Can be unlawful or financially unfair. | Reject premature closure until an approved, effective policy can price it transparently. |
| Implementing the entire generic rule engine, statements, notifications, holders, and event publication before the core lifecycle works. | Expands scope while leaving the highest-risk transitions unverified. | Build narrow, testable policies for the supported v1 product; defer extension points until invariants are proven. |
| Exposing raw bank account numbers, identity data, or internal rule details in broad account/list responses. | Increases privacy and fraud exposure. | Mask destinations, minimize DTOs, and apply ownership/staff scopes. |

## Feature Dependencies

```text
Active product/rate policy
  → eligibility validation and quote
  → immutable booked-term snapshot
  → pending account creation
  → idempotent initial funding
  → ACTIVE state
  → accrual policy + deterministic calculator
  → daily/date accrual records
  → maturity calculation and idempotent transition
  → maturity instruction / closure decision
  → eligible payout destination
  → durable payout/settlement record
  → CLOSED or renewal outcome
```

```text
Authoritative authentication + customer/holder mapping
  → ownership checks on every FD operation
  → staff approval boundary for premature closure
  → payout destination ownership and mandate checks
```

```text
Financial transaction types + decimal money policy + concurrency control
  → funding transaction
  → accrual idempotency
  → maturity/closure settlement
  → reconciliation and audit trail
```

Additional dependency rules:

- `PAYOUT` maturity instruction requires a valid payout account; `SWEEP` additionally requires a target product and cross-product settlement, so it is not v1.
- `RENEW_PRINCIPAL` and `RENEW_PRINCIPAL_AND_INTEREST` require a new product/rate resolution at maturity and a new account/snapshot; they must not mutate the old account’s terms.
- Premature closure requires accrued-to-date calculation, applicable-rate lookup, penalty policy, approval policy, and a payout/settlement decision. It cannot be safely built as a status update.
- Recurring interest payout requires a defined relationship between accrual, payout, compounding, principal, and closure. It should not be enabled merely because the OpenAPI enum lists monthly or quarterly values.
- Statements and notices depend on durable transactions, accruals, maturity/closure events, and truthful payout statuses. They are consumers of lifecycle facts, not substitutes for them.

## Recommended v1 Boundary for This Milestone

### Include

1. **Server-authoritative product/rate selection** for one clearly defined FD product family, active/effective offer validation, supported currency, tenure and amount checks, and a persisted immutable term snapshot.
2. **Booking and initial funding** with `PENDING → ACTIVE`, ownership/staff authorization, durable idempotency, append-only funding transaction, and no duplicate account/principal effect on retry.
3. **Deterministic accrual** for the supported product’s documented convention, with decimal arithmetic, persisted account/date records, database-backed uniqueness, cumulative readback, catch-up, and safe manual/scheduled reruns.
4. **Maturity processing** with calendar-aware date calculation, final accrual, `ACTIVE → MATURED` idempotency, maturity amount, and an explicit no-op/retry result for already processed accounts.
5. **Normal closure and recorded payout/settlement** at maturity, using a verified/eligible payout destination or an explicit operational exception. Persist payout status and settlement reference; do not imply an external transfer that did not occur.
6. **A narrow premature-closure path only if the applicable policy is available.** It must calculate the applicable held-period rate and disclosed penalty, require the chosen approval boundary, and record gross/net components. If policy or tax semantics are not ready, return a clear unsupported/business error rather than approximating.
7. **Internal lifecycle audit facts and operational visibility** for state transitions, funding, accrual, maturity, closure, payout, and failures. This is not the same as external event publication.
8. **PostgreSQL-backed contract/service/security/scheduler/regression tests** that exercise duplicate requests, concurrent processing, ownership failures, date boundaries, monetary rounding, and restart/retry behavior.

### Defer

- Recurring interest payouts and partial withdrawals/splits.
- Auto-renewal, sweep, cross-product movement, and non-trivial multi-currency/non-resident variants.
- Full holder/nominee/deceased-claim mutation workflows, while retaining safe primary-holder ownership checks.
- Notices, statements/PDF downloads, external event publishing, and reference-data synchronization beyond the data needed to validate v1 bookings.
- Full tax withholding/reporting and jurisdiction-specific regulatory reporting unless product ownership explicitly makes them a milestone acceptance criterion.
- Real payment-rail integration, reversals, returns, and settlement reconciliation beyond the recorded settlement adapter boundary.
- Angular fixed-deposit screens; backend lifecycle behavior remains the acceptance boundary for this milestone.

### Suggested v1 acceptance scenarios

| Scenario | Required observable result |
|---|---|
| Same booking request is submitted twice after a timeout. | One account and one funding effect; both retries resolve to the same durable result. |
| Product is deactivated after an FD is active. | New bookings reject it; the active FD retains its booked rate and terms. |
| Caller submits a mismatched rate or maturity amount. | Request is rejected or server values replace the claim; no mispriced account is persisted. |
| Funding is under, over, wrong-currency, or repeated. | No activation and no duplicate financial effect; response identifies the validation/business failure. |
| Accrual is run twice for the same date, concurrently or after restart. | One accrual row/effect and one cumulative value; repeat returns the existing result. |
| Accrual job misses several dates. | Authorized catch-up fills only the eligible missing dates, with no duplicates. |
| Maturity endpoint and scheduler race. | One final maturity transition and one settlement decision. |
| Maturity falls on a month-end, leap day, or non-business day. | Result follows the documented calendar/business-day policy and is reproducible. |
| Unauthorized customer accesses another customer’s account ID. | No account data or mutation is exposed. |
| Payout adapter fails and is retried. | Payout remains retryable/failed according to policy, without a second payout or false `PROCESSED` status. |
| Premature closure has no effective penalty/rate policy. | Operation is blocked with an explicit business error; no guessed penalty or closure occurs. |

## Sources

- **[HIGH] Repository project scope:** `.planning/PROJECT.md` — active FD requirements, explicit out-of-scope items, financial/security/operational constraints.
- **[HIGH] Repository architecture:** `.planning/codebase/ARCHITECTURE.md` — Spring modular-monolith boundaries, Flyway/schema-first persistence, existing scheduler, and `/fd/v1` placeholder behavior.
- **[HIGH] Repository concerns:** `.planning/codebase/CONCERNS.md` — legacy FD bridge, failing scheduled jobs, schema/entity fragility, `double` calculation risk, and missing lifecycle tests.
- **[HIGH] Existing API contract:** `BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml` — lifecycle operations, statuses, accrual/payout/closure models, and current request shapes.
- **[HIGH] Existing schema:** `BACKEND/src/main/resources/db/migration/V1__bankingtech_public_schema.sql` — normalized FD accounts, transactions, accrual, payout, closure, maturity instruction, holder, event, and reference tables.
- **[HIGH, official, 2025-04-01] Reserve Bank of India, *Interest Rate on Deposits Directions, 2025*: https://www.rbi.org.in/Scripts/NotificationUser.aspx?Id=12825&Mode=0 — disclosed rates, term-deposit rules, maturity on non-business days, overdue interest, premature-withdrawal rate, and penalty disclosure requirements. Applies only if India is the governing jurisdiction.
- **[HIGH, official] DICGC, *A Guide to Deposit Insurance*: https://www.dicgc.org.in/guide-to-deposit-insurance — fixed-deposit coverage, principal-plus-interest treatment, ownership-capacity aggregation, and joint-account implications. Insurance display/reporting is not included in this milestone unless required by the licensed operating model.

## Evidence and Gaps

- The repository provides a broad contract and normalized tables but does not yet establish the product’s jurisdiction, exact day-count convention, tax policy, payment-rail semantics, or authoritative authentication path. These are requirements decisions, not safe implementation assumptions.
- The API exposes more lifecycle concepts than the current database constraints guarantee. Before implementation, the plan should explicitly address account/date accrual uniqueness, funding/payout idempotency, immutable term snapshots, payout verification, and the `ACTIVE` default versus pending funding.
- Product/rate discovery is not itself a complete `/fd/v1` customer journey. The roadmap should either reuse an existing product endpoint as the selection boundary or add a narrowly scoped quote/eligibility operation without redesigning the existing contract.
