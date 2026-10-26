# Agent 10 (file: agent6-frontend) - Frontend / UI / UX / Accessibility / Contract audit

Scope: /private/tmp/claude-501/-Users-yashralhan-Projects-PokerThen-/e0312acd-4510-4ec5-98db-ebead3794720/scratchpad/repo/FRONTEND (Angular 18, standalone components). Read-only; builds run in a scratch copy (`scratchpad/fe-copy`).

## 1. Build / test results (what actually ran)

| Command (in scratch copy) | Outcome |
|---|---|
| `npm ci` | OK (install-script warnings only) |
| `npx ng build` | PASS in 4.4s. Warnings: initial bundle 837 kB vs 512 kB budget (landing chunk 544 kB, three.js); fd-account-detail.scss 6.17 kB and product-builder.scss 8.19 kB over the 4.1 kB budget |
| `npx ng test --watch=false` | NOT RUNNABLE: `angular.json` has no `test` target (error: Unknown argument: watch). package.json has no test script, no vitest/karma/jasmine dependency |
| `npx tsc -p tsconfig.spec.json --noEmit` | FAIL: `Cannot find type definition file for 'vitest/globals'`. The 11 `*.spec.ts` files use vitest but vitest is not installed, so no unit test can run |
| `node scripts/check-i18n.mjs` (npm run check:i18n) | FAIL: AssertionError "Missing template translations": 4 strings have no translation entry: "You selected {0} with a {1} annual rate...", "This page manages existing fixed-deposit offers.", "New product", "Save draft" |
| Static serve of built dist + headless Chrome screenshots (login, signup, dashboard at 375/768/1440; `/api/*` mocked as 503) | RAN. 9 PNGs in `/Users/yashralhan/Projects/PokerThen/audit-reports/evidence/screenshots/fe-{login,signup,dashboard}-{375,768,1440}.png`. `/dashboard` unauthenticated redirects to the login page (route guard works). No backend, so no authenticated flows were exercised |

dist/ staleness: `FRONTEND/dist` is gitignored (`.gitignore`: dist/) yet present in the archive. The file list of the committed `dist/frontend/browser` is identical to a fresh `ng build` of src (same hashed chunk names), so it is NOT stale vs src. It was built with the default (non-production) config: no `localhost:8080` in it, i.e. relative API URLs.

## 2. Route map

Role source: `AuthService.currentRole()` reads `localStorage.role` (not decoded from JWT). Guards: authGuard = token present; staffGuard = ROLE_BANK_OFFICER|ROLE_ADMIN (else redirect /fd); adminGuard = ROLE_ADMIN (else /dashboard).

| Path | Component | Guard | Main API calls |
|---|---|---|---|
| `/` | Landing | none | none |
| `/login` | Login | none | POST /api/v1/auth/login |
| `/signup` | Signup | none | POST /api/v1/auth/register |
| `/dashboard` | DashboardStub | auth | GET /api/v1/auth/me (customerProfile) |
| `/kyc` | Kyc (canDeactivate) | auth | /api/v1/customers/me/kyc (GET, PUT draft, POST document, DELETE document, POST rekyc) |
| `/savings` | SavingsAccounts | auth | savings-accounts list/create/currencies/demo-options/demo-opening |
| `/fd` | FdHome | auth | GET /fd-products/eligible |
| `/fd/book/:productId` | FdBooking | auth | savings list, POST /fixed-deposits |
| `/fd/accounts` | MyDeposits | auth | GET /fixed-deposits |
| `/fd/accounts/lookup` | FdAccountLookup | auth | GET /fixed-deposits/{id} |
| `/fd/accounts/:id` | FdAccountDetail | auth | fund, close, premature-closure (POST/GET), admin approve, accruals, transactions, savings |
| `/fd/not-found`, `/fd/**` | FdNotFound | auth | none |
| `/loans`, `/loans/:id`, `/applications`, `/applications/:id` | LoanPage | auth only (any role) | customer-products/loan, loans*, POST /loans |
| `/forex`, `/forex/requests`, `/forex/requests/:id`, `/forex/:id` | ForexPage | auth only | customer-products/forex, forex-requests (GET, POST) |
| `/staff/fd-products` | FdProductsAdmin | auth+staff | fd-products, products/{id}, translations |
| `/staff/product-builder` | ProductBuilder | auth+staff | product-configurations/*, product-categories |
| `/staff/customers` | CustomerAdmin | auth+staff | customers (list, by-user, PUT) |
| `/staff/fd`, `/staff/savings` | StaffAccountDashboard | auth+staff | /fd/v1/staff/* (deposit/withdraw with Idempotency-Key) |
| `/compliance` | FdCompliance | auth+staff | compliance/fd/clock, verify-rate-disclosure, evaluate-effective-date |
| `/staff/operations/clock` | ClockDashboard | auth+admin | /api/admin/clock (GET, advance, reset) |
| `/staff/ledger` | LedgerAdmin | auth+admin | /api/v1/admin/ledger |
| `/staff/loans`, `/staff/loans/:id` | LoanAdmin | auth+admin | admin/loans (list, detail, history, approve, reject, disburse) |
| `**` | redirect `/` | none | none |

Route observations: `/kyc`, `/loans*`, `/forex*`, `/applications*` accept any authenticated role including staff (staff can open customer pages; backend decides). Order: `kyc` declared before `''`, harmless.

## 3. API contract table (every HTTP call in FRONTEND/src)

Gateway = `services/api-gateway/nginx.conf`. "Backend" = controller found in services/*/src/main/java.

| UI call | Gateway route | Backend controller | Mismatch? |
|---|---|---|---|
| POST /api/v1/auth/login | /api/v1/auth/ -> identity | AuthController.login | UI ignores MFA: backend has /login/mfa/verify and /mfa/* but the UI never calls them; an MFA-enabled account cannot log in (UI-04) |
| POST /api/v1/auth/register | ok | AuthController.register | none seen |
| GET /api/v1/auth/me | ok | AuthController.me | none |
| GET/PUT/POST/DELETE /api/v1/customers/me/kyc[/draft|/document|/rekyc] | /api/v1/customers/ -> identity (6m body limit for document) | KycController | ok |
| GET /api/v1/customers, GET /customers/by-user/{id}, PUT /customers/{id} | ok | CustomerAdminController | ok. PATCH /customers/{id}/status not used by UI |
| GET /api/v1/fd-products, POST .../{id}/activate, /deactivate, PUT .../{id} | /api/v1/fd-products/ -> product | FdOfferController | ok |
| GET /fd-products/eligible (via /api/v1) | -> product | EligibleFdOfferController | ok |
| GET /api/v1/products/{id}; PUT /products/{id}/translations/{locale} | -> product | ProductCatalogController | ok |
| /api/v1/product-configurations (metadata, list, validate-identity, validate, preview, POST, PUT, DELETE, activate) | -> product | ProductConfigurationController | ok |
| POST /api/v1/product-categories | -> product | ReferenceDataController | ok |
| GET /api/v1/customer-products/{family}[/{id}] | -> product | CustomerOfferController | ok |
| GET /api/v1/compliance/fd/clock; POST verify-rate-disclosure, evaluate-effective-date | -> product | FdComplianceController | ok |
| GET/POST /api/v1/savings-accounts (+/currencies, /demo-options, /{id}, /{id}/demo-opening) | -> fd-account | SavingsAccountController | ok |
| GET /api/v1/fixed-deposits, /{id}, POST /fixed-deposits, POST /{id}/fund, /{id}/close | -> fd-account | FixedDepositController, FdLifecycleController | ok |
| GET /{id}/accruals, /accruals/cumulative, /transactions, GET/POST /premature-closure | ok | FdLifecycleController | ok. `listJournals` (ADMIN-only) is defined but never called |
| POST /api/v1/admin/fixed-deposits/{id}/premature-closure/{cid}/approve (called from customer page) | /api/v1/admin/fixed-deposits/ -> fd-account | FdLifecycleController.approvePremature: `@PreAuthorize BANK_OFFICER/ADMIN` and rejects approver == owner | CONTRACT BUG: customer UI always gets 403 (UI-01) |
| GET /api/v1/admin/ledger | ok | FdLedgerAdminController | ok |
| /api/admin/clock (GET, /advance, /reset) | ok | FdClockController | ok |
| /fd/v1/staff/accounts, /savings-accounts, /savings-accounts/{id}/transactions, POST .../{deposit|withdraw} | /fd/v1/staff/ -> fd-account | StaffAccountController | ok |
| /api/v1/loans (GET list/detail/history, POST) | ok | LoanForexController | ok |
| /api/v1/admin/loans (list, detail, history, approve, reject, disburse) | ok | LoanForexController | ok |
| /api/v1/forex-requests (GET list, GET id, POST) | ok | LoanForexController | ok |

No dead UI call (UI call with no backend mapping) was found. All UI URLs are routed by the gateway.

### Backend endpoints with no UI
- FD: POST /admin/fixed-deposits/accrual-batch and /maturity-batch (batch trigger); GET /fixed-deposits/{id}/journals (customer-facing/ADMIN view); staff approval of premature closure (only the broken customer button exists, see UI-01).
- POST /api/calculator/estimate (FdCalculatorController): no FD calculator UI exists at all (no category dropdown/cap to review).
- Reports: /api/reports/portfolio, /portfolio/{username}, /operational: no UI.
- Forex: only request creation/listing exists; no execution/approval endpoint or UI. Loans: full admin UI exists.
- Identity admin: users, roles, branches, employees, classification types, PATCH customer status, change-password, password forgot/reset (`/auth/password/*`, routed by gateway), MFA enrol/enable/disable, refresh, logout, mfa verify: no UI (no forgot-password link on login).
- Product: products CRUD/status/by-code, currencies CRUD, categories PUT/DELETE, api/product: mostly no UI (product-builder covers a subset).
- CASA: no transfer endpoint or UI. Savings: staff deposit/withdraw has UI.

## 4. UI defect register

| ID | Route | Severity | Repro / evidence | Cause | Fix | Regression test |
|---|---|---|---|---|---|---|
| UI-01 | /fd/accounts/:id | HIGH | Customer requests premature closure, then clicks "Accept closure amount"; request goes to the staff-only approve endpoint and gets 403 (also backend forbids approver == owner). So the customer can never complete the manual FD withdrawal; the customer sees only a pending request, and no staff screen can approve it | `fd.service.approvePrematureClosure` posts to /admin/... from customer page; no staff approval UI exists anywhere (grep of staff features) | Add staff approval screen (or backend self-approval flow for owner) and remove/relabel customer button; show quote/penalty before submitting | Spec: customer page does not call /admin/ URL; staff e2e approves a pending closure |
| UI-02 | /fd/accounts/:id | MEDIUM | "Withdraw" for ACTIVE FD is the "Request premature closure" action; penalty summary appears only after the request is created (backend inserts the PENDING row on request), not before the confirm. Confirmation text says only "This submits your premature-closure request" | Quote is a side effect of POST; no preview GET | Add preview/quote endpoint; show penalty and net payable inside the confirm panel | Spec on confirm panel content |
| UI-03 | gateway FRONTEND/nginx.conf | HIGH (deploy) | Config proxies `location /fd/` to the gateway. SPA routes `/fd`, `/fd/accounts`, `/fd/book/:id` start with /fd/ so a browser refresh or deep link returns the gateway 404 JSON instead of index.html | Path collision between SPA routes and API prefix `/fd/v1/staff` | Narrow to `location /fd/v1/` | Docker smoke test: GET /fd/accounts returns text/html |
| UI-04 | /login | MEDIUM | Login ignores MFA branch; account with MFA enabled cannot sign in. No forgot-password link although /auth/password/* exists | Missing screens | Add MFA step and forgot/reset pages | Login spec with mfa_required response |
| UI-05 | all authenticated | HIGH | 401 interceptor calls `auth.logout()` but never navigates; the user stays on the page with a cleared session until next navigation; no JWT `exp` check, a stale/expired token in localStorage still passes `authGuard` (token != null) and loads pages that then fail. No 403 handling | `auth.interceptor.ts`, `auth.service.isAuthenticated()` | Decode exp, treat expired as unauthenticated, redirect to /login?returnUrl on 401, show forbidden message on 403 | Interceptor spec with 401 and expired token |
| UI-06 | /login | MEDIUM | Token, role are in localStorage (XSS-readable); `login.ts` additionally writes `username` and `roles` keys that `logout()` never removes, so identity data persists after sign-out. Role comes from localStorage not the JWT, so it can be edited client-side to open staff routes (backend @PreAuthorize still protects data) | auth.service.logout only clears token/role; login.ts duplicate writes | Remove duplicate writes, clear all keys on logout, derive role from verified token, consider in-memory token or HttpOnly cookie | Spec: logout leaves storage empty |
| UI-07 | /login, /signup | LOW | Password inputs are type=password (masked; verified in screenshot). Login has no show/hide toggle; signup length message only "Min. 8 characters" (policy parity with backend not verified); login error `<p>` lacks role="alert" | templates | add role=alert/aria-live and aria-describedby on errors | a11y spec |
| UI-08 | /fd/accounts/:id and booking | MEDIUM | Dates shown raw (`open_date`, `maturity_date` as strings), amounts use `number:'1.2-2'` regardless of currency (JPY has 0 decimals, KWD 3; savings uses 1.2-4); locale ignores selected language (Hindi/Arabic still en-US digits) | DecimalPipe/hard-coded formats | Use currency-aware formatting from decimalPlaces and locale from TranslationService | Formatting spec per currency |
| UI-09 | /staff/* , /kyc, loans, forex | MEDIUM | Approve/reject/disburse/clock buttons are guarded by `busy` flags in FD/savings pages (good: busyAction, submitting, Idempotency-Key kept in storage) but I did not find equivalent confirm dialogs for admin loan approve/reject/disburse and clock reset; unverified at runtime | source review only | add confirm + disable on submit | UI spec per action |
| UI-10 | whole app | MEDIUM (a11y) | 87 aria/role attributes exist; skip-link, form labels wrap inputs (good); focus-visible styles exist in workspace, but `_glass.scss:89` sets `outline: none` for glass inputs; muted gold-on-dark text and placeholder-only hints not contrast-verified (UNVERIFIED, no axe run); custom glass-select needs keyboard/aria verification; page title is static "MoneyPal Bank" on all routes | CSS/templates | Run axe; per-route titles; ensure focus ring replaces outline | axe CI check |
| UI-11 | /login 375px | UNVERIFIED | In the 375px headless screenshot the card is clipped at the right edge. CSS has max-width 380px and media queries at 900/480 px, and headless Chrome enforces a minimum window width around 500px, so this is probably a capture artifact. Needs a real device/DevTools check | | | |
| UI-12 | build/tests | MEDIUM | 11 spec files cannot run (no vitest, no test target); `check:i18n` fails with 4 untranslated strings (product-builder/fd-booking); initial bundle exceeds budget by 325 kB (three.js landing) | package.json/angular.json | add vitest/test builder, wire i18n check and tests into CI, lazy-load three | CI job |
| UI-13 | environment | MEDIUM | `environment.production.ts` hard-codes `http://localhost:8080`. The Docker image builds with `--configuration production`, so the browser calls the gateway on localhost:8080 directly (cross-origin; gateway CORS allows only localhost/127.0.0.1) and the nginx /api, /auth proxy in FRONTEND/nginx.conf is unused. Works only on the same machine; breaks on any real host | environment.production.ts | Use `apiBaseUrl: ''` with same-origin proxy | Docker smoke test from non-localhost origin |
| UI-14 | /dashboard | LOW | Dashboard is a link hub ("stub"); no balances/summary, so "balance refresh after transactions" lives only in savings (reload on success) and FD detail (refreshAfterWrite re-fetches account, savings, history). Good | | | |
| UI-15 | i18n | LOW | 6 languages, selector at bottom-right fixed overlay, html lang/dir switch (rtl for ar) works; translate pipe falls back to English key, `check:i18n` currently failing (UI-12); selector overlaps content on small screens (clearance padding 96px mitigates) | | | |

Positive findings: reactive forms with required validators; duplicate-submit guards (`busyAction`, `submitting`, `loading`) on login, savings, FD actions; Idempotency-Key with persisted pending submissions on every FD write; loading/error/empty states present on FD pages; confirm step for fund/close/premature; error normalisation maps validationErrors; guards redirect unauthenticated users (verified by screenshot); 42 media queries across stylesheets; no `console.*`, `innerHTML` or `bypassSecurity` in app code.

## 5. Top findings
1. UI-01 customer premature-closure approval calls staff-only endpoint -> manual FD withdrawal cannot complete; no staff approval UI.
2. UI-03 FRONTEND/nginx.conf `/fd/` proxy swallows SPA deep links.
3. UI-05/UI-06 session handling: no expiry check, no redirect on 401, no 403 handling, stale localStorage keys, client-editable role.
4. UI-13 production build points at localhost:8080.
5. No FD calculator, reports, batch trigger, MFA, password reset UI despite backend endpoints; tests unrunnable and i18n check failing.
