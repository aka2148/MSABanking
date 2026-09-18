# Architecture Patterns

**Domain:** Fixed-deposit lifecycle in an existing Spring Boot modular monolith
**Project:** Banking Lab
**Researched:** 2026-09-18
**Overall confidence:** HIGH for the current-repository assessment; MEDIUM for target design choices that require product and settlement-policy confirmation.

## Recommended Architecture

Keep the backend as a modular monolith, but make `fd` an application-owned bounded context with explicit ports to `product`, `customer/security`, and settlement infrastructure. The existing package-by-layer shape can be retained during this milestone; the important change is ownership and call direction, not a broad package rewrite.

```text
HTTP /fd/v1                 HTTP /api/fd (legacy)       scheduler/admin trigger
       |                            |                         |
       v                            v                         v
FdV1Controller          LegacyFdCompatibilityAdapter       FdBatchCoordinator
       |                            |                         |
       +----------------------------+-------------------------+
                                    v
                         FD application use cases
             Booking | Funding | Accrual | Maturity | Closure | Payout
                                    |
                 authorization + idempotency + audit ports
                                    |
       +----------------------------+-----------------------------+
       v                            v                             v
FD repositories/entities     ProductTermsPort              Customer/actor port
       |                            |                             |
       +----------------------------+-----------------------------+
                                    v
                         PostgreSQL via Flyway schema
```

The `/fd/v1` OpenAPI file and the existing Flyway schema remain the external and persistence compatibility boundaries. Controllers must not call repositories directly, return JPA entities, or implement state transitions. The legacy `/api/fd` controller should become a thin adapter over the same application services, not a second implementation.

### Component Boundaries

| Component | Responsibility | Communicates With |
|-----------|----------------|-------------------|
| `fd.api.controller.FdV1Controller` | Bind typed request DTOs, validate HTTP input, map use-case results/errors to the existing OpenAPI responses, enforce coarse route roles | FD application services, `ActorContext` resolver |
| `fd.api.LegacyFdCompatibilityAdapter` | Translate username/term-days/legacy field names and legacy response shapes to current commands; preserve `/api/fd` during migration | FD application services, customer/reference adapter |
| `fd.application.AccountBookingService` | Resolve product/rate/currency/holder, calculate dates, snapshot immutable booking terms, create `PENDING` account, and record the booking audit event | `ProductTermsPort`, reference repositories, account/holder/transaction/idempotency/audit repositories |
| `fd.application.FundingService` | Validate and post the initial funding transaction exactly once; activate a pending account only after funding rules pass | Account and transaction repositories, state machine, idempotency, audit |
| `fd.application.AccountLifecycleService` | Own allowed transitions, mutable account updates, mature/close/renew orchestration, and optimistic/pessimistic concurrency policy | Account repository, accrual/payout/closure services, audit |
| `fd.application.AccrualService` | Compute deterministic interest for one account and business date, persist one accrual row, and expose cumulative history | Account repository, accrual repository, product/rate snapshot, audit |
| `fd.application.MaturityService` | Accrue through the maturity date, transition due accounts, apply maturity instructions, and create a renewal or payout work item | Account/lifecycle, accrual, instruction, payout, transaction, audit services |
| `fd.application.ClosureService` | Calculate premature-closure penalty from the frozen terms/rule outcome, enforce approval, and produce the closure settlement record | Account, closure, transaction, payout, rule/reference, audit repositories |
| `fd.application.PayoutService` | Validate payout destination, create or complete an idempotent payout, and post the corresponding ledger transaction | Payout-account/payout/transaction repositories, `SettlementPort`, audit |
| `fd.application.FdQueryService` | Read-only account, transaction, accrual, payout, event, and reference views with owner/staff filtering and pagination | Repositories/projections only |
| `fd.application.ReferenceSyncService` | Implement `/internal/sync/*` as authenticated upserts into the local `fd_*_ref` tables; reject stale or conflicting revisions | Product/user/currency/role/rule reference repositories, audit |
| `fd.domain.FdStateMachine` | Pure transition rules for `PENDING`, `ACTIVE`, `MATURED`, `CLOSED`, and `PREMATURELY_CLOSED`; no Spring or persistence calls | Lifecycle services |
| `fd.domain.InterestPolicy` / `ClosurePolicy` | Pure `BigDecimal` calculations over a booking snapshot and an explicit `Clock`/business date | Accrual and closure services |
| `fd.persistence` repositories | Encapsulate JPA access, locking queries, pagination, and projections; expose no controller-facing entities | PostgreSQL tables created by Flyway |
| `fd.audit.AuditService` | Append an immutable account event for every financial or state-changing command, including actor, request id, before/after state, terms, and amounts | `fd_account_events`, transaction boundary |
| `fd.scheduler.FdBatchCoordinator` | Thin trigger only; claim bounded work and invoke one-account use cases with a system actor | Accrual and maturity application services |
| `product` module | Own product administration; expose a read-only `ProductTermsPort` to FD rather than sharing product entities | FD booking/rate resolver |
| `security/customer` boundary | Resolve authenticated principal to user/reference identity and staff/customer role; do not let FD inspect raw `SecurityContextHolder` | Controllers and authorization service |

### Current Code vs Target Design

| Area | Current repository | Target for this milestone |
|------|-------------------|--------------------------|
| FD HTTP | `FdV1ContractController` accepts `Map<String,Object>` and returns `501`; `/api/fd` invokes intentionally unavailable service methods | Replace maps with contract DTOs and delegate every implemented v1 operation to application use cases; legacy routes delegate through an adapter |
| Booking | `FdAccountService.createFdAccount` always throws; `FdAccount` defaults to `ACTIVE` | Create a validated `PENDING` booking, persist product/rate/currency/tenure snapshot, then activate only through a successful idempotent funding operation |
| Product/rates | `FdProduct` maps only `fd_product_ref`; rate and rule fields are `@Transient`, so `FdProductService` cannot actually validate a rate | Add a `ProductTermsPort` and resolver backed by authoritative product/rule data or a verified reference projection; never trust a client-supplied rate without validation; snapshot the resolved outcome on the account |
| State | `FdStatus` exists but arbitrary setters and the contract's status patch are unconstrained | Make transitions explicit and reject illegal or repeated transitions with deterministic conflict/idempotent responses |
| Accrual/maturity | Scheduler calls methods that always throw; no accrual entity/repository exists | Add accrual/maturity services, repositories, unique business keys, bounded batch processing, and per-account transactions |
| Authorization | Controllers read `SecurityContextHolder` and perform ad hoc username ownership checks; v1 controller has no method guards | Resolve an `ActorContext` once at the edge, enforce coarse roles at routes and ownership/staff/internal policy again in application services |
| Idempotency | Existing UUID columns do not provide request idempotency; accrual/payout tables lack business-key uniqueness | Add an idempotency record or equivalent unique operation keys and database uniqueness for date-based financial effects |
| Audit | `fd_account_events` exists but all v1 event operations are placeholders and its `is_published` flag is not a complete audit contract | Write append-only audit events in the same transaction as each state/financial mutation; treat publication as a later outbox concern |

## Data Model and Ownership

### Product and Rate Data

`fd_accounts` already contains the fields needed for a durable booking snapshot: `fa_product_id`, `fa_applied_rate_outcome_id`, principal, currency, tenure, interest rate, compounding frequency, payout frequency, open date, maturity date, and maturity amount. Those values must be copied at booking time and never recomputed from a subsequently edited product for an existing account.

The current `com.bank.product.entity.FdProduct` is only a local projection of `fd_product_ref`; its rate and term properties are transient. Therefore `FdProductService` is not a sufficient pricing authority. Introduce this boundary:

```java
public interface ProductTermsPort {
    ProductTerms resolveActiveTerms(ProductTermsQuery query, LocalDate effectiveDate);
}
```

The adapter may initially read the existing product/rule reference tables or a trusted upstream product client, but it must return a complete immutable `ProductTerms` value: active product, permitted currency, tenure range, rate, compounding/payout rules, applied outcome id, and closure-penalty rule. If the existing product module cannot supply those values, fail booking rather than falling back to `CreateAccountRequest.interest_rate` as an authority. The request rate can be compared for compatibility, not trusted.

Product changes affect future bookings only. Product deactivation must not invalidate already-booked accounts. Reference synchronization should be an upsert operation with source identity/version checks; the current `sync*` methods returning `204` without persistence are unsafe because they acknowledge work that was not done.

### Account Aggregate and Related Records

Treat `fd_accounts` as the lifecycle aggregate root. The account row, its state transition, and the financial/audit records created by one command are committed together. Related tables remain separate persistence entities because the schema is already normalized:

- `fd_accounts`: current lifecycle state plus immutable booking snapshot.
- `fd_transactions`: append-only account ledger entries; use transaction type master data rather than the unused Java `TransactionType` enum as the database id.
- `fd_interest_accrual`: one row per account and accrual business date.
- `fd_interest_payout`: payout attempt/status, separate from the ledger transaction.
- `fd_premature_closure`: requested/approved/completed closure calculation and approval actor.
- `fd_payout_accounts`: customer-provided destination, with verification required before settlement.
- `fd_maturity_instructions`: latest instruction used only at maturity; enforce one effective instruction per account.
- `fd_account_events`: append-only lifecycle/audit trail and future publication source.
- `account_holder_roles`: ownership and operating mandate; primary holder is not enough for future joint-account authorization.

Do not create broad JPA relationships across all of these tables just for convenience. Keep foreign-key ids or small read projections at the aggregate boundary so lazy loading cannot accidentally cause unbounded reads or expose internal entities through JSON.

## State Transitions

The state machine should be the only code allowed to change `fa_status`:

```text
PENDING --funding succeeds--> ACTIVE
ACTIVE  --maturity date reached--> MATURED
MATURED --payout/settlement succeeds--> CLOSED
ACTIVE  --approved premature closure + settlement--> PREMATURELY_CLOSED
MATURED --renew instruction--> (new ACTIVE account, source remains MATURED or is CLOSED per policy)
```

Rules:

1. `PENDING` is bookable but not accruable, matureable, or closable.
2. `ACTIVE` requires a valid initial deposit transaction and immutable booking terms.
3. `MATURED` is an accounting transition, not proof that money was paid out.
4. `CLOSED` and `PREMATURELY_CLOSED` are terminal for the account. A retry returns the prior result when the same idempotency key is supplied and a conflict for a different command.
5. Public `PATCH /accounts/{id}` may change `auto_renewal` and other explicitly mutable instructions only. It must not accept an arbitrary status supplied by a caller.
6. Renewal creates a new account through the normal booking path using an explicit source-account reference and a new product/rate snapshot; do not mutate the old account's historical terms.

The domain state machine should accept the current state, command, actor/policy result, and effective date, then return a transition decision. It should not query the database or call payout systems.

## Data Flow

### Booking and Funding

1. Controller validates `CreateAccountRequest`, extracts a principal, request id, and optional idempotency key, and maps it to `CreateAccountCommand`.
2. `AccountBookingService` resolves the actor and target holder. A customer can book only for an owned/eligible holder; staff may target a customer only with an explicit staff role and an audit actor.
3. The service resolves active product terms, currency and rule outcome through ports, validates amount/tenure/frequency, calculates maturity using an injected clock/time-zone policy, and allocates an account number using a database-safe allocator.
4. In one transaction, it inserts the account as `PENDING`, primary holder, idempotency result, and `ACCOUNT_BOOKED` event. The account contains the resolved rate/rule snapshot.
5. Funding is a separate command because the contract exposes `/activate`. It locks the account, verifies `PENDING`, validates the deposit transaction type/currency/amount, inserts the initial ledger row exactly once, changes state to `ACTIVE`, and appends `ACCOUNT_ACTIVATED`.
6. The response is mapped from a DTO/projection. It never serializes the JPA entity directly.

### Accrual and Maturity

```text
batch trigger or POST /accrual/run
        -> validate actor/account and normalize accrual_date to business date
        -> lock one ACTIVE account
        -> check existing (account_id, accrual_date) effect
        -> calculate from account snapshot using BigDecimal
        -> insert fd_interest_accrual and audit event
        -> commit

maturity batch or POST /mature
        -> find due ACTIVE accounts in bounded pages
        -> invoke one-account maturity transaction
        -> accrue missing final day(s), lock account, re-check due/state
        -> set MATURED and record event
        -> apply instruction as a separate idempotent payout/renewal command
```

Accrual must be deterministic for a supplied date and clock. Use `BigDecimal` with the currency scale and an explicitly documented rounding mode. Do not use the existing calculator's `double` path for persisted financial effects. The cumulative amount should be derived from persisted accrual rows or updated under the same account lock, never from an in-memory counter.

### Closure and Payout

Normal closure is permitted only for a matured account and must verify the primary/selected payout destination. Premature closure is a two-step workflow: customer/staff requests a calculation (`PENDING`), an authorized approver approves it, and the completion command locks the account, rechecks the approval and state, writes the closure amount/penalty, records the payout/ledger effect, transitions the account, and audits all actors.

Keep `SettlementPort` separate from the database transaction. If there is no real external ledger in this repository, implement the milestone's recorded settlement as a durable internal transaction/payout status and document that it is not an external bank transfer. If an external settlement provider is introduced, commit a `PENDING` work item and use an outbox/retry worker; do not hold a PostgreSQL transaction open across a network call and do not mark an account closed before the defined settlement success policy.

## Transaction Boundaries and Concurrency

Use public application-service methods as transaction boundaries. Spring's declarative transaction support is appropriate here; the current repository already uses `@Transactional`, and Spring documents annotation-based transaction semantics at the service bean boundary. Keep read queries `readOnly` and keep each financial mutation short.

| Use case | Transaction boundary | Required concurrency control |
|----------|----------------------|-------------------------------|
| Create booking | One transaction for idempotency claim, account, holder, and booking event | Unique idempotency key; database-safe account-number allocation |
| Activate/fund | One transaction for lock, validation, transaction insert, state change, and event | `PESSIMISTIC_WRITE` account lock or a real numeric `@Version`; unique funding operation key |
| Update instructions | One transaction for owner check, effective instruction update, and event | Account lock; enforce one current instruction |
| Single accrual | One transaction per account/date | Unique `(account_id, accrual_date)` plus account lock; duplicate request returns existing row |
| Maturity | One transaction per account, not one transaction for the whole batch | Account lock and re-check of status/date; idempotent maturity operation |
| Closure/payout | One transaction for internal financial records and state transition | Account lock; unique closure/payout operation; external settlement handled after commit |
| Batch coordinator | No long outer transaction; page/claim work and call transaction-scoped use cases | `FOR UPDATE SKIP LOCKED`/claim table for multi-instance workers |

The existing `fa_version` is a nullable `VARCHAR` business field, not a safe JPA optimistic-lock column. Either add an additive numeric row-version column and map it with `@Version`, or use explicit pessimistic locking for all lifecycle mutations until that migration exists. Do not rely on `synchronized` in `AccountNumberGenerator`: it only protects one JVM, while the current `MAX(accountNumber)` strategy can collide across instances.

Prefer a small additive Flyway migration after verifying the live schema. Candidate changes are a numeric account row-version, unique date-based financial-effect indexes, unique reference/UUID indexes, an idempotency table, and a branch counter/allocation table. Do not edit V1-V5 in place or make a destructive normalization migration during the lifecycle work.

## Idempotency and Auditability

### Idempotency

Require `Idempotency-Key` for booking, funding, accrual, payout, closure completion, maturity, and renewal commands. Store at least:

```text
operation_scope, idempotency_key, request_hash, aggregate_id,
status, response_reference, created_at, completed_at
```

The unique key is scoped to the authenticated actor and operation. On replay, return the saved result only when the request hash matches; return a conflict for key reuse with different input. A database unique constraint is the final protection, not an in-memory map. For date-natural operations, also enforce database uniqueness for `(account_id, accrual_date)` and `(account_id, payout_date, payout_kind)` so scheduler/manual/API races converge safely.

### Audit

Every mutation writes an append-only `fd_account_events` row in the same transaction as the business mutation. Event payloads should include operation id, request id, actor id/type, prior and next status, effective/business date, product/rate/rule snapshot references, amount/currency, reason, and outcome. Do not place secrets, raw bank account credentials, or unnecessary personal data in JSONB. Use `created_by` for a display actor and add explicit actor/request columns in an additive migration if audit queries need them.

`is_published` is not an audit guarantee. An unpublished event is still an audit record; publication/outbox delivery is a separate concern. Restrict event creation to internal application services, even though the OpenAPI document currently exposes `POST /events`. Customers may read only their own events; staff/internal actors require separate role checks.

## Authorization Boundary

Use a two-layer policy:

1. Controllers apply coarse `@PreAuthorize` rules: authenticated customer for self-service reads/commands, bank officer/admin for staff actions, and a service credential/authority for `/internal/sync/*` and batch operations.
2. Application services enforce resource-level policy using an `ActorContext` and holder/reference repositories. This protects calls from legacy adapters, schedulers, tests, and future non-HTTP entry points.

Never accept `primary_holder_id`, `approved_by`, or a target username as proof of authorization. Resolve the authenticated user to the local/reference user id, verify holder role/mandate, and derive the approval actor from the security context. Staff targeting another customer must be explicit and audited. Internal sync routes must not remain covered only by the application's broad `anyRequest().authenticated()` rule; the current `SecurityConfig` permits all `/auth/**` and has no v1-specific method guards.

## Scheduling and Batch Flow

Replace `FdBatchScheduler`'s direct calls to the throwing legacy service with a thin `FdBatchCoordinator`. Keep cron configuration outside business logic and inject a `Clock` plus configured business time zone. The coordinator should:

1. Acquire a database/distributed execution lease or claim due rows so two application instances do not process the same work unnecessarily.
2. Read only eligible ids in bounded pages, ordered by account id or maturity date.
3. Invoke `AccrualService.run(accountId, businessDate, SYSTEM_BATCH_ACTOR)` and `MaturityService.process(accountId, businessDate, SYSTEM_BATCH_ACTOR)` separately.
4. Let one failed account roll back its own transaction and be retried/recorded without rolling back the whole population.
5. Emit metrics/log fields for batch id, account id, business date, processed/skipped/failed counts, and duplicate/idempotent outcomes.

Run accrual before maturity for the same business date, but make maturity independently safe if a prior accrual run was missed. The manual `/api/admin/batch/*` endpoints should invoke the same coordinator and return a batch result, not a success string after a method that may fail.

## Compatibility Adapters

The normalized schema uses `fd_user_ref` ids and `fa_primary_holder_id`; the legacy controller expects usernames and older `customerId`, `depositAmount`, `termDays`, and withdrawal semantics. The adapter must:

- Resolve username to the local/reference user id before entering the FD use case.
- Convert `termDays` explicitly to the v1 tenure model. Reject or document non-30-day values; do not silently apply the entity's approximate `days / 30` conversion.
- Translate legacy withdrawal to the premature-closure workflow, including a compatibility response for pending approval rather than pretending it is an immediate withdrawal.
- Preserve old JSON aliases only at the adapter DTO layer; remove compatibility getters from domain entities after callers migrate.
- Keep the v1 controller and legacy adapter backed by the same service so a route cannot bypass idempotency, authorization, state rules, or audit.
- Add contract tests for both route families before deprecating or removing `/api/fd`.

Do not make `FdV1ContractController` call the legacy service, because that would preserve the current `BankingException` behavior and make the contract implementation depend on obsolete username semantics.

## Anti-Patterns to Avoid

### Anemic entity setters as lifecycle logic
**What:** Any controller or service sets `FdAccount.status` directly, or accepts arbitrary status through PATCH.
**Why bad:** Illegal transitions, duplicate payouts, and un-audited state changes become possible.
**Instead:** Commands enter the state machine through application services; persistence setters are package-private or otherwise not exposed to HTTP mapping.

### Client-controlled pricing
**What:** Persist `interest_rate` from the request without resolving an active product/rule outcome.
**Why bad:** A caller can book an unauthorized rate and future product edits can make historical calculations non-reproducible.
**Instead:** Resolve and snapshot product terms; treat the request value only as a compatibility assertion.

### JVM locking and in-memory idempotency
**What:** Depend on `synchronized`, maps, or a prior read before inserting a financial effect.
**Why bad:** It fails across instances and races under retries.
**Instead:** Use PostgreSQL uniqueness, row locks/versions, and durable idempotency records.

### One transaction around the whole batch
**What:** Load every active account and process all accrual/maturity work in one transaction.
**Why bad:** Long locks, memory growth, poor retry behavior, and one bad account can roll back all work.
**Instead:** Page ids and commit one account/operation at a time.

### External payout inside the database transaction
**What:** Hold an account lock while calling a bank/ledger service.
**Why bad:** Timeouts create uncertain commit/side-effect combinations.
**Instead:** Persist a pending operation, use an outbox/worker or settlement adapter, and make completion idempotent.

### Returning entities directly from controllers
**What:** Serialize `FdAccount`/`FdTransaction` from `/api` or `/fd/v1`.
**Why bad:** Compatibility aliases, transient values, lazy relationships, and internal fields leak into the API.
**Instead:** Map explicit versioned DTOs and use projections for lists.

## Scalability Considerations

| Concern | At 100 users | At 10K users | At 1M users |
|---------|--------------|--------------|-------------|
| Booking/account numbers | DB uniqueness plus a locked counter table is sufficient | Counter allocation with retry and indexed lookup | Partition/sequence strategy by branch; monitor hot counters; never `MAX` allocation |
| Accrual | One-account transactions and unique date keys | Keyset pages plus `SKIP LOCKED` claims and metrics | Dedicated worker pool, partitioned due-account queries, archival/reporting strategy |
| Account reads | Repository methods and DTO mapping | Indexed owner/status/maturity queries with page limits | Read projections/reporting tables; avoid loading entity graphs |
| Audit/events | Append to `fd_account_events` | Index account/type/date and paginate | Partition/archive events while preserving immutable retention requirements |
| Idempotency | One relational table with retention cleanup | Indexed scoped keys and bounded response references | Retention/archival policy and a separate durable operation store if table volume demands it |
| External settlement | Internal recorded transaction | Outbox plus retry/timeout state | Provider partitioning, reconciliation, and settlement ledger integration |

## Suggested Build Order

1. **Compatibility baseline and persistence mapping** — Test the current Flyway migrations against PostgreSQL, inventory live columns/types, map the missing FD tables with explicit DTOs, and add only additive indexes/constraints needed for idempotency and dates. Avoid changing V1-V5. This prevents Hibernate validation and schema drift from hiding later lifecycle defects.
2. **Product/rate and reference boundary** — Implement `ProductTermsPort`, active product/rule/currency validation, reference-sync upserts, and immutable booking-term value objects. Fix the current `FdProduct` transient-field assumption before enabling booking.
3. **Booking, funding, authorization, and idempotency** — Implement typed v1 account commands, `PENDING -> ACTIVE`, durable idempotency, safe account-number allocation, ownership/staff policy, and same-transaction audit. Add PostgreSQL integration and route contract tests.
4. **Ledger and accrual** — Implement transaction-type lookup, append-only funding/interest ledger records, deterministic `BigDecimal` interest policy, unique `(account,date)` accrual, cumulative queries, and replay/concurrency tests.
5. **Maturity and scheduler** — Implement due-account queries, per-account locks/claims, `ACTIVE -> MATURED`, maturity instruction handling, manual batch delegation, and failure/observability behavior. Verify missed-day and duplicate-run scenarios.
6. **Closure, payout, and renewal** — Implement payout-account verification policy, normal closure, premature request/approval/completion, penalty snapshot, payout status, settlement boundary, renewal as a new account, and terminal-state/idempotency tests.
7. **Compatibility hardening and regression** — Route legacy `/api/fd` through the new use cases, preserve tested response aliases, remove placeholder/no-op paths only after compatibility tests pass, and run the existing authentication/product/calculator/report regression suite.

This ordering minimizes compatibility risk because the schema and contract are verified before behavior is connected, product terms are authoritative before money is booked, and every later batch/closure operation reuses the already-tested booking, state, authorization, idempotency, and audit primitives.

## Sources

**Repository evidence (HIGH confidence):**

- `.planning/PROJECT.md` — milestone constraints, scope, and authoritative schema/contract decisions.
- `.planning/codebase/ARCHITECTURE.md` — current modular-monolith layers, entry points, and data flow.
- `.planning/codebase/CONCERNS.md` — known FD placeholders, scheduler failures, schema/entity fragility, authorization gaps, and concurrency limits.
- `BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml` — v1 operations, state values, and request/response shapes.
- `BACKEND/src/main/resources/db/migration/V1__bankingtech_public_schema.sql` and V2-V5 — normalized FD tables, foreign keys, numeric types, and current migration risks.
- `BACKEND/src/main/java/com/bank/fd/**`, `com/bank/product/**`, `com/bank/security/**` — current controllers, services, entities, repositories, scheduler, product projection, and security configuration.

**Official documentation (MEDIUM/HIGH confidence for framework behavior):**

- Spring Framework, “Using `@Transactional`”: https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html — service-level declarative transaction semantics.
- Spring Data JPA, “Locking”: https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html — repository lock metadata for concurrency-sensitive queries.
- Spring Framework, “Task Execution and Scheduling”: https://docs.spring.io/spring-framework/reference/integration/scheduling.html — scheduler/executor abstractions and the reason to keep scheduled triggers separate from work orchestration.
- PostgreSQL 18, “Constraints”: https://www.postgresql.org/docs/current/ddl-constraints.html — database-enforced uniqueness, foreign keys, and check constraints as final integrity protection.

## What Might Be Missing

- The repository does not show a real external ledger/payment provider, so the exact meaning of “processed payout” and the commit point for bank transfer settlement needs product/operations confirmation.
- The OpenAPI contract has no `Idempotency-Key` parameter and no explicit funding endpoint body; the implementation should add header support without breaking existing payloads and confirm whether contract revision is permitted.
- Product/rate authority is incomplete in the current Java module: the schema contains upstream product/rule tables, while `FdProduct` maps only `fd_product_ref`. A phase-specific decision is required on whether to query those tables directly or consume a verified product-service adapter.
- Joint-holder mandate rules, tax withholding, business-day calendars, and renewal policy are represented in the contract/schema only partially and should remain explicit follow-up decisions rather than hidden defaults.
