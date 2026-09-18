# Project Research Summary

**Project:** Banking Lab
**Domain:** Backend-first fixed-deposit banking lifecycle in an existing Spring Boot modular monolith
**Researched:** 2026-09-18
**Confidence:** HIGH overall for repository constraints and core engineering patterns; MEDIUM for unresolved product, jurisdiction, and settlement policy

## Executive Summary

Banking Lab is not primarily a deposit calculator. It is an existing Spring Boot 3.5.5/Java 17 banking backend whose next milestone must turn a broad, partly placeholder `/fd/v1` surface into a persisted and auditable fixed-deposit contract: an eligible customer selects an active offer, books and funds a principal, retains the exact contracted terms, accrues interest once per eligible date, reaches maturity once, and receives an explicitly recorded closure/settlement outcome. The existing Flyway PostgreSQL schema and OpenAPI contract are compatibility boundaries; the implementation should remain a modular monolith rather than introduce a workflow engine, second persistence abstraction, or platform rewrite.

The recommended approach is a thin HTTP/legacy-adapter layer over FD application use cases, with PostgreSQL/Flyway enforcing uniqueness and lifecycle invariants that Java checks alone cannot guarantee. Use JPA for ordinary aggregates, narrow native SQL/JdbcTemplate for `ON CONFLICT`, row claims, and `SKIP LOCKED`, `BigDecimal` plus one versioned calculation policy for all money, explicit state transitions, immutable booking and financial records, durable idempotency, service-level authorization, and injected UTC/business-time clocks. Add PostgreSQL Testcontainers and contract/concurrency tests early; H2 and mocks are insufficient evidence for this domain.

The largest risks are financial drift (`double`, inconsistent rounding, approximate 30-day terms), duplicate effects under retries or overlapping jobs, mutable product terms changing historical contracts, unsafe account-number allocation, IDOR/staff-target spoofing, and false-success placeholder or payout responses. Mitigate them by making policy and schema readiness the first phase, requiring database-backed idempotency and locks, snapshotting terms at booking, authorizing in both controller and service layers, and refusing to claim external payment success when only an internal settlement record exists. Product jurisdiction, tax, day-count, business-day, authentication-authority, and real payment-rail decisions remain explicit requirements gaps rather than implementation assumptions.

## Key Findings

### Recommended Stack

Retain the current Java 17, Spring Boot 3.5.5, Spring Data JPA/Hibernate, PostgreSQL, Flyway, Spring Security, validation, Actuator, Maven, and existing modular-monolith structure. Put complete financial use cases behind application-service transaction boundaries. Add only targeted capabilities: PostgreSQL Testcontainers for migration and concurrency proof, and optionally ShedLock plus its JDBC provider when more than one application instance can run scheduled triggers. ShedLock prevents overlapping trigger execution but does not replace per-account idempotency or database claiming.

**Core technologies and patterns:**
- **Java 17 + `BigDecimal`/`java.time`:** authoritative monetary and date arithmetic with explicit scale, rate units, `RoundingMode`, `Clock`, and business timezone; never `double`, `Math.pow`, or implicit casts for posted money.
- **Spring Boot 3.5.5 + Spring Data JPA:** retain curated dependency management and use services as financial transaction boundaries; do not add a workflow/state-machine framework.
- **PostgreSQL + Flyway:** authoritative schema, forward-only additive migrations, named checks/unique indexes, `timestamptz` for instants, and `date` for business dates; keep `ddl-auto=validate`.
- **JPA plus narrow native SQL/JdbcTemplate:** use JPA for normal persistence and explicit native operations for PostgreSQL `ON CONFLICT`, `FOR UPDATE SKIP LOCKED`, conditional updates, and safe claims.
- **PostgreSQL Testcontainers:** run authoritative migrations and verify numeric scale, constraints, locks, transaction rollback, and PostgreSQL behavior; H2 remains only for tests that do not depend on PostgreSQL semantics.
- **OpenAPI contract tests:** validate the checked-in `/fd/v1` document with MockMvc/security tests and a Java 17-compatible validator v2 line only after Maven dependency convergence is confirmed.
- **Actuator and structured logging:** expose aggregate job health/counters and bounded timings without sensitive account or financial payloads; disable default SQL logging outside controlled diagnostics.

Do not introduce Moneta, Spring Statemachine, Spring Batch, Quartz, Redis locks, Kafka/outbox, jOOQ, R2DBC, or Spring Cloud Contract for the core milestone. Envers is optional for mutable administrative history and is not a substitute for the immutable financial/lifecycle ledger.

### Expected Features

The repository boundary is narrower than the full contract. The OpenAPI document contains holders, statements, notices, recurring payouts, renewal, events, and sync concepts, but those do not become delivery commitments merely because routes exist. Every enabled route must have typed DTOs, authorization, persistence, idempotency, and an honest status/error contract; `501` and no-op `204` responses must not be presented as successful business operations.

**Must have (table stakes):**
- **Server-authoritative product/rate selection:** only active/effective offers and eligible amount, tenure, currency, and customer combinations can book; caller-supplied rates and maturity amounts are assertions, not authority.
- **Immutable booking terms:** persist the resolved rate/rule/product/currency/tenure, calculation policy, dates, and applicable closure terms used by the deposit; later product changes affect future bookings only.
- **Booking and initial funding:** create `PENDING`, accept exactly one correctly matched funding effect, and transition to `ACTIVE` only after funding succeeds; prevent the schema's current `ACTIVE` default from activating unfunded accounts.
- **Ownership and role controls:** derive customer/holder and approval identity from the authenticated actor; enforce resource-level checks in services as well as coarse route checks for customers, staff, and system jobs.
- **Deterministic accrual:** use a documented day-count, compounding, interval, scale, and rounding policy; persist one accrual effect per account/business date and support safe catch-up and reruns.
- **Maturity:** calculate calendar-aware maturity, accrue through the defined endpoint, and transition `ACTIVE -> MATURED` exactly once with an idempotent result.
- **Closure and recorded settlement:** support normal maturity closure and a separate premature-closure path only where effective rate/penalty/approval policy exists; record gross interest, deductions, net payable, status, and settlement reference without overstating external payment success.
- **Append-only evidence and reconciliation:** record funding, accrual, state transitions, closure, and payout/settlement facts with actor, request/job identity, policy/version, amounts, and correlation metadata; reconcile projections from durable records.
- **Verification:** PostgreSQL migration, contract, authorization, rounding/date-boundary, retry/concurrency, scheduler rerun, and existing API regression coverage.

**Should have (competitive/operational):**
- **Operational visibility:** bounded dashboards/queries for stuck funding, accrual gaps, failed payout, overdue maturity, and reconciliation breaks; add after core state transitions, even though customer UI is out of scope.
- **A comparison/quote experience:** product comparison and short-lived rate reservation can improve selection, but only after the authoritative product resolver and snapshot are stable.
- **Explicit catch-up and manual processing:** authorized manual commands using the same idempotent services as scheduled work, with batch run outcomes and safe retry behavior.

**Defer (v2+):**
- Recurring interest payouts, partial withdrawals/splits, sweep, auto-renewal, and cross-product movement.
- Full joint-holder/nominee/deceased-claim workflows and broad holder mutation.
- Full tax withholding/certificates/reporting unless product ownership makes it an acceptance criterion.
- Notices, statements/PDFs, external event publication, and non-essential reference-data synchronization.
- Real payment-rail integration, reversals, returns, and reconciliation beyond the recorded settlement adapter boundary.
- Angular fixed-deposit screens and end-to-end frontend integration; this milestone is backend API first.

**Recommended v1 boundary:** one clearly defined product family, one explicit calculation policy, active/effective selection, immutable terms, idempotent booking/funding, deterministic daily accrual, maturity, normal closure, and recorded payout/settlement. Include premature closure only if the governing policy and approval semantics are confirmed; otherwise return an explicit business-unsupported result rather than inventing a penalty or tax behavior.

### Architecture Approach

Keep the modular monolith but make `fd` an application-owned bounded context. Both `/fd/v1` and the legacy `/api/fd` adapter, plus scheduler/admin triggers, must delegate to the same application use cases. Controllers bind typed DTOs and map errors; they do not call repositories, expose entities, or set status. FD services own use-case transactions, authorization, idempotency, state transitions, audit, and settlement boundaries. Product/rate and customer/security concerns are accessed through ports so the FD domain does not depend on mutable product entities or raw `SecurityContextHolder` access.

**Major components:**
1. **Typed `/fd/v1` controller and legacy compatibility adapter** — preserve the existing contract and old route shapes at the edge while translating both to one command model; never preserve a second lifecycle implementation.
2. **FD application services** — booking, funding, accrual, maturity, closure, payout, query, and batch coordination; each public financial mutation is a short, complete transaction.
3. **Domain policy layer** — explicit `FdStateMachine`, immutable booking terms, interest/day-count/rounding policy, closure policy, and injected clock; pure and independently testable.
4. **Persistence boundary** — JPA entities/repositories and projections for normalized FD tables, plus narrow PostgreSQL-native queries for uniqueness, locks, claims, and conditional transitions.
5. **Product terms and actor ports** — resolve active product/rule/currency terms and authenticated customer/staff/system identity without trusting request IDs, roles, or rates.
6. **Immutable financial/audit records** — account ledger, accrual, payout/settlement, premature closure, lifecycle events, idempotency records, and batch outcomes; commit business mutation and audit evidence together.
7. **Batch coordinator and settlement port** — scheduler is only a trigger; process bounded per-account work with explicit logical dates and reruns. Settlement remains an adapter boundary and must not be called while holding a database transaction open.

The account aggregate should permit only `PENDING -> ACTIVE -> MATURED -> CLOSED` and `ACTIVE -> PREMATURELY_CLOSED` through named commands. `MATURED` means the accounting transition occurred, not that payment succeeded. Renewal, if later added, creates a new account and snapshot rather than mutating history.

### Critical Pitfalls

1. **Floating-point and inconsistent rounding** — replace the existing `double` calculator path with one `BigDecimal` policy shared by quote, accrual, maturity, and closure; define scale and rounding at each posting boundary and add golden/reconciliation tests.
2. **Wrong dates and day-count assumptions** — do not use universal 365/30-day conversions or server-local time. Resolve a product policy, inject `Clock`/business `ZoneId`, centralize maturity predicates, and test month-end, leap-day, DST, and exact-boundary cases.
3. **Duplicate financial effects** — database uniqueness and conditional insert/update must be the correctness mechanism for booking, funding, accrual, maturity, closure, and payout. Use durable idempotency keys, row locks/versions, bounded claims, and replay tests; scheduler serialization alone is insufficient.
4. **Mutable terms and unsafe persistence** — snapshot product/rate/rule terms at booking, replace `MAX(...) + 1`/JVM synchronization with a PostgreSQL sequence or counter, add additive constraints/indexes, and verify migrations against clean and representative legacy databases.
5. **Authorization and false-success boundaries** — service-layer ownership checks, explicit staff/system policy, typed DTOs, and negative tests are required before enabling routes. Do not return success for placeholders, no-op sync, local-only payout rows, or client-controlled status/rate/identity.

## Implications for Roadmap

Based on research, the roadmap should use the following build order. Tests for each phase are part of the phase, not a final cleanup task.

### Phase 1: Policy, Contract, and PostgreSQL Baseline
**Rationale:** Financial behavior cannot be safely implemented while the current schema, contract, date conventions, and calculation semantics are ambiguous. This also exposes migration drift before new behavior depends on it.
**Delivers:** PostgreSQL Testcontainers baseline; Flyway clean/legacy validation; typed v1 DTO/error approach; versioned money/rate/day-count/rounding/time policy; explicit lifecycle transition matrix; additive migration plan; injected clock; decision on authoritative authentication adapter and product-term source.
**Addresses:** calculation policy, product eligibility prerequisites, lifecycle invariants, compatibility boundary, and test foundation.
**Avoids:** `double` money, 30-day maturity, nullable active accounts, edited applied migrations, H2-only confidence, and hidden contract drift.

### Phase 2: Product Terms, Booking, Funding, and Authorization
**Rationale:** All later money effects depend on a valid product contract and a correctly owned, funded account. Authorization and idempotency must be present before exposing any FD mutation.
**Delivers:** `ProductTermsPort`/resolver; active/effective eligibility; immutable booking snapshot; safe PostgreSQL account-number allocation; `PENDING` booking; durable booking/funding idempotency; exactly-once initial funding and `PENDING -> ACTIVE`; actor/ownership/staff policy; same-transaction audit; v1 booking/funding contract tests.
**Addresses:** product/rate selection, booking, initial funding, ownership, append-only funding, and server-authoritative values.
**Uses:** JPA services, Flyway constraints/indexes, `BigDecimal`, PostgreSQL uniqueness/locks, typed DTOs, and Testcontainers.
**Avoids:** current `ACTIVE` default, transient product-rate fields, `MAX + 1`, caller-supplied customer/rate/status, duplicate funding, and client-success-before-funding.

### Phase 3: Ledger and Deterministic Accrual
**Rationale:** Accrual is the first recurring financial effect and establishes the ledger/reconciliation primitives maturity and closure will reuse.
**Delivers:** allowlisted financial transaction types; immutable funding/interest postings; pure calculation policy implementation; one accrual row/effect per account/date; cumulative readback/reconciliation; manual catch-up; idempotent concurrent/restart-safe accrual service.
**Addresses:** daily accrual, financial history, safe reruns, supported product policy, and operational gap detection.
**Avoids:** binary floating point, double rounding, generic public transaction posting, in-memory counters, duplicate account/date rows, and unbounded entity loads.

### Phase 4: Maturity and Scheduled Processing
**Rationale:** Maturity depends on funded accounts and accrual but must be independently safe when jobs miss dates. The scheduler should be connected only after one-account services are proven.
**Delivers:** due-account projections; bounded batch coordinator; explicit scheduler timezone; optional ShedLock based on deployment topology; row claim/lock strategy; `ACTIVE -> MATURED`; final accrual catch-up; durable batch-run/outcome observability; manual and scheduled paths using the same services.
**Addresses:** maturity calculation, idempotent transition, missed-date recovery, scheduler safety, and operational visibility.
**Avoids:** direct calls to current throwing services, giant transactions, swallowed failures, duplicate cron/manual work, server-local dates, and treating ShedLock as idempotency.

### Phase 5: Closure, Payout, Settlement, and Audit Hardening
**Rationale:** Closure and payout are the highest-risk terminal effects and require the preceding aggregate, ledger, authorization, and idempotency primitives.
**Delivers:** normal maturity closure; verified/eligible payout destination policy; durable payout instruction/status/idempotency; explicit recorded-settlement adapter; premature request/approval/completion only if policy is confirmed; gross/interest/penalty/tax/net components; terminal state rules; immutable lifecycle/audit evidence and reconciliation tests.
**Addresses:** normal closure, conditional premature closure, payout/settlement, auditability, and operational exceptions.
**Avoids:** double payout, false `PROCESSED`, external calls inside DB transactions, undisclosed penalties, guessed tax, destination changes after queueing, and closure before settlement semantics are defined.

### Phase 6: Compatibility, Contract, Security, and Regression Release Gate
**Rationale:** The existing repository has a legacy bridge, split authentication contracts, broad route exposure, and established non-FD behavior that must remain intact. Hardening belongs after the new use cases exist, but security tests gate every earlier endpoint.
**Delivers:** legacy `/api/fd` adapter over the new services; compatibility aliases and explicit lossy-term handling; complete v1 OpenAPI/status/error tests; authorization matrix across direct service and HTTP paths; no-placeholder/no-op route scan; existing auth/product/calculator/customer/report regression suite; migration and deployment checklist.
**Addresses:** compatibility preservation, route enablement, ownership/staff boundaries, and project requirement to preserve existing APIs.
**Avoids:** IDOR, staff target spoofing, unauthenticated internal sync/batch routes, false-success `501`/`204`, authentication contract widening, and regressions outside FD.

### Phase Ordering Rationale

- Policy, schema, and contract decisions precede behavior because the existing migrations and OpenAPI document are authoritative and currently disagree with safe financial semantics in places (`double`, missing idempotency header, pending/funding representation, and nullable fields).
- Product resolution and funding precede accrual; accrual precedes maturity; maturity and ledger evidence precede closure/payout. Each later phase reuses already-tested invariants rather than creating parallel paths.
- Authorization and typed request boundaries are implemented with booking and tested before route enablement; final compatibility hardening then verifies that legacy adapters cannot bypass the same rules.
- Database uniqueness, row locking, immutable records, and PostgreSQL integration tests are cross-cutting exit criteria, not optional hardening after the lifecycle is built.

### Research Flags

Phases likely needing deeper research during planning:
- **Phase 1:** exact existing production schema, legacy data violations, contract revision policy, authoritative authentication path, product/rule source, and calculation/day-count/business-time policy are repository/product decisions not resolved by framework documentation.
- **Phase 2:** product rule schema semantics, account-number format/branch allocation, and whether idempotency can be added to the existing contract without breaking consumers need code and stakeholder validation.
- **Phase 5:** jurisdiction-specific premature closure, tax/withholding, payout destination verification, settlement success semantics, and approval roles are policy-sensitive and under-specified.
- **Phase 6:** legacy `/api/fd` field conversions, split authentication behavior, and compatibility response expectations need targeted contract discovery.

Phases with standard patterns (skip standalone research-phase unless implementation uncovers a gap):
- **Phase 3:** `BigDecimal` domain policy, service transactions, PostgreSQL unique business keys, and Testcontainers-backed repository testing are well documented.
- **Phase 4:** bounded Spring scheduling plus PostgreSQL claiming/locking and rerunnable per-account jobs follow established patterns; validate the deployment topology before adding ShedLock.

## Confidence Assessment

| Area | Confidence | Notes |
|------|------------|-------|
| Stack | HIGH | Existing Spring Boot/JPA/Flyway/PostgreSQL baseline and official Spring/PostgreSQL guidance strongly support the recommendation; exact ShedLock and OpenAPI-validator dependency convergence remains MEDIUM until Maven resolves it. |
| Features | HIGH | Project scope, existing OpenAPI/schema, and repository concerns establish the v1 boundary; jurisdiction-specific tax, penalty, and regulatory details are MEDIUM. |
| Architecture | HIGH | Current modular-monolith boundaries and placeholder paths are well evidenced; product authority, external settlement, and contract evolution remain unresolved target choices. |
| Pitfalls | HIGH | Most risks are directly confirmed by codebase concerns and official database/Spring behavior; ecosystem-specific release guidance is less certain. |

**Overall confidence:** HIGH for the roadmap shape and engineering safeguards; MEDIUM for product-policy details that must be locked during requirements planning.

### Gaps to Address

- **Governing jurisdiction and licensed operating model:** confirm whether India/RBI rules apply before encoding premature-closure, overdue maturity, nomination, tax, or disclosure behavior. Until then, make policy configurable and do not infer it from field names such as IFSC/TDS.
- **Calculation policy:** decide rate units, day-count, accrual interval, opening/maturity-day inclusion, compounding, business-day adjustment, timezone, scale, and rounding; persist the applied policy/version in the booking snapshot.
- **Product/rate authority:** decide whether FD reads existing local reference tables directly or consumes a verified product adapter. The current `FdProduct` transient fields are not authoritative.
- **Contract evolution:** `/fd/v1` lacks an explicit `Idempotency-Key` and a clear funding body while existing request models expose floating-point-like money. Confirm additive header/schema changes and backward-compatible status/error semantics.
- **Settlement boundary:** confirm whether v1 is strictly a durable internal recorded settlement or has a real provider. Define what `PROCESSED`, `FAILED`, and `UNKNOWN` mean before terminal closure.
- **Authentication authority:** choose/isolate the `/api/auth` versus `/auth` split and define the `ActorContext` mapping FD can rely on; do not let FD inherit broad or inconsistent public route permissions.
- **Existing data readiness:** run preflight queries for null terms, invalid effective dates, duplicate references, and status inconsistencies before adding `NOT NULL`/unique constraints; quarantine or repair legacy rows explicitly.
- **Deployment topology:** determine whether multiple backend instances run. Add ShedLock only if needed, but retain database idempotency and claims regardless.
- **Tax and payout-account verification:** the schema has partial signals but not a complete model. Either implement a declared v1 policy or expose an explicit unsupported/business exception; never silently treat missing policy as zero.

## Sources

### Primary (HIGH confidence)
- `.planning/PROJECT.md` — milestone scope, authoritative schema/contract constraints, active requirements, and out-of-scope decisions.
- `.planning/codebase/CONCERNS.md` — current placeholders, scheduler failures, authentication/security gaps, schema/entity fragility, and test gaps.
- `.planning/research/STACK.md` — retained stack, PostgreSQL/Testcontainers patterns, financial precision, idempotency, migrations, and dependency boundaries.
- `.planning/research/FEATURES.md` — table stakes, v1 boundary, anti-features, dependencies, and acceptance scenarios.
- `.planning/research/ARCHITECTURE.md` — bounded-context components, data flow, transaction boundaries, compatibility adapter, and build order.
- `.planning/research/PITFALLS.md` — repository-specific critical/moderate/minor pitfalls and phase exit evidence.
- Existing `fd-service-openapi.yaml` and Flyway migrations under `BACKEND/src/main/resources/` — current external contract and normalized persistence model, as cited by the research files.

### Official framework and database sources
- Spring Boot 3.5 build systems and Testcontainers: https://docs.spring.io/spring-boot/3.5/reference/using/build-systems.html and https://docs.spring.io/spring-boot/3.5/reference/testing/testcontainers.html
- Spring Data JPA transactions, locking, and auditing: https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html, https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html, and https://docs.spring.io/spring-data/jpa/reference/auditing.html
- PostgreSQL numeric, locking, constraints, transaction, and date/time behavior: https://www.postgresql.org/docs/current/datatype-numeric.html, https://www.postgresql.org/docs/current/explicit-locking.html, https://www.postgresql.org/docs/current/ddl-constraints.html, and https://www.postgresql.org/docs/current/functions-datetime.html
- Flyway versioned migration guidance: https://documentation.red-gate.com/flyway/flyway-concepts/migrations/versioned-migrations
- Java 17 `BigDecimal`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/math/BigDecimal.html

### Policy and compatibility sources (validate for applicability)
- Reserve Bank of India, *Interest Rate on Deposits Directions, 2025*: https://www.rbi.org.in/Scripts/NotificationUser.aspx?Id=12825&Mode=0 — relevant only if India is the governing jurisdiction.
- ShedLock documentation: https://github.com/lukas-krecan/ShedLock — deployment-dependent distributed trigger exclusion; exact version compatibility requires Maven verification.
- Atlassian OpenAPI Request Validator release notes: https://github.com/atlassian/openapi-request-validator/blob/master/RELEASE-NOTES.md — v2/v3 Java/Spring compatibility boundary; verify dependency convergence.

---
*Research completed: 2026-09-18*
*Ready for roadmap: yes*
