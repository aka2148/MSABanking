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
