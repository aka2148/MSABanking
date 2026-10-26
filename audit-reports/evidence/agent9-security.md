# Agent 9 - API, Authentication & Security Audit

Scope: active services `identity-customer-service` (IDC), `product-service` (PRD), `fd-account-service` (FD), `api-gateway/nginx.conf`, `docker-compose.yml`, root `.env`/`.env.example`/`.gitignore`, `scripts/*.ps1`.
Repo copy: `/private/tmp/claude-501/-Users-yashralhan-Projects-PokerThen-/e0312acd-4510-4ec5-98db-ebead3794720/scratchpad/repo` (paths below are relative to it; `S/` = `services/`).
Method: static read-only review. Nothing was executed. Secret values are never printed. Everything not marked UNVERIFIED is a code-level fact.
Expected RBAC baseline from the lab manual: L1 bcrypt and default CUSTOMER role, L2 bcrypt login (401 on bad credentials), L3/L4 JWT with a role claim, `@PreAuthorize` plus `@EnableMethodSecurity`, officer/admin-only customer lists, customer sees own profile only, L21 API keys in environment variables.

## 0. Executive summary

The baseline is better than typical lab code. Positives, all confirmed:
- bcrypt hashing.
- Self-registration is hard-wired to ROLE_CUSTOMER.
- `@EnableMethodSecurity` is on in all three services, so `@PreAuthorize` is live.
- All SQL is parameterized.
- Customer-owned resources (savings, FD, loan, forex) are scoped by the JWT `userId`, so the IDOR checks pass.
- Password reset is well built: 256-bit token, SHA-256 at rest, 30-minute expiry, single use, sessions revoked.
- TOTP secrets are AES-GCM encrypted and replay-protected.
- IDC does per-request revocation through `tokenVersion`.
- The `.env` is gitignored and untracked.

Material problems:
- SE-001 (P1): FD and PRD never check revocation. Logout, suspension, password change and role removal do not take effect for up to 24 hours.
- SE-002 (P1): any customer can self-verify KYC with no officer review, and KYC gates FD, loan and forex.
- SE-003 (P1): staff teller deposit and withdraw is unlimited, has no maker-checker, and records no actor.
- SE-004 (P1/P2): demo opening credit mints unlimited money. It is enabled in docker-compose.
- SE-005 (P2): the MFA challenge token is accepted as a bearer token by PRD.
- SE-006 (P2): the stack is not network-segregated. Direct service ports, the DB, Kafka and an unauthenticated kafka-ui are published, and anyone who can reach Kafka can forge `user.snapshot` events to mark a user VERIFIED.
- Also notable: no rate limiting anywhere, customers can list DRAFT/INACTIVE products, and a historical hard-coded JWT secret sits in git history.

## 1. Facts about config, secrets and the repo

| Item | Finding | Evidence |
|---|---|---|
| `.env` tracked in git? | No. `git ls-files .env` is empty. `.env` is ignored by `.gitignore:2`, `.dockerignore` excludes `.env` and `.env.*` except the example, and `git log --all -- .env` is empty. `.env.example` is tracked and holds placeholders only. | `.gitignore`, `.dockerignore` |
| `.env` in BACKEND.zip? | Not present (`unzip -l` shows no `.env*`). The `.env` file does exist in the extracted working copy (15 lines, 14 keys, values not read). | |
| JWT secret strength | `.env` JWT_SECRET is base64 decoding to 64 bytes. That is adequate for HS512, and not a weak or default value. | `.env` |
| MFA key | A separate value from JWT_SECRET in `.env`. However `application.yml` and compose fall back to JWT_SECRET if unset (SE-016). | `S/identity-customer-service/src/main/resources/application.yml:25`, `docker-compose.yml:105` |
| JWT default in yml | None. All three yml files use `${JWT_SECRET}` with no fallback, and compose uses `:?` for IDC. | yml `app.jwt.secret`; `docker-compose.yml:104` |
| DB, root and demo-admin passwords | `.env` has random-looking values (48, 48 and 64 characters). Not weak or default. `initialize-demo-env.ps1` generates them randomly. | `scripts/initialize-demo-env.ps1:35-42` |
| Historic hard-coded secret | Git history has commits where `secret: <128-char literal>` was written in `BACKEND/src/main/resources/application.yml` and in the test ymls. It is not the current `.env` value. Commits include 8301c59, ef7948b and others. The legacy BACKEND is not an active service. | `git log -G'secret: ...'` (SE-014) |
| Compiled artifacts in git | 60 files under `BACKEND/target` and `BACKEND/bin` are tracked. | `git ls-files BACKEND/target` |
| Swagger/OpenAPI | No springdoc dependency in the active services. Nothing is exposed. | the three `pom.xml` files |
| Actuator | Exposes only `health,info`. `show-details: never`. `info.env.enabled: false`. Not routed by the gateway (404). Services publish ports directly, so `/actuator/health` is reachable. | yml `management:` |
| Dependencies | Spring Boot 3.5.5, jjwt 0.11.5, mysql-connector-j and Flyway managed by Boot, spring-kafka. No known-CVE hit evident from the poms. Not checked with a scanner (UNVERIFIED). jjwt 0.11.5 is old and 0.12.x is current, but it has no known CVE. | `pom.xml` files |

## 2. JWT, authentication and session design

- **Algorithm**: HS512 signing (`AuthController.java:185`). Parsers use `parserBuilder().setSigningKey(key)`, so `alg=none` and algorithm confusion are rejected by jjwt. Confirmed.
- **Claims**: `sub`=username, `userId`, `roles` (comma-separated, a single string), `tokenVersion`, `iat`, `exp`. Lab text says `role`; here it is `roles`. This is a harmless deviation.
- **Expiry**: default 24 h (`JWT_EXPIRATION_MS:86400000`, yml:31). There is no separate refresh token. `POST /refresh` issues a fresh 24 h token for any valid bearer, so a stolen token can be renewed forever until logout or a password change bumps `tokenVersion`. See SE-008.
- **IDC filter** (`JwtAuthenticationFilter.java:33-38`): the DB check does the following.
  - It verifies `usr_status=ACTIVE` and that `tokenVersion` matches.
  - It re-reads roles from the DB, not from the token.
  - An invalid token is swallowed silently. The request continues unauthenticated and gets 403, not 401, because no AuthenticationEntryPoint is configured.
- **PRD filter** (`product-service/.../JwtFilter.java:31-36`): trusts the signed `roles` claim. It checks no revocation or status. It returns 401 on a bad token.
- **FD filter** (`fd-account-service/.../JwtFilter.java:22`): trusts the `roles` and `userId` claims. No revocation check. A bad token is ignored, which gives anonymous and then 403.
- **Revocation**: `logout`, `change-password`, password reset, MFA enable/disable, role change and status change all bump `ucr_token_version`. Only IDC honors it.
- **Login lockout**: 5 failed attempts lock the account for 15 minutes, hard-coded (the yml keys `app.auth.max-failed-attempts` and `lock-minutes` are not read).
- **bcrypt**: `new BCryptPasswordEncoder()` with the default strength 10. Acceptable. Strength 12 is recommended.
- **Password policy**: only min 8 and max 100. No complexity or breach check.
- **Registration**: the INSERT hard-codes `ROLE_CUSTOMER` (`AuthController.java:59-62`). `RegisterRequest` has no role field. No self-register-as-ADMIN path exists. Confirmed.
- **CORS** (nginx): it reflects the Origin only for `localhost` and `127.0.0.1`. No credentials are allowed. OPTIONS returns 204 at the gateway. Services have no `@CrossOrigin`. Confirmed adequate for local use. A production origin needs to be added.
- **CSRF**: disabled with stateless bearer tokens. Fine. The token lives in `localStorage` (`FRONTEND/src/app/core/services/auth.service.ts:82`), which is XSS-exposed (SE-017).
- **Error handling**: only PRD has an `@RestControllerAdvice`; its handler returns `{message}` for `ResponseStatusException` and for validation errors, with no stack trace. IDC and FD use Boot defaults. Boot 3.5 defaults give no stack trace or message. No leakage was found, but there is no generic `Exception` handler anywhere.
- **Gateway header spoofing**: no service reads `X-User`, `X-Forwarded-*` or any header besides `Authorization` (grep). Confirmed none.
- **Logging**: no PII or secrets are logged. Only outbox/Kafka failure messages with event UUIDs, plus an exception object in the consumer log (`ReferenceSnapshotConsumer.java:37,72`). The KYC draft `toString` is redacted deliberately.

## 3. Endpoint authorization matrix

Legend: Ctr = authenticated, no role needed. OWN = scoped to the JWT `userId` or username. OFF = BANK_OFFICER or ADMIN. ADM = ADMIN. Verdict: OK, WARN, FAIL. "SC" is the SecurityConfiguration matcher.

### 3.1 identity-customer-service (SC: permitAll on `/api/v1/auth/register`, `/login`, `/login/**`, `/auth/password/forgot`, `/auth/password/reset`, health, info and `/error`; everything else is authenticated)

| Endpoint | Method | Expected (manual) | Actual enforcement | Ownership | Verdict |
|---|---|---|---|---|---|
| `/api/v1/auth/register` | POST | public | permitAll; `@Valid`; role fixed to CUSTOMER; returns JWT immediately, no email verification | n/a | WARN: SE-009, SE-011 |
| `/api/v1/auth/login` | POST | public | permitAll; bcrypt; lock after 5 | n/a | WARN: SE-009, SE-010 |
| `/api/v1/auth/login/mfa/verify` | POST | public (challenge token) | permitAll; TOTP with replay protection and shared lockout | n/a | OK |
| `/api/v1/auth/mfa/enroll` | POST | authed | `@PreAuthorize("isAuthenticated()")` | OWN | WARN: SE-012 |
| `/api/v1/auth/mfa/enable` | POST | authed | same; needs only a TOTP code | OWN | WARN: SE-012 |
| `/api/v1/auth/mfa/disable` | POST | authed | same; needs only a TOTP code (no password) | OWN | WARN: SE-012 |
| `/api/v1/auth/refresh` | POST | authed | isAuthenticated; renews 24 h | OWN | WARN: SE-008 |
| `/api/v1/auth/logout` | POST | authed | bumps tokenVersion (IDC only) | OWN | WARN: SE-001 |
| `/api/v1/auth/me` | GET | CUSTOMER (lab L3) | isAuthenticated; any role | OWN | OK |
| `/api/v1/auth/change-password` | POST | authed | isAuthenticated; current password required; history of 5 | OWN | OK |
| `/auth/password/forgot` | POST | public | permitAll; same response for found/not found; token 32 bytes SecureRandom, SHA-256 at rest, 30 min; DB-delete-then-insert | n/a | WARN: SE-013 |
| `/auth/password/reset` | POST | public | permitAll; one-shot; bumps tokenVersion; clears the lock | n/a | OK |
| `/auth/password/change` | POST | authed | anyRequest authenticated (no `@PreAuthorize`) | OWN | OK, duplicate of change-password |
| `/api/v1/customers/me/kyc` | GET | CUSTOMER | class `@PreAuthorize("hasRole('CUSTOMER')")` | OWN | OK |
| `.../kyc/draft` | PUT | CUSTOMER | same; auto-VERIFIES at 9 of 9 fields | OWN | FAIL: SE-002 |
| `.../kyc/document` | POST/DELETE | CUSTOMER | same; magic-byte check; 5 MB | OWN | WARN: SE-015 |
| `.../kyc/rekyc` | POST | CUSTOMER | same | OWN | OK |
| `/api/v1/customers` | GET, `/search` | OFF | class `@PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")` | all (staff) | OK |
| `/api/v1/customers/{id}` | GET | OFF | same | staff-wide | OK (names and risk only) |
| `/api/v1/customers/by-user/{userId}` | GET | OFF | same | staff-wide | OK |
| `/api/v1/customers` | POST | OFF | same; `kycStatus` and `riskCategory` are free strings | n/a | WARN: SE-007 |
| `/api/v1/customers/{id}` | PUT | OFF | same; sets KYC and risk; no enum validation | n/a | WARN: SE-007 |
| `/api/v1/customers/{id}/status` | PATCH | OFF | same; `Map<String,String>` body; no existence check (500 on unknown id) | n/a | WARN: SE-007 |
| `/api/v1/users`, `/users/{id}`, `/users/{id}/roles` | GET | OFF | method-level OFF | staff | OK |
| `/api/v1/users/{id}/status` | PATCH | ADM | `hasRole('ADMIN')`; last-admin guard; bumps tokenVersion | n/a | OK, but see SE-001 |
| `/api/v1/roles` | GET | OFF | OFF | n/a | OK |
| `/api/v1/roles` POST, `/roles/{id}` PUT | | ADM | ADM; allows any `ROLE_[A-Z0-9_]{2,45}`; PUT can rename an existing code | n/a | WARN: SE-018 |
| `/api/v1/users/{id}/roles` | POST/DELETE | ADM | ADM; whitelist of the 3 roles; last-admin guard | n/a | OK |
| `/api/v1/branches*`, `/employees*`, `/customer-classification-types*` | GET | OFF | OFF | n/a | OK |
| same | POST/PUT/PATCH | ADM | ADM | n/a | OK |
| `/actuator/health`, `/info` | GET | public | permitAll | n/a | OK |

### 3.2 product-service (SC: permitAll health and info, plus GET on `/api/product/**`, `/api/v1/currencies/**`, `/api/v1/product-categories/**`, `/api/v1/fd-products/**`; `/api/v1/product-configurations/**` requires ROLE_BANK_OFFICER or ROLE_ADMIN; the rest is authenticated)

| Endpoint | Method | Expected | Actual enforcement | Ownership | Verdict |
|---|---|---|---|---|---|
| `/api/product` | GET | public | permitAll; ACTIVE only | n/a | OK |
| `/api/product/all` | GET | OFF | `@PreAuthorize` OFF; the matcher is permitAll | n/a | OK (guarded by method security) |
| `/api/v1/products` | GET (`?status=`) | OFF for DRAFT/INACTIVE | any authenticated user; `status` is a free parameter | n/a | FAIL: SE-005b |
| `/api/v1/products/{id}`, `/by-code/{code}` | GET | authed | any authenticated user; `{id}` returns any status | n/a | WARN: SE-005b |
| `/api/v1/products` POST/PUT, `/status`, `/translations` | | OFF | OFF | n/a | OK |
| `/api/v1/products/{id}` | DELETE | ADM | ADM | n/a | OK |
| `/api/v1/currencies*`, `/product-categories*` | GET | public | permitAll | n/a | OK |
| same | POST/PUT | OFF | OFF | n/a | OK |
| same | DELETE | ADM | ADM | n/a | OK |
| `/api/v1/fd-products` | GET list | OFF | permitAll in SC, but class `@PreAuthorize` OFF means an anonymous caller gets 403 | n/a | OK (relies on method security) |
| `/api/v1/fd-products/eligible` | GET | public | permitAll; no `@PreAuthorize`; ACTIVE products and rates only | n/a | OK |
| `/api/v1/fd-products` POST/PUT, `/activate`, `/deactivate` | | OFF | class OFF | n/a | OK |
| `/api/v1/product-configurations/**` (all 8 operations incl. DELETE and activate) | | OFF | URL matcher | n/a | OK |
| `/api/v1/customer-products/{family}`, `/{family}/{id}` | GET | CUSTOMER | any authenticated, including an MFA challenge token (SE-005) | n/a | WARN |
| `/api/v1/compliance/fd/*` | | OFF | class OFF | n/a | OK |

### 3.3 fd-account-service (SC: permitAll health, info and `/error`; everything else authenticated; roles come from method annotations)

| Endpoint | Method | Expected | Actual enforcement | Ownership | Verdict |
|---|---|---|---|---|---|
| `/api/v1/savings-accounts` | GET | CUSTOMER | authenticated; no role restriction | OWN (`sa_holder_id=userId`) | OK |
| `/savings-accounts/{id}`, `/{id}/transactions` | GET | CUSTOMER | authenticated | OWN (404 if not owner) | OK |
| `/savings-accounts` | POST | CUSTOMER | authenticated; no cap on the number of accounts | OWN | WARN: SE-004 |
| `/savings-accounts/admin` | POST | OFF | method OFF | any user | OK |
| `/savings-accounts/{id}/demo-opening` | POST | none (demo) | authenticated; `@DecimalMin("0.01")` only; no max; flag is `true` in compose | OWN | FAIL: SE-004 |
| `/savings-accounts/currencies`, `/demo-options` | GET | authed | authenticated | n/a | OK |
| `/api/v1/fixed-deposits` | GET, GET `/{id}` | CUSTOMER | authenticated | OWN | OK |
| `/fixed-deposits` | POST | CUSTOMER | `requireVerified` (KYC and risk); `@Valid`, principal `@DecimalMin(0.01)`; offer bounds enforced against product terms | OWN account | OK |
| `/fixed-deposits/{id}/fund` | POST | CUSTOMER | as above; idempotency | OWN (404) | OK |
| `/fixed-deposits/{id}/close`, `/premature-closure` (POST/GET) | | CUSTOMER | authenticated | OWN (404) | OK |
| `/fixed-deposits/{id}/transactions`, `/accruals`, `/accruals/cumulative` | GET | CUSTOMER | authenticated | OWN (404) | OK |
| `/fixed-deposits/{id}/journals` | GET | ADM | `hasRole('ADMIN')` | n/a | OK |
| `/api/v1/admin/fixed-deposits/{id}/premature-closure/{cid}/approve` | POST | OFF | OFF; approver must differ from the FD owner | n/a | OK |
| `/api/v1/admin/fixed-deposits/accrual-batch`, `maturity-batch` | POST | OFF | OFF; `businessDate` param can backdate | n/a | WARN: SE-020 |
| `/api/v1/loans`, `/{id}`, `/{id}/history` | GET | CUSTOMER | authenticated | OWN (`la_customer_id=userId`) | OK |
| `/api/v1/loans` | POST | CUSTOMER | `requireVerified`; `@DecimalMin(0.01)`; product min/max enforced via PRD | OWN destination account | OK |
| `/api/v1/admin/loans*` GET and `approve`, `reject`, `disburse` | | OFF/ADM | ADM only (`hasRole('ADMIN')`); officers are denied | n/a | WARN: SE-019 |
| `/api/v1/forex-requests` GET, `/{id}` | | CUSTOMER | authenticated | OWN | OK |
| `/api/v1/forex-requests` | POST | CUSTOMER | `requireVerified`; `@Valid`; product bounds | OWN | OK |
| `/api/v1/admin/ledger` | GET | ADM | class ADM | n/a | OK |
| `/api/admin/clock*` | | ADM | class ADM; `days` is `@Positive @Max(3650)` | n/a | OK |
| `/api/reports/portfolio` | GET | CUSTOMER/OFF/ADM | `hasAnyRole` | OWN (by `actor.getName()`) | OK |
| `/api/reports/portfolio/{username}` | GET | OFF | OFF | any user | OK |
| `/api/reports/operational` | GET | OFF | OFF | n/a | OK |
| `/api/calculator/estimate` | POST | public | authenticated only; `termDays` and `principal` have no upper bound | n/a | WARN: SE-020 |
| `/fd/v1/staff/accounts`, `/savings-accounts`, `/{id}/transactions` | GET | OFF | class OFF; account numbers masked (`••••1234`) | staff | OK |
| `/fd/v1/staff/savings-accounts/{id}/deposit`, `/withdraw` | POST | OFF | class OFF; any account; `@DecimalMin(0.01)` with no max; the actor is not recorded on the transaction | any account | FAIL: SE-003 |

IDOR result: every customer-facing FD/savings/loan/forex endpoint filters by `userId` taken from the signed JWT, never from the request. No horizontal escalation was found by reading the code. Confidence: confirmed for the read paths and every write path above. Runtime exploit testing was not performed (UNVERIFIED).

## 4. Findings

### SE-001 - P1 - FD and PRD ignore token revocation (logout, suspension, password change, role removal)
- **Path**: `S/fd-account-service/.../JwtFilter.java:22`, `S/product-service/.../JwtFilter.java:31-36`. Contrast `S/identity-customer-service/.../JwtAuthenticationFilter.java:33-38`.
- **Scenario**: an admin suspends a customer (`PATCH /users/{id}/status`), or a customer logs out after a leaked token. The IDC token version is bumped, but the same bearer still works on FD and PRD for up to 24 h. This covers loans, FD funding and closing, and savings reads. Removing the ADMIN role also leaves the token's `roles` claim valid in FD for the full lifetime. Pure code fact; token reuse itself is UNVERIFIED at runtime.
- **Impact**: the "disabled user is locked out" requirement from the lab (revocation, suspension) is not met for money-moving services.
- **Confidence**: confirmed (code).
- **Fix**: shorten the access-token lifetime to 5-15 minutes with a rotating refresh token, and/or have FD and PRD call an IDC introspection endpoint or consume a `user.snapshot` status. FD already stores `fur_user_status` (`fd_user_ref`), so the filter can check ACTIVE there. Also add the `tokenVersion` to the FD snapshot.
- **Regression test**: log in, call `GET /api/v1/savings-accounts`, then suspend the user. The call must return 401/403 within the cache TTL.

### SE-002 - P1 - Customers self-verify KYC; KYC is the only gate for FD, loan and forex
- **Path**: `S/identity-customer-service/.../KycService.java:91` (`if (confirmed.size() == 9)` sets `cus_kyc_status='VERIFIED'`). Gate consumers: `CustomerProductAccess.requireVerified` in FD.
- **Scenario**: a new customer PUTs nine syntactically valid fields (an ID matched by regex only). The server flips them to VERIFIED immediately. No document is required for the transition, and no officer or external check is involved.
- **Impact**: the bank's KYC control does not exist in practice. Any registrant can book FDs, apply for loans and request forex. Officers can still set REJECTED or risk HIGH later (`CustomerProductAccess.java:15`).
- **Confidence**: confirmed (code). May be an intended demo feature (comment in code says "auto-verify"). It still contradicts bank KYC and must be flagged.
- **Fix**: auto-fill to `PENDING_REVIEW`; require officer approval via `PATCH /customers/{id}/status`; require an uploaded document.
- **Regression test**: submit nine valid fields as a new CUSTOMER and expect `kycStatus` to stay PENDING/UNDER_REVIEW and `POST /fixed-deposits` to return 403.

### SE-003 - P1 - Teller deposit/withdraw: unlimited, no dual control, no actor in the ledger
- **Path**: `S/fd-account-service/.../StaffAccountController.java:83-110` (`transact`; `MoneyRequest` at the end of the file, `@DecimalMin("0.01")` only).
- **Scenario**: a BANK_OFFICER with a customer profile, or colluding with one, deposits any amount into any savings account (including their own) with a single call. A withdrawal is the same. The `savings_transactions` row (line 108) stores only `STAFF-<uuid>` and remarks. The actor appears only in `fd_operation_idempotency.foi_actor_key`. There is no event/outbox row.
- **Impact**: insider fraud with a weak audit trail; the ledger cannot say who posted the entry.
- **Confidence**: confirmed (code).
- **Fix**: add a maker-checker threshold, a per-transaction and per-day limit, a block on acting on one's own accounts, an actor column plus an audit event, and an upper bound validator on `amount`.
- **Regression test**: an officer deposit above the limit must return 403/409, an officer cannot post to an account they hold, and the audit row contains the actor.

### SE-004 - P1/P2 - Demo opening credit mints unlimited funds; enabled in compose
- **Path**: `S/fd-account-service/.../SavingsAccountController.java:52-71`; `docker-compose.yml:156` (`APP_DEMO_OPENING_BALANCE_ENABLED: "true"`).
- **Scenario**: any authenticated user opens a savings account (`POST /savings-accounts`, no cap on the count), then calls `/demo-opening` with a huge `amount`. Only `@DecimalMin("0.01")` applies, so there is no upper bound (a precision overflow is possible at the DB column scale: UNVERIFIED). The guard "once per unused account" is bypassed by opening more accounts.
- **Impact**: money creation with no source. P2 if this stays a labelled demo, P1 if the compose profile is used outside a lab.
- **Confidence**: confirmed (code and compose).
- **Fix**: default the flag to false, cap the amount, cap the number of accounts per customer, restrict it to a profile or non-prod build, and add an audit trail.
- **Regression test**: with the flag false it returns 404; with the flag true an amount above the cap returns 400.

### SE-005 - P2 - MFA challenge token accepted as a bearer token by PRD
- **Path**: issued at `AuthController.java:85-87` (same key, HS512, claim `purpose=MFA_LOGIN`, 5 min). PRD `JwtFilter.java:31-32` accepts any validly signed token and builds an authenticated principal with no authorities.
- **Scenario**: an attacker who knows only the password logs in, receives `mfaToken`, and presents it to PRD. `anyRequest().authenticated()` passes for `/api/v1/customer-products/**` and `/api/v1/products*`. FD throws an NPE on the missing `userId` claim and stays anonymous. IDC rejects it because `tokenVersion` is missing.
- **Impact**: partial MFA bypass limited to read-only product endpoints today; fragile design.
- **Confidence**: confirmed (code); exploit UNVERIFIED at runtime.
- **Fix**: use a different signing key or `aud`/`typ` for the challenge; make every filter reject tokens whose `purpose` claim is set.
- **Regression test**: call PRD with an MFA challenge token and expect 401.

### SE-005b - P2 - Customers can read DRAFT/INACTIVE products
- **Path**: `S/product-service/.../ProductCatalogController.java:28-30` (`GET /api/v1/products?status=DRAFT`, `GET /api/v1/products/{id}`), SC `anyRequest().authenticated()`.
- **Scenario**: a CUSTOMER lists `?status=DRAFT` or iterates ids to see unpublished products and terms.
- **Impact**: disclosure of unreleased pricing. Lower than P1 because no PII is involved.
- **Confidence**: confirmed (code).
- **Fix**: force `ACTIVE` for non-staff callers.
- **Regression test**: a CUSTOMER requesting `?status=DRAFT` receives only ACTIVE items or 403.

### SE-006 - P2 - Direct service, DB and Kafka exposure; Kafka forgery; kafka-ui
- **Path**: `docker-compose.yml:8` (Kafka 29092), `:42` (MySQL 3307-3309), `:116,136,158` (8081-8083), `:171` (kafka-ui 8084, no authentication), `nginx.conf` (no auth; routing only).
- **Scenario**: the gateway performs no authentication or rate limiting. Clients can bypass it by hitting the services directly. Anyone who can reach Kafka (plain-text, no ACLs) can publish a `user.snapshot` event with `kycStatus=VERIFIED` and `status=ACTIVE`; `ReferenceSnapshotConsumer.java` trusts the payload (only `id>0` and `version>0` are checked). kafka-ui shows the event payloads (names, customer numbers, KYC) to anyone.
- **Impact**: KYC/risk gating can be forged; PII is exposed from kafka-ui.
- **Confidence**: confirmed config; exploit is a runtime hypothesis (UNVERIFIED).
- **Fix**: bind these ports to 127.0.0.1 or remove the mappings, enable SASL/ACLs, put the services on an internal network only, protect kafka-ui, and sign the events or verify the source.
- **Regression test**: from outside the compose network, ports 3307-3309, 8081-8084 and 29092 must refuse connections.

### SE-007 - P2 - Officer endpoints set KYC/risk without validation or four-eyes
- **Path**: `S/identity-customer-service/.../CustomerAdminController.java:60-97` (`create`, `update`, `updateStatus`).
- **Scenario**: an officer can set any `kycStatus` or `riskCategory` string. `PATCH /{id}/status` has no existence check (a missing id raises a 500 via `EmptyResultDataAccessException`, UNVERIFIED), no allowed-value list, and no audit event. The audit columns that exist in PRD (`pro_updated_by`) are constants (`'API'`).
- **Impact**: weak control over the data that drives the loan, FD and forex gate.
- **Confidence**: confirmed (code).
- **Fix**: use enums, validate, write a KYC event row, and record the actor.
- **Regression test**: an invalid status returns 400; each change writes an audit event with the actor name.

### SE-008 - P2 - Unlimited token refresh
- **Path**: `AuthController.java:155-160`.
- **Scenario**: a stolen or old 24 h token is renewed indefinitely, as long as it is valid (and, via IDC only, ACTIVE).
- **Impact**: session lifetime is not bounded.
- **Confidence**: confirmed (code).
- **Fix**: add an absolute session lifetime (`sessionStart` claim) and a rotating refresh token.
- **Regression test**: refresh beyond the absolute lifetime returns 401.

### SE-009 - P2 - No rate limiting or bot control on public endpoints
- **Path**: `nginx.conf` (no `limit_req`), `AuthController` register/login, `PasswordResetController.forgot`.
- **Scenario**: unlimited registrations (each creates a user and customer rows), unlimited reset emails (a cost and abuse vector through the Resend API), and a password-guess flood spread over many usernames. The per-account lockout is the only control.
- **Impact**: DoS, mail spam and credential stuffing.
- **Confidence**: confirmed (absence in code); exploitation UNVERIFIED.
- **Fix**: add nginx `limit_req` per IP for the auth routes, and add CAPTCHA/throttling on register and forgot.
- **Regression test**: the 11th login in a minute from one IP returns 429.

### SE-010 - P3 - Login discloses account state and username existence
- **Path**: `AuthController.java:75-79`.
- **Scenario**: an unknown user returns 401 immediately with no bcrypt work (a timing oracle). A suspended account returns 403 "Account is not active" and a locked one returns 423 before the password is checked. An attacker learns the state of an account without credentials. Also the lock is a DoS lever: 5 bad passwords lock a victim for 15 minutes.
- **Confidence**: confirmed (code).
- **Fix**: run a dummy bcrypt match for unknown users; check the status only after the password verifies (or return a generic 401).
- **Regression test**: timings and bodies are equal for unknown and known-wrong-password users.

### SE-011 - P3 - Registration weaknesses
- **Path**: `AuthController.java:51-70`, `RegisterRequest` (line 189).
- **Scenario**: a 409 "Username is already taken" lets anyone enumerate usernames. The email is unverified and uniqueness is not checked in code (a DB constraint is UNVERIFIED), so someone can register using a victim's email and trigger reset mail to it. `phone`, `address` and `legalEntityType` are unvalidated. The password policy is 8 characters only.
- **Confidence**: confirmed (code).
- **Fix**: verify the email, enforce uniqueness, validate the optional fields, and strengthen the password policy.
- **Regression test**: registering a duplicate email returns 409/400.

### SE-012 - P3 - MFA design gaps
- **Path**: `AuthController.java:122-153`, `TotpService.java`.
- **Gaps**: no recovery/backup codes and no admin MFA reset, so a lost authenticator permanently locks the user out (UNVERIFIED whether a DB reset path exists). Enroll and enable need no password re-authentication, so a stolen token lets an attacker enrol MFA, which also bumps `tokenVersion` and locks out the real user. The used-step table `mfa_used_totp_steps` is never pruned. TOTP is SHA-1 (standard). MFA is not mandatory for ADMIN or BANK_OFFICER.
- **Confidence**: confirmed (code).
- **Fix**: require the current password for enable/disable, add hashed backup codes, prune the table, and enforce MFA for staff.
- **Regression test**: enabling MFA without the password returns 400/401.

### SE-013 - P3 - Password reset side channels
- **Path**: `PasswordResetController.java:48-66`.
- **Scenario**: for an existing customer user, a mail-API failure returns 503 (line 62), while a non-existent user returns the 200 message, so mail errors reveal existence. The reset link carries the token in a query string (Referer/log exposure). The default reset URL is `http://localhost:4200`. Staff (non-customer) accounts cannot reset because the query joins `customers` (functional, not security).
- **Confidence**: confirmed (code).
- **Fix**: return the same 200 regardless of mail failure (queue the mail), and use a URL fragment for the token.
- **Regression test**: a forced mail failure returns the same 200 for known and unknown users.

### SE-014 - P3 - Secret hygiene in the repo history
- **Path**: git history of `BACKEND/src/main/resources/application.yml` and the test ymls (a 128-char literal; commits include 8301c59 and ef7948b). `BACKEND/target` and `BACKEND/bin` (60 files) are tracked.
- **Impact**: it does not match the current `.env` secret, so there is no live compromise. A legacy secret could still be reused elsewhere.
- **Confidence**: confirmed (history); reuse is UNVERIFIED.
- **Fix**: rotate anything that ever shared that value; purge `target/` and `bin/`; add a secret-scan CI step.
- **Regression test**: `gitleaks` in CI returns zero findings on HEAD.

### SE-015 - P3 - KYC data at rest
- **Path**: `KycService.java` (`proof_of_id_value` stored in plaintext; only `proof_number_masked` is returned to the API; document bytes in the DB).
- **Scenario**: a DB leak exposes government IDs and documents. The API response correctly omits the raw value.
- **Confidence**: confirmed (code). Column encryption in the migration SQL is UNVERIFIED.
- **Fix**: encrypt at the application level or store only a hash and the last 4 digits.
- **Regression test**: the raw ID is never present in a DB dump; the response contains only the masked value.

### SE-016 - P3 - MFA key falls back to the JWT secret
- **Path**: `application.yml:25`, `docker-compose.yml:105`.
- **Impact**: if `MFA_ENCRYPTION_KEY` is unset, one secret protects both signing and TOTP encryption. `.env` currently sets a separate value. The key is derived by SHA-256 with no KDF cost or versioning (rotation is impossible without re-enrolment).
- **Fix**: fail fast when the key is unset; add a key-id prefix to support rotation.
- **Regression test**: starting without `MFA_ENCRYPTION_KEY` must fail.

### SE-017 - P3 - Token and role in `localStorage`
- **Path**: `FRONTEND/src/app/core/services/auth.service.ts:82-93`.
- **Impact**: XSS can steal the 24 h token. The role in `localStorage` is only UI state (the server enforces roles), so tampering gives no privilege, though it shows hidden screens.
- **Fix**: shorten the token lifetime (see SE-001/008) and add a CSP at the gateway.
- **Regression test**: the response headers include a CSP.

### SE-018 - P3 - Role administration
- **Path**: `IdentityAdminController.java:43-48`.
- **Scenario**: `PUT /roles/{id}` lets an ADMIN rename an existing code, for example `ROLE_CUSTOMER` into an arbitrary code. This breaks the hard-coded lookups; also `createRole` accepts unused codes. Only ADMIN can do either.
- **Confidence**: confirmed (code).
- **Fix**: make the three system roles immutable.
- **Regression test**: renaming a system role returns 409.

### SE-019 - P3 - Loan workflow lacks separation of duties; role mismatch with the manual
- **Path**: `LoanForexController.java:100,124` (`approve`, `disburse`), ADMIN only.
- **Scenario**: no check that approver is not the applicant (the premature-closure flow does have it, `FdLifecycleController`). An ADMIN who also has a customer profile can approve and disburse their own loan. Officers cannot approve loans, which may contradict the manual's officer role (UNVERIFIED against the lab text). Loan data shown to ADMIN includes `customerName` and `customerNumber` unmasked.
- **Fix**: add an approver != applicant check; decide the intended role.
- **Regression test**: self-approval returns 403.

### SE-020 - P3 - Inputs without upper bounds
- **Path**: `FdCalculatorController.java:59-62` (`principal`, `termDays` have no max); the batch endpoints' `businessDate` parameter; savings/staff amounts (see SE-003/004).
- **Scenario**: very large values cause long computation or overflow of `Math.pow` (UNVERIFIED), and a batch endpoint can accrue or mature for an arbitrary past or future date.
- **Fix**: add `@Max`/`@DecimalMax` and restrict `businessDate` to the demo clock.
- **Regression test**: an oversized `termDays` returns 400.

### SE-021 - P3 - DemoAdminBootstrap behaviour
- **Path**: `DemoAdminBootstrap.java:36-73`.
- **Facts (confirmed)**: it runs only when `DEMO_ADMIN_USERNAME`/`DEMO_ADMIN_PASSWORD` are set. The password must be 20-100 characters. It never ships a default password (positive). If the admin already exists, it silently overwrites the password hash on every start without bumping `tokenVersion` or logging it. The script default username is `moneypal.admin` (guessable). The account has no MFA.
- **Fix**: log the reset; bump `tokenVersion`; require MFA; disable in production profiles.
- **Regression test**: starting with a different password invalidates existing admin tokens.

### SE-022 - P3 - 403 instead of 401 for missing or invalid tokens (IDC and FD)
- **Path**: SC and filters in IDC and FD.
- **Impact**: the lab expects 401 for unauthenticated and 403 for unauthorized; clients cannot tell an expired token from a permission error. PRD returns 401 for a bad token.
- **Fix**: configure `AuthenticationEntryPoint` returning 401.
- **Regression test**: a request with no token returns 401.

## 5. Checks with no finding

- **SQL injection**: every `jdbc.*` call uses `?` placeholders. String concatenation exists only for generated placeholder lists, constant column lists, and allow-listed filters (grep on all three services). Only LIKE wildcard characters in `search` are unescaped (nuisance only). No injection found; confidence probable because the long truncated lines were not all read in full.
- **Mass assignment**: all request bodies are records or `Map` with explicit fields. No entity binding. Unknown JSON properties are ignored by Boot, so a `role` field in the register body is dropped.
- **Money input**: amounts use `BigDecimal` with `@DecimalMin(0.01)` and `setScale(..., UNNECESSARY)`. Negative and zero amounts are rejected. Product limits apply for FD, loan and forex.
- **Service-to-service**: FD calls PRD with the caller's forwarded `Authorization` header (`LoanForexController.java:188`). There is no service identity. It works because PRD only needs "authenticated". This depends on SE-001 and SE-005 hardening, and an admin's approval call forwards the admin's own token (fine).
- **Actuator and Swagger**: not exposed beyond health/info.
- **PII masking**: staff account lists mask account numbers (`StaffAccountController.mask`). Customer responses show full values to their own owner only. KYC raw ID is never returned.

## 6. Priority order for fixes

1. SE-001, SE-002, SE-003, SE-004 (money and identity integrity).
2. SE-005/005b, SE-006, SE-009 (token confusion, exposure, rate limits).
3. SE-007, SE-008, SE-012, SE-019 (workflow and session controls).
4. P3 items.

## 7. UNVERIFIED items (need a runtime check)

- Real 403/401 status codes and bodies.
- Whether `mfa_used_totp_steps` and other tables have the expected unique indexes.
- DB-level email uniqueness.
- Overflow behaviour on huge amounts.
- bcrypt behaviour on passwords longer than 72 bytes.
- Whether frontend roles map to server routes.
- Any dependency CVE scan (no scanner was run).
