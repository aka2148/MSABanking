# Current and product-linked deposit accounts

## Scope and migrations

The implementation keeps savings and current accounts in `savings_accounts`. Existing rows use the migration defaults (`SAVINGS`, no product, zero minimum balance, no interest terms) and are not otherwise rewritten. FD Flyway resources V21 and V22 add account type/product snapshots, frozen minimum balance and interest terms, lifecycle audit, daily accrual rows, the current-account liability code, and the `SAVINGS_INTEREST` journal operation. Both migrations are included in `services/fd-account-service/pom.xml`. No legacy Java or existing migration was changed.

## Product offers and opening

`GET /api/v1/customer-products/SAVINGS` and `/CURRENT` expose currently published, effective products that have currencies and pricing bands and no unsupported eligibility rules or charges. CASA offers include the `MINIMUM_BALANCE` attribute (default 0) and pricing bands as interest bands.

- `POST /api/v1/savings-accounts` retains plain account opening; optional `productId` links an offer and freezes its code, name, minimum balance, currency bands, and business-date start.
- `POST /api/v1/savings-accounts/admin` is the staff opening variant.
- `GET /api/v1/current-accounts` lists the customer's non-closed current accounts; `GET /{id}` and `GET /{id}/transactions` are owner scoped.
- `POST /api/v1/current-accounts` accepts `{productId,currencyCode,displayName}` and requires `Idempotency-Key`; same-key same-body replays, changed-body conflicts.
- `POST /api/v1/current-accounts/admin` accepts `{userId,productId,currencyCode,displayName}` for bank staff/admin.

Customer current-account opening calls `CustomerProductAccess.requireVerified`. Product currency must be configured and present in the FD currency reference. Product terms are frozen, so later product changes do not rewrite existing accounts. Current-account opening has no MPIN or per-customer cap.

## Ledger, transfers, and debit floor

`PostingService.depositLedgerCode(accountId)` resolves `SAVINGS` or `CURRENT_ACCOUNT` from the locked account type. The journal validator permits either deposit code to reference a savings-account row. Savings accounts keep their existing `SAVINGS` journal code and FD operation keys. FD funding, settlement, customer/staff movements, transfers, forex, and loan posting use the shared deposit posting logic. Ledger reconciliation includes both liability codes.

Every posting debit checks the locked balance against `sa_minimum_balance` before writing. A violation returns HTTP 409 with code `BELOW_MINIMUM_BALANCE` and the configured floor and currency. Plain savings defaults to a zero floor. Credits are not floor-limited. Account views include type, product metadata, minimum balance, and available balance (`max(0,balance-minimum)`).

## Interest

`SavingsInterestAccountProcessor` uses the EOD balance seen by the job for each processed date, Actual/365, and 8-decimal HALF_UP daily interest. It catches up dates after the opening date on demo-clock jumps. The frozen currency bands select the day's rate; missing/zero-rate days create no accrual row. Accounts without terms and CLOSED accounts do not accrue; ACTIVE and INACTIVE accounts do.

The default `app.savings.interest.credit-frequency` is `MONTHLY`; `QUARTERLY` is also accepted. Once a period end is reached, the processor sums uncredited rows, rounds to currency scale HALF_UP, posts a positive result with `INTEREST_EXPENSE` debit and the account's liability code credit, then marks rows with `INTEREST-<accountId>-<yyyyMM>`. The journal key is `SAVINGS_INTEREST:<accountId>:<yyyyMM>`, operation is `SAVINGS_INTEREST`, and actor is `SYSTEM`. A zero result is marked without a journal. Unique account/date rows plus account-level transactions make reruns idempotent.

The engine is called from the daily FD batch and a separate scheduled task using the existing scheduler enable setting and `app.savings.interest.schedule.cron`. `POST /api/v1/admin/savings-interest/run` is ADMIN-only and optionally accepts `{businessDate}`; it returns aggregate processed/accrual/credit/error counts. A failure is isolated per account.

## Lifecycle and statements

Bank officers/admins use `POST /fd/v1/staff/savings-accounts/{id}/status` with `{action:FREEZE|UNFREEZE|CLOSE,reason}`. Reasons are 5–140 characters. Freeze is ACTIVE→INACTIVE; unfreeze is INACTIVE→ACTIVE. Close requires zero balance and no pending/active FD, pending loan, or requested forex reference. Each successful transition creates an audit row. Ordinary credits/debits remain blocked while INACTIVE; explicit system credits for interest and FD payout can credit INACTIVE accounts. CLOSED accounts are excluded from customer account lists and interest runs.

`GET /api/v1/savings-accounts/{id}/statement?from=&to=&page=&size=` provides newest-first pages (default 25, maximum 100), inclusive ISO date filters, opening and closing balances, and paging totals. `GET /api/v1/savings-accounts/{id}/statement.csv?from=&to=` returns a no-store attachment with CSV formula-injection prefixes and currency-scale amounts. Existing transactions endpoints retain their history shape.

## Gateway and UI

The gateway routes `/api/v1/current-accounts` and `/api/v1/admin/savings-interest` to fd-account-service. Existing savings statement and `/fd/v1/staff` paths remain routed to that service.

Customer routes are `/current-accounts` (published catalogue), `/current-accounts/open/:productId`, and `/current-accounts/accounts`. The current-account page includes history, date-filtered statement pages, and CSV download. Savings opening offers an optional published SAVINGS product. Savings transfer/withdrawal pickers, FD funding, loan destination, and forex account pickers include active accounts of both types with a type tag. The staff account screen filters by SAVINGS/CURRENT and exposes teller operations, staff opening, lifecycle controls, and manual interest runs. Product interest and minimum-balance terms are shown on offer/opening cards.

## Verification and residual limits

The disposable MySQL Testcontainers database is used by service tests; no running application database or compose stack is targeted. Product tests passed 35/35 in the current run. FD and frontend verification are recorded in the execution report. The full requested end-to-end smoke workflow is not yet implemented. UI still has separate current-account and savings screens. Transfers, withdrawals, FD funding, loan destinations, and forex pickers now include both deposit types. The requested end-to-end smoke workflow and full feature-specific test matrix remain incomplete. Angular build could not run because no active Node.js version was configured.

No TDS, charges, overdraft, or external payment rails are added. Interest uses the approved EOD snapshot simplification, including the current balance for every missed date after a demo-clock jump.