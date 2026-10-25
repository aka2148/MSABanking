# Premature Closure Handoff

**Author:** Sudhiksha implementation handoff  
**Date:** 25 October 2026  
**Repository baseline:** `95c2ded0367aa3e06039bdfc600d159a74ac3f9e` (`main`)  
**Scope:** MoneyPal active `fd-account-service` implementation and Angular FD/staff screens.

## Approved policy and workflow

- Retain four-eyes review: a staff member approves or rejects a customer's request, and the FD owner cannot decide their own request.
- Use only `annualRate` and `prematurePenaltyPoints` frozen in `fa_terms_snapshot` at booking. Missing or malformed values fail closed; mutable product references are never used as fallback.
- Penalized annual rate is `max(0, annualRate - penaltyPoints / 100)`. Interest uses principal × rate × actual elapsed days / 365, rounded HALF_UP to the currency's configured scale. Quotes are valid through the end of their business date.
- Customers can request and view a quote, see the pending decision state and rejection reason, and cannot invoke staff approval. Staff can list pending quotes, approve, or reject with an optional reason.
- MATURED accounts expose normal maturity closure only. The existing maturity/accrual workflow and settlement path remain shared with their existing implementation.

## Posting sequence

For an approved early closure, all writes execute in the existing service transaction:

1. Recognize paid interest with an `INTEREST` journal: `INTEREST_EXPENSE` debit and `FIXED_DEPOSIT` credit for the quoted interest. A zero-interest quote needs no interest journal.
2. Credit the linked savings account once for principal plus paid interest.
3. Write `FD_INTEREST` (when nonzero), `FD_PENALTY` (when nonzero), and `FD_CLOSURE` history rows, plus the `SETTLEMENT:<fdId>` journal: `FIXED_DEPOSIT` debit and `SAVINGS` credit for the exact payout.
4. Mark the FD `PREMATURELY_CLOSED`, persist the staff decision, emit the lifecycle event, and complete the idempotency operation.

The recognized interest restores the FD liability before payout. The settlement debit then consumes principal plus interest, so the closed FD liability should net to zero. Savings, journals, transactions, closure status, event, and idempotency completion roll back together on a posting failure. No external payment rail is called.

## API and schema changes

- Customer request/history: `POST` and `GET /api/v1/fixed-deposits/{id}/premature-closure`.
- Staff queue: `GET /api/v1/admin/fixed-deposits/premature-closures?page=&pageSize=`.
- Staff decision: `POST /api/v1/admin/fixed-deposits/{id}/premature-closure/{closureId}/approve` or `/reject`, with `Idempotency-Key`.
- Migration `V12__premature_closure_decision_audit.sql` adds `REJECTED` and `EXPIRED` states plus decision metadata and a queue index.
- `services/fd-account-service/pom.xml` includes V12 in the service migration resources. The legacy `BACKEND` Java implementation was not changed.

## Compatibility for Krishang's PostingService

Preserve the existing `fd_journals` / `fd_journal_lines` contract and the two balanced pairs described above. `INTEREST:<fdId>:PREMATURE` and `SETTLEMENT:<fdId>` are stable journal keys. A shared posting abstraction must accept the account codes, currency-scaled amounts, FD/savings references, and transaction boundary while retaining those keys or provide an explicit compatibility lookup. The service currently posts these lines directly; no shared `PostingService` was available in this checkout.

## Verification boundary

The calculator tests, service financial unit tests, and MySQL Testcontainers migration/lifecycle suite pass. The suite ran against a disposable MySQL 8.4 container, applied all 12 migrations, and verified payout, balanced journals, zero net FD liability, one savings credit, closure history/event, idempotent replay, and rollback after an injected failure. Rejection/expiry-specific API cases and a running gateway/browser workflow remain unverified. See `HANDOFF_SUDHIKSHA_TEST_REPORT.md` for exact counts and limitations.
