# Ledger and savings transfer handoff

## Readiness after client-testing fixes

Follow-up I-K: customer cash withdrawal was not present in history and remains
out of scope; teller withdrawal is attested officer-only. KYC review refreshes
in place, and rejected customers may re-apply with a single-use KYC_REAPPLY MPIN
proof, subject to the three-per-24-hour limit and mandatory staff approval.

Baseline suites were identity 24, FD 55, product 34, and Angular production
build passed. Current source compiles for identity and FD, and the Angular build
passes. Current integration reruns are recorded in `identity-current.log` and
`fd-current.log`; migration-count assertions were updated for customer V12 and
FD V16. Docker-dependent gateway smoke remains unverified unless an isolated
compose project is explicitly started with `-p moneypal-e2e`.

**Baseline:** `efe761c` (`main`) with the approved ledger and transfer changes in the working tree  
**Active implementation:** `services/fd-account-service` (MySQL 8.4 + Flyway), Angular `FRONTEND/`, and the existing nginx gateway. `BACKEND/` Java is legacy; only its FD service migration directory supplies migrations to the active service.

## PostingService contract

`PostingService` is authorization and credential agnostic. It receives explicit account IDs, money, references, account codes, and actor username; it does not read Spring Security context. All public posting methods require an already active transaction through `Propagation.MANDATORY`. Callers own one `@Transactional` unit covering balance changes, savings history, journal rows, aggregate status, and idempotency completion.

```java
MovementResult postSavingsMovement(
    long accountId, long currencyId, BigDecimal amount,
    String entryType, String reference, String remarks, String actorUsername,
    String journalKey, String operation, String debitCode, String creditCode)

TransferResult postSavingsTransfer(
    long fromId, long toId, long currencyId, BigDecimal amount,
    String reference, String remarks, String actorUsername)

long postJournal(
    String key, Long fdAccountId, long savingsAccountId, long currencyId,
    String operation, String actorUsername, BigDecimal savingsBalanceAfter,
    List<LedgerLine> lines)

static void validateLines(List<LedgerLine> lines, int currencyScale)

record LedgerLine(String accountCode, Long savingsAccountId,
                  BigDecimal debit, BigDecimal credit)
```

`postSavingsMovement` and `postSavingsTransfer` lock active savings rows, validate the currency scale and nonnegative result, update balance and row version, create history with its resulting balance, and create the journal in the same database transaction. Transfers lock both accounts in ascending account ID order and create two rows with one reference. Every `SAVINGS` line created by new posting paths carries `jl_savings_account_id`.

Journal validation requires at least two lines, one positive side per line, active chart codes, exact currency scale, one journal currency, and equal debit/credit totals. A savings account reference on a line must point to a savings account in the journal currency. Amount and line validation failures return HTTP 400; an insufficient balance or duplicate business reference returns HTTP 409. A repeated `fj_key` is always a conflict; the posting layer never skips a duplicate journal while allowing a balance movement. HTTP retries replay from the caller's idempotency record before posting.

## Reference and posting rules

| Operation | Savings/history reference | Journal key | Debits | Credits |
|---|---|---|---|---|
| Demo opening | `DEMO_OPENING:<savingsAccountId>` | same | `DEMO_SUSPENSE` | `SAVINGS` for the credited account |
| Teller deposit | `STAFF-<uuid>` | `TELLER_DEPOSIT:<reference>` | `CASH_TELLER` | `SAVINGS` for the credited account |
| Teller withdrawal | `STAFF-<uuid>` | `TELLER_WITHDRAWAL:<reference>` | `SAVINGS` for the debited account | `CASH_TELLER` |
| Savings transfer | `TRF-<uuid>` shared by both history rows | `TRANSFER:<reference>` | `SAVINGS` for source | `SAVINGS` for destination |
| FD funding | existing behavior | `FUNDING:<fdId>` | `SAVINGS` | `FIXED_DEPOSIT` |
| FD maturity interest | existing key | `INTEREST:<fdId>:MATURITY` | `INTEREST_EXPENSE` | `FIXED_DEPOSIT` |
| Premature interest | existing key | `INTEREST:<fdId>:PREMATURE` | `INTEREST_EXPENSE` | `FIXED_DEPOSIT` |
| FD settlement | existing key | `SETTLEMENT:<fdId>` | `FIXED_DEPOSIT` | `SAVINGS` |

The first savings account in each journal header is the account debited or credited first; for transfers it is the source. New operation actor usernames are stored in `fj_created_by`.

### Reusing the service for maturity and closure

Maturity interest recognition is a journal-only movement: while the batch's existing transaction is active, call `postJournal("INTEREST:" + fdId + ":MATURITY", fdId, savingsId, currencyId, "INTEREST", actorUsername, null, List.of(new LedgerLine("INTEREST_EXPENSE", null, interest, zero), new LedgerLine("FIXED_DEPOSIT", null, zero, interest)))`. Keep the current accrual rows, FD transaction, and maturity status writes in that same transaction.

For an FD payout, the lifecycle now calls the shared `PostingService` credit path, which writes the deposit credit, history, and settlement journal atomically; the caller continues to write FD closure history/status/event and complete its idempotency operation in the same transaction. Deposit ledger code is selected from the account type. Do not change the established FD keys listed above.

## V13 and reconciliation

`V13__shared_ledger.sql` adds a case-sensitive `chart_of_accounts` seeded with the five codes in the table above, permits teller and transfer operation codes, adds `fj_created_by`, adds nullable line-level savings account references, and creates the one-row `fd_ledger_cutover` timestamp. Existing journal rows are not backfilled or corrected. The line savings-account reader rule is `COALESCE(jl_savings_account_id, fj_savings_account_id)`.

`GET /api/v1/admin/ledger/reconciliation` is ADMIN-only and read-only. It reports journal header/line differences, line-level debit/credit by currency and cutover period, per-account savings balance/history/journal nets, running history sequence differences, and separates `HISTORICAL` rows from `CURRENT` rows. A `currentHistoryJournalMismatch` or `currentBalanceHistoryMismatch` flag, a current journal header mismatch, a current currency debit/credit difference, or a current history sequence mismatch indicates a current defect. Historical differences are reported for investigation and are never automatically repaired.

## Idempotency and API behavior

Transfer endpoint: `POST /api/v1/savings-accounts/transfers`, customer JWT only. The controller resolves owner and KYC access, validates the explicit request fields, hashes only `fromAccountId`, normalized destination account number, canonical amount (`stripTrailingZeros()`), and trimmed remark, then checks for a replay. The separate transactional `SavingsTransferService.transfer(...)` claims scope `SAVINGS_TRANSFER`, checks destination and currency, calls `PostingService`, and marks the idempotency row completed. Same key/body replays the existing reference; same key with a different body is 409. Amount `10` and `10.00` share a hash. Keys are actor-scoped. Request bodies and secrets are not logged or stored in idempotency records.

The gateway already routes `/api/v1/savings-accounts/` and `/api/v1/admin/ledger/`; no nginx change was needed. Loan disbursement now returns 409 and leaves an approved application, balance, history, and journal untouched until receivable accounting exists. A previously disbursed loan still returns its existing record.

## Later changes A–E status

See [HANDOFF_LATER_CHANGES.md](HANDOFF_LATER_CHANGES.md) for the implemented staff-only demo opening, MPIN workflow, KYC validation updates, product template descriptions, default LOW risk category, environment settings, exact MPIN proof bindings, and residual risks. The transfer request hash continues to include only its explicit business fields; credentials are neither logged nor stored in idempotency records.

## Verified gaps and outstanding risks

### L-O continuation

Customer Management now treats KYC status as display-only and rejects a
differing PATCH status before other writes. KYC review responses are no-store,
sanitize legacy reviewer metadata, and expose compact resubmission history.
Customer savings withdrawal is a CUSTOMER-only MPIN `WITHDRAWAL` operation on
the shared ledger (`SAVINGS` to `WITHDRAWAL_CLEARING`, migration V17) with
actor-scoped idempotency and replay. The backend ledger integration passes;
Angular checks and gateway smoke remain unverified until Node is configured.

- Before V13, demo opening and teller deposit/withdrawal changed savings balances and history without journals. Loan disbursement credited savings and changed loan status/history without a receivable journal. Those historical rows are not retroactively adjusted.
- The migration cutover deliberately classifies all prior postings as historical, even if a preexisting journal is balanced. Current reconciliation flags are the gate for new paths.
- Existing FD funding, maturity, premature-interest, and settlement implementations remain separate from `PostingService` to preserve their current semantics and keys. They should be migrated only as a reviewed follow-up.
- Settlement and maturity remain internal ledger records, not external payments. The lab's teller cash and demo suspense accounts are illustrative accounting codes, not a reconciled physical cash process.
- The frontend has no configured `test` target. A Jasmine-style transfer spec was added, but only the Angular build and translation checker can currently run from the package scripts.
- Audit files named OpenCode and Claude were absent from the checkout; findings were checked against current code and the available handoff/regression reports instead.

## Deposit account type extension

Current product-backed deposits add `CURRENT_ACCOUNT` as a second liability line for current accounts. PostingService selects the code from the account type; savings journals remain `SAVINGS`. Transfers can journal both deposit codes. Debit postings enforce the frozen minimum-balance floor. FD funding and settlement now use the shared posting service while retaining their established idempotency and journal keys. See `HANDOFF_CURRENT_ACCOUNT.md` for the endpoints, migrations, interest processing and lifecycle.
