# Domain Pitfalls

**Domain:** Spring Boot/PostgreSQL fixed-deposit banking lifecycle
**Project:** Banking Lab
**Researched:** 2026-09-18
**Overall confidence:** HIGH for repository-specific findings; MEDIUM for ecosystem guidance

## How to Read This Report

The repository is a brownfield FD implementation, not a greenfield calculator. The authoritative persistence boundary is the Flyway PostgreSQL schema under `BACKEND/src/main/resources/db/migration/`, and the public integration boundary is `/fd/v1`. The existing legacy `/api/fd` bridge, compatibility aliases, placeholder controller operations, and scheduler methods that throw must be treated as migration risks rather than harmless scaffolding.

The phase names below are proposed roadmap anchors because no roadmap exists yet:

- **Phase 1 — Financial policy and domain invariants:** money scale, day-count, rate, rounding, dates, and legal state transitions.
- **Phase 2 — PostgreSQL persistence and booking:** mappings, snapshots, funding state, account numbering, constraints, and transaction boundaries.
- **Phase 3 — Accrual and maturity processing:** daily accrual, maturity selection, idempotency, locking, retries, and batch operations.
- **Phase 4 — Closure, payout, and audit:** premature closure, penalties, payout settlement, immutable history, and operational evidence.
- **Phase 5 — Security and compatibility hardening:** ownership authorization, staff actions, legacy adapters, contract tests, migration rollout, and regression coverage.

Severity means the likely financial, regulatory, or availability impact if the pitfall reaches production.

## Critical Pitfalls

### 1. Treating `double` as the financial calculation boundary

**Severity/impact:** Critical — deterministic differences can change interest, maturity, penalty, and payout amounts; repeated accrual can compound the error.

**What commonly goes wrong:** A service converts `BigDecimal` principal and rate to binary floating point, performs `Math.pow`, and rounds only the final display value. Different code paths then produce slightly different results, and persisted ledger values no longer reconcile with calculator responses. `BigDecimal` itself is decimal and arbitrary precision, but `double` is not an acceptable source for regulated money arithmetic.

**Warning signs in this repository:** `FdCalculatorService.calculate` calls `doubleValue()` for principal, rate, and time, uses `Math.pow`, then wraps the result with `BigDecimal.valueOf`; database columns use `DECIMAL(18,4)` while the calculator returns scale 2; there is no shared money/rate policy object.

**Prevention:** Define one calculation policy and one service used by estimation, booking, accrual, premature closure, and maturity. Keep inputs and intermediate values as `BigDecimal`; use explicit rate units (for example, percent versus decimal), a documented `MathContext`, and named `RoundingMode`. Normalize money to currency scale at the ledger boundary, not opportunistically in controllers. Never calculate from a formatted API string or from a binary floating-point value.

**Verification approach:** Add golden-value tests for simple, monthly, quarterly, and yearly compounding; rates such as `6.01%`; large principals; zero/near-zero interest; and values that land exactly on a half-cent. Assert both numeric value and scale at persistence boundaries. Add a property test that estimation, accrual total, maturity, and premature payout agree for the same policy inputs. A repository search must fail CI for `doubleValue()`, `Math.pow`, or `double` in FD monetary calculation code unless explicitly allow-listed.

**Roadmap phase:** Phase 1 — Financial policy and domain invariants.

### 2. Rounding at the wrong stage or with an undocumented mode

**Severity/impact:** Critical — daily rounding can overpay or underpay across thousands of accounts; inconsistent half-up/half-even behavior creates unreconcilable statements.

**What commonly goes wrong:** Implementations round principal, daily interest, each compounding period, cumulative interest, tax, penalty, and final payout using different rules. They also compute `maturityAmount - principal` after the maturity amount has already been rounded, silently losing fractions. Rounding is treated as presentation rather than a product/accounting policy.

**Warning signs in this repository:** The calculator rounds `maturityAmount` to scale 2 and then derives `totalInterest`; entities and PostgreSQL columns allow scale 4; `FdAccount` has a transient `accruedInterest` although the schema stores accrual rows; there is no declared currency decimal-place lookup in the calculation path.

**Prevention:** Record a financial policy per product/currency: calculation precision, ledger scale, display scale, rounding mode, whether accrual is rounded per day or only at settlement, and how residual fractions are carried. Keep an unrounded/high-precision accumulator where policy requires it; round exactly once at the defined posting boundary. Ensure `principal + posted interest - charges = settlement` using the same scale and sign convention. Persist the applied policy/version with the FD snapshot so later product changes cannot recalculate history under new rules.

**Verification approach:** Build a rounding matrix with half-cent, sub-cent, negative/zero penalty, and repeated-period cases. Re-run the same accrual command and assert no additional row or amount. Reconcile every test account from transactions/accruals to maturity and payout totals. Add database checks for non-negative principal and amounts within the agreed scale; reject values with excess scale rather than silently truncating them.

**Roadmap phase:** Phase 1 for the policy; Phase 3 and Phase 4 for accrual and settlement enforcement.

### 3. Assuming a universal 365-day or 30-day day-count convention

**Severity/impact:** Critical — every interest amount and maturity date can be wrong, especially leap-year, month-end, and non-annual-tenure cases.

**What commonly goes wrong:** The application uses `termDays / 365` for all products, converts months to `months * 30`, or mixes calendar months with elapsed days. Banking products may require Actual/365, Actual/366, Actual/Actual, 30/360, or product-specific conventions. The convention also determines whether the opening or maturity day earns interest.

**Warning signs in this repository:** `FdCalculatorService` hardcodes `365.0`; `FdAccount.getTermDays()` returns `tenureMonths * 30`; `setTermDays()` truncates with integer division; `CreateFdRequest` accepts days while the authoritative table stores months; no day-count or timezone field is present in the account snapshot.

**Prevention:** Make day-count convention an explicit product/rate attribute and resolve it during booking. Define an interval convention (`[open, maturity)` or equivalent), leap-day behavior, and whether the booking day is included. Do not expose a lossy days/months compatibility conversion as the domain model. Use `LocalDate` for product dates and a configured business timezone; use `OffsetDateTime` only for timestamps/audit events. If the existing schema cannot store the policy, add a forward-only migration and preserve the historical default for legacy rows.

**Verification approach:** Test 1, 28, 29, 30, and 31-day terms; Jan 31 plus one month; Feb 28/29; year-end; leap years; DST boundary dates; and booking just before/after midnight in the configured zone. Compare Java results with an independent reference calculation and, where SQL date arithmetic is used, PostgreSQL integration tests. Assert that `maturityDate` and eligible accrual dates are derived from the same interval definition.

**Roadmap phase:** Phase 1 — Financial policy and domain invariants.

### 4. Getting maturity boundaries and time zones wrong

**Severity/impact:** Critical — an account can mature early, late, or twice; customer payout eligibility and interest entitlement become disputable.

**What commonly goes wrong:** A maturity check uses `now() > maturityDate` in one path and `>=` in another, compares a UTC timestamp to a local business date, or adds 24-hour durations where a calendar day/month is intended. A scheduler’s server timezone silently changes the business date.

**Warning signs in this repository:** `FdAccount` stores `TIMESTAMPTZ` but exposes `LocalDate` by truncating the offset; date setters force UTC midnight; report code uses `LocalDate.now()` without an injected clock; scheduler cron expressions have no declared timezone; tenure compatibility uses 30-day approximations.

**Prevention:** Choose one authoritative business timezone and inject a `Clock`. Define maturity as a half-open accrual interval with a precise effective-at timestamp or business date. Calculate calendar maturity with `LocalDate.plusMonths` or the documented product rule, then convert to a timestamp only at the boundary. Configure scheduler timezone explicitly. Centralize predicates such as `isMatured(asOf)` and forbid ad-hoc comparisons in controllers and repositories.

**Verification approach:** Freeze the clock at the instant before, exactly at, and immediately after maturity; test UTC offsets around local midnight and DST transitions. Run maturity processing twice with the same `asOf` time and assert one transition and one settlement. Add SQL fixtures that verify the query selects exactly the intended half-open interval.

**Roadmap phase:** Phase 1 for the contract; Phase 3 for scheduled selection and transitions.

### 5. Re-evaluating mutable product/rate data instead of snapshotting the booked FD

**Severity/impact:** Critical — a rate or rule change can rewrite an existing customer’s contractual return and make historic calculations impossible to reproduce.

**What commonly goes wrong:** Booking stores only `productId`, then accrual or payout reads the product’s current rate, category boost, compounding, currency, or penalty rule. Product rows are edited in place or retired without preserving the version used by existing accounts.

**Warning signs in this repository:** `FdAccount` has `productId` and optional `appliedRateOutcomeId`, but the active rate and rule policy are nullable; `product` effective dates are `VARCHAR(30)`; the v1 controller accepts untyped `Map<String,Object>`; no immutable FD product snapshot table or policy version is visible.

**Prevention:** At booking/funding, validate an active product/rate outcome and persist the exact contractual inputs: principal, currency, rate, day-count, compounding, payout, tenure, penalty, eligibility decision, product version, and effective dates. Treat the snapshot as immutable after activation. Product maintenance must create a new version or effective-dated row; it must not mutate the terms of an active account. Recalculate only from the stored snapshot.

**Verification approach:** Book an account, change/deactivate the product, then accrue and mature it; assert the original terms and amounts remain unchanged. Test that an out-of-effective-period rate cannot be selected. Compare audit/snapshot values with the response and ledger entries.

**Roadmap phase:** Phase 2 — PostgreSQL persistence and booking.

### 6. Making accrual and maturity jobs non-idempotent

**Severity/impact:** Critical — duplicate interest, duplicate payout, duplicate maturity transitions, or duplicate renewal can create direct financial loss.

**What commonly goes wrong:** A scheduler finds all active accounts and inserts an accrual row every time it runs. Multiple application instances, overlapping manual triggers, retries after a timeout, and a scheduler restart all repeat the same business date. Updating a status without a unique business key does not prevent duplicate ledger effects.

**Warning signs in this repository:** `FdBatchScheduler` runs the same service from cron and `BatchTriggerController` exposes manual triggers; the service currently throws, so no idempotency contract exists; `fd_interest_accrual` has no visible unique constraint on `(account_id, accrual_date)`; `fd_interest_payout` has no visible idempotency key; `fd_account_events` only has a published flag.

**Prevention:** Define a deterministic operation key, normally `(account_id, accounting_date, operation_type, policy_version)` for accrual and `(account_id, maturity_event)` for maturity/payout. Enforce uniqueness in PostgreSQL, perform insert-or-ignore/claim semantics inside a transaction, and make reruns return the existing result. Lock or claim work in bounded batches. Separate “selected”, “posted”, and “failed” states where payout has an external side effect. Never use a JVM boolean or scheduler single-threading as the correctness mechanism.

**Verification approach:** Run two scheduler invocations concurrently across multiple application contexts against PostgreSQL. Kill or time out a worker after the ledger insert and retry it. Assert one accrual per business date, one maturity transition, one payout instruction, and a reconciled cumulative amount. Query for duplicate business keys as a standing health check.

**Roadmap phase:** Phase 3 — Accrual and maturity processing; payout posting continues in Phase 4.

### 7. Retrying financial work without distinguishing safe retries from duplicate effects

**Severity/impact:** Critical — a timeout can cause a client or worker to submit the same booking, closure, or payout twice.

**What commonly goes wrong:** Generic retry advice retries all exceptions, including validation failures and a transaction whose commit outcome is unknown. A request is retried after the server committed but before the response arrived. A nested transaction commits a ledger row while the outer operation later rolls back or reports failure.

**Warning signs in this repository:** Batch endpoints say “triggered and completed successfully” immediately after methods that currently throw; there is no request idempotency key in `CreateFdRequest` or the v1 skeleton; `uuid` is generated automatically but is not documented as a deduplication key; no retry classification or job-run record is visible.

**Prevention:** Require idempotency keys for booking, funding, closure approval, payout, and manual batch commands; scope keys to actor and operation and persist the request fingerprint/result. Retry only transient connection/deadlock/serialization failures, and retry the complete transaction from the beginning. Do not retry non-idempotent downstream calls without an outbox or provider idempotency key. Keep the financial state mutation and its audit/ledger rows in one `@Transactional` boundary; use `REQUIRES_NEW` only with an explicit reason and sized connection pool.

**Verification approach:** Submit the same request concurrently and after an induced response timeout. Inject deadlock/serialization and connection failures at each transaction boundary. Assert one domain result, stable replay response, no orphan ledger row, and no false success message. Test that failed validation is never retried.

**Roadmap phase:** Phase 2 for booking idempotency; Phase 3/4 for batch and payout retries.

### 8. Allocating account numbers with `MAX(...) + 1` and JVM synchronization

**Severity/impact:** Critical — concurrent instances can issue the same account number; collision failures can strand a funded booking or cause clients to retry into duplicate accounts.

**What commonly goes wrong:** Two transactions query the same maximum, calculate the same next sequence, and insert. `synchronized` protects only one object in one JVM. Lexicographic `MAX(accountNumber)` and malformed suffixes can also produce incorrect ordering or reset the sequence.

**Warning signs in this repository:** `AccountNumberGenerator.generateNextAccountNumber` is `synchronized`, queries `findMaxAccountNumberByPrefix`, defaults a blank branch to `000`, parses a suffix in Java, and has only unit tests with a mocked repository. `fa_account_number` is unique, but uniqueness only rejects the loser; it does not allocate a safe next number.

**Prevention:** Use a PostgreSQL sequence or a counter table keyed by branch, incremented atomically with `UPDATE ... RETURNING`/`INSERT ... ON CONFLICT`, and format the returned number. Validate branch code instead of silently rewriting it. Keep the unique constraint and translate collisions into a controlled retry only if the operation remains idempotent. Avoid deriving business sequence from display strings.

**Verification approach:** Run hundreds of concurrent bookings across two JVM/application contexts and assert uniqueness, contiguous-or-explicitly-gapped policy, correct branch prefix, and no duplicate domain account. Add a PostgreSQL test for sequence/counter rollback semantics and a failure test for malformed/unknown branch codes.

**Roadmap phase:** Phase 2 — PostgreSQL persistence and booking.

### 9. Allowing concurrent state transitions to overwrite each other

**Severity/impact:** Critical — closure, maturity, renewal, accrual, and payout can race and produce impossible states or double settlement.

**What commonly goes wrong:** Each endpoint reads an account as `ACTIVE`, checks a rule in Java, then writes a new status without a version predicate or row lock. A maturity job and customer closure request can both succeed. A stale entity can overwrite a newer change.

**Warning signs in this repository:** `FdAccount` has a string `fa_version` column but no visible JPA `@Version`; status is a free persisted enum string; repositories expose only `findByStatus` and no locked/claiming queries; legacy controller performs ownership lookup and mutation in separate calls.

**Prevention:** Model an explicit transition table and allowed source states. Use a numeric optimistic-lock column (`@Version`) or `SELECT ... FOR UPDATE` for each financial state mutation; use a conditional update such as `WHERE status = 'ACTIVE'` where appropriate. Lock account rows before calculating settlement, and order locks consistently. Ensure stale updates fail with a domain conflict rather than silently winning.

**Verification approach:** Use barriers/latches to race maturity against premature closure, two closure approvals, and accrual against payout. Assert exactly one legal transition, one settlement, and a clear conflict response for the loser. Test optimistic-lock retry only when the operation is safe to re-evaluate.

**Roadmap phase:** Phase 3 for maturity/accrual; Phase 4 for closure/payout.

### 10. Authorizing the route but not the account owner or action target

**Severity/impact:** Critical — any authenticated customer could read or mutate another customer’s FD if object-level checks are missed; staff parameters can become an impersonation path.

**What commonly goes wrong:** Controllers use a broad role check such as “customer or staff” and trust a path ID, query parameter, caller-supplied holder ID, or username. Authorization is applied only to HTTP routes, while services and batch paths remain callable without ownership checks. Returning a generic “not found” versus “forbidden” is also handled inconsistently and can leak account existence.

**Warning signs in this repository:** The legacy booking endpoint accepts `customerUsername` and allows staff to choose a target; `FdAccountController` checks ownership in the controller after loading by account number; `FdV1ContractController` has no visible authorization annotations at all; v1 list operations accept caller-provided `holder_id`; internal sync routes return success without an authentication boundary.

**Prevention:** Resolve the authenticated principal to the persisted FD user reference server-side. Put ownership/staff authorization in the service layer as well as the HTTP boundary, and make repository queries ownership-scoped (`findByIdAndPrimaryHolderId` or holder-role join) rather than load-then-check. Separate customer commands from staff/admin commands; never accept a client-controlled role or target identity for self-service. Require explicit internal authentication and audit actor/source for sync and batch endpoints. Deny by default and test every v1 operation.

**Verification approach:** Run negative tests for every account read, transaction, accrual, payout, closure, holder, notice, statement, and event operation using a different customer, untrusted holder ID, staff actor, disabled actor, and unauthenticated caller. Test direct service invocation, not only MockMvc. Verify that response status and body do not expose another customer’s data.

**Roadmap phase:** Phase 5 — Security and compatibility hardening, before any v1 route is enabled.

### 11. Treating an audit log as mutable metadata rather than financial evidence

**Severity/impact:** Critical — the bank cannot explain who booked, changed, accrued, closed, approved, or paid an account; disputed balances cannot be reconstructed.

**What commonly goes wrong:** A mutable account row is considered the history. Ledger rows omit actor, request/job ID, policy version, prior/new state, reason, and correlation ID. Audit records are written after an external payout or are deleted/cascaded with the account. Sensitive data is logged in clear text.

**Warning signs in this repository:** Tables have generic `created_by`, timestamps, and UUIDs, but FD transaction/accrual/event entities do not map those audit columns; `fd_account_events` has payload but no visible actor/action/idempotency fields; `FdTransaction` has no transaction direction or immutable sequence; the API skeleton exposes event creation without policy.

**Prevention:** Separate the current account projection from append-only financial postings and an append-only lifecycle audit trail. Every mutation records actor/system job, authenticated subject, request/idempotency key, correlation ID, timestamp, previous state, new state, reason, applied terms/policy version, and source channel. Use database permissions or application rules to prevent update/delete of posted financial rows. Outbox/audit insertion must commit with the state mutation; external payout evidence must be linked after provider confirmation without rewriting the original posting.

**Verification approach:** Attempt to update/delete posted transaction, accrual, closure, and audit rows through repositories and SQL roles. Reconstruct account balance and lifecycle history from append-only records only. Assert actor/correlation metadata is present for API, scheduler, and manual batch paths, while secrets, OTPs, and full identity documents never enter logs.

**Roadmap phase:** Phase 4 — Closure, payout, and audit.

### 12. Enabling payout or renewal before settlement is idempotent and externally safe

**Severity/impact:** Critical — the internal database can say “paid” while the bank account was not credited, or the provider can receive the same payout twice.

**What commonly goes wrong:** A transaction commits a `PAID` state before calling a provider, or calls the provider inside a long database transaction and retries on timeout without knowing whether the provider accepted it. Auto-renewal creates a new FD before the prior payout/closure is finalized.

**Warning signs in this repository:** `fd_interest_payout` has a status but no visible provider reference/idempotency key; `fd_payout_accounts` stores destination details without verification/status history; maturity instructions and payouts are placeholders; `autoRenewal` is a boolean on the account without an explicit renewal event model.

**Prevention:** Use a durable payout instruction with a unique business key and `PENDING/PROCESSING/SUCCEEDED/FAILED/UNKNOWN` state. Pass the same idempotency key to the payment provider, reconcile unknown outcomes, and only mark the FD settled after durable provider confirmation. Keep auto-renewal as an explicit, audited transition with a new account/snapshot and a link to the matured account. Do not expose “completed” until all required effects are durably recorded.

**Verification approach:** Simulate provider success, rejection, timeout-after-acceptance, timeout-before-acceptance, and worker crash. Replay the worker and assert one provider instruction and one internal settlement. Verify that a failed payout leaves the account recoverable and does not permit an untracked second renewal.

**Roadmap phase:** Phase 4 — Closure, payout, and audit.

## Moderate Pitfalls

### 13. Relying on nullable FD financial columns without explicit lifecycle invariants

**Severity/impact:** High — null rates, maturity dates, currency IDs, or maturity amounts can crash reports or produce incomplete settlements.

**What commonly goes wrong:** The schema permits nullable `fa_interest_rate`, `fa_maturity_date`, and `fa_maturity_amount`, and the entity mirrors that permissiveness. Code assumes all active accounts are fully booked. Reports then dereference nulls or calculate with missing terms.

**Warning signs in this repository:** `CONCERNS.md` already identifies nullable rate/maturity report failures; `FdAccount` maps these fields as nullable; `getActiveAccounts()` returns every `ACTIVE` row without a readiness predicate.

**Prevention:** Define which fields are required at each status. Keep `PENDING` as the only state allowed to lack funding/terms; require snapshot, currency, principal, maturity, and rate before `ACTIVE`. Add PostgreSQL `CHECK` constraints where possible and service validation for cross-table invariants. Query “ready active accounts” explicitly and quarantine invalid legacy rows for repair.

**Verification approach:** Seed null and inconsistent rows and assert they cannot become active or be processed. Run reports and jobs against quarantined data; they should return diagnostics, not partially pay or crash the whole batch. Add migration preflight queries for existing violations.

**Roadmap phase:** Phase 2 for constraints; Phase 3 for job quarantine behavior.

### 14. Making the ledger reconstructability depend on mutable projections

**Severity/impact:** High — balances and maturity amounts drift if a projection update is missed, retried, or overwritten.

**What commonly goes wrong:** Code stores a current balance or cumulative amount on the account and treats it as authoritative, while transaction rows and accrual rows are incomplete or use inconsistent signs. A job rerun updates the projection but does not create the missing posting—or creates the posting twice.

**Warning signs in this repository:** `FdAccount.accruedInterest` is `@Transient`; `fd_interest_accrual` has cumulative amount but no visible uniqueness; `FdTransaction` has `balanceAfter` but no direction/sequence; there is no repository query for an authoritative account statement or reconciliation.

**Prevention:** Decide which is authoritative: preferably immutable postings/accrual records with derived projections. Define signed debit/credit semantics and a deterministic ordering key. Update projections in the same transaction as postings, and provide a repair/reconciliation command that compares projection to the append-only source. Never “fix” a mismatch by editing historical rows.

**Verification approach:** Rebuild balances from an account’s postings and compare to every stored projection after booking, accrual, closure, payout, and retry. Inject failures between posting and projection update and verify transaction rollback or a detectable repair state.

**Roadmap phase:** Phase 3 for accrual ledger; Phase 4 for settlement reconciliation.

### 15. Assuming Spring `@Transactional` alone makes a batch safe

**Severity/impact:** High — one huge transaction can hold locks, exhaust memory, or roll back thousands of successful account calculations after one bad row; self-invocation can also bypass proxy behavior.

**What commonly goes wrong:** A scheduled method loads all active accounts, performs external calls, and relies on the annotation on a caller or private method. A failure is caught and logged, leaving an operator believing the run completed. A nested `REQUIRES_NEW` transaction is added without accounting for connection-pool pressure.

**Warning signs in this repository:** `FdAccountService.getActiveAccounts()` returns an unbounded list; scheduler catches `Exception` and logs only; legacy batch methods are invoked from scheduler/controller; no batch run or per-account outcome is persisted.

**Prevention:** Use a short transaction per claimed account or bounded chunk, with deterministic pagination/claiming and a durable batch-run record. Keep external I/O outside the financial transaction or use an outbox. Let failures be classified and surfaced; do not convert a partial failure into a success log. Put transactional methods on separate Spring beans when proxying matters, and size the pool before using `REQUIRES_NEW`.

**Verification approach:** Process a fixture larger than one batch page, inject one account failure, and assert other accounts are handled according to the chosen policy and the run is marked partial/failed. Inspect lock duration, connection use, and restart behavior. Add a test proving the transaction boundary is active through the public bean call.

**Roadmap phase:** Phase 3 — Accrual and maturity processing.

### 16. Assuming a JVM scheduler is a distributed scheduler

**Severity/impact:** High — every replica can execute the same daily financial job; a single slow run can overlap the next run.

**What commonly goes wrong:** `@Scheduled` is enabled on every application instance, with no leader election, distributed lock, database claim, or job-run uniqueness. Manual endpoints run alongside cron without coordination.

**Warning signs in this repository:** `FdBatchScheduler` is a singleton component with midnight/1 AM cron expressions; manual `/api/admin/batch` endpoints call the same operations; there is no job lock table, lock provider, or configured scheduler timezone.

**Prevention:** Prefer database-level idempotency and row claiming as the primary safety net, plus a distributed lock or single batch worker for efficiency. Record job name, logical business date, run ID, owner, heartbeat, start/end, and outcome. Make manual triggers submit/replay a run rather than bypassing the same coordinator. Add a timeout and recovery policy for abandoned runs.

**Verification approach:** Start multiple application instances against one PostgreSQL database and invoke cron/manual paths concurrently. Assert one logical run per operation/date and that abandoned claims can be recovered. Verify metrics and alerts distinguish skipped, running, partial, and failed runs.

**Roadmap phase:** Phase 3 — Accrual and maturity processing.

### 17. Using weak schema constraints as a substitute for domain validation

**Severity/impact:** High — invalid currency, negative amounts, impossible terms, duplicate primary payout accounts, and illegal statuses can enter through any adapter, SQL client, or future API.

**What commonly goes wrong:** Bean Validation is applied only to the legacy request, while the v1 controller accepts `Map<String,Object>` and the database has few FD `CHECK`/unique constraints. Foreign keys prove existence but not that a product is active, a rate is effective, or an amount is positive.

**Warning signs in this repository:** `CreateFdRequest` validates minimum amount and term but accepts arbitrary frequency/branch strings; v1 operations use untyped maps; V1 creates FD numeric columns and foreign keys but no visible checks for scale, positive principal, status transitions, or one primary payout account.

**Prevention:** Use typed versioned DTOs and domain value objects/enums. Validate at API, service, and database boundaries. Add named PostgreSQL checks for positive monetary amounts, valid status/frequency domains, nonnegative tenure, maturity after open date, and penalty/net payable relationships. Add partial unique indexes for business rules such as one active primary payout account. Keep cross-row rules in transactional services/constraints suited to PostgreSQL, not unverifiable `CHECK` expressions.

**Verification approach:** Bypass the controller with repository/JDBC tests and confirm invalid data is rejected. Test unknown enum values, excessive decimal scale, negative/overflow values, blank branch codes, and invalid effective dates. Run schema inspection assertions against the live PostgreSQL catalog.

**Roadmap phase:** Phase 2 — PostgreSQL persistence and booking.

### 18. Applying a migration that works only on a clean database

**Severity/impact:** High — production deployment can fail midway, drop constraints incorrectly, or silently leave old data incompatible with the new FD lifecycle.

**What commonly goes wrong:** A migration assumes exact prior column types, drops all foreign keys by broad catalog selection, changes `SERIAL`/integer IDs without checking every dependent column, or adds `NOT NULL`/unique constraints before backfilling existing rows. Editing an already-applied version makes environments diverge.

**Warning signs in this repository:** V2 and V5 create temporary FK backups and broadly drop/re-add constraints; V4 changes one address ID without an obvious dependent-column migration; V1 contains many `SERIAL` IDs while later migrations selectively convert only some tables; V3 leaves `refresh_tokens.user_id INT` while users are converted to `BIGINT`; the current migrations contain no visible FD backfill/constraint validation strategy.

**Prevention:** Treat applied Flyway versions as immutable and add forward-only migrations. For each schema change, inventory live dependencies, backfill in bounded steps, validate data, add constraints, then switch code. Use explicit named constraints and targeted type changes; verify every FK column and JPA mapping has the same type. Run `flyway validate`, migrate, and Hibernate schema validation against a PostgreSQL clone containing representative legacy data. Plan expand/contract migrations for columns used by old and new code.

**Verification approach:** Apply all migrations to an empty PostgreSQL database and separately to a copy seeded with legacy rows, nulls, large IDs, and existing FK relationships. Compare `information_schema`/`pg_catalog` types, constraints, indexes, and Flyway checksums. Test rollback/recovery procedure in a disposable environment; do not rely on H2.

**Roadmap phase:** Phase 2 — PostgreSQL persistence and booking; rollout gates continue in Phase 5.

### 19. Losing data or correctness through entity/schema compatibility aliases

**Severity/impact:** High — old callers can appear to work while writing the wrong conceptual field, losing tenure precision or bypassing the normalized FD reference model.

**What commonly goes wrong:** Compatibility getters are treated as the domain API. `customerId` is confused with `primaryHolderId`; days are converted to approximate months; `LocalDate` is converted to UTC midnight; legacy services and normalized v1 services mutate different models. A field alias hides a required migration instead of making it observable.

**Warning signs in this repository:** `FdAccount.getCustomerId()` aliases `primaryHolderId`; `getTermDays()` uses 30-day months and `setTermDays()` truncates; `getBookingDate()`/`setBookingDate()` and maturity accessors translate between date and timestamp; `FdAccountService` deliberately throws for legacy operations while `FdAccountController` still exposes them.

**Prevention:** Make `/fd/v1` typed DTOs and normalized reference IDs authoritative. Put legacy adapters at the edge, translate explicitly, reject lossy conversions, and emit migration telemetry. Preserve old routes only when they delegate to the same application service and have compatibility tests; otherwise return a clear versioned migration error, never a misleading success. Remove aliases only after repository-wide caller migration and a deprecation window.

**Verification approach:** Contract-test old and v1 routes against the same fixture and compare domain outcomes. Test non-30-day terms, non-UTC offsets, multi-holder accounts, and unknown reference IDs. Add static checks that legacy services do not contain production `throw new BankingException("...unavailable...")` once the phase claims completion.

**Roadmap phase:** Phase 5 — Security and compatibility hardening.

### 20. Returning `501` or `204 No Content` in a route that callers interpret as success

**Severity/impact:** High — clients, schedulers, or upstream reference systems can believe an FD was created/synchronized when nothing was stored.

**What commonly goes wrong:** A broad route skeleton is deployed before business semantics exist. Internal sync endpoints return 204 without persistence, while the legacy route throws. Consumers build retries and state transitions around ambiguous responses.

**Warning signs in this repository:** Most `FdV1ContractController` methods return `501 NOT_IMPLEMENTED`; internal product/currency/user/role/rule sync endpoints return 204 without storing anything; legacy `/api/fd` booking, lookup, withdrawal, accrual, and maturity methods throw migration exceptions.

**Prevention:** Do not advertise an operation until its authorization, persistence, idempotency, and error contract exist. Use explicit capability/version negotiation and a stable error schema for unsupported operations. For implemented sync, return an idempotent result containing reference ID/version and whether data was created, updated, or unchanged. Remove or isolate dead legacy routes only after compatibility tests and a documented migration response.

**Verification approach:** Contract-test every OpenAPI operation for status, schema, authorization, persistence, and replay behavior. Assert an internal sync response is not success unless the expected row/version exists. Scan deployed route mappings for placeholder response helpers before release.

**Roadmap phase:** Phase 5, with each operation enabled only after the phase that implements its underlying lifecycle.

### 21. Testing only calculator examples and mocks instead of PostgreSQL lifecycle behavior

**Severity/impact:** High — the application can pass unit tests while failing on numeric precision, PostgreSQL locking, Flyway schema, enum mapping, transaction rollback, or authorization boundaries.

**What commonly goes wrong:** Tests cover construction and a few happy-path calculations, use mocked repositories for account numbering, and never run v1 endpoints or scheduled jobs against real PostgreSQL. H2 differences hide `TIMESTAMPTZ`, numeric scale, indexes, locking, and migration behavior.

**Warning signs in this repository:** FD coverage is limited to `AccountNumberGeneratorTest` and four calculator cases; `FdV1ContractController` has no tests; `TESTING.md` reports no integration tests or coverage gate; `CONCERNS.md` identifies missing accrual, maturity, closure, migration, null-field, and multi-instance tests.

**Prevention:** Add a PostgreSQL-backed integration suite using disposable containers or an equivalent real PostgreSQL test service. Test migrations from empty and legacy fixtures, MockMvc/service authorization, transaction rollback, concurrent booking, idempotent jobs, maturity boundaries, and ledger reconciliation. Keep pure calculation golden tests separate from persistence tests. Make `mvn verify` fail on the critical FD suite and publish coverage/contract artifacts.

**Verification approach:** CI must run Flyway migrations and Hibernate validation against PostgreSQL, then run parallel/concurrency tests and the API contract suite. Include a mutation/failure-injection test for duplicate jobs and unknown payout outcomes. A passing `mvn test` with an embedded/mock database alone is not sufficient evidence.

**Roadmap phase:** Phase 5 — Security and compatibility hardening, with tests introduced alongside Phases 1–4 rather than deferred entirely.

## Minor Pitfalls

### 22. Using server-local current time in reports and jobs

**Severity/impact:** Medium — report windows and due accounts differ between environments or around midnight.

**Warning signs:** `ReportService` calls `LocalDate.now()` directly; scheduler cron has no timezone; `@PrePersist` uses `OffsetDateTime.now(UTC)` rather than a shared clock.

**Prevention:** Inject `Clock` and business `ZoneId`; pass an explicit `asOf` date into calculation and batch services. Persist the logical processing date separately from execution timestamp.

**Verification approach:** Run tests with clocks before/after UTC and local midnight, at DST transitions, and with a backdated as-of date. Assert repeatable report and batch selection.

**Roadmap phase:** Phase 1 for the time policy; Phase 3 for jobs.

### 23. Treating UUIDs as idempotency keys without binding them to an operation

**Severity/impact:** Medium — a random row UUID identifies a record but does not prevent a repeated business request.

**Warning signs:** `@PrePersist` generates `fa_uuid`, `ft_uuid`, and accrual UUIDs, but request DTOs have no client key and repositories expose no lookup by business operation key.

**Prevention:** Distinguish resource UUID, request idempotency key, provider idempotency key, and correlation ID. Persist the key with actor, operation, request hash, result, and expiry/retention policy; enforce a unique constraint on the correct scope.

**Verification approach:** Replay the same key with the same and different payloads. The same payload returns the original result; a different payload is rejected as a key conflict; a new key creates a distinct operation.

**Roadmap phase:** Phase 2 for booking; Phase 3/4 for jobs and payouts.

### 24. Allowing high-volume reports to load every active entity

**Severity/impact:** Medium — operational reports compete with accrual/maturity work and can exhaust memory or hold stale snapshots.

**Warning signs:** `FdAccountService.getActiveAccounts()` returns an unbounded entity list; `ReportService` aggregates active accounts in application memory.

**Prevention:** Add indexed, bounded projections and database-side aggregates; page batch work by primary key; select only fields needed for calculation. Keep report reads read-only and separate from mutation transactions.

**Verification approach:** Run explain plans and load tests with realistic account counts; assert no unbounded entity query is used by reporting or batch paths.

**Roadmap phase:** Phase 3 for processing queries; later reporting hardening can share the indexes.

## Phase-Specific Warnings

| Phase topic | Likely pitfall | Mitigation and exit evidence |
|---|---|---|
| Phase 1: financial policy | Mixing percent/decimal rate units, 365-day assumptions, calendar dates, and elapsed timestamps | Publish a versioned calculation policy; golden tests cover leap years, boundaries, compounding, penalty, scale, and rounding. |
| Phase 2: booking/persistence | Mutable product terms, unsafe account numbering, partial booking, weak database constraints | Persist immutable terms snapshot; PostgreSQL counter/sequence; one transaction for validation, account, funding posting, and audit; concurrent booking test passes. |
| Phase 3: accrual/maturity | Duplicate jobs, overlapping transitions, giant transactions, silent partial failures | Unique business keys, row claim/lock, durable batch-run records, retry classification, explicit logical date, and rerun/recovery tests. |
| Phase 4: closure/payout/audit | Double payout, premature approval, untraceable adjustments, external timeout ambiguity | Append-only postings and audit, durable payout state, provider idempotency, reconciliation, and failure-injection tests. |
| Phase 5: security/compatibility | IDOR, staff target spoofing, unprotected v1 routes, 501/204 false success, migration drift | Service-layer ownership checks, negative authorization matrix, typed contract tests, Flyway validate, legacy fixture migration, and no-placeholder release scan. |

## Detection Checklist for This Repository

Before enabling any FD endpoint or scheduler, the roadmap should require:

1. `grep`/static checks show no FD monetary `double`, `doubleValue`, or `Math.pow` path outside a documented adapter.
2. A single injected clock, timezone, day-count, rate-unit, and rounding policy is used by estimate, booking, accrual, maturity, and closure.
3. PostgreSQL constraints and indexes enforce positive money, unique business operation keys, valid references, and account-number uniqueness.
4. Two application instances can book and process the same account without duplicate numbers, accruals, transitions, or payouts.
5. Every state mutation has an allowed source state, optimistic/pessimistic concurrency control, actor, request/job ID, and audit event.
6. Customer reads and writes are ownership-scoped in repository/service queries; staff actions use separate authorization and audit rules.
7. Flyway is validated and executed against both an empty database and a representative legacy PostgreSQL fixture; H2-only evidence is rejected.
8. Every exposed v1 operation has a typed request/response, explicit unsupported behavior, persistence assertion, authorization test, and replay test.

## Sources

**HIGH confidence — official documentation:**

- Oracle Java SE 17 `BigDecimal`: decimal arbitrary-precision arithmetic, explicit rounding, and the warning that `BigDecimal(double)` reflects binary floating-point representation: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html
- Spring Data JPA locking: repository methods can declare JPA lock modes; locking must be explicit rather than inferred from `@Transactional`: https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html
- Spring Framework scheduling: scheduler/executor abstractions do not themselves provide distributed job coordination: https://docs.spring.io/spring-framework/reference/integration/scheduling.html
- Spring Framework transaction propagation: physical transaction boundaries, rollback-only behavior, and connection-pool risks of `REQUIRES_NEW`: https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html
- Spring Security method security: service-layer authorization, object-aware checks, and the warning that unannotated methods are not secured: https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html
- PostgreSQL transaction isolation: Read Committed permits anomalies for multi-step decisions; Repeatable Read/Serializable can require complete transaction retries; sequences are not rolled back: https://www.postgresql.org/docs/current/transaction-iso.html
- PostgreSQL constraints: use named checks, unique/partial indexes, foreign keys, and appropriate delete behavior; `CHECK` constraints cannot safely enforce cross-row rules: https://www.postgresql.org/docs/current/ddl-constraints.html
- PostgreSQL date/time functions: calendar intervals, DST behavior, timestamp time zones, and half-open `OVERLAPS` semantics: https://www.postgresql.org/docs/current/functions-datetime.html
- Flyway versioned migrations: applied migrations are tracked by version/checksum and should be changed by adding a new migration, not editing an applied script: https://documentation.red-gate.com/flyway/flyway-concepts/migrations/versioned-migrations

**HIGH confidence — repository evidence:**

- `.planning/PROJECT.md` — FD lifecycle, compatibility, financial correctness, PostgreSQL, scheduler, and regression constraints.
- `.planning/codebase/CONCERNS.md` — known `double` calculations, placeholder/throwing FD paths, scheduler failures, ownership gaps, account-number race, nullable data, and test gaps.
- `.planning/codebase/TESTING.md` — current absence of PostgreSQL integration, contract, concurrency, and runnable frontend tests; backend FD tests are limited.
- `BACKEND/src/main/java/com/bank/calculator/service/FdCalculatorService.java` — binary floating-point calculations and final-only rounding.
- `BACKEND/src/main/java/com/bank/fd/entity/FdAccount.java` — compatibility aliases, 30-day conversion, UTC date translation, nullable lifecycle fields, and transient accrual.
- `BACKEND/src/main/java/com/bank/fd/service/AccountNumberGenerator.java` — synchronized `MAX`-based allocation.
- `BACKEND/src/main/java/com/bank/fd/scheduler/FdBatchScheduler.java` and `BatchTriggerController.java` — overlapping scheduled/manual execution paths and swallowed failures.
- `BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java` — untyped placeholder operations and no-op internal synchronization routes.
- `BACKEND/src/main/resources/db/migration/V1__bankingtech_public_schema.sql` through `V5__*.sql` — normalized FD tables, nullable financial columns, numeric scales, missing visible business uniqueness, and broad ID/FK alignment migrations.
