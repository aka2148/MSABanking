# Phase 5: Protection, Tests, and Regression Gate - Research

**Researched:** 2026-09-22
**Status:** Ready for planning

## Summary

Phase 5 hardens the FD workflow built in Phases 1–4. Codebase inspection shows: (1) the durable idempotency entity + repository from Phase 1 (`FdOperationIdempotency`, `FdOperationIdempotencyRepository`) exist but are **referenced by zero services** — no controller reads an `Idempotency-Key` header; (2) all ownership checks throw Spring's `AccessDeniedException`, which `GlobalExceptionHandler` maps to **403**, conflicting with locked decision D-05 (**404**); staff bypass does not exist — the holder match is absolute and mutating endpoints carry `hasRole('CUSTOMER')`, which would reject staff authorities at method security; (3) `GlobalExceptionHandler` (Map envelope via `buildResponse`) already maps invalid transitions → 409 and unsupported premature closure → 422, so D-11 is an extension job, not a rewrite; (4) roles are `ROLE_CUSTOMER` / `ROLE_BANK_OFFICER` / `ROLE_ADMIN` (JWT `roles` claim → `SimpleGrantedAuthority`), so staff = `BANK_OFFICER` or `ADMIN`; (5) test dependencies (`spring-boot-starter-test`, `spring-security-test`, `spring-boot-testcontainers`, Testcontainers `postgresql`, H2) are already in `BACKEND/pom.xml`, and established patterns exist (`FdBookingFundingIntegrationTest`, `FdPostgresSchemaTest`, `FdV1*ContractTest`, domain unit tests).

## Standard Stack

- Reuse, do not invent: `FdOperationIdempotency` / `FdOperationIdempotencyRepository` for duplicate safety (D-03); `FdActorResolver` for actor → `fd_user_ref` (D-04); `FdLifecycleStateMachine` + `InvalidFdTransitionException` for key-less duplicate rejection (already 409); `GlobalExceptionHandler#buildResponse` as the single error envelope (D-11 allows "ApiErrorResponse / GlobalExceptionHandler style" — the handler style already covers every FD error path, while `ApiErrorResponse` is only used by 501 placeholder bodies).
- Tests: JUnit 5 + AssertJ/Mockito (spring-boot-starter-test), MockMvc + spring-security-test (`@WithMockUser`, JWT filter), Testcontainers PostgreSQL 16 Alpine via `spring-boot-testcontainers` (Docker-gated skip pattern already established). No new dependencies.

## Architecture Patterns

- **Idempotency guard (new `FdIdempotencyGuard` service):** `claim(scope, actorKey, idempotencyKey, requestHash)` → first call persists PENDING claim; replay with same key+hash returns the completed claim so the service reloads the recorded aggregate and rebuilds the identical success DTO (D-01); same key + different hash → new `FdDuplicateOperationException` → 409 referencing the existing record; key-less repeats are detected by natural duplicate signals (account already ACTIVE with DEPOSIT txn, accrual row for date exists, already MATURED/CLOSED) and rejected via `InvalidFdTransitionException` (already 409) with the message extended to reference the existing record (D-02). Scopes: `BOOKING`, `FUNDING`, `ACCRUAL`, `MATURITY`, `CLOSURE` (premature request/approve use `PREMATURE_REQUEST` / `PREMATURE_APPROVE`).
- **Ownership (new `FdAccessPolicy` helper or extension of `FdActorResolver`):** `requireOwned(username, account, authorities)` — holder-id match passes; else `ROLE_BANK_OFFICER`/`ROLE_ADMIN` authority passes (D-06, reuse `SecurityConfig` role names, no redesign); else throw `ResourceNotFoundException("FD account not found")` so the existing handler returns 404 without leaking existence (D-05). Replaces all 9 `throw new AccessDeniedException("Authenticated customer does not own this FD account")` sites (AccrualService ×2, ClosureService, HistoryService, FundingService, PrematureClosureService, MaturityService, FdV1ContractController ×2 at getAccount/getPrematureClosure).
- **Controller threading:** add `@RequestHeader(value="Idempotency-Key", required=false) String idempotencyKey` to the five mutating endpoints (createAccount, fundAccount, runAccrual, matureAccount, closeAccount, requestPrematureClosure, approvePrematureClosure); widen mutating `@PreAuthorize("hasRole('CUSTOMER')")` to `hasAnyRole('CUSTOMER','BANK_OFFICER','ADMIN')` so staff mutations reach the service layer (D-06); read endpoints already `isAuthenticated()` — unchanged.
- **Error contract:** add `FdDuplicateOperationException` → 409 handler; add `Idempotency-Key` to CORS `allowedHeaders` in `SecurityConfig` (currently only Authorization/Content-Type/Accept/Origin/X-Requested-With); keep 400/401/404/409/422 mapping per D-11; responses keep truthful internal-record wording (D-12).
- **Dont hand roll:** no new idempotency table, no parallel ownership mechanism, no envelope redesign, no role-model change, no Angular work, no payment/ledger infrastructure.

## Common Pitfalls

- `hasRole('CUSTOMER')` matches authority `ROLE_CUSTOMER` (Spring adds the prefix) — widening must use `hasAnyRole` with bare names, not `hasAuthority`, to stay consistent with the existing style.
- `GlobalExceptionHandler.handleAccessDeniedException` maps Spring `AccessDeniedException` → 403 globally; FD ownership must therefore NOT throw `AccessDeniedException` anymore — use `ResourceNotFoundException` (404) or a subclass, never a second 403→404 translation layer.
- Testcontainers tests must skip (not fail) when Docker is unavailable — copy the established assume/conditional pattern from `FdPostgresSchemaTest`; otherwise CI-without-Docker turns red.
- `FundingService` uses `findByIdForUpdate` (pessimistic lock) — the idempotency claim lookup must happen inside the same `@Transactional` boundary to stay race-safe for double-submit.
- Booking has no natural dedup signal, so key-less double booking legitimately creates two pending FDs — D-02's key-less 409 applies to repeats with a detectable existing effect (fund/accrue/mature/close), not to booking; the plan must state this explicitly rather than pretend booking is key-less safe.

## Validation Architecture

- **Unit layer:** domain math/lifecycle/validation (`FdInterestCalculator`, `FdLifecycleStateMachine`, `PrematurePenalty`, compounding validation) plus new guard/policy unit tests (`FdIdempotencyGuardTest` with mocked repository, access-policy ownership matrix customer/staff/stranger, handler status-code mapping). Runner: `mvn -pl . test -Dtest='...'` (surefire, in-memory/H2 where persistence is needed — no Docker).
- **API layer:** MockMvc contract tests with mocked auth (`@WithMockUser` / JWT filter bypass per `SecurityFlowTest` pattern) covering REST booking→funding→lifecycle flows, 404-on-cross-customer ownership probes, and per-status error codes (400/401/404/409/422).
- **Persistence layer:** Testcontainers PostgreSQL 16 Alpine (Boot-managed, generated credentials per Phase 1 gate) covering Flyway migration validity, history linkage, and duplicate-action protection end-to-end for all five operations; skip-only-when-Docker-unavailable.
- **Regression gate:** full `mvn test` green (auth, customer, product, calculator, reporting, migration, non-FD) — TEST-03 exit criterion, not just FD-scoped tests.
