<user_constraints source="[VERIFIED: .planning/phases/02-product-booking-and-funding/02-CONTEXT.md]">
## User Constraints (from CONTEXT.md)

### Locked Decisions

### API Surface
- **D-01:** Implement Phase 2 on the existing versioned `/fd/v1` surface rather than reviving the legacy `/api/fd` booking path.
- **D-02:** Add a customer-facing `GET /fd/v1/products` eligible-offer endpoint. Keep `/fd/v1/ref/*` for internal/reference-data semantics rather than using the raw reference route for customer offers.
- **D-03:** Use the existing `/fd/v1/accounts` account-creation contract for booking and add an explicit account funding operation under the same versioned surface.
- **D-04:** Preserve the existing contract and error conventions; do not implement a second compatibility API in this phase.

### Booking Terms and Offers
- **D-05:** Customer booking accepts tenure in months and a currency code. Do not use the legacy days-based request as the authoritative Phase 2 contract.
- **D-06:** Eligible offers expose complete booking choices: active product name/code, currency, minimum and maximum principal, supported tenure-month range, annual rate, compounding/payout terms, and the maturity-estimate basis needed by the REST demonstration.
- **D-07:** Use one server-owned annual rate per offer across its supported tenure range for the academic scope. Do not introduce a tenure-to-rate matrix in this phase.
- **D-08:** Product terms, rate, currency eligibility, amount limits, term limits, maturity date, and maturity estimate are resolved or calculated server-side. Client-supplied rate, status, customer identity, and derived maturity values are not authoritative.
- **D-09:** On booking, snapshot the resolved principal, rate, term, currency, dates, maturity estimate, applied rate reference, and policy version into the FD account so later product changes do not rewrite the booking.

### Funding and Activation
- **D-10:** Funding is an explicit operation, separate from booking and separate from merely changing a status.
- **D-11:** The funding amount must equal the booked principal exactly. Partial funding, overfunding, and multiple funding stages are not part of this phase.
- **D-12:** A successful funding operation records an internal funding transaction, then transitions the FD from `PENDING` to `ACTIVE` using the Phase 1 lifecycle boundary.
- **D-13:** The response must describe the internal demonstration record truthfully; it must not imply that an external payment rail was called or that money moved outside the application.
- **D-14:** The unfunded booking remains `PENDING` and must not be treated as active or eligible for later processing.

### Customer Association and Access
- **D-15:** Phase 2 booking is available to authenticated customers creating an FD for themselves. Staff/admin booking on behalf of another customer is not added here.
- **D-16:** The customer/holder identity is derived from the local authenticated actor and is not accepted as a caller-controlled request field.
- **D-17:** If the authenticated local customer has no FD-specific reference row, the booking flow resolves or creates the minimal FD user reference required by the existing schema. The client does not manage that synchronization.

### Frontend Boundary
- **D-18:** No Angular FD screens, routes, services, or visual changes are required for Phase 2. REST, Swagger, and Postman remain the acceptance surface; the existing Angular landing/auth/dashboard shell is preserved.

### the agent's Discretion
- Exact Java DTO, service, adapter, and repository class names, provided they follow the existing domain-package conventions.
- The concrete mapping from normalized product/rule/currency tables into `ProductTermsPort`, provided it remains server-authoritative and does not replace existing product services.
- Exact HTTP status/error-code details where the existing OpenAPI contract does not already fix them, provided validation, missing-resource, unsupported-term/currency, and invalid-transition failures remain structured and truthful.
- The exact idempotency implementation details may use the Phase 1 durable operation identity boundary; Phase 5 owns the full duplicate-action test and protection gate.

### Deferred Ideas (OUT OF SCOPE)
- Angular FD screens and dashboard integration remain deferred unless a later phase proves the API cannot be demonstrated adequately without them.
- Staff/admin booking on behalf of customers is deferred to the Phase 5 authorization work.
- Partial funding, overfunding, recurring funding, and tenure-specific rate matrices are outside the academic Phase 2 scope.
- Interest accrual, maturity processing, closure, settlement/history completion, and full duplicate/ownership test gates remain in Phases 3-5.
</user_constraints>

<phase_requirements source="[VERIFIED: .planning/REQUIREMENTS.md and .planning/ROADMAP.md]">
## Phase Requirements

| ID | Description | Research Support |
|----|-------------|------------------|
| PROD-01 | An authenticated customer can view active FD products and the supported term, rate, amount, and maturity information needed to choose one. | Product-offer endpoint and normalized product/rule/currency adapter in the Product Terms findings. |
| BOOK-01 | An authenticated customer can book an FD associated with their customer/account identity using a valid product, principal, currency, and term. | Booking service, actor-to-`fd_user_ref` resolution, account snapshot, and `/fd/v1/accounts` contract guidance. |
| BOOK-02 | The server validates product eligibility, positive amount, supported term/currency, required fields, and customer association before saving a booking. | Validation matrix, fail-closed terms adapter, authenticated actor boundary, and service transaction guidance. |
| BOOK-03 | The booking stores the agreed principal, rate, term, open date, maturity date, and maturity estimate so later product changes do not unexpectedly rewrite the demonstration account. | Snapshot mapping and required additive policy-version migration findings. |
| FUND-01 | A newly booked FD starts in a pending/unfunded status and becomes active only after one valid funding operation for the required principal. | Funding transaction, row lock, state-machine `FUND` transition, and rollback/integration test map. |
</phase_requirements>

# Phase 2: Product Booking and Funding - Research

**Researched:** 2026-09-19  
**Domain:** Spring Boot FD product terms, PostgreSQL booking, and internal funding  
**Confidence:** HIGH for repository facts; MEDIUM for unresolved product-reference and maturity-estimate choices

## Summary

Phase 1 provides the correct boundaries but not the behavior: `ProductTermsPort`/`ProductTerms` are server-authoritative contracts, `FdAccount` is pending-safe, the lifecycle state machine owns `PENDING -> ACTIVE`, and V6 provides row locking/versioning and durable operation identity. The current `/fd/v1` controller still returns `501`, while the legacy `/api/fd` booking service intentionally throws; Phase 2 should connect only the versioned controller to new application services. [VERIFIED: `.planning/phases/01-existing-fd-baseline/01-01-SUMMARY.md`, `.planning/phases/01-existing-fd-baseline/01-02-SUMMARY.md`, `.planning/phases/01-existing-fd-baseline/01-03-SUMMARY.md`, `BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java`, `BACKEND/src/main/java/com/bank/fd/service/FdAccountService.java`]

The major planning risk is reference-data shape, not HTTP plumbing. `fd_product_ref`, `fd_currency_ref`, `fd_rule_outcome_ref`, and `fd_user_ref` are local FD reference tables used by `fd_accounts`, but amount/term/rate/frequency rules live in the normalized `product`, `product_rule`, `rule_condition`, `rule_outcome`, `product_currency_config`, and attribute tables. Only `FdProduct` is currently mapped in Java, and its rule fields are explicitly transient. There is no visible seed data or synchronization implementation in the repository, so the plan must define deterministic reference fixtures/provisioning and a fail-closed adapter before PROD-01 can be demonstrated. [VERIFIED: `BACKEND/src/main/resources/db/migration/V1__bankingtech_public_schema.sql`, `BACKEND/src/main/java/com/bank/product/entity/FdProduct.java`, `BACKEND/src/main/java/com/bank/product/service/FdProductService.java`, `BACKEND/src/main/java/com/bank/fd/entity/FdAccount.java`, `BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java`]

**Primary recommendation:** add a small `fd.application` booking/funding service layer behind typed `/fd/v1` DTOs; implement a read-only normalized-terms adapter, resolve/create the authenticated customer's `fd_user_ref`, snapshot all authoritative booking values into a `PENDING` account, and make `POST /fd/v1/accounts/{id}/fund` one short transaction that validates exact principal, inserts a `DEPOSIT`-type internal transaction, and applies the Phase 1 `FUND` transition. Add only the forward migration needed to persist the policy version and any deterministic reference identity required by the adapter. [RECOMMENDED: based on the verified repository boundaries above]

## Architectural Responsibility Map

| Capability | Primary Tier | Secondary Tier | Rationale |
|------------|-------------|----------------|-----------|
| List eligible FD offers | API / Backend | Database / Storage | The controller should expose a customer DTO; the application service resolves active product/rule/currency data from PostgreSQL. [VERIFIED: `FdV1ContractController.java`, `ProductTermsPort.java`, `V1__bankingtech_public_schema.sql`] |
| Resolve authenticated customer and FD holder reference | API / Backend | Database / Storage | JWT supplies the local username; customer/profile and `fd_user_ref` repositories resolve the server-owned holder ID. [VERIFIED: `JwtAuthenticationFilter.java`, `CustomerService.java`, `CustomerProfile.java`, `V1__bankingtech_public_schema.sql`] |
| Validate and snapshot a booking | API / Backend | Database / Storage | Service-layer validation owns cross-table eligibility and date/maturity calculation; `fd_accounts` owns the durable booking snapshot. [VERIFIED: `ProductTerms.java`, `FdAccount.java`, `V1__bankingtech_public_schema.sql`] |
| Record funding and activate the account | API / Backend | Database / Storage | Funding is a domain command that locks the account, writes the internal transaction, then applies the state machine transition in one database transaction. [VERIFIED: `FdLifecycleStateMachine.java`, `FdAccountRepository.java`, `FdTransaction.java`] |
| Angular FD screens | — | — | Explicitly out of scope; REST/Swagger/Postman is the acceptance surface. [VERIFIED: `02-CONTEXT.md`, `AGENTS.md`] |

## Project Constraints (from AGENTS.md)

- Continue the existing Spring Boot/Angular system rather than replacing working modules. [VERIFIED: `AGENTS.md`]
- Preserve existing authentication, customer, product, calculator, reporting, legacy FD, migration, and API behavior unless the FD workflow requires a change. [VERIFIED: `AGENTS.md`]
- Reuse existing services, entities, repositories, calculators, authentication mechanisms, Flyway migrations, and project conventions before adding replacements. [VERIFIED: `AGENTS.md`]
- Keep the implementation understandable for a college project: simple service-layer logic, clear domain models, minimal dependencies, and focused tests. [VERIFIED: `AGENTS.md`]
- Use `BigDecimal` with explicit scale and rounding for monetary calculations. [VERIFIED: `AGENTS.md`]
- Validate lifecycle transitions, customer association, ownership, role access, and duplicate actions at the appropriate service/API boundaries. [VERIFIED: `AGENTS.md`]
- Use PostgreSQL/Flyway persistence and add only necessary schema changes and constraints. [VERIFIED: `AGENTS.md`]
- Prefer REST/Swagger/Postman as the v1 demonstration surface; Angular FD screens are deferred. [VERIFIED: `AGENTS.md`]
- Do not introduce distributed locks, workflow engines, event/outbox infrastructure, real payment rails, elaborate reconciliation, or a major authentication rewrite for this milestone. [VERIFIED: `AGENTS.md`]
- Do not expose secrets, credentials, or sensitive account data in source, documentation, logs, or tests. [VERIFIED: `AGENTS.md`]
- Read the project planning artifacts and relevant phase artifacts before changing code, plan phases before implementation, and verify each phase against its success criteria. [VERIFIED: `AGENTS.md`]

## Standard Stack

### Core

| Library / technology | Version | Purpose | Why standard |
|---|---:|---|---|
| Java | 17 | Backend language/runtime | Already declared by the Maven project. [VERIFIED: `BACKEND/pom.xml`] |
| Spring Boot | 3.5.5 | Application/runtime baseline | Existing parent and project baseline; do not replace it. [VERIFIED: `BACKEND/pom.xml`, `.planning/codebase/STACK.md`] |
| Spring MVC + Jakarta Validation | Boot-managed | Typed REST DTO binding and request validation | Existing controllers use Spring MVC and `@Valid`; the v1 baseline already established typed account requests. [VERIFIED: `BACKEND/pom.xml`, `CreateAccountContractRequest.java`, `FdV1ContractController.java`] |
| Spring Data JPA/Hibernate | Boot-managed | Entity mappings, repositories, locks, and persistence | Existing FD/account/product/customer repositories use this boundary. [VERIFIED: `BACKEND/pom.xml`, `FdAccountRepository.java`, `FdProductRepository.java`] |
| Spring Security + local JWT | Boot-managed + `jjwt` 0.11.5 | Authenticated customer endpoint boundary | Existing stateless bearer filter and method-security configuration are the project authority. [VERIFIED: `SecurityConfig.java`, `JwtAuthenticationFilter.java`, `BACKEND/pom.xml`] |
| PostgreSQL | Existing deployment/test target | Durable normalized FD data and transactional funding | Flyway SQL is PostgreSQL-oriented and the application datasource uses PostgreSQL. [VERIFIED: `application.yml`, `V1__bankingtech_public_schema.sql`] |
| Flyway | Boot-managed | Forward-only schema evolution | Existing startup and Testcontainers tests apply migrations; do not edit V1-V6 in place. [VERIFIED: `application.yml`, `FdPostgresSchemaTest.java`, Phase 1 summaries] |

### Supporting

| Library / technology | Version | Purpose | When to use |
|---|---:|---|---|
| `springdoc-openapi-starter-webmvc-ui` | 2.8.13 | Swagger/OpenAPI exposure | Update the static FD contract alongside controller DTOs. [VERIFIED: `BACKEND/pom.xml`, `application.yml`, `fd-service-openapi.yaml`] |
| Testcontainers PostgreSQL | Boot-managed | Real PostgreSQL migration and booking/funding integration tests | Use for schema/transaction behavior; it is already test-scoped in Maven. [VERIFIED: `BACKEND/pom.xml`, `FdPostgresSchemaTest.java`] |
| H2 | Test-scoped, Boot-managed | Fast existing Spring tests | Use only for lightweight unit/context compatibility; it is not the authoritative PostgreSQL migration gate. [VERIFIED: `application-test.yml`, `FdPostgresSchemaTest.java`, Phase 1 summary] |
| `BigDecimal`, `Clock`, `FdCalculationPolicy`, `FdTimePolicy` | Java 17 / repository types | Money, UTC business date, scale, rounding, and reproducible booking estimates | Use for all Phase 2 derived money/date values; do not call the legacy `double` calculator as authority. [VERIFIED: `FdCalculationPolicy.java`, `FdTimePolicy.java`, `FdCalculatorService.java`, `application.yml`] |

### Alternatives Considered

| Instead of | Could use | Tradeoff / decision |
|---|---|---|
| `/fd/v1` application services | Revive `/api/fd` | Rejected by locked D-01; the legacy service intentionally throws and uses days/username semantics. [VERIFIED: `02-CONTEXT.md`, `FdAccountService.java`, `CreateFdRequest.java`] |
| Normalized terms adapter | Trust `FdProduct` transient fields | Rejected: those fields are not persisted, so they cannot be an authority. [VERIFIED: `FdProduct.java`] |
| PostgreSQL/Testcontainers integration | H2-only integration | H2 is useful for fast tests but cannot validate PostgreSQL Flyway constraints/locking; keep the existing Docker-gated test pattern. [VERIFIED: `application-test.yml`, `FdPostgresSchemaTest.java`] |
| Simple application service | Generic rule engine/workflow infrastructure | Rejected by academic project constraints; support the small known FD rule vocabulary explicitly and fail when required terms are absent. [VERIFIED: `AGENTS.md`, `.planning/PROJECT.md`] |

**Installation:** No new dependency is required for the recommended design. Use the existing backend build:

```bash
mvn -q test
```

**Version verification:** Existing Java/Spring dependency versions were verified from `BACKEND/pom.xml`; no package upgrade is recommended for this phase. Testcontainers versions remain Maven-parent managed rather than being overridden. [VERIFIED: `BACKEND/pom.xml`]

## Architecture Patterns

### System Architecture Diagram

```text
Authenticated local JWT / username
              |
              v
      /fd/v1/products ------------------------------+
              |                                      |
              v                                      v
   Offer query service -> ProductTermsPort -> normalized product/rule/currency data
              |                                      |
              +------------------------------> Offer DTO

Authenticated customer + product_id + principal + tenure_months + currency_code
              |
              v
      POST /fd/v1/accounts
              |
              v
 Actor resolver -> resolve/create fd_user_ref -> ProductTermsPort
              |                                      |
              +--> UTC date/estimate policy --------+
                                                     v
                                  fd_accounts snapshot, status=PENDING
                                                     |
                                                     v
                                         Booking response DTO

PENDING account + exact principal (+ matching currency) + authenticated customer
              |
              v
      POST /fd/v1/accounts/{id}/fund
              |
              v
 Pessimistic account lock -> validate PENDING/exact amount/currency
              |
              v
 fd_transactions(DEPOSIT/internal record)
              |
              v
 FdLifecycleStateMachine(FUND) -> fd_accounts.status=ACTIVE -> funding response DTO

No external payment rail is called in this phase.
```

The diagram follows the repository's controller → application service → repository/entity layering and shows the required database write order. [VERIFIED: `.planning/codebase/ARCHITECTURE.md`, `02-CONTEXT.md`, `FdLifecycleStateMachine.java`, `FdAccountRepository.java`]

### Recommended Project Structure

```text
BACKEND/src/main/java/com/bank/fd/
├── api/controller/FdV1ContractController.java       # /products, create account, fund
├── api/dto/                                         # typed request/response contracts
├── application/
│   ├── AccountBookingService.java                  # PENDING booking transaction
│   ├── FundingService.java                          # funding + FUND transition transaction
│   ├── FdActorResolver.java                         # local auth -> customer/fd_user_ref
│   ├── ProductTermsAdapter.java                     # normalized terms -> ProductTerms
│   └── ProductOfferQueryService.java                # customer offer list
├── entity/                                          # FdAccount, transaction, reference projections
├── repository/                                      # account, transaction, ref, terms queries
└── domain/                                          # existing lifecycle/time/money policies
```

Keep product administration in `com.bank.product`; the FD adapter may consume it or its repositories but must not replace `FdProductService`. [RECOMMENDED: follows `AGENTS.md`, `.planning/codebase/ARCHITECTURE.md`, and `02-CONTEXT.md`]

### Pattern 1: Server-authoritative terms resolution

**What:** Treat the HTTP request as a lookup query containing product, principal, tenure, and currency code. Resolve all rate, eligibility, frequency, reference IDs, and derived values from server-owned data before constructing `FdAccount`. [VERIFIED: `ProductTermsQuery.java`, `ProductTerms.java`, `ProductTermsPort.java`, D-05 through D-09]

**When to use:** Every offer response and booking command. Never persist `interest_rate`, `open_date`, `maturity_date`, `maturity_amount`, holder ID, or status from caller input. [VERIFIED: `CreateAccountContractRequest.java`, D-08]

```java
ProductTerms terms = productTermsPort.resolveActiveTerms(
        new ProductTermsQuery(productId, principal, tenureMonths, currencyCode),
        timePolicy.businessDate());
// Copy terms + requested principal + derived dates/estimate into the account snapshot.
```

Source: `[VERIFIED: BACKEND/src/main/java/com/bank/fd/application/ProductTermsPort.java]` and `[VERIFIED: BACKEND/src/main/java/com/bank/fd/application/ProductTerms.java]`.

### Pattern 2: One service transaction per financial command

**What:** Booking commits holder resolution, account creation, and the pending snapshot together. Funding commits account lock, validation, internal transaction insert, and `PENDING -> ACTIVE` together. [RECOMMENDED: based on the existing repository transaction pattern and locked D-12]

**Why:** Spring documents annotation-based transaction semantics for bean methods, and Spring Data JPA documents explicit lock metadata on repository methods. [CITED: https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html; https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html]

### Pattern 3: Reference projection versus normalized authority

**What:** Map only the read fields needed for the offer resolver. Use `fd_product_ref`/`fd_currency_ref`/`fd_rule_outcome_ref` IDs for the FD account foreign keys, while reading authoritative rule values from normalized tables or a verified projection. Reject missing or ambiguous mappings. [VERIFIED: `V1__bankingtech_public_schema.sql`, `FdProduct.java`, `FdAccount.java`]

**When to use:** `GET /fd/v1/products` and booking. Do not expose raw `/ref/*` rows as a customer offer and do not silently fall back to client rate data. [VERIFIED: D-02, D-08]

### Anti-Patterns to Avoid

- **Client-controlled rate/status/holder:** These fields are either present in the current baseline DTO or legacy controller but are explicitly non-authoritative for Phase 2. [VERIFIED: `CreateAccountContractRequest.java`, `FdAccountController.java`, D-08, D-16]
- **Direct entity serialization:** `FdAccount` contains compatibility accessors and internal version/UUID fields; map explicit API response DTOs. [RECOMMENDED: based on `FdAccount.java` and existing OpenAPI `Account` schema]
- **Status-only activation:** Calling `/activate` or setting an enum without the funding transaction violates D-10 through D-14. [VERIFIED: `FdLifecycleStateMachine.java`, `02-CONTEXT.md`]
- **Legacy days conversion:** `FdAccount.setTermDays` only accepts whole 30-day units; it is not the Phase 2 months contract. [VERIFIED: `FdAccount.java`, D-05]
- **Using `FdCalculatorService` as the booking authority:** It converts values to `double`, uses `Math.pow`, and accepts percent-style rate inputs, while Phase 1 uses decimal-fraction rates and explicit policy. [VERIFIED: `FdCalculatorService.java`, `FdCalculationPolicy.java`, Phase 1 summary]
- **Assuming `synchronized MAX(accountNumber)` is distributed-safe:** the current generator serializes only within one JVM and relies on the database unique key for collisions. [VERIFIED: `AccountNumberGenerator.java`, `.planning/codebase/CONCERNS.md`]

## Product and Reference Data Findings

### Where each value lives

| Needed value | Current storage | Current Java coverage | Planning consequence |
|---|---|---|---|
| FD product code/name/status used by account FK | `public.fd_product_ref` | `FdProduct` + `FdProductRepository` | Use active local FD references for account `fa_product_id`, but do not expect terms there. [VERIFIED: `V1__bankingtech_public_schema.sql`, `FdProduct.java`] |
| Normalized product identity/effective status | `public.product` | No entity/repository found | Adapter must map local FD product code to normalized product data or use a verified local projection. [VERIFIED: `V1__bankingtech_public_schema.sql`, repository grep] |
| Amount/tenure eligibility | `product_rule`, `rule_condition`, and/or product attribute tables | No entity/repository found | Define a small explicit rule vocabulary and parse/validate `value_from`/`value_to` or attribute values; do not build a generic rule engine. [VERIFIED: `V1__bankingtech_public_schema.sql`, `AGENTS.md`] |
| Annual rate, compounding, payout | `rule_outcome` (`ro_outcome_value`, `ro_unit`, `ro_compounding_basis`, `ro_payout_frequency`) | No entity/repository found | Resolve one active rate outcome, normalize decimal-fraction rate, and map it to local `fd_rule_outcome_ref`. [VERIFIED: `V1__bankingtech_public_schema.sql`, `ProductTerms.java`] |
| Currency eligibility | `product_currency_config` + normalized `currency` | No entity/repository found | Match request currency code case-insensitively to a supported active currency, then obtain the local `fd_currency_ref` ID for the account FK. [VERIFIED: `V1__bankingtech_public_schema.sql`, D-05] |
| Account currency FK | `fd_currency_ref` | No Java entity/repository found | Add a minimal local reference entity/repository or a focused projection. `fa_currency_id` points here, not directly to normalized `currency`. [VERIFIED: `V1__bankingtech_public_schema.sql`, `FdAccount.java`] |
| Applied rate FK | `fd_rule_outcome_ref` | No Java entity/repository found | Add a minimal local reference entity/repository. The normalized `rule_outcome` ID is not declared as an FK target for `fa_applied_rate_outcome_id`. [VERIFIED: `V1__bankingtech_public_schema.sql`] |
| Holder FK | `fd_user_ref` | No Java entity/repository found | Resolve by local `User.userUuid`, create the reference if absent, then store its generated `fur_user_id`. [VERIFIED: `V1__bankingtech_public_schema.sql`, `User.java`, D-17] |
| Funding transaction type | `transaction_types` | Java `TransactionType` enum only; no mapped entity/repository | Resolve a database row by stable code such as `DEPOSIT`; do not persist the Java enum ordinal/name into `ft_txn_type_id`. [VERIFIED: `V1__bankingtech_public_schema.sql`, `TransactionType.java`, `FdTransaction.java`] |

### Required adapter/repository work

1. Add read-only projections/entities and repositories for the minimum normalized product/rule/currency rows and local FD reference rows. [RECOMMENDED: direct consequence of the missing mappings listed above]
2. Implement `ProductTermsPort` with this order: active `FdProduct` by local ID → normalized product by stable product code → active supported currency → active FD/rule outcome → amount/tenure/frequency values → validated `ProductTerms`. [RECOMMENDED: preserves both account foreign keys and server-owned normalized authority]
3. Fail with a structured unsupported/missing-terms error when no deterministic mapping exists. Never use `CreateAccountRequest.interest_rate` as a fallback. [VERIFIED: D-08; RECOMMENDED from `FdProduct` transient fields]
4. Ensure the adapter returns a local `appliedRateOutcomeId` and local `currencyId`, not an ID from a different table whose identity is only numerically coincidental. [VERIFIED: V1 foreign keys]
5. Decide how normalized rows become local `fd_*_ref` rows. The existing `/internal/sync/*` methods are placeholders/no-ops, and V1 contains DDL but no seed `INSERT` statements. [VERIFIED: `FdV1ContractController.java`, `V1__bankingtech_public_schema.sql`]

### Data mapping blocker to resolve before implementation

The schema does not declare a foreign key or source-ID column linking `product` to `fd_product_ref`, `currency` to `fd_currency_ref`, or `rule_outcome` to `fd_rule_outcome_ref`; the local tables contain only reduced copies of identifying/value fields. Exact code/value matching can work only if the data contract guarantees uniqueness and synchronization. Otherwise add a forward-only source-identity column/constraint or provision deterministic fixtures. [VERIFIED: `V1__bankingtech_public_schema.sql`]

## API and DTO Plan

### `GET /fd/v1/products`

Add a typed customer offer response, not a raw `FdProduct` entity. Each offer should contain `product_id`, `product_code`, `product_name`, `currency_code`, `minimum_amount`, `maximum_amount`, `minimum_tenure_months`, `maximum_tenure_months`, `annual_rate` as a decimal fraction, `compounding_frequency`, `payout_frequency`, and `maturity_estimate_basis`. Include the local applied-rate reference only if the existing contract needs it; do not require the client to assemble internal IDs. [VERIFIED: D-02, D-06, `ProductTerms.java`]

The offer list can expose a range/basis, while booking returns the exact maturity date and estimate for the submitted principal and tenure. If the product owner expects an exact per-offer maturity amount before the client supplies principal/tenure, add explicit query parameters and document that behavior; this is not currently fixed by the OpenAPI file. [RECOMMENDED; OPEN DECISION]

### `POST /fd/v1/accounts`

Replace the current placeholder's authoritative request shape with a typed DTO containing only:

```json
{
  "product_id": 1,
  "principal_amount": 1000.00,
  "tenure_months": 12,
  "currency_code": "USD"
}
```

The current baseline DTO instead requires `currency_id`, `interest_rate`, and accepts `open_date`/`applied_rate_outcome_id`; those are incompatible with D-05 and D-08 and must not remain authoritative. The request must not contain `primary_holder_id`, customer username, status, rate, maturity date, or maturity amount. [VERIFIED: `CreateAccountContractRequest.java`, `fd-service-openapi.yaml`, D-05, D-08, D-16]

Return `201` with an explicit `AccountResponse` containing account ID/number, product identity, holder reference only as a non-sensitive internal identifier if required, principal, currency code, locked rate/terms, open/maturity dates, maturity estimate, `status=PENDING`, and an explicit unfunded indicator. Do not return a JPA entity directly. [RECOMMENDED: based on existing OpenAPI `Account` and D-09/D-14]

### `POST /fd/v1/accounts/{accountId}/fund`

Add the explicit funding route selected in context. Use a typed body with `amount` and `currency_code` so the service can reject under/overfunding and wrong-currency requests; the server compares amount exactly to the booked principal and ignores any client status. The response should contain the account snapshot, `status=ACTIVE`, and a truthful `funding_recorded=true`/internal-record description without claiming external settlement. [RECOMMENDED: D-10 through D-13]

Keep unrelated `/fd/v1` routes as their existing explicit placeholders until their owning phases implement them. Do not make `/activate` a second funding implementation; if retained, it must remain clearly unsupported/deprecated in the contract until a later decision. [RECOMMENDED: D-03, D-04 and current `FdV1ContractController.java`]

### Error and authentication behavior

- Keep the class-level authenticated boundary and add a customer-role guard to implemented self-service mutations; `SecurityConfig` already requires authentication for all non-allowlisted routes and enables method security. [VERIFIED: `FdV1ContractController.java`, `SecurityConfig.java`]
- Resolve username from the authenticated principal and then resolve the local `CustomerProfile`; never accept caller-supplied holder/customer IDs. [VERIFIED: `JwtAuthenticationFilter.java`, `CustomerService.java`, D-16]
- Map missing product/currency/rule/reference to not-found or unsupported-term errors; invalid amount/term/field input to validation/business errors; an invalid transition or already-active funding request to conflict. The exact code strings remain discretionary, but `ApiErrorResponse` is the existing v1 placeholder shape and `GlobalExceptionHandler` currently emits a different map for domain exceptions, so the plan must deliberately unify or document the chosen v1 response. [VERIFIED: `ApiErrorResponse.java`, `GlobalExceptionHandler.java`, D-04]
- Do not alter legacy `/api/fd` into a second booking path. Its current unavailable error is safer than a misleading success until compatibility is explicitly planned. [VERIFIED: `FdAccountService.java`, D-01/D-04]

## Booking and Funding Transaction Design

### Booking sequence

1. Authenticate as a local customer and resolve the username to `CustomerProfile`/`User`; reject absent or non-customer identity. [VERIFIED: `CustomerService.java`, `User.java`, D-15/D-16]
2. Find or create one `fd_user_ref` row using the local user's stable UUID, customer number, display name, and active status. Add a unique identity constraint if the live schema does not already guarantee one. [VERIFIED: V1 schema, D-17; RECOMMENDED constraint]
3. Normalize currency code and principal scale; call `ProductTermsPort` with product ID, principal, tenure months, and currency code. [VERIFIED: `ProductTermsQuery.java`]
4. Validate active product, supported currency, amount range, tenure range, rate/unit, frequency, and applied local reference. [VERIFIED: `ProductTerms.java`, D-06/D-08]
5. Use `FdTimePolicy.businessDate()` and UTC to calculate open date, calendar maturity date, and the agreed estimate. Phase 1 defines Actual/365, opening-inclusive/maturity-exclusive dates, two-decimal `HALF_UP`, and `academic-v1`; the compounding formula still needs to be fixed explicitly. [VERIFIED: `FdTimePolicy.java`, `FdCalculationPolicy.java`, `application.yml`; OPEN DECISION for formula]
6. Allocate the required account number using the existing generator only after deciding the server-owned branch strategy. The request contains no branch; the current generator defaults blank input to `000`. [VERIFIED: `AccountNumberGenerator.java`, D-05; OPEN DECISION]
7. Persist `FdAccount` with the resolved local product/currency/rate references, principal, tenure, rate, frequencies, open/maturity dates, maturity amount, policy version, primary holder, and `PENDING` status. [VERIFIED: `FdAccount.java`, V1/V6 schema, D-09]
8. Commit as one service transaction and return a DTO. No funding transaction is written during booking. [RECOMMENDED: D-10/D-14 and service-layer conventions]

### Funding sequence

1. Resolve the authenticated customer and obtain the account with an owner-scoped lookup, or enforce ownership in the service before mutation. [RECOMMENDED: D-15/D-16; current repository only has unscoped `findByIdForUpdate`]
2. Lock the account with existing `findByIdForUpdate`, verify `PENDING`, exact principal equality using `BigDecimal.compareTo`, and matching currency. [VERIFIED: `FdAccountRepository.java`, D-11/D-14]
3. Resolve the transaction type master row by a stable database code (`DEPOSIT` is the existing Java vocabulary) and create `FdTransaction` with principal amount, account currency, UTC timestamp, balance-after convention, and a non-sensitive internal-record remark. [VERIFIED: `TransactionType.java`, `FdTransaction.java`, V1 schema; RECOMMENDED code choice]
4. Apply `FdLifecycleCommand.FUND` through `FdLifecycleStateMachine`; do not assign `ACTIVE` directly. [VERIFIED: `FdLifecycleStateMachine.java`, Phase 1 summary]
5. Save transaction and account within the same `@Transactional` service method. A failed validation or transition must roll back both. [RECOMMENDED: Spring transaction guidance cited above]
6. Return the active account DTO and state that the funding is an internal demonstration record, not an external payment. [VERIFIED: D-12/D-13]

### Schema changes

**Necessary or strongly indicated:**

- Add a forward-only migration for `fa_policy_version` because V1/V6 have no column for the D-09 policy-version snapshot and `fa_version` is a legacy string distinct from the numeric `fa_row_version`. Backfill/validate legacy rows before adding a non-null constraint, or define a documented legacy default. [VERIFIED: `FdAccount.java`, V6 migration, Phase 1 summary; RECOMMENDED migration]
- Add a unique local-user identity constraint/index for `fd_user_ref.fur_user_uuid` if legacy data preflight confirms no duplicates; D-17 requires resolve-or-create semantics and V1 currently declares the column only `NOT NULL`. [VERIFIED: V1 schema, D-17; RECOMMENDED migration]
- Add/seed a deterministic transaction-type row for the chosen funding code only if the target database does not already contain it. V1 creates `transaction_types` but does not seed it. [VERIFIED: V1 schema; OPEN DATA PROVISIONING DECISION]

**Do not add without evidence:**

- Do not redesign `fd_accounts`, replace V1-V6, add a payment table, add an outbox, or introduce distributed locks; those contradict project constraints. [VERIFIED: `AGENTS.md`, `.planning/PROJECT.md`]
- Do not make nullable legacy financial columns globally `NOT NULL` without a PostgreSQL preflight/backfill. V1 permits nullable rate/maturity fields and V6 deliberately used diagnostic preflight for its constraints. [VERIFIED: V1/V6 migrations, Phase 1 summary]
- Add conditional checks for maturity/rate only if existing rows can pass them and the new status invariants are agreed; service validation remains necessary for cross-table active-product rules. PostgreSQL documents that row checks cannot enforce arbitrary cross-row/table conditions. [CITED: https://www.postgresql.org/docs/current/ddl-constraints.html]

## Don't Hand-Roll

| Problem | Don't build | Use instead | Why |
|---|---|---|---|
| Lifecycle transition | Controller status assignment or a second enum switch | `FdLifecycleStateMachine` + `FdLifecycleCommand.FUND` | Phase 1 established the single transition vocabulary. [VERIFIED: `FdLifecycleStateMachine.java`] |
| Money/date policy | `double`, `Math.pow`, local system time, or ad-hoc rounding | `BigDecimal`, `FdCalculationPolicy`, `FdTimePolicy`, injected UTC `Clock` | The project has explicit policy fields and the legacy calculator uses an incompatible floating-point path. [VERIFIED: `FdCalculationPolicy.java`, `FdTimePolicy.java`, `FdCalculatorService.java`] |
| Authentication/identity | Request holder ID, username target, or custom JWT parser | Existing Spring Security principal + `CustomerService` + local JWT filter | Identity is a locked server-owned decision and security infrastructure already exists. [VERIFIED: `SecurityConfig.java`, `JwtAuthenticationFilter.java`, `CustomerService.java`] |
| Product/rate authority | Copying transient `FdProduct` fields or trusting request rate | `ProductTermsPort` backed by normalized/reference data | The current projection does not persist rule values. [VERIFIED: `FdProduct.java`, `ProductTermsPort.java`] |
| Database evolution | Editing V1-V6 or using Hibernate DDL | Additive Flyway migration plus PostgreSQL Testcontainers gate | Existing configuration uses Flyway and Hibernate validation. [VERIFIED: `application.yml`, `FdPostgresSchemaTest.java`] |
| Concurrent funding | JVM boolean/synchronized flag | Existing pessimistic account lock plus one database transaction | Spring Data exposes explicit lock metadata and V6 provides the locked repository method. [CITED: https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html; VERIFIED: `FdAccountRepository.java`] |
| External settlement | Payment client, fake provider, or “payment succeeded” status | Internal `fd_transactions` record with truthful response | External rails are out of scope and D-13 forbids implying one was called. [VERIFIED: `AGENTS.md`, `02-CONTEXT.md`] |
| Generic rule engine | New configurable rule DSL | Small explicit adapter for the supported academic rule vocabulary | Generic rule-engine redesign is out of scope. [VERIFIED: `AGENTS.md`, `.planning/PROJECT.md`] |

## Common Pitfalls

### Pitfall 1: Local reference IDs are confused with normalized IDs

**What goes wrong:** A normalized `product.pro_product_id`, `currency.cur_currency_id`, or `rule_outcome.ro_outcome_id` is written into an account column that references an `fd_*_ref` table. [VERIFIED: V1 foreign keys]

**How to avoid:** Resolve and persist the local FD reference IDs explicitly; add source identity or deterministic mapping before enabling offers. [RECOMMENDED]

**Warning signs:** Foreign-key violations in PostgreSQL, duplicate numeric IDs that appear to work in H2, or an offer whose response ID cannot be found in `/fd/v1/ref/*`. [RECOMMENDED verification]

### Pitfall 2: No reference rows means no customer offers

**What goes wrong:** The clean Flyway migrations create tables but do not insert products, rules, currencies, or transaction types; `/internal/sync/*` currently returns placeholders. [VERIFIED: V1 migration, `FdV1ContractController.java`]

**How to avoid:** Define a safe fixture/seed or external provisioning step for the demonstration and test it on PostgreSQL. Do not make the adapter invent rate/limits. [RECOMMENDED]

### Pitfall 3: Maturity estimate formula is silently inconsistent with Phase 3

**What goes wrong:** Booking uses the legacy percentage/days/double calculator while later accrual uses the Phase 1 decimal-fraction/Actual-365 policy. [VERIFIED: `FdCalculatorService.java`, Phase 1 summary]

**How to avoid:** Lock the estimate formula and compounding interpretation before coding. Prefer one BigDecimal calculation component shared with Phase 3, or explicitly record the estimate as a display-only basis that Phase 3 is required to match. [RECOMMENDED; OPEN DECISION]

### Pitfall 4: Booking stores terms but not the policy version

**What goes wrong:** Product values are copied into existing nullable columns, but the policy version required by D-09 has nowhere durable to live. [VERIFIED: `FdAccount.java`, V1/V6 migrations]

**How to avoid:** Add `fa_policy_version` in an additive migration and map it; do not overload `fa_version` or `fa_row_version`. [RECOMMENDED]

### Pitfall 5: Funding writes a transaction and status separately

**What goes wrong:** A transaction exists for a still-pending account, or the account becomes active without a transaction after a partial failure. [RECOMMENDED failure mode based on D-12]

**How to avoid:** Lock, validate, insert the transaction, apply the state-machine result, and commit in one service transaction. [RECOMMENDED; Spring transaction source above]

### Pitfall 6: Account number allocation collides under retries/concurrency

**What goes wrong:** The existing generator uses synchronized `MAX` lookup and a default branch prefix; another process can calculate the same next number. [VERIFIED: `AccountNumberGenerator.java`, `.planning/codebase/CONCERNS.md`]

**How to avoid:** Keep the unique database constraint, make booking retry behavior explicit, and record this as a Phase 5 duplicate/concurrency gate. For Phase 2, do not claim distributed allocator guarantees that the current implementation does not provide. [RECOMMENDED; D-17 discretion boundary]

### Pitfall 7: Error shape diverges between placeholders and implemented routes

**What goes wrong:** Placeholder routes return `ApiErrorResponse`, while the global exception handler returns timestamp/status/error maps; Swagger then describes neither consistently. [VERIFIED: `ApiErrorResponse.java`, `GlobalExceptionHandler.java`, `fd-service-openapi.yaml`]

**How to avoid:** Add explicit v1 mappings for validation, unsupported terms, missing resource, and transition conflict and update OpenAPI responses together. [RECOMMENDED]

## Code Examples

### Server-owned booking query

```java
ProductTermsQuery query = new ProductTermsQuery(
        request.getProductId(),
        request.getPrincipalAmount(),
        request.getTenureMonths(),
        request.getCurrencyCode());
ProductTerms terms = productTermsPort.resolveActiveTerms(query, timePolicy.businessDate());
```

This uses the existing validated query/value contracts rather than the legacy days/rate DTO. [VERIFIED: `ProductTermsQuery.java`, `ProductTerms.java`, `CreateAccountContractRequest.java`]

### Funding transition shape

```java
FdAccount account = accountRepository.findByIdForUpdate(accountId)
        .orElseThrow(() -> new ResourceNotFoundException("FD account not found"));
if (account.getStatus() != FdStatus.PENDING) {
    throw new InvalidFdTransitionException(account.getStatus(), FdLifecycleCommand.FUND);
}
if (amount.compareTo(account.getPrincipalAmount()) != 0) {
    throw new InvalidInputException("Funding amount must equal booked principal");
}
FdTransition transition = stateMachine.transition(account.getStatus(), FdLifecycleCommand.FUND);
// Persist the DEPOSIT transaction and apply transition inside one @Transactional service method.
```

The lock and command types are existing repository/domain contracts; the transaction/entity mutation is the Phase 2 implementation task. [VERIFIED: `FdAccountRepository.java`, `FdLifecycleStateMachine.java`, `FdLifecycleCommand.java`, `FdTransaction.java`]

### Account-number compatibility callout

```java
String accountNumber = accountNumberGenerator.generateNextAccountNumber("000");
```

This is the existing API, but the branch choice and concurrency limitation must be documented rather than hidden. [VERIFIED: `AccountNumberGenerator.java`; OPEN DECISION for server branch policy]

## State of the Art

| Old/current approach | Phase 2 approach | Impact |
|---|---|---|
| `/fd/v1` methods return `501` | Implement only products, booking, and explicit funding behind application services | The API becomes demonstrable without falsely claiming later lifecycle support. [VERIFIED: current controller, D-03/D-04] |
| Create request accepts client rate, currency ID, and open date | Request accepts product/principal/tenure-months/currency-code; server resolves all derived values | Removes client authority over financial terms. [VERIFIED: baseline DTO, D-05/D-08] |
| `FdAccount` default was historically `ACTIVE` | V6 default and Java initializer are `PENDING`; funding alone applies `FUND` | Unfunded bookings cannot appear active. [VERIFIED: V6, `FdAccount.java`, Phase 1 summary] |
| `FdProduct` exposes transient rule fields | Product Terms adapter reads normalized/reference data | Prevents persistence from silently losing amount/rate/term rules. [VERIFIED: `FdProduct.java`, V1 schema] |
| Legacy calculator uses `double` and days/percent inputs | Phase 2 uses Phase 1 decimal-fraction/UTC/scale policy | Estimate and later lifecycle calculations need a single explicit convention. [VERIFIED: calculator, policy, application config] |

**Deprecated/outdated for Phase 2 authority:** legacy `/api/fd` booking, days-based `CreateFdRequest`, caller-provided rate/status/holder values, and direct activation without funding. Preserve routes where required, but do not use them as the Phase 2 implementation path. [VERIFIED: `02-CONTEXT.md`, `FdAccountService.java`, `CreateFdRequest.java`]

## Assumptions Log

> Confirm these before locking the plan. Repository facts are not assumptions; these are unresolved implementation choices or external-data expectations.

| # | Claim | Section | Risk if wrong |
|---|---|---|---|
| A1 | The chosen academic maturity-estimate formula can be shared with Phase 3, or Phase 2 may introduce a clearly versioned estimate component without final accrual semantics. [ASSUMED] | Booking sequence / Pitfalls | Booking response and later accrual/maturity could disagree. |
| A2 | A stable funding transaction code such as `DEPOSIT` can be provisioned or is already present in the target database. [ASSUMED] | Funding sequence / Schema changes | Funding cannot satisfy the required `ft_txn_type_id` foreign key. |
| A3 | The academic demo can use the existing account-number generator's server-owned default branch `000` unless branch derivation is chosen. [ASSUMED] | Booking sequence | Account numbers may be wrong for the intended demonstration or collide under concurrent booking. |
| A4 | Product/rule/currency rows can be deterministically related by code/value or will receive source identity through the planned migration/fixture. [ASSUMED] | Product mapping blocker | Offers could be ambiguous or persist the wrong local foreign-key reference. |

## Open Questions

1. **What exact normalized rule representation is populated in the target PostgreSQL database?**
   - What we know: V1 defines generic product/rule/condition/outcome tables and reduced `fd_*_ref` tables; Java maps only `FdProduct`. [VERIFIED: `V1__bankingtech_public_schema.sql`, `FdProduct.java`]
   - What's unclear: Which rule type/name encodes amount range, tenure range, annual rate, compounding, payout, and maturity basis, and whether the local reference rows are synchronized. [VERIFIED: repository inspection]
   - Recommendation: make the adapter contract fixture-driven and fail closed; do not plan implementation around guessed rule names until fixtures or live schema rows are identified.

2. **What is the exact maturity-estimate formula for compounding frequencies?**
   - What we know: Phase 1 fixes rate unit, day count, dates, timezone, scale, and rounding; `ProductTerms` carries compounding/payout strings. [VERIFIED: Phase 1 summary, `ProductTerms.java`]
   - What's unclear: Simple versus periodic compounding, intermediate rounding, and whether a non-full-month tenure uses calendar months or Actual/365 days for the estimate. [VERIFIED: no formula in Phase 1 contracts]
   - Recommendation: lock this before implementation and add golden-value tests; do not reuse the legacy double calculator unchanged.

3. **How should local FD reference rows be provisioned for a clean/demo database?**
   - What we know: V1 has no visible seed inserts and `/internal/sync/*` remains a placeholder. [VERIFIED: V1 migration, `FdV1ContractController.java`]
   - What's unclear: Whether the real Supabase database already contains compatible rows, and whether a new migration may seed academic reference data.
   - Recommendation: add a non-secret PostgreSQL fixture/provisioning task to the plan and test both empty/missing-data failure and a valid offer fixture.

4. **What server-owned branch code should account-number generation use?**
   - What we know: booking request has no branch field; the existing generator defaults blank branch input to `000`. [VERIFIED: `AccountNumberGenerator.java`, D-05]
   - What's unclear: whether to derive a branch from `CustomerProfile.homeBranchId` or retain `000` for the lab.
   - Recommendation: choose and document one server policy before booking service implementation.

5. **Should Phase 2 require `Idempotency-Key` now or only use row locking?**
   - What we know: V6 has `fd_operation_idempotency`, and context leaves exact use discretionary while Phase 5 owns the full duplicate-action gate. [VERIFIED: V6 migration, `FdOperationIdempotencyRepository.java`, `02-CONTEXT.md` discretion section]
   - What's unclear: whether the OpenAPI revision may add the header for booking/funding in this phase.
   - Recommendation: accept and persist the header if contract revision is permitted; otherwise rely on the state/lock transaction for Phase 2 and explicitly defer replay semantics to Phase 5.

## Environment Availability

| Dependency | Required by | Available | Version | Fallback |
|---|---|---:|---|---|
| Java | Backend compile/tests | ✓ | 17.0.20 | — |
| Maven | Backend build/tests | ✓ | 3.9.12 | — |
| Docker daemon | PostgreSQL Testcontainers gate | ✗ | Docker CLI 29.8.0; server unavailable | Run unit/H2 tests locally; run PostgreSQL gate with Docker-enabled CI/developer environment. |
| PostgreSQL client (`psql`/`pg_isready`) | Optional local DB inspection | ✗ | — | Use Testcontainers when Docker is available or the configured Supabase connection through the application; do not print credentials. |
| Supabase/PostgreSQL target | Runtime booking/demo | ? | Not probed | Requires the existing `SUPABASE_DB_URL`, username, and password environment configuration. [VERIFIED: `application.yml`] |
| Node/npm | Angular FD changes | Not required | Node 20.20.2 / npm 10.8.2 present | No frontend work is planned. |

The existing `FdPostgresSchemaTest` is configured to skip when Docker is unavailable; in this environment that means PostgreSQL migration assertions were not independently executed during research. [VERIFIED: environment probe, `FdPostgresSchemaTest.java`, Phase 1 summary]

**Missing dependencies with no fallback:** Docker is a blocking prerequisite for authoritative local PostgreSQL/Testcontainers verification, though implementation can proceed against existing unit/H2 checks and be gated in Docker-enabled CI. [VERIFIED: environment probe and test configuration]

## Validation Architecture

### Test Framework

| Property | Value |
|---|---|
| Framework | JUnit 5 via Spring Boot Test; Mockito/Spring Security Test already available |
| Config file | `BACKEND/pom.xml`; H2 profile at `BACKEND/src/test/resources/application-test.yml` |
| Quick run command | `mvn -q -Dtest=ProductTermsAdapterTest,AccountBookingServiceTest,FundingServiceTest test` |
| Full suite command | `mvn -q test` from `BACKEND` |

Existing backend tests use JUnit/Spring Boot/MockMvc and the PostgreSQL gate uses Testcontainers PostgreSQL 16 Alpine. [VERIFIED: `BACKEND/pom.xml`, existing `src/test/java` files]

### Phase Requirements → Test Map

| Req ID | Behavior | Test type | Automated command | File exists? |
|---|---|---|---|---|
| PROD-01 | Authenticated customer receives active complete offers; inactive/missing terms are not offered. | Unit + MockMvc + PostgreSQL integration | `mvn -q -Dtest=ProductTermsAdapterTest,FdV1BookingFundingIntegrationTest test` | ❌ Wave 0 |
| BOOK-01 | Customer booking resolves holder, creates one pending account, and returns locked terms. | Service + REST integration | `mvn -q -Dtest=AccountBookingServiceTest,FdV1BookingFundingIntegrationTest test` | ❌ Wave 0 |
| BOOK-02 | Invalid product, principal, tenure, currency, required fields, and missing customer profile fail before account persistence. | Unit + REST + PostgreSQL rollback test | `mvn -q -Dtest=AccountBookingServiceTest,FdV1BookingFundingIntegrationTest test` | ❌ Wave 0 |
| BOOK-03 | Product/rate/source changes after booking do not change account snapshot or returned estimate/policy version. | PostgreSQL integration | `mvn -q -Dtest=FdV1BookingFundingIntegrationTest test` | ❌ Wave 0 |
| FUND-01 | New account is pending; exact internal funding writes one transaction and transitions to active; wrong amount/status fails. | Service + PostgreSQL integration | `mvn -q -Dtest=FundingServiceTest,FdV1BookingFundingIntegrationTest test` | ❌ Wave 0 |

### Sampling Rate

- **Per task commit:** `mvn -q -Dtest=<focused test class> test`
- **Per wave merge:** `mvn -q -Dtest=FdV1BookingFundingIntegrationTest,FdPostgresSchemaTest test` with Docker enabled where possible
- **Phase gate:** `mvn -q test` green, with any Docker skip explicitly reported and the PostgreSQL suite run in Docker-enabled CI before `/gsd-verify-work`. [VERIFIED: Phase 1 testing pattern]

### Wave 0 Gaps

- [ ] `BACKEND/src/test/java/com/bank/fd/application/ProductTermsAdapterTest.java` — normalized/reference mapping, active filtering, amount/tenure/currency/rate validation, ambiguous/missing data.
- [ ] `BACKEND/src/test/java/com/bank/fd/application/AccountBookingServiceTest.java` — actor resolution, pending snapshot, fixed clock, exact request authority, and invalid cases.
- [ ] `BACKEND/src/test/java/com/bank/fd/application/FundingServiceTest.java` — exact amount/currency, status transition, transaction type lookup, and rollback decisions.
- [ ] `BACKEND/src/test/java/com/bank/fd/api/FdV1BookingFundingIntegrationTest.java` — authenticated MockMvc booking/funding route flow and response/error shape.
- [ ] Extend `BACKEND/src/test/java/com/bank/fd/FdPostgresSchemaTest.java` — V7/reference fixture, `fd_user_ref` uniqueness, policy snapshot column, transaction FK, and persistence checks.
- [ ] Update `BACKEND/src/test/java/com/bank/fd/api/FdV1ContractBoundaryTest.java` — `/products`, new request fields, funding route, and preserved placeholder assertions.
- [ ] Docker-enabled run — required to execute rather than skip the authoritative PostgreSQL assertions. [VERIFIED: current environment/test configuration]

## Security Domain

### Applicable ASVS Categories

| ASVS Category | Applies | Standard control |
|---|---|---|
| V2 Authentication | Yes | Keep `/fd/v1` behind existing JWT authentication and reject unauthenticated requests. [VERIFIED: `SecurityConfig.java`, `FdV1ContractController.java`] |
| V3 Session Management | Yes, at boundary | Reuse existing stateless bearer request context; Phase 2 must not add tokens, sessions, or custom crypto. [VERIFIED: `SecurityConfig.java`, `JwtAuthenticationFilter.java`] |
| V4 Access Control | Yes | Customer role plus service-layer self-ownership; do not accept holder/customer identity from request. [VERIFIED: D-15/D-16, `CustomerService.java`] |
| V5 Input Validation | Yes | Jakarta validation at HTTP edge plus service validation for active terms, exact money, currency, and state. [VERIFIED: `CreateAccountContractRequest.java`, `ProductTermsQuery.java`, V6 checks] |
| V6 Cryptography | No new cryptography | Use existing JWT/BCrypt configuration; never implement signing, password hashing, or payment credentials in Phase 2. [VERIFIED: `SecurityConfig.java`, `JwtTokenProvider.java`, `AGENTS.md`] |

ASVS is used as a threat-category checklist; the repository-specific controls above are the implementation authority. [CITED: https://github.com/OWASP/ASVS]

### Known Threat Patterns for this stack

| Pattern | STRIDE | Standard mitigation |
|---|---|---|
| Caller changes account/holder/customer ID | Elevation of privilege / information disclosure | Resolve actor from JWT, use owner-scoped service/repository checks, and never accept `primary_holder_id`. [VERIFIED: D-16, `FdAccountController.java`, `FdAccountRepository.java`] |
| Caller supplies rate/status/maturity | Tampering | Ignore derived/client authority; resolve terms and dates server-side and snapshot them. [VERIFIED: D-08/D-09] |
| Replay or concurrent funding | Tampering / repudiation | Account row lock, exact amount check, transaction + transition atomicity, and the V6 operation identity boundary where enabled. [VERIFIED: `FdAccountRepository.java`, V6 migration] |
| SQL/native adapter injection | Tampering | Use parameterized Spring Data/JPA queries and typed values; do not concatenate product/currency input into SQL. [RECOMMENDED control] |
| Sensitive data in logs/API | Information disclosure | Return only explicit DTO fields; do not log credentials, tokens, raw customer data, or payment details. [VERIFIED: `AGENTS.md`, `JwtAuthenticationFilter.java`] |
| Precision/scale abuse | Tampering / financial loss | `BigDecimal`, explicit digits/scale, positive/range validation, and PostgreSQL numeric constraints. [VERIFIED: `CreateAccountContractRequest.java`, V1/V6 schema] |

## Sources

### Primary (HIGH confidence)

- `AGENTS.md` — project constraints, scope, security, persistence, and forbidden infrastructure.
- `.planning/PROJECT.md`, `.planning/REQUIREMENTS.md`, `.planning/ROADMAP.md`, `.planning/STATE.md` — milestone goals, Phase 2 requirements, and Phase 1 policy decisions.
- `.planning/phases/02-product-booking-and-funding/02-CONTEXT.md` — locked Phase 2 API, terms, funding, identity, frontend, and discretion decisions.
- `.planning/phases/01-existing-fd-baseline/01-01-SUMMARY.md` — lifecycle, money/date, actor, and product-terms contracts.
- `.planning/phases/01-existing-fd-baseline/01-02-SUMMARY.md` — V6 schema, locks/versioning, repository contracts, and operation identity.
- `.planning/phases/01-existing-fd-baseline/01-03-SUMMARY.md` — typed v1 boundary, authentication guard, OpenAPI baseline, and Docker skip behavior.
- `BACKEND/src/main/resources/db/migration/V1__bankingtech_public_schema.sql` and `V6__fd_lifecycle_baseline.sql` — actual table fields, foreign keys, defaults, checks, indexes, and idempotency table.
- `BACKEND/src/main/java/com/bank/fd/**`, `com/bank/product/**`, `com/bank/customer/**`, and `com/bank/security/**` — current controller, application contracts, entity, repository, product, customer, and security patterns.
- `BACKEND/src/main/resources/static/openapi/fd-service-openapi.yaml` — current route, operation ID, and schema contract.
- `.planning/codebase/ARCHITECTURE.md`, `CONCERNS.md`, `CONVENTIONS.md`, `STACK.md`, `STRUCTURE.md`, `TESTING.md` — repository maps and known risks.

### Secondary (MEDIUM/HIGH confidence)

- Spring Data JPA, “Locking” — explicit `@Lock` metadata on repository query methods: [CITED: https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html]
- Spring Framework, “Using `@Transactional`” — annotation-based transaction semantics on Spring beans: [CITED: https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html]
- PostgreSQL 18, “Constraints” — named checks, unique constraints, foreign keys, and limits of cross-table checks: [CITED: https://www.postgresql.org/docs/current/ddl-constraints.html]
- OWASP Application Security Verification Standard — security verification category reference: [CITED: https://github.com/OWASP/ASVS]

### Tertiary (LOW confidence)

- None used for the implementation recommendation. Unresolved choices are listed as open questions or `[ASSUMED]` rather than presented as repository facts.

## Metadata

**Confidence breakdown:**
- Standard stack: HIGH — versions and dependencies are present in `BACKEND/pom.xml`; no new library is required. [VERIFIED: `BACKEND/pom.xml`]
- Architecture: HIGH for current boundaries, MEDIUM for the reference adapter because source-to-local reference identity is not modeled. [VERIFIED: code paths and V1 schema]
- Pitfalls: HIGH for current codebase risks, MEDIUM for target data provisioning and estimate formula until fixtures/policy are confirmed. [VERIFIED: current code and open questions]

**Research date:** 2026-09-19  
**Valid until:** 2026-10-19 for the repository assessment; revalidate dependency/runtime and target-database claims before implementation.
