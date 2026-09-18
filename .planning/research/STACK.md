# Technology Stack

**Project:** Banking Lab fixed-deposit lifecycle
**Researched:** 2026-09-18
**Scope:** Implementation stack and production patterns for the existing Java 17/Spring Boot/PostgreSQL/JPA/Flyway backend. The existing stack is treated as fixed; this report does not recommend a platform rewrite.
**Overall confidence:** HIGH for Spring/PostgreSQL patterns; MEDIUM for third-party version compatibility until Maven resolves the exact Boot 3.5.5 dependency graph.

## Recommendation

Keep the modular-monolith shape and the current Spring Boot 3.5.5 parent. Use Spring Data JPA/Hibernate for ordinary aggregate persistence, PostgreSQL-native SQL for the few concurrency primitives that JPA cannot express precisely, and Flyway for every schema/constraint/index change. Financial correctness should come from `BigDecimal`, explicit rounding policy, database constraints, immutable lifecycle/accrual records, and short, service-level transactions—not from a monetary framework or a state-machine framework.

The most important proposed test addition is a real PostgreSQL Testcontainers suite that runs the authoritative Flyway migrations. H2 may remain for fast non-schema tests, but it must not be the only proof of locking, `numeric` behavior, `ON CONFLICT`, `SKIP LOCKED`, timestamp semantics, or migration compatibility.

For scheduled work, keep Spring `@Scheduled` as the trigger. Add a PostgreSQL-backed ShedLock guard only if more than one application instance can run; use database row claiming and unique business keys inside the job regardless. ShedLock prevents overlapping schedule invocations, but it is not an idempotency mechanism and is not a distributed scheduler.

## Existing Stack (retain)

| Technology | Version/status | Role | Decision |
|------------|----------------|------|----------|
| Java | 17 | Runtime and language | Retain. Use `java.time`, `BigDecimal`, records for immutable DTOs where compatible with existing APIs. |
| Spring Boot parent | 3.5.5 | Dependency management and application runtime | Retain for this milestone. Let its BOM manage Spring, Hibernate, PostgreSQL, Flyway, Jackson, HikariCP, and test versions. |
| `spring-boot-starter-data-jpa` | Boot-managed | JPA repositories, Hibernate ORM, transactions | Retain. Put use-case transaction boundaries in application services. |
| PostgreSQL JDBC driver | Boot-managed, runtime | Authoritative database connectivity | Retain. Use the same PostgreSQL major version in integration tests as production/Supabase where possible. |
| `flyway-core` + `flyway-database-postgresql` | Boot-managed | Schema-first migrations | Retain. Add forward-only migrations for FD lifecycle constraints, idempotency keys, audit records, and indexes. |
| `spring-boot-starter-validation` | Boot-managed | Request and configuration validation | Retain. Add scale/range validation for monetary and rate inputs before persistence. |
| `spring-boot-starter-actuator` | Boot-managed | Health, metrics, operational visibility | Retain. Add bounded job counters/timers and failure gauges; do not expose sensitive financial data. |
| `spring-boot-starter-test` + `spring-security-test` | Boot-managed | JUnit Jupiter, Mockito, Spring test support, security tests | Retain and extend with PostgreSQL integration tests. |
| H2 | Test scope | Fast lightweight tests | Retain only for tests that do not assert PostgreSQL behavior. Do not use it as the FD persistence compatibility gate. |

Spring Boot explicitly recommends using its curated dependency management without overriding versions unnecessarily. The POM should therefore leave Boot-managed dependencies versionless and upgrade the Boot patch line deliberately rather than independently overriding Hibernate, the JDBC driver, Jackson, or Flyway.

## Proposed Additions

| Dependency/pattern | Version guidance | Scope | Recommendation and compatibility risk |
|--------------------|------------------|-------|----------------------------------------|
| `org.testcontainers:postgresql` | Use the version managed by the Boot 3.5.5 parent; do not hard-code a conflicting version initially | Test | Add. Runs the real PostgreSQL engine and Flyway migrations. Pin the container image to the production PostgreSQL major, never `latest`. Requires Docker/CI container runtime. |
| `org.testcontainers:junit-jupiter` | Boot-managed | Test | Add with the PostgreSQL module. Use JUnit Jupiter lifecycle support or Spring-managed containers when the Spring context is cached. |
| `org.springframework.boot:spring-boot-testcontainers` | Exactly the Boot parent version, 3.5.5 | Test | Add if using `@ServiceConnection`; it lets the container supply JDBC/Flyway connection details. |
| `net.javacrumbs.shedlock:shedlock-spring` | Latest compatible 7.x release available in Maven Central at implementation time; pin the resolved version | Main | Optional but recommended for multi-instance scheduled triggers. The project README reports 7.x tested with Spring Boot 3.5 and Java 17. Verify the exact artifact against Boot 3.5.5 before merge. |
| `net.javacrumbs.shedlock:shedlock-provider-jdbc-template` | Same exact ShedLock version | Main | Add with `shedlock-spring` when distributed schedule exclusion is required. Store its table through Flyway and configure `usingDbTime()` so lock decisions use database time. |
| `spring-boot-starter-aop` | Boot-managed, 3.5.5 | Main | Add only with ShedLock's annotation-based Spring integration, to make proxying explicit and supportable. Test direct/manual job invocation because proxying is part of the behavior. |
| Hibernate Envers (`org.hibernate.orm:hibernate-envers`) | Use the Hibernate version managed by Boot; do not set a separate version | Main, optional | Use only for historical snapshots of mutable administrative projections. It is not a substitute for an FD transaction/accrual/settlement ledger and requires Flyway-owned audit tables when `ddl-auto=validate` is retained. |
| `com.atlassian.oai:swagger-request-validator-mockmvc` | v2.x line; v2.46.0 is the latest v2 release verified in the project research sources, subject to dependency convergence | Test | Recommended for validating MockMvc requests/responses against the existing OpenAPI document. Do not use v3: its official release notes require Java 21, Spring 7, and Boot 4. The v2 artifact is a deliberate compatibility choice for Java 17/Spring 6.2. |

Do not add a second database abstraction for this milestone. `spring-jdbc` is already transitively available through the JPA stack; use `JdbcTemplate` or a repository native query only for PostgreSQL-specific claim/insert operations, keeping those queries in the FD persistence package.

## Financial Precision

### Monetary values

Use `java.math.BigDecimal` in Java and PostgreSQL `numeric(precision, scale)` in Flyway-managed columns. Map amounts explicitly with `@Column(precision = ..., scale = ...)` only when those values match the existing schema; with `ddl-auto=validate`, annotations do not change the database. Do not use `double`, `float`, Java `Math.round`, PostgreSQL `real`, PostgreSQL `double precision`, or PostgreSQL `money` for principal, interest, payout, fees, or balances.

PostgreSQL documents `numeric`/`decimal` as exact and specifically recommended for monetary amounts, while floating-point types are inexact. PostgreSQL can round a value that exceeds a declared scale on storage, so request validation must reject unsupported fractional digits rather than silently relying on database coercion.

### Calculation policy

* Represent principal, accrued interest, payout, and ledger amounts as `BigDecimal`.
* Represent rates as `BigDecimal` using the existing schema's rate convention; do not convert percent values through binary floating point. If a future schema change is allowed, basis points as an integer are preferable for fixed-rate lookup, but do not redesign the authoritative existing schema for this milestone.
* Inject a single `Clock` bean and use UTC for instants. Use a configured business timezone only when deriving a business `LocalDate`; never call `LocalDate.now()` or `Instant.now()` throughout services.
* Define scale and `RoundingMode` at each financial boundary: calculation intermediate, daily accrual, final payout, and API serialization. Treat rounding mode as a product rule, not an implementation detail.
* Persist the product/rate/rule snapshot used for an account. Recalculating a historical account from today's product definition is incorrect.
* Add tests for half-cent boundaries, zero/negative/maximum values, leap days, maturity-date inclusivity, repeated calculation, and a rate change after booking.

## Lifecycle State Transitions

Do not introduce Spring Statemachine, a workflow engine, or a generic transition library. The FD lifecycle is a small, financially meaningful state graph and should be explicit in the domain service:

1. Define a closed enum of persisted states and an allowed-transition table.
2. Put transition methods on an FD aggregate/domain object or a dedicated transition policy; reject illegal transitions with a domain exception.
3. Record `from_state`, `to_state`, actor (`customer`, staff, or `system-job`), reason, correlation/idempotency key, and event time in an immutable lifecycle record.
4. Enforce important terminal-state and uniqueness rules in PostgreSQL as well as Java. Application checks alone are unsafe under concurrent requests.
5. Add `@Version` to mutable account aggregates for optimistic conflict detection. For accrual, maturity, closure, and payout, prefer a row lock or a single conditional `UPDATE ... WHERE state = ...` so a second request cannot perform the same transition.

Use `@Transactional` on the service method that represents one complete use case. Controllers should validate/authorize and delegate; repositories should not define the financial unit of work. Spring Data's own guidance recommends a service/facade boundary when several repository operations must participate in one transaction. Avoid self-invocation of transactional methods and avoid keeping a transaction open while waiting for external funding or payout systems.

## Idempotent Accrual and Maturity Jobs

### Correctness pattern

Make idempotency a database property, not a scheduler property:

```text
business key = (fd_account_id, operation_type, effective_business_date[, rule_version])
unique constraint on the business key
insert immutable accrual/settlement record
if insert wins: apply the aggregate update in the same transaction
if conflict: return the already-completed result; do not apply the amount again
```

For PostgreSQL, implement the winning operation with `INSERT ... ON CONFLICT DO NOTHING RETURNING id` (via a narrow native repository query or `JdbcTemplate`). Only a returned inserted row may cause the account balance or accrual total to change. Do not implement “check then insert”; two workers can both pass the check.

For maturity, use a unique maturity/settlement key and a conditional state update. A rerun must observe `MATURED`, `PAYOUT_PENDING`, `PAID`, or the existing equivalent and return without creating another payout instruction. External payout calls must carry the same idempotency key and be represented by a persisted instruction/status; never assume a remote timeout means the payout did not happen.

### Worker claiming and locks

* For a small single-instance job, a bounded ordered query plus the unique business key is sufficient.
* For multiple workers/instances, select eligible rows with PostgreSQL `FOR UPDATE SKIP LOCKED` in short transactions, or use an explicit claim/status/lease column. Process a bounded batch and commit per account or small batch, not one transaction for the entire portfolio.
* Use `@Lock(PESSIMISTIC_WRITE)` for ordinary entity reads where JPA semantics are enough. Use a native SQL query for `SKIP LOCKED`, `NOWAIT`, or PostgreSQL-specific `ON CONFLICT`; do not assume a JPA lock annotation produces those clauses.
* Acquire locks in a stable order (account ID ascending, then related ledger rows) and set bounded transaction/query timeouts. PostgreSQL detects deadlocks but aborts one transaction; the service may retry a narrowly scoped deadlock/serialization failure, never an unknown payout outcome.
* Keep job processing deterministic and resumable. Log job run ID, operation, batch size, selected/completed/skipped/failed counts, and duration; never log full account numbers or monetary-sensitive payloads unnecessarily.

### ShedLock boundary

ShedLock is appropriate to prevent two nodes from starting the same daily sweep simultaneously. Use a Flyway-created `shedlock` table, `@SchedulerLock`, a conservative `lockAtMostFor` longer than the measured worst-case run, and the JDBC provider's database-time option. A missed lock is skipped, not queued. Therefore the job must be safe to rerun, and per-account unique keys/row locks remain mandatory. If the sweep must be distributed, use database row claiming; ShedLock alone is not a work queue.

Do not use an in-memory lock, JVM `synchronized`, or an application-local `ConcurrentHashMap` for financial lifecycle exclusion. Those fail across instances and restarts.

## Auditing and Financial Records

Use two complementary mechanisms:

1. **Business ledger/lifecycle records (mandatory):** Flyway-managed immutable FD accrual, state-transition, funding, closure, and payout/settlement records. These contain the business actor, system job identity, reason, effective date, amount, currency, rule/product snapshot reference, idempotency key, and correlation ID. They are the authoritative audit trail for money and lifecycle behavior.
2. **JPA metadata auditing (useful):** Spring Data JPA `@CreatedDate`, `@LastModifiedDate`, `@CreatedBy`, and `@LastModifiedBy` with `@EnableJpaAuditing` and an `AuditorAware` that resolves the authenticated principal or returns a stable `system-job` identity. Apply this to mutable operational entities, not as the only record of a financial event.

Hibernate Envers may be added for “what did this mutable row look like?” investigations, but its revision tables do not replace an append-only financial ledger, do not automatically capture rejected/no-op attempts, and can add migration/retention complexity. Spring Boot Actuator's default in-memory `AuditEventRepository` is also unsuitable for production financial audit; it is for operational/security audit events and is explicitly documented as limited for development use.

## Contract Testing

Treat `BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml` as the existing external contract. Do not create a second contract DSL for this milestone.

Recommended test layers:

| Layer | Tool/pattern | What it proves |
|-------|--------------|----------------|
| Domain unit tests | JUnit Jupiter, AssertJ from `spring-boot-starter-test` | Calculation precision, transition matrix, date/rate rules, idempotent result semantics. No mocks for arithmetic. |
| Repository/migration tests | `@SpringBootTest` or focused `@DataJpaTest` + PostgreSQL Testcontainers + `@ServiceConnection` | Flyway migrations, Hibernate validation, numeric columns, constraints, locks, `ON CONFLICT`, `SKIP LOCKED`, and real transaction behavior. |
| HTTP contract tests | MockMvc + `spring-security-test` + `swagger-request-validator-mockmvc` v2.x | Every delivered `/fd/v1` request and response matches the checked-in OpenAPI path, status, headers, and schema. Include 4xx/409/422 error shapes. |
| Concurrency tests | Two application transactions against one PostgreSQL container, with latches/awaitility only for coordination | Duplicate booking/accrual/maturity/closure requests produce one financial effect and a deterministic conflict/result. |
| Job rerun tests | Invoke the job service twice with the same business date and with an interrupted first run | No duplicate ledger rows, no duplicate payout instructions, and resumable processing. |

The OpenAPI validator's v3 release is not compatible with this application: its official release notes require Java 21/Spring 7/Boot 4. Stay on the v2 artifact line and verify that Maven resolves a single Spring 6.2/Jackson version. If dependency convergence is unacceptable, prefer a test-only core validator or explicit OpenAPI schema assertions rather than upgrading the whole application to Boot 4.

Spring Cloud Contract 4.3.x is compatible with the Spring Cloud 2025.0 release train and Boot 3.5, but it should not be introduced here: it would add a parallel consumer-driven contract format when this repository already owns a static OpenAPI contract. Reconsider it only when an independent consumer needs generated stubs/provider verification.

## Flyway and PostgreSQL Migration Rules

* Keep `spring.jpa.hibernate.ddl-auto=validate`; entities do not own production schema creation.
* Add one forward-only, reviewed Flyway migration for each logical change. Include constraints, unique indexes, partial indexes, and job/audit tables in migrations.
* Test every migration from a clean PostgreSQL database and against a copy/representative snapshot of the current schema before deployment.
* `baseline-on-migrate: true` is a legacy-adoption setting and can hide an unexpectedly empty/unmanaged database if used casually. Keep it only where the deployment baseline is intentionally documented; otherwise fail closed and baseline explicitly during controlled adoption.
* Do not edit an applied migration. Do not use Hibernate auto-DDL, `data.sql`, or startup code to backfill financial records.
* Use `timestamptz`/UTC for instants and `date` for business dates. Add indexes matching the job predicates, such as active state plus maturity/accrual date, and inspect query plans on production-sized data.
* Add database `CHECK` constraints for non-negative principal/amounts where the domain permits, valid scale/ranges, and state/date consistency. Prefer a unique constraint over a Java-only duplicate check.

## Configuration Changes for FD Safety

The current `application.yml` has `spring.jpa.show-sql: true`, formatted SQL, and a tracked JWT secret. For the FD milestone:

* Set SQL logging off by default outside a controlled local profile; use parameter-safe structured logs and slow-query metrics instead.
* Set Hibernate JDBC timezone to UTC and define a single application `Clock`.
* Configure bounded datasource/pool, transaction, and statement timeouts appropriate to the batch size. A job must fail and resume, not hold locks indefinitely.
* Expose only aggregate job metrics and health state through Actuator; restrict actuator details and the OpenAPI UI in production.
* Remove secrets from tracked YAML as a separate security task; FD work must not copy the current static-secret pattern into idempotency or scheduler configuration.

## Alternatives Rejected / Do Not Introduce

| Option | Decision | Why |
|--------|----------|-----|
| JSR 354/Moneta | Reject for this milestone | Adds an abstraction without solving the required schema scale, rounding, or audit rules. `BigDecimal` plus explicit currency/scale is clearer for the current single relational model. |
| Spring Statemachine/workflow engine | Reject | Too much machinery for a small explicit FD state graph; obscures transition rules and complicates persistence/audit compatibility. |
| Spring Batch | Defer | Useful for very large restartable jobs, but its metadata tables and operational model are unnecessary for the current bounded daily FD sweeps. Revisit when volume and SLA require partitioned batch execution. |
| Quartz | Reject for now | Spring scheduling plus database claiming/ShedLock is sufficient; adding a second scheduler does not provide financial idempotency. |
| Redis/Hazelcast/in-memory locks | Reject | Adds infrastructure and does not provide authoritative financial exclusion. PostgreSQL already owns the lifecycle state. |
| Kafka/events/outbox | Defer | Event publishing is explicitly out of scope for this milestone. Persist settlement intent and idempotency first; introduce an outbox only when a real downstream consumer exists. |
| jOOQ or R2DBC | Reject for now | Creates a second persistence style in a JPA modular monolith. Use small native SQL/JdbcTemplate boundaries for PostgreSQL-only operations. |
| H2-only integration testing | Reject | H2 cannot be the authority for PostgreSQL locking, migration, numeric, and conflict semantics. |
| Spring Cloud Contract | Defer | Current OpenAPI document is the contract; duplicating it in a DSL increases drift. |
| OpenAPI validator v3 | Reject | Requires Java 21/Spring 7/Boot 4 according to its release notes, incompatible with the current Java 17/Boot 3.5.5 baseline. |

## Installation Shape

The initial proposed POM additions are intentionally small and test-heavy:

```xml
<!-- Testcontainers: versions inherited from the Spring Boot 3.5.5 parent -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-testcontainers</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>postgresql</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>junit-jupiter</artifactId>
  <scope>test</scope>
</dependency>

<!-- Only if multiple instances can run scheduled jobs; pin one verified 7.x version -->
<dependency>
  <groupId>net.javacrumbs.shedlock</groupId>
  <artifactId>shedlock-spring</artifactId>
  <version>${shedlock.version}</version>
</dependency>
<dependency>
  <groupId>net.javacrumbs.shedlock</groupId>
  <artifactId>shedlock-provider-jdbc-template</artifactId>
  <version>${shedlock.version}</version>
</dependency>
```

Add the v2 OpenAPI validator only in test scope and only after `mvn dependency:tree` confirms no Spring/Jackson downgrade. Add Envers only if a concrete history query requires it.

## Confidence and Compatibility Risks

| Area | Confidence | Notes |
|------|------------|-------|
| BigDecimal/PostgreSQL numeric | HIGH | Confirmed against PostgreSQL 18 official numeric documentation; exact behavior is applicable to supported PostgreSQL versions. |
| Spring service transactions | HIGH | Confirmed against Spring Data JPA transactionality documentation and fits the existing repository/service structure. |
| JPA locking + PostgreSQL row locks | HIGH | Spring Data `@Lock` and PostgreSQL `FOR UPDATE` are documented; native SQL remains necessary for `SKIP LOCKED`/`ON CONFLICT`. |
| Flyway ownership | HIGH | Existing schema-first `ddl-auto=validate` design and current Flyway migrate guidance support forward-only migrations. |
| Testcontainers/Spring Boot integration | HIGH | Spring Boot 3.5 documentation documents PostgreSQL containers, `@ServiceConnection`, and context lifecycle concerns. |
| ShedLock | MEDIUM | Official project documentation reports Java 17 and Boot 3.5 compatibility for 7.x, but the exact latest Maven Central release and Boot 3.5.5 convergence must be verified in the build. |
| OpenAPI validator v2 | MEDIUM | Official release notes establish the v3 incompatibility and v2 Spring 6/Boot 3 support; exact v2.46.0 dependency convergence with Boot 3.5.5 needs a Maven build check. |
| Envers | MEDIUM | The capability is established, but schema/table naming and audit retention must be reconciled with the existing Flyway schema before adoption. |

## Sources

* Spring Boot 3.5 build systems and curated dependency management: https://docs.spring.io/spring-boot/3.5/reference/using/build-systems.html (HIGH)
* Spring Boot 3.5 Testcontainers and `@ServiceConnection`: https://docs.spring.io/spring-boot/3.5/reference/testing/testcontainers.html (HIGH)
* Spring Data JPA transaction boundaries: https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html (HIGH)
* Spring Data JPA locking: https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html (HIGH)
* Spring Data JPA auditing: https://docs.spring.io/spring-data/jpa/reference/auditing.html (HIGH)
* Spring Boot Actuator auditing limitations: https://docs.spring.io/spring-boot/3.5/reference/actuator/auditing.html (HIGH)
* PostgreSQL numeric types: https://www.postgresql.org/docs/current/datatype-numeric.html (HIGH)
* PostgreSQL explicit/row/advisory locking: https://www.postgresql.org/docs/current/explicit-locking.html (HIGH)
* PostgreSQL `SELECT ... FOR UPDATE ... SKIP LOCKED`: https://www.postgresql.org/docs/current/sql-select.html (HIGH)
* Flyway migrate command and schema-history behavior: https://documentation.red-gate.com/flyway/reference/commands/migrate (HIGH)
* Testcontainers PostgreSQL module guidance: https://java.testcontainers.org/modules/databases/ (HIGH)
* ShedLock compatibility, JDBC provider, and at-most-once-overlap semantics: https://github.com/lukas-krecan/ShedLock (MEDIUM; release/version must be checked in Maven)
* Spring Cloud release compatibility (reference only; not recommended for this milestone): https://spring.io/projects/spring-cloud (MEDIUM)
* Atlassian OpenAPI Request Validator release notes, including v2/v3 compatibility boundary: https://github.com/atlassian/openapi-request-validator/blob/master/RELEASE-NOTES.md (MEDIUM/HIGH for the stated compatibility facts)
