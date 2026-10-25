# Ledger and savings transfer test report

**Date:** 2026-10-25  
**Baseline:** `efe761c` (`main`)  
**Scope:** shared ledger, demo opening, teller movements, savings transfers, reconciliation, and loan disbursement guard.

## Results

| Check | Baseline | Final |
|---|---|---|
| `mvn -f services/fd-account-service/pom.xml test` | 30 tests passed, 0 failures/errors/skips | 41 tests passed, 0 failures/errors/skips; BUILD SUCCESS |
| Angular production build | Passed, existing size warnings | Passed; initial bundle 842.07 kB exceeds the 512 kB budget and existing component style budgets are exceeded |
| `npm run check:i18n` | Not run | Failed on 33 untranslated strings elsewhere in existing templates; newly added transfer labels are present in all six supported language columns |
| Angular unit/spec runner | No configured test target | Not run; `angular.json` and `package.json` do not configure a runner |
| `git diff --check` | Not run | Passed; Git reports a line-ending normalization warning for `customer-messages.ts` |

The final Maven suite includes existing FD lifecycle, migration, loan/forex, and calculator coverage plus seven ledger invariant integration tests and four PostingService unit tests. Integration tests used disposable Testcontainers MySQL 8.4 databases. All 13 Flyway migrations, including V13, applied in those disposable databases. No live/shared database was used or changed.

During implementation, an intermediate run exposed a transaction-proxy issue in a test fault injection and a loan error response assertion mismatch. Both were corrected; the final full suite passes. The test-only rollback failure is injected inside an explicit transaction and leaves no production test hook.

## New integration coverage

- Transfer debit and credit, paired history and balanced journal entries.
- Idempotent retry and same-key/different-body rejection.
- Ownership, KYC, account state, currency, amount precision, insufficient funds, self-transfer, destination lookup, and remark validation.
- Teller deposit/withdrawal journal entries, replay, and overdraft prevention.
- Disabled demo opening, configured maximum amount, and balanced demo-opening journal.
- Concurrent debits, same-key transfer replay, and opposite-direction transfers with stable lock ordering.
- Full transactional rollback across balances, history, journals, and idempotency.
- Reconciliation response has no journal-header mismatch after successful postings.
- Approved loan disbursement is rejected and creates no loan transaction.

## Unverified

- Angular Jasmine-style spec is added but could not be executed because this repository has no configured frontend test runner. To run it, add/configure the project’s runner and invoke its test target; `npm run build` verifies the production app compiles.
- `npm run check:i18n` needs the 33 existing untranslated template strings resolved before it can pass. The current checker output did not list any newly added transfer message keys.
- No live gateway/browser walkthrough was run. The gateway already contains the savings and admin-ledger proxy routes; a local manual walkthrough can use the existing customer UI and `GET /api/v1/admin/ledger/reconciliation` with an admin token.
- Database cutover starts at migration time. Old savings histories/journals are kept as historical and are not backfilled or automatically reconciled.

## Decision

**GO for local API demonstration** of ledger-backed teller/demo movements and same-currency savings transfer with idempotency. **NO-GO for production financial use**: teller/demo counterpart accounts are illustrative, old history is not migrated, and gateway/browser plus frontend unit execution remain unverified. Loan disbursement intentionally remains unavailable until receivable accounting exists.
