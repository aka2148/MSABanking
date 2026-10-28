# MoneyPal later changes handoff (A–E)

## Implemented

## Client testing fixes A-H (current working tree)

> **Current A-H correction:** Teller withdrawal is officer-authenticated and requires
> an identity-verification attestation; it does not accept or verify customer MPIN.
> The earlier MPIN table/text below predates this correction and is superseded.

## Follow-up I-K

K history confirms no customer self-service cash-withdrawal endpoint, route, or
MPIN purpose existed in repository history; online customers use transfers and FD
settlement, while cash withdrawal is a branch/teller operation. Teller withdrawal
uses officer authentication plus identity attestation and never customer MPIN.

The staff review panel now refreshes queue/count/detail in place after decisions,
guards stale responses, clamps emptied pages, and uses no-store queue/detail
responses. Rejected customers can re-apply at `POST /api/v1/customers/me/kyc/reapply`
after obtaining a single-use `KYC_REAPPLY` proof bound to `customerUserId|cycleNumber`.
The operation is limited to three attempts per rolling 24 hours, records
`KYC_REAPPLIED`, preserves the draft/document, resets confirmations, and always
requires a subsequent submit and staff approval. Customer responses expose
`canReapply` and `reapplyAttemptsRemaining`; staff detail exposes prior decisions,
`attemptNumber`, and `isResubmission` without full ID or MPIN data.
The FD service now has migration V17 for the customer withdrawal journal
operation and `WITHDRAWAL_CLEARING` chart account. Its mirrored MPIN binding
registry accepts the withdrawal vector for drift detection.

## Follow-up L-O

Customer Management KYC status is display-only. Customer creation initializes
`PENDING`; review/re-KYC/re-apply workflows are the only status transitions.
The admin PATCH rejects a differing status before applying any other fields and
accepts an identical status as a no-op. The UI uses a status pill and keeps the
review action in the actions area; risk/entity controls use shared glass selects.

KYC queue/detail responses are `no-store` and sanitize legacy
`reviewerUserId`/`reviewer_username` fragments at the response boundary without
altering stored audit data. Resubmission attempts and previous decisions are
shown compactly, and the queue count is refreshed after a decision.

Customers can simulate a savings withdrawal at
`POST /api/v1/savings-accounts/withdrawals`. It is CUSTOMER-only, requires a
verified KYC session, actor-scoped Idempotency-Key, and one-time MPIN proof with
purpose `WITHDRAWAL` bound to `accountId|amount`. Posting uses the shared
transactional ledger path (`SAVINGS` debit to `WITHDRAWAL_CLEARING`), writes
history and balance atomically, and replays an identical request. Staff teller
withdrawal remains a separate attested endpoint. The Angular savings page
provides the withdrawal form, MPIN input, validation, and refresh behavior.

The active services now return safe `{message, code?}` error envelopes. MPIN
verification errors are handled separately from the protected operation that
follows, and identity/FD use one canonical binding rule for numeric strings,
account numbers, IDs, and currency codes.

## Follow-up P-Q: customer password recovery

The identity service and Angular now use a username-or-primary-phone recovery
flow: request a six-digit code, verify it, then submit a new password with a
short-lived single-use reset token. Requests return a uniform accepted response
for existing and non-existing/ineligible accounts. Only active customers with a
verified primary email are eligible. OTPs are stored as a domain-separated HMAC,
reset tokens as SHA-256 hashes, and verification allows five attempts. Request
resend is limited per account; nginx also applies an IP request limit. Password
reset enforces the current password policy and history, clears login lock state,
increments token version, and returns a session through the existing auth path.
No MPIN fields or events are changed.

The mail sender uses the configured Brevo API asynchronously after commit,
redacts request bodies in Spring client logs, never logs provider response
bodies, and burns an OTP on delivery failure. `PASSWORD_RESET_URL` is deprecated
and ignored; configure `PASSWORD_RESET_ENABLED`, `PASSWORD_RESET_API_KEY`,
`PASSWORD_RESET_API_URL`, and `PASSWORD_RESET_FROM` in the ignored local `.env`.
The checked-in API key is blank. `services/identity-customer-service/README.md`
documents the endpoints and the loopback mock-mail test command.

Migration V16 adds the OTP table in the identity customer's migration resource
set. The old link-based reset routes are removed; authenticated password change
remains at `/auth/password/change`. Login now resolves a unique primary phone
number consistently with the login UI. The Angular `/forgot-password` page keeps
the code and reset token in component memory only and uses the existing session
flow after success.

### P-Q verification

The focused final run passed all 11 tests: seven identity migration/recovery
tests, three Brevo sender tests, and one auth DTO redaction test. This includes
the in-app password change and old-route regression checks plus phone login. The
full identity suite ran 39 tests and had 16 failures, all in existing auth/KYC
expectations or the withdrawal golden vector; the earlier baseline had 28 tests
and 17 failures. The migration-count expectation and all P-Q-specific tests now
pass. The earlier Angular production build and password-reset check passed;
translation and KYC checks retain their pre-existing failures. A fresh Node run
was unavailable in this shell because nvm has no active version configured.
The nginx configuration test passed in a disposable nginx container. No shared
Compose database or volume was used.

Teller withdrawals use the officer's authentication plus an identity-verification
attestation (`ID_DOCUMENT_CHECKED`, `SIGNATURE_VERIFIED`, `BIOMETRIC_VERIFIED`,
or `OTHER`), an optional reference, and a required remark. Customer MPIN is no
longer accepted by the teller endpoint. Migration V16 records the attestation
inside the same transaction as the ledger movement.

Migration V12 adds the staff KYC review access log. Staff can queue, inspect
masked details/documents, and approve or reject submitted applications with a
reason. The old customer-list status mutation is blocked with HTTP 400; risk
updates remain available. The customer response includes the latest decision
without reviewer identity.

`POST /api/v1/fixed-deposits/estimate` uses the same quote resolver as booking
and performs no writes. The Angular booking preview consumes it. The forex page
uses the shared workflow-page visual system. `scripts/e2e-smoke.mjs` is a
Node 18+ built-in-fetch smoke probe for an isolated gateway stack.

- **A — Staff-only demo funds:** customer demo routes and customer UI were removed. `GET /api/v1/savings-accounts/demo-options` and `POST /api/v1/savings-accounts/{id}/demo-opening` require bank staff/admin roles and the demo feature flag. A staff member may credit an active, zero-balance savings account with no transaction history once, subject to the optional cap. The credit is journaled as `DEMO_SUSPENSE` to `SAVINGS`, and is attributed to the staff actor. Staff dashboard confirms the amount and refreshes account data on success.
- **B — MPIN:** identity service stores a bcrypt hash of a peppered HMAC, encrypted MPIN for teller verification, failure counter, timed lock, and audit events. Setup/change require the customer password; customer reset by staff clears the MPIN and requires setup again. Customer verification issues a short-lived, purpose-bound HS256 proof. Money services validate actor, subject, issuer, expiry, purpose, canonical field binding, and one-time `jti`; proof consumption shares the money-operation transaction. Replays return the prior idempotent result before a proof is required. Protected operations are:

  | Operation | Customer proof fields (in order) | Call site |
  |---|---|---|
  | Savings transfer | `fromAccountId\|toAccountNumber\|amount` | `POST /api/v1/savings-accounts/transfers` |
  | Teller withdrawal | `accountId\|amount` | staff teller withdrawal |
  | FD funding | `fdId\|principal` | FD fund endpoint |
  | FD close | `fdId` | FD close endpoint |
  | Premature closure request | `fdId` | FD premature request endpoint |
  | Loan application | `productId\|amount\|currencyCode\|tenureMonths\|destinationSavingsAccountId` | loan application endpoint |
  | Forex request | `productId\|amount\|fundingCurrencyCode\|destinationCurrencyCode` | forex request endpoint |

  Values are encoded as stable normalized scalar strings and joined with `|` before SHA-256 binding. Strings are trimmed and decimal amounts use canonical decimal form. Remarks and free text are excluded. The forex endpoint has no destination account field, so its proof binds the actual request fields rather than inventing one. Staff withdrawal verifies the customer's MPIN for that customer's user ID while the staff identity is the proof actor. Deposit, FD booking, demo credit, account opening, and read operations do not require MPIN.

- **C — KYC:** `UNEMPLOYED` is accepted as an occupation; future date of birth and future ID issue dates receive specific validation errors. The browser date inputs are capped at today's UTC date and perform the same early checks. Re-KYC requires MPIN verification before a new request; the page provides an MPIN setup link.
- **D — Product templates:** descriptions clarify illustrative rates/terms, defaults that can be changed in the builder, and unsupported tax lock-in enforcement. No product payload/default rates were changed.
- **E — Risk category:** migration V8 backfills missing/blank values to `LOW` and sets the column default. New customer signup/admin creation default to `LOW`; admin updates accept only `LOW`, `MEDIUM`, or `HIGH`. `CUSTOMER_DEFAULT_RISK_CATEGORY` configures the signup default with safe default `LOW`.

## MPIN API and behavior

Identity endpoints are under `/api/v1/auth/mpin`: status, setup, change, customer proof verification, staff verification for teller use, and staff reset. The shared Angular MPIN input masks digits and keeps PINs in component memory only. It clears the field after each attempt and after form changes. Setup asks for the account password, while change asks for the current MPIN; neither password nor PIN is persisted in browser storage.

The verifier distinguishes `MPIN_REQUIRED` (missing proof) and `MPIN_PROOF_INVALID` (malformed, expired, wrong purpose/actor/subject/binding, or already consumed). Identity verification reports `MPIN_INVALID`, `MPIN_LOCKED`, and `MPIN_NOT_SET`. The default lock policy is five failed attempts and 30 minutes. Locked responses include the unlock time. Same, ascending/descending, and repeated-pattern PINs are rejected. Staff reset removes the existing PIN and lock state; the customer must set a new PIN with their account password.

Identity proof signing and FD verification derive the same key from `JWT_SECRET` and the `mpin-proof-v1` HMAC label. The MPIN digest pepper is derived from `MFA_ENCRYPTION_KEY` with a separate label unless `MPIN_PEPPER` is set. The proof is an HS256 JWT with issuer `identity`, type `mpin-proof`, customer subject, actor username, purpose, binding hash, unique `jti`, and a 90-second lifetime. V14 stores consumed-proof `jti` values. `APP_MPIN_REQUIRED=false` is a rollback switch; normal configuration is `true`.

## Configuration

Safe local example values are documented in `.env.example` and compose wiring:

- `CUSTOMER_DEFAULT_RISK_CATEGORY=LOW`
- `MPIN_MAX_ATTEMPTS=5`
- `MPIN_LOCK_MINUTES=30`
- `MPIN_PEPPER` optional and empty by default (derived key fallback)
- `APP_MPIN_REQUIRED=true`
- Demo opening remains disabled by default (`APP_DEMO_OPENING_BALANCE_ENABLED=false`); set an optional `APP_DEMO_OPENING_MAX_AMOUNT` only for a controlled demonstration.

Keep `JWT_SECRET` and `MFA_ENCRYPTION_KEY` stable and private. Changing the JWT key invalidates sessions and outstanding MPIN proofs. Changing the MFA key or MPIN pepper prevents teller verification of stored MPINs. Never commit real secrets.

## Verification and residual risks

See [HANDOFF_KRISHANG_TEST_REPORT.md](HANDOFF_KRISHANG_TEST_REPORT.md) for baseline and final command outcomes. `npm run build`, `npm run check:mpin`, and the product-rules script verify the frontend work. Translation checking still reports the repository's pre-existing untranslated template strings. No live/compose database was used; integration tests use disposable Testcontainers.

- A six-digit PIN has a small keyspace; online lockout, peppered hashes, encryption, and rate controls reduce exposure but do not remove this limit.
- Production traffic must use HTTPS so PINs and short-lived proofs are protected in transit.
- The consumed-proof table grows over time and needs an explicitly reviewed retention/cleanup policy.
- Existing database replicas or downstream consumers that treat risk category as non-null must be checked before deploying V8.
- An abandoned FD booking can remain unfunded; booking is intentionally not MPIN protected, while funding is.
- Demo suspense/teller cash remain illustrative ledger accounts, and demo opening is a local demonstration feature.

## Current account and CASA ledger update

Deposit posting now supports both `SAVINGS` and `CURRENT_ACCOUNT` liability lines selected by account type. Existing savings accounts retain the `SAVINGS` code. Product-linked accounts freeze minimum balance and interest bands; all debit paths enforce the floor under account locks. FD funding/settlement use shared posting methods without changing existing keys. See `HANDOFF_CURRENT_ACCOUNT.md` for current behavior and known UI/smoke limitations.
