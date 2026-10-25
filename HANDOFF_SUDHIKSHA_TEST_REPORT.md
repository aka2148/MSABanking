# Premature Closure Test Report

**Date:** 25 October 2026  
**Baseline SHA:** `95c2ded0367aa3e06039bdfc600d159a74ac3f9e`  
**Decision:** **GO for code review of the implemented workflow.** The disposable MySQL migration and lifecycle suite now passes, including persisted accounting and rollback assertions. Broader gateway/UI runtime and reject/expiry-specific API scenarios remain unverified before production release.

## Commands and results

| Command | Result |
|---|---|
| `git diff --check` | Passed; no whitespace errors. |
| `mvn -f services/fd-account-service/pom.xml clean "-Dtest=PrematureClosureCalculatorTest,FdBookingAndCalculatorFinancialTest,FdAccountMigrationTest" test` | Clean build success. 21 tests discovered: 18 passed, 0 failed, 0 errored, 3 skipped. `FdBookingAndCalculatorFinancialTest`: 14 passed. `PrematureClosureCalculatorTest`: 4 passed. `FdAccountMigrationTest`: 3 skipped because Testcontainers could not find Docker. |
| `docker version` | Docker Desktop 29.8.2 client and server available. |
| `mvn -f services/fd-account-service/pom.xml "-Dtest=FdAccountMigrationTest" test` | Final run passed: 3 tests, 0 failures, 0 errors, 0 skipped. Testcontainers created and removed an isolated MySQL 8.4 `moneypal_fd` database. All 12 migrations, including V12, applied. |
| `mvn -f services/fd-account-service/pom.xml "-Dtest=FdAccountMigrationTest#customerCanBookFundAccrueMatureAndCloseWithIdempotentRequests" test` | Passed: 1 test, 0 failures, 0 errors, 0 skipped. |
| `npm.cmd run build` (in `FRONTEND`) | Passed. Angular production bundle compiled; existing bundle/style budget warnings remain. |

The first sandbox Maven invocation could not create `C:\.m2\repository`; the approved elevated rerun completed. An initial no-Docker run had 3 integration tests skipped. Once Docker was available, the first rollback harness attempt failed because MySQL blocked trigger creation without SUPER; the test was changed to inject a synthetic BIGINT row-version overflow. The next attempt exposed MockMvc's propagated `ServletException`; the assertion was corrected, and the final full integration class passed. A plain `npm run build` was blocked by PowerShell's execution policy; `npm.cmd run build` completed. The first sandbox Angular build could not resolve repository paths; the approved elevated rerun completed and exposed/fixed staff DTO field-name errors before passing.

## Financial/database verification

- Unit verified: Actual/365 calculation, HALF_UP currency rounding, the approved 1000 principal / 0.005 annual-rate fixture yielding 5 interest and 1005 net payout, penalty-rate floor at zero, and invalid input rejection.
- Unit verified: frozen snapshot rate is used despite a different live account rate; missing penalty snapshot fails closed without a mutable product lookup.
- Integration verified on disposable MySQL: V12 migration; staff queue; self-approval denial; quote/payout consistency; approved decision audit; interest expense debit and FD liability credit; settlement FD debit and savings credit; FD liability net zero; one savings credit and one closure transaction; closure event; same-key replay without a second credit; and transaction rollback after injected row-version overflow, followed by a successful retry. The synthetic FD payout was 200.66 (principal 200.00 plus rounded paid interest 0.66).
- No shared or live database was contacted or changed. The Testcontainers database was isolated and removed at test completion.
- **Still unverified:** rejection and stale-expiry API integration cases, product-catalog mutation after booking as an end-to-end scenario, and calls through a running gateway/browser session. The existing calculation/unit coverage verifies frozen account snapshot sourcing and fail-closed behavior.

## Findings and regressions

- Fixed the customer screen's call to the staff-only approval endpoint and removed premature withdrawal from MATURED actions.
- Added staff-side pending request queue and approval/rejection actions; owner/staff separation is enforced server-side.
- Fixed frozen-rate sourcing and missing-snapshot fallback; added paid-interest recognition before settlement to address the FD liability residual identified in the handoff evidence.
- No Java compile failures or Angular compile failures remain in the executed builds. Angular bundle/style budget warnings are present and are not newly investigated here.
- Separate OpenCode and Claude audit reports were not present in the checkout; implementation was based on Shaunak's handoff and current code evidence, as approved by the user.

## Remaining verification and next dependency

Add targeted disposable-MySQL coverage for rejection, quote expiration, post-booking product reference mutation, and second-close rejection, then exercise gateway/UI paths. Reconcile the stable journal keys/account codes with Krishang's shared `PostingService` before that consolidation. No tests are counted as passed unless they completed successfully.
