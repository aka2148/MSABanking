# Codebase Concerns

**Analysis Date:** 2026-09-18

## Tech Debt

**Split authentication implementations:**
- Issue: The application exposes both the legacy local-JPA flow under `/api/auth` and a separate user-service/session flow under `/auth`. They return different token and response shapes, use different session semantics, and are both wired into the same security configuration.
- Files: `BACKEND/src/main/java/com/bank/security/controller/AuthController.java`, `BACKEND/src/main/java/com/bank/security/controller/LoginController.java`, `BACKEND/src/main/java/com/bank/security/service/SessionService.java`, `FRONTEND/src/app/core/services/auth.service.ts`
- Impact: Clients can authenticate against different identity paths with inconsistent logout, refresh, MFA, and role behavior. The frontend is coupled to the legacy path.
- Fix approach: Choose one authentication contract, publish it as the source of truth, remove or isolate the other controller, and add contract tests for login, refresh, logout, and authorization.

**Legacy FD bridge and contract skeleton:**
- Issue: The `/api/fd` service methods throw migration exceptions, while the large `/fd/v1` controller returns `501 NOT_IMPLEMENTED`; internal synchronization methods return `204` without storing anything.
- Files: `BACKEND/src/main/java/com/bank/fd/service/FdAccountService.java`, `BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java`, `BACKEND/src/main/java/com/bank/fd/controller/FdAccountController.java`
- Impact: FD account booking, lookup, accrual, maturity, statements, payouts, and lifecycle operations are unavailable despite their routes being present. Consumers can mistake no-op synchronization responses for successful replication.
- Fix approach: Implement the selected `/fd/v1` contract behind role and ownership checks, then remove the legacy controller or make it return an explicit migration response without invoking dead service methods.

**Frontend/backend registration contract drift:**
- Issue: The signup form collects identity, KYC, branch, address, and currency fields, but `RegisterRequest` only accepts the legacy username/password/email/name/phone/address/category shape. The extra fields are ignored and the selected file is never posted.
- Files: `FRONTEND/src/app/features/auth/signup/signup.ts`, `FRONTEND/src/app/core/models/auth.models.ts`, `BACKEND/src/main/java/com/bank/security/dto/RegisterRequest.java`, `BACKEND/src/main/java/com/bank/security/controller/AuthController.java`
- Impact: Users receive a successful registration without DOB, document proof, branch, legal entity, postal address, currency, or uploaded KYC document being persisted. Sensitive document numbers are collected without a complete verification workflow.
- Fix approach: Define one versioned registration DTO, reject unknown or unsupported fields during development, persist only validated KYC data server-side, and implement a secured multipart upload with size/type validation.

**Environment and deployment configuration:**
- Issue: The frontend hardcodes the API URL to `http://localhost:8080/api/auth`; the backend CORS allowlist contains only local origins; and the frontend package has no environment/build configuration.
- Files: `FRONTEND/src/app/core/services/auth.service.ts`, `FRONTEND/package.json`, `BACKEND/src/main/java/com/bank/security/config/SecurityConfig.java`
- Impact: A deployed HTTPS frontend cannot reliably call the backend, and a production origin is either blocked or would require source changes. The hardcoded HTTP URL also creates a clear transport-security failure outside local development.
- Fix approach: Inject environment-specific HTTPS API URLs, configure CORS from an allowlist property, and add production configuration checks that fail closed when required values are absent.

**Tracked build output:**
- Issue: Backend `target/` artifacts are tracked, and running `mvn test` modifies committed Surefire reports. `FRONTEND/.gitignore` ignores frontend output but there is no repository-level ignore policy for backend build output.
- Files: `BACKEND/target/surefire-reports/TEST-com.bank.SecurityFlowTest.xml`, `BACKEND/target/surefire-reports/com.bank.SecurityFlowTest.txt`, `FRONTEND/.gitignore`
- Impact: Build results create noisy diffs, stale bytecode can obscure source changes, and CI/reproducibility depends on generated files in version control.
- Fix approach: Remove generated artifacts from version control, add `BACKEND/target/` and other build output to the repository ignore rules, and build from clean workspaces in CI.

## Known Bugs

**Login cannot complete through the frontend contract:**
- Symptoms: The frontend sends `identifier`, while `/api/auth/login` binds `username`. A successful backend response contains `token` but no `success` flag, while the frontend only navigates when `res.success` is truthy.
- Files: `FRONTEND/src/app/core/services/auth.service.ts`, `FRONTEND/src/app/features/auth/login/login.ts`, `FRONTEND/src/app/core/models/auth.models.ts`, `BACKEND/src/main/java/com/bank/security/dto/LoginRequest.java`, `BACKEND/src/main/java/com/bank/security/dto/JwtResponse.java`
- Trigger: Submit the frontend login form against the `/api/auth` backend.
- Workaround: Align the request field and response schema, or point the frontend at the separate `/auth` contract and implement its response handling.

**Dashboard is not protected:**
- Symptoms: `/dashboard` is directly routable and renders a placeholder without checking authentication. Login navigation is the only apparent entry path from the UI.
- Files: `FRONTEND/src/app/app.routes.ts`, `FRONTEND/src/app/features/dashboard/dashboard-stub.ts`
- Trigger: Navigate directly to `/dashboard` in a new browser session.
- Workaround: None; add an authentication guard and make dashboard data calls require a valid server-side authorization context.

**Legacy scheduled jobs fail by design:**
- Symptoms: The midnight and 1 AM jobs call methods that throw `BankingException`, then log an error on every run.
- Files: `BACKEND/src/main/java/com/bank/fd/scheduler/FdBatchScheduler.java`, `BACKEND/src/main/java/com/bank/fd/service/FdAccountService.java`
- Trigger: Scheduler execution or the permitted admin batch endpoints.
- Workaround: Disable the jobs until the FD v1 batch operations exist, or route them to implemented idempotent services.

**Portfolio reports are unavailable and operational reports can fail on nullable data:**
- Symptoms: Customer portfolio reporting throws `UnsupportedOperationException`. Operational reporting loads every active account and dereferences potentially null rate and maturity values.
- Files: `BACKEND/src/main/java/com/bank/reports/service/ReportService.java`, `BACKEND/src/main/java/com/bank/reports/controller/ReportController.java`, `BACKEND/src/main/java/com/bank/fd/entity/FdAccount.java`
- Trigger: Call `/api/reports/portfolio` or use an active account with missing `fa_interest_rate` or `fa_maturity_date`.
- Workaround: None; implement the reference-ID mapping and null-safe aggregate queries before exposing reporting to users.

## Security Considerations

**Static JWT signing key in tracked configuration:**
- Risk: `app.jwt.secret` is a literal value in the production configuration file, so anyone with repository access can mint valid legacy JWTs. The test profile also contains a static signing key.
- Files: `BACKEND/src/main/resources/application.yml`, `BACKEND/src/test/resources/application-test.yml`, `BACKEND/src/main/java/com/bank/security/jwt/JwtTokenProvider.java`
- Current mitigation: JWT parsing validates the configured HMAC signature and expiration.
- Recommendations: Remove keys from tracked files, require an injected secret or key store at startup, rotate any exposed key, use separate environment/test keys, and prefer asymmetric signing with key rotation for distributed services.

**Client-controlled role during public registration:**
- Risk: `/api/auth/register` accepts `RegisterRequest.role`, chooses that value when present, and creates the corresponding role assignment. Because `/api/auth/**` is public, a caller can attempt to register with an elevated role.
- Files: `BACKEND/src/main/java/com/bank/security/controller/AuthController.java`, `BACKEND/src/main/java/com/bank/security/dto/RegisterRequest.java`, `BACKEND/src/main/java/com/bank/security/config/SecurityConfig.java`
- Current mitigation: The frontend sends a customer-oriented payload and the DTO defaults a missing role to customer.
- Recommendations: Ignore role input on public signup, assign customer server-side, and reserve staff/admin provisioning for an authenticated administrative workflow with audit logging.

**Overbroad public authentication routes:**
- Risk: `permitAll()` covers `/auth/**`, not only login. Account status, MFA challenge, OTP send, and other flows in `LoginController` accept public requests; `account-status` has no required-session check and accepts a caller-supplied user ID.
- Files: `BACKEND/src/main/java/com/bank/security/config/SecurityConfig.java`, `BACKEND/src/main/java/com/bank/security/controller/LoginController.java`
- Current mitigation: Some operations call `requiredSession`, but route-level authorization is not consistently applied.
- Recommendations: Permit only login, registration, and explicitly designed recovery endpoints; require a session for account status and password operations; bind MFA/OTP destinations to server-resolved verified contacts; rate-limit all public auth operations.

**Production diagnostics and development endpoints are public:**
- Risk: Actuator routes, Swagger/OpenAPI routes, and the H2 console are permitted without authentication; health details are configured as `always`. SQL logging is enabled in the main profile.
- Files: `BACKEND/src/main/java/com/bank/security/config/SecurityConfig.java`, `BACKEND/src/main/resources/application.yml`
- Current mitigation: Actuator exposes a limited endpoint list, but the security allowlist still permits the entire actuator path.
- Recommendations: Restrict actuator and documentation exposure by network or role, disable H2 console in production, set health details to never or restricted, and disable SQL/bind-parameter logging outside controlled diagnostics.

**In-memory refresh sessions and OTP challenges:**
- Risk: `SessionService` keeps access-session state and refresh tokens only in process memory even though a `refresh_tokens` table exists. `OtpService` keeps OTPs in an unbounded map, has no cleanup or attempt limit, and consumes a challenge with a non-atomic read/update sequence.
- Files: `BACKEND/src/main/java/com/bank/security/service/SessionService.java`, `BACKEND/src/main/java/com/bank/security/service/OtpService.java`, `BACKEND/src/main/resources/db/migration/V3__create_refresh_tokens.sql`
- Current mitigation: UUID-based identifiers, expiration checks, and refresh rotation are present in the in-memory implementation.
- Recommendations: Persist hashed refresh tokens with revocation metadata, add cleanup and concurrency-safe one-time consumption, cap OTP attempts and issuance, and connect OTP creation to a real delivery provider without logging sensitive material.

**Self-service mutation of customer category:**
- Risk: The profile update DTO exposes `category`, and `CustomerService.updateProfile` writes it for any authenticated customer. Category values influence FD calculation boosts and should not be self-asserted.
- Files: `BACKEND/src/main/java/com/bank/customer/dto/CustomerProfileDto.java`, `BACKEND/src/main/java/com/bank/customer/service/CustomerService.java`, `BACKEND/src/main/java/com/bank/customer/controller/CustomerController.java`, `BACKEND/src/main/java/com/bank/calculator/service/FdCalculatorService.java`
- Current mitigation: Role checks protect the endpoint, but category changes are not separated from ordinary profile edits.
- Recommendations: Use separate write DTOs, remove category from customer self-service updates, and derive changes from verified eligibility or a staff workflow.

## Performance Bottlenecks

**Landing page WebGL cost:**
- Problem: The landing route loads Three.js and renders continuously with a 64x64 sphere, texture, animated arcs, and device pixel ratio up to 2. The lazy landing chunk is approximately 543 kB raw in the production build.
- Files: `FRONTEND/src/app/features/landing/components/globe-hero/globe-hero.ts`, `FRONTEND/package.json`, `FRONTEND/angular.json`
- Cause: Animation does not pause when the tab/component is hidden, and the reduced-motion preference is not applied to the WebGL component.
- Improvement path: Use an intersection/visibility pause, cap mobile pixel ratio, honor reduced motion, lazy-load or replace the effect on constrained devices, and keep bundle budgets aligned with the actual route cost.

**Unbounded report aggregation:**
- Problem: Operational reporting retrieves every active FD entity and aggregates in application memory.
- Files: `BACKEND/src/main/java/com/bank/reports/service/ReportService.java`, `BACKEND/src/main/java/com/bank/fd/repository/FdAccountRepository.java`
- Cause: No database-side count/sum/average query, date filtering, pagination, or projection is used.
- Improvement path: Add indexed aggregate projections and bounded maturing-account queries; return report DTOs rather than full persistence entities.

**Global serialized user-service client:**
- Problem: All calls through `UserServiceClientAdapter.execute` are synchronized, so one slow or failing dependency blocks unrelated authentication calls while retries run.
- Files: `BACKEND/src/main/java/com/bank/security/userservice/UserServiceClientAdapter.java`
- Cause: The circuit-breaker counters and all network execution share one monitor, and retries are not differentiated by transient versus client errors.
- Improvement path: Use a tested resilience library or separate concurrency-safe breaker state from request execution, retry only safe transient failures, and expose timeout/breaker metrics.

## Fragile Areas

**FD schema/entity compatibility layer:**
- Files: `BACKEND/src/main/java/com/bank/fd/entity/FdAccount.java`, `BACKEND/src/main/java/com/bank/fd/service/FdAccountService.java`, `BACKEND/src/main/resources/db/migration/V1__bankingtech_public_schema.sql`
- Why fragile: The entity uses compatibility aliases such as `getCustomerId()` and converts months to approximate 30-day terms, while the schema uses FD reference IDs and exact lifecycle tables. Legacy controllers still assume the old customer model.
- Safe modification: Treat the normalized schema as authoritative, use explicit DTOs, remove aliases after callers migrate, and test migrations against PostgreSQL rather than only Hibernate-created H2 tables.
- Test coverage: `BACKEND/src/test/java/com/bank/SecurityFlowTest.java` does not exercise FD v1; only account-number generation covers a small part of this area.

**Custom form control and responsive shell:**
- Files: `FRONTEND/src/app/shared/components/glass-select/glass-select.ts`, `FRONTEND/src/app/shared/components/glass-select/glass-select.html`, `FRONTEND/src/app/shared/components/glass-shell/glass-shell.ts`
- Why fragile: `GlassSelect` has no ARIA combobox/listbox semantics, keyboard navigation, Escape handling, or focus management. `GlassShell` defines `onResize()` but does not register a resize listener, and its reduced-motion flag is unused.
- Safe modification: Add focused accessibility and resize tests before changing these shared components; use native select behavior where possible or implement the full keyboard/focus contract.
- Test coverage: The existing specs only assert component creation and do not test interaction, focus, resize, or reduced-motion behavior.

**Authentication and persistence boundaries:**
- Files: `BACKEND/src/main/java/com/bank/security/controller/AuthController.java`, `BACKEND/src/main/java/com/bank/security/service/LoginAttemptService.java`, `BACKEND/src/main/java/com/bank/security/userservice/UserServiceClientAdapter.java`
- Why fragile: Controllers mix persistence, role provisioning, profile construction, and authentication. The local flow and remote user-service flow implement different lockout/session behavior, making security changes easy to apply to only one path.
- Safe modification: Move registration and authentication orchestration into transactional services, define one port/adapter boundary, and add negative authorization tests for every public route.
- Test coverage: The integration test covers one local registration/login path but not the remote flow, MFA, refresh/revocation, rate limits, role escalation, or duplicate registration races.

## Scaling Limits

**Single-instance session and OTP state:**
- Current capacity: State is held in process-local `ConcurrentHashMap` instances.
- Limit: Multiple backend instances do not share sessions or challenges, and process restart invalidates active sessions while expired entries remain until accessed.
- Scaling path: Move state to a shared store with TTLs, persist revocation/audit records, and make issuance/consumption operations atomic.

**Account-number allocation:**
- Current capacity: `generateNextAccountNumber` serializes only within one JVM and calculates the next value by querying the current maximum.
- Limit: Concurrent instances or processes can compute the same number; the database uniqueness constraint then causes a collision failure rather than a controlled allocation.
- Scaling path: Allocate sequences per branch in PostgreSQL or use a locked counter table with retry on unique conflicts.

## Dependencies at Risk

**Missing automated dependency/security audit:**
- Risk: The project has no npm test/audit script, dependency update policy, or Maven dependency scanning configuration visible in the manifests.
- Impact: Vulnerable transitive packages and incompatible upgrades can reach builds without an automated repository check.
- Migration plan: Add reproducible CI checks for `npm audit`/approved alternatives and Maven vulnerability scanning, pin and review upgrades, and record exceptions with expiry dates.
- Files: `FRONTEND/package.json`, `FRONTEND/package-lock.json`, `BACKEND/pom.xml`

## Missing Critical Features

**KYC/document workflow:**
- Problem: The UI collects identity proof metadata and a file, but there is no upload, malware/type/size validation, verification status workflow, or secure document storage path.
- Blocks: Regulatory onboarding and reliable customer identity verification.
- Files: `FRONTEND/src/app/features/auth/signup/signup.ts`, `FRONTEND/src/app/core/models/auth.models.ts`, `BACKEND/src/main/resources/db/migration/V1__bankingtech_public_schema.sql`

**Session-aware frontend:**
- Problem: No token storage policy, HTTP authorization interceptor, refresh flow, logout flow, route guard, or role-aware navigation exists in the frontend.
- Blocks: Secure dashboard access and all authenticated banking modules.
- Files: `FRONTEND/src/app/core/services/auth.service.ts`, `FRONTEND/src/app/app.routes.ts`, `FRONTEND/src/app/core/models/auth.models.ts`

## Test Coverage Gaps

**Frontend test execution:**
- What's not tested: The package has no `test` script, `npm test -- --run` fails with “Missing script: test”, and existing tests include a stale `Auth` import and an assertion for a title absent from the root template.
- Files: `FRONTEND/package.json`, `FRONTEND/src/app/core/services/auth.spec.ts`, `FRONTEND/src/app/app.spec.ts`, `FRONTEND/src/app/app.html`
- Risk: Broken authentication, form validation, routing, and shared-control behavior can reach users without a runnable frontend test gate.
- Priority: High

**Authentication contract and authorization:**
- What's not tested: Frontend/backend field compatibility, response handling, token persistence, route guards, role escalation, public `/auth` route exposure, logout/revocation, MFA, OTP rate limits, and refresh rotation.
- Files: `FRONTEND/src/app/features/auth/login/login.ts`, `FRONTEND/src/app/features/auth/signup/signup.ts`, `BACKEND/src/main/java/com/bank/security/config/SecurityConfig.java`, `BACKEND/src/test/java/com/bank/SecurityFlowTest.java`
- Risk: Authentication can appear to succeed in UI while not establishing a usable session, and privilege or account-enumeration flaws can go undetected.
- Priority: High

**Financial and lifecycle behavior:**
- What's not tested: FD v1 endpoints, migration compatibility, interest accrual persistence, maturity processing, premature closure, payout/statement workflows, null financial fields, and multi-instance account-number allocation.
- Files: `BACKEND/src/main/java/com/bank/fd/api/controller/FdV1ContractController.java`, `BACKEND/src/main/java/com/bank/fd/service/FdAccountService.java`, `BACKEND/src/main/java/com/bank/calculator/service/FdCalculatorService.java`, `BACKEND/src/main/java/com/bank/fd/service/AccountNumberGenerator.java`
- Risk: Core banking operations remain unverified, and use of `double` for rate calculations can introduce monetary rounding differences.
- Priority: High

---

*Concerns audit: 2026-09-18*
