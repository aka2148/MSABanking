# MoneyPal later changes test report

**Scope:** staff-only demo opening, MPIN-bound actions, KYC corrections, product descriptions, and LOW risk default (A–E). The previous ledger handoff remains relevant to V13 behavior.

## Baseline before this implementation

- `mvn -f services/identity-customer-service/pom.xml test`: 19 tests, 12 failures and 2 errors. Signup test fixtures omitted required address fields, and `AuthEmailVerificationTest` used the old `AuthController` constructor.
- `mvn -f services/fd-account-service/pom.xml test`: 41 tests passed before MPIN enforcement changes.
- `mvn -f services/product-service/pom.xml test`: 21 tests passed.
- An earlier documented frontend build passed at HEAD. The attempted fresh baseline build could not read the workspace under the restricted invocation; its successful escalated build occurred after implementation began, so it is not counted as a pre-change baseline.

## Current verification

| Check | Result |
|---|---|
| `npm run build` | Passed after the navigation edit. Angular production bundle generated; existing bundle and SCSS budget warnings remain. |
| `npm run check:mpin` | Passed. Checks weak-PIN rejection, verify-before-action wiring for transfer/FD/loan/forex/teller, re-KYC submission, and local field clearing. |
| `node scripts/check-i18n.mjs` | Fails on the existing untranslated-template-string list (33 unrelated strings). MPIN/KYC additions are translated in the six configured locales. |
| `node scripts/check-product-rules.mjs` | Passed after completing the fixture's effective dates for both identity validation and template serialization. |
| `mvn -f services/product-service/pom.xml test` | Passed, 21 tests, 0 failures/errors/skips. |
| `mvn -f services/identity-customer-service/pom.xml clean test` | Passed, 20 tests, 0 failures/errors/skips. Includes risk-category migration/signup/admin rules, KYC date and occupation validation, re-KYC MPIN verification, encrypted MPIN storage, and five-attempt lockout. |
| `mvn -f services/fd-account-service/pom.xml clean test` | Passed, 41 tests, 0 failures/errors/skips. Integration fixtures issue purpose-bound test proofs; transfer, teller, FD funding/closure, loan, forex, migration, and ledger coverage pass. |

All integration database runs used disposable Testcontainers MySQL 8.4 instances. No compose or shared database was used. Baseline and current Maven commands used the local Maven cache; the cache emitted an unrelated Flyway GitHub metadata 401 warning, while dependency resolution and service tests proceeded.

## Go/no-go

**GO for local API demonstration** with the MPIN-protected workflow and staff-only demo funds. All three backend service suites pass, as do the Angular build, MPIN logic check, and product-rules check. The i18n checker still fails on the 33 pre-existing untranslated template strings listed above. This is not a production financial-use approval; see residual risks in [HANDOFF_LATER_CHANGES.md](HANDOFF_LATER_CHANGES.md).
# Client-testing fixes A-H final verification (2026-10-27)

- Identity customer service: 24 tests, 0 failures, 0 errors (disposable MySQL Testcontainers).
- FD account service: 55 tests, 0 failures, 0 errors (disposable MySQL Testcontainers).
- Product baseline: 34 tests passed.
- Angular production build: passed (`Application bundle generation complete`).
- `git diff --check`: passed (only normal CRLF conversion warnings).
- The isolated Node smoke probe was added but not run because the local Node version is not configured; no compose stack or user database was started.
- `check:i18n` and `check:mpin` remain unavailable/ failing due existing repository tooling issues (pre-existing untranslated strings and Angular JIT/esbuild environment mismatch); the production build is the frontend gate.

## Follow-up I-K verification

- Identity follow-up tests: 27 passed, 0 failures/errors (includes KYC re-apply integration flow, migration V13, and two KYC_REAPPLY vectors).
- FD follow-up vector and ledger integration tests: passed; current FD reports aggregate 57/0/0 (the prior 55-test baseline plus the added migration/vector/ledger coverage).
- Identity and FD compile checks passed.
- Frontend build and `.mjs` checks were UNVERIFIED in this environment because Node reports no active configured version (`nvm install <version>` / `nvm use <version>`).
- Gateway smoke was UNVERIFIED; run `node scripts/e2e-smoke.mjs` with generated isolated-stack credentials and only tear down using `docker compose -p moneypal-e2e ... down -v`.

## Follow-up L-O verification (2026-10-27)

- FD `LedgerInvariantIntegrationTest`: 8 tests passed, 0 failures/errors/skips,
  including customer withdrawal MPIN enforcement, idempotent replay, customer-only
  authorization, balanced clearing journal, and cleanup across disposable MySQL.
- FD migration/vector checks passed with migration count 17 and withdrawal golden
  vectors for integer and scale-preserving amounts. Identity compilation and the
  existing KYC follow-up tests remain passing from the preceding run.
- `git diff --check` passed. The frontend build, frontend static checks, and
  gateway smoke remain UNVERIFIED because this shell has no active Node.js
  version. No compose stack or shared database was used.

Overall L-O handoff is **NO-GO for the full client-testing gate** until Node is
configured and the Angular build/checks plus isolated gateway smoke are rerun;
the backend withdrawal integration gate is green.
