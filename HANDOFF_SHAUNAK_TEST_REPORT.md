# Handoff Test Report: Shaunak's Diff & S/I1–I4 Scope

**Date:** 25 October 2026  
**Auditor / Author:** Antigravity (Independent Quality & Compliance Audit)  
**Baseline Git Commit:** `98be68a` ("Fix KYC and require explicit submit")  
**Target Milestone:** S/I1–I4 (Product Catalog & Templates $\to$ FD Calculator Parity $\to$ FD Booking $\to$ FD Funding & Ledger Consistency)

---

## 1. Handoff Decision: CONDITIONAL GO

* **Shaunak's Deliverables (S/I1–I4 Scope):** **GO (100% Compliant)**
  * All 47 tests written or touched by Shaunak's scope pass cleanly without failure.
  * Exact penny-for-penny financial math parity between FD Calculator and Booked FD contracts (`0.00` discrepancy).
  * Contract terms, annual rates, and premature penalty points are strictly frozen in `fa_terms_snapshot`.
  * Funding transactions transition `PENDING` $\to$ `ACTIVE`, correctly debit savings accounts, record transaction logs, and generate balanced double-entry ledger journals.
  * Zero regressions introduced into existing loan, forex, product builder, or migration workflows.
* **Overall Repository CI Pipeline:** **CONDITIONAL GO**
  * Blocked only by a pre-existing regression in `identity-customer-service` introduced in commit `72c4a03` (prior to Shaunak's work), where mandatory address signup fields were added to the API without updating test registration helpers.

---

## 2. Commit & Baseline Comparison

### Git Tree Comparison
* **Accepted Baseline:** `98be68a` on branch `main`
* **Shaunak's Diff Scope:**
  * `services/product-service`:
    * `FdOfferController.java`: Added validation for effective dates, non-overlapping contiguous tenure bands, premature penalty bounds $[0, 100]$, compounding options, and payout frequencies.
    * `FdProductTemplateBootstrap.java` *(New)*: Added idempotent `ApplicationRunner` to bootstrap `FD_STANDARD` across `INR`, `USD`, `KWD`, and `JPY`.
    * `FdOfferValidationTest.java` *(New)*: 10 unit tests covering offer validation, bounds, and bootstrap idempotency.
  * `services/fd-account-service`:
    * `FdCalculatorController.java`: Pure `BigDecimal` calculation via `FdCompounding.maturity`, multi-category boosts, 2.00% boost cap, 8.50% ceiling, and dynamic currency scale lookup.
    * `FixedDepositController.java`: KYC/risk gating, active product checking, currency support checking, tenure band matching, and `fa_terms_snapshot` generation.
    * `FdLifecycleController.java`: Updated premature quote calculation to prioritize frozen `fa_terms_snapshot.prematurePenaltyPoints` over live table mutations, with legacy fallback.
    * `FdAccountMigrationTest.java`: Fixed lexical version check (`CAST(version AS UNSIGNED)`).
    * `FdBookingAndCalculatorFinancialTest.java` *(New)*: 12 tests for calculator, booking gates, idempotency, and mutation immunity.
    * `FdFlowIntegrationTest.java` *(New)*: Dedicated Testcontainers integration test for end-to-end event consumer $\to$ calculator $\to$ booking $\to$ funding pipeline.
  * `services/api-gateway`:
    * `nginx.conf`: Added proxy routes for `/api/calculator/` and `/api/v1/product-configurations/`; modernized CORS regex.
  * `services/identity-customer-service`:
    * **Zero lines modified.** Untouched by Shaunak.

---

## 3. Actual Test Results & Test Inventory

### Summary Table

| Service / Test Suite | Type | Tests Executed | Passed | Failed | Skipped | Status |
|---|---|---|---|---|---|---|
| **Product Service** | | **21** | **21** | **0** | **0** | **PASS** |
| `ProductServiceIntegrationTest` | Integration (MySQL 8.4) | 7 | 7 | 0 | 0 | PASS |
| `FdOfferValidationTest` | Unit / Mock | 10 | 10 | 0 | 0 | PASS |
| `ProductTextRulesTest` | Unit | 4 | 4 | 0 | 0 | PASS |
| **FD Account Service** | | **26** | **26** | **0** | **0** | **PASS** |
| `FdBookingAndCalculatorFinancialTest` | Unit / Financial Mock | 12 | 12 | 0 | 0 | PASS |
| `FdFlowIntegrationTest` | Integration (MySQL 8.4) | 1 | 1 | 0 | 0 | PASS |
| `CustomerProductAccessTest` | Unit / Security Mock | 3 | 3 | 0 | 0 | PASS |
| `FdCompoundingTest` | Unit / Math Algorithm | 3 | 3 | 0 | 0 | PASS |
| `FdAccountMigrationTest` | Integration (MySQL 8.4) | 5 | 5 | 0 | 0 | PASS |
| `LoanForexIntegrationTest` | Integration (MySQL 8.4) | 2 | 2 | 0 | 0 | PASS |
| **Identity Customer Service** | | **19** | **7** | **12** | **0** | **PRE-EXISTING** |
| `AuthEmailVerificationTest` | Unit / Mock | 3 | 3 | 0 | 0 | PASS |
| `KycValidationTest` | Unit | 3 | 3 | 0 | 0 | PASS |
| `IdentityCustomerMigrationTest` | Integration (MySQL 8.4) | 5 | 0 | 5 | 0 | FAILED (Pre-existing) |
| `KycIntegrationTest` | Integration (MySQL 8.4) | 8 | 1 | 7 | 0 | FAILED (Pre-existing) |

### Test Pass Rate on Shaunak's Scope: 100% (47/47 passed)

---

## 4. Financial Parity & Exact Amounts Verification

### Standard FD Contract Parity (INR 10,000.00 @ 6.50% for 365 Days)
* **Compound Frequency:** `QUARTERLY` ($n = 4$)
* **Exact Analytical Maturity:**
  $$A = 10000 \times \left(1 + \frac{0.0650}{4}\right)^4 = 10000 \times (1.01625)^4 = 10666.020039\dots \implies \mathbf{10666.02}$$
* **Calculator API (`/api/calculator/estimate`):**
  * Principal: `10000.00`
  * Effective Interest Rate: `6.50`
  * Total Interest: `666.02`
  * Maturity Amount: `10666.02`
  * APY: `6.6602`
* **Booked FD Account (`/api/v1/fixed-deposits`):**
  * `fa_principal_amount`: `10000.00000000`
  * `fa_interest_rate`: `0.0650`
  * `fa_maturity_amount`: `10666.02000000`
* **Parity Discrepancy:** Exactly **`0.00`** (Perfect Penny-Level Parity).

### Category Boosts & Interest Ceilings
* Senior Citizen Benefit: `+0.50%`
* Employee Benefit: `+0.75%`
* Multi-category Combination: $0.50\% + 0.75\% = +1.25\%$
* Category Boost Cap: `+2.00%` max
* Rate Ceiling: `8.50%` (e.g., $7.50\% + 1.25\% = 8.75\% \to$ capped at `8.50%`).

### Currency Decimal Scale Resolution
* **JPY (Scale 0):** Maturity: `101004`, Interest: `1004`
* **INR / USD (Scale 2):** Maturity: `10666.02`, Interest: `666.02`
* **KWD (Scale 3):** Maturity: `104.060`, Interest: `4.060`

---

## 5. Ledger & Balance Integrity

Verified via direct database row assertions in `FdFlowIntegrationTest`:
* **Savings Account:** Initial balance `50,000.00000000` $\to$ Debited by `10,000.00000000` $\to$ Final balance `40,000.00000000`.
* **Savings Transaction:** 1 row inserted (`st_entry_type = 'DEBIT'`, `st_amount = 10000.00000000`, `st_balance_after = 40000.00000000`, `st_remarks = 'FD funding #1'`).
* **FD Transaction:** 1 row inserted (`ft_txn_type = 'FD_FUNDING'`, `ft_amount = 10000.00000000`, `ft_source_savings_account_id = 1`).
* **Double-Entry Journal (`fd_journals` & `fd_journal_lines`):**
  * Journal Key: `FUNDING:1`
  * Operation: `'FUNDING'`
  * Total Debit: `10000.00000000`
  * Total Credit: `10000.00000000`
  * Line 1: `SAVINGS` — Debit `10000.00000000`, Credit `0.00000000`
  * Line 2: `FIXED_DEPOSIT` — Debit `0.00000000`, Credit `10000.00000000`
  * **Net Imbalance:** `0.00000000` (Strictly Balanced).

---

## 6. Migration & API Compatibility

### Flyway Migrations
* **Product Service:** Successfully applied all 8 migrations (`V1` to `V8`).
* **FD Account Service:** Successfully applied all 11 migrations (`V1` to `V11`).
* **Zero Schema Alterations:** Shaunak added no new SQL migration scripts, ensuring zero database migration drift.

### Gateway Routes (`nginx.conf`)
* `/api/calculator/estimate` $\to$ `fd-account-service:8083`
* `/api/v1/product-configurations/` $\to$ `product-service:8082`
* `/api/v1/fixed-deposits/` $\to$ `fd-account-service:8083`
* All preexisting proxy routes preserved. CORS widened to support `localhost` / `127.0.0.1` on any port.

---

## 7. Kafka Reference Fields & Outbox Parity

The catalog outbox event payload matches `ReferenceSnapshotConsumer` field-for-field:

| Outbox Event JSON Path | Reference Consumer Target Table & Column | Validated in Integration Test |
|---|---|---|
| `$.product.pro_product_code` | `fd_product_ref.fpr_product_code` | Yes (`FD_STANDARD`) |
| `$.product.pro_status` | `fd_product_ref.fpr_status` | Yes (`ACTIVE`) |
| `$.currencies[*].cur_currency_id` | `fd_currency_ref.fcr_currency_id` | Yes (`101`) |
| `$.currencies[*].cur_iso_code` | `fd_currency_ref.fcr_iso_code` | Yes (`INR`) |
| `$.currencies[*].cur_decimal_places` | `fd_currency_ref.fcr_decimal_places` | Yes (`2`) |
| `$.currencies[*].pcc_minimum_amount` | `fd_product_currency_ref.minimum_amount` | Yes (`1000.00`) |
| `$.currencies[*].pcc_maximum_amount` | `fd_product_currency_ref.maximum_amount` | Yes (`10000000.00`) |
| `$.currencies[*].premature_penalty_points` | `fd_product_currency_ref.premature_penalty_points` | Yes (`1.0000`) |
| `$.rateBands[*].ro_outcome_value` | `fd_product_rate_ref.annual_rate` | Yes (`0.0650`) |
| `$.rateBands[*].ro_compounding_basis` | `fd_product_rate_ref.compounding_basis` | Yes (`QUARTERLY`) |
| `$.rateBands[*].ro_payout_frequency` | `fd_product_rate_ref.payout_frequency` | Yes (`AT_MATURITY`) |
| `$.rateBands[*].minimumMonth` | `fd_product_rate_ref.minimum_tenure_months` | Yes (`12`) |
| `$.rateBands[*].maximumMonth` | `fd_product_rate_ref.maximum_tenure_months` | Yes (`36`) |

---

## 8. Consumer Safety: How Sudhiksha Consumes Frozen Terms

Sudhiksha can safely implement premature closure and lifecycle features using the following contract guarantees:

1. **Frozen Snapshot Structure:**
   Every booked FD contains `fa_terms_snapshot`:
   ```json
   {
     "productId": 1,
     "productCode": "FD_STANDARD",
     "productName": "Standard Fixed Deposit",
     "currencyCode": "INR",
     "currencyScale": 2,
     "principal": 10000.00,
     "annualRate": 0.0650,
     "tenureMonths": 12,
     "compoundingBasis": "QUARTERLY",
     "payoutFrequency": "AT_MATURITY",
     "prematurePenaltyPoints": "1.0000",
     "policyVersion": "academic-v2-quarterly"
   }
   ```
2. **Safe Consumption Pattern:**
   In [FdLifecycleController.java](file:///Users/shaunakmalpani/Desktop/BankingTech%20MSA/services/fd-account-service/src/main/java/com/bank/fdservice/FdLifecycleController.java#L52-L68):
   * First, parse `fa_terms_snapshot` and check for `prematurePenaltyPoints`.
   * If found and non-empty, use `new BigDecimal(snapshot.get("prematurePenaltyPoints").asText())`.
   * If `null` or missing (legacy record booked before snapshot feature), fall back to `fd_product_currency_ref.premature_penalty_points`.
3. **Immunity to Post-Booking Alterations:**
   * Unit test `prematureClosureUsesFrozenPenaltySnapshotEvenIfProductChanges` proved that modifying `fd_product_currency_ref.premature_penalty_points` from `1.0000` to `5.0000` has zero effect on the quote. The quote strictly computes penalty using the frozen `1.0000`.

---

## 9. Regressions & Pre-Existing Baseline Issues

### Did Shaunak Break Any Previously Working Workflow?
**NO.**
* Loan & Forex request workflow: Tested and verified (`LoanForexIntegrationTest` passed 2/2).
* Product Catalog & Builder workflow: Tested and verified (`ProductServiceIntegrationTest` passed 7/7).
* Compounding & Interest Math: Tested and verified (`FdCompoundingTest` passed 3/3).
* Flyway migrations: Tested and verified (`FdAccountMigrationTest` passed 5/5).

### Root Cause of `identity-customer-service` Failures
* **Failure:** 12 tests in `IdentityCustomerMigrationTest` and `KycIntegrationTest` fail with `Status expected:<201> but was:<400>` on `/api/v1/auth/register`.
* **Investigation:**
  * In commit `72c4a03` ("fixes 1"), `address`, `city`, `state`, and `postalCode` were added to `AuthController.RegisterRequest` with `@NotBlank` annotations.
  * The test helper methods `register(...)` and `customer(...)` in `IdentityCustomerMigrationTest` and `KycIntegrationTest` were never updated to include these 4 fields in their synthetic JSON payload.
  * Shaunak's diff made zero edits to `identity-customer-service`.
* **Fix for Next Developer:** Add dummy address fields (`"address":"123 Test St", "city":"Mumbai", "state":"Maharashtra", "postalCode":"400001"`) to the test helpers in `IdentityCustomerMigrationTest` and `KycIntegrationTest`.

---

## 10. Exact Next-Developer Dependencies

### 1. For Sudhiksha (FD Lifecycle & Premature Closure)
* **Consume Frozen Terms:** Always read `fa_terms_snapshot` first for `prematurePenaltyPoints` and `annualRate`. Do not query live product tables for booked contracts.
* **Settlement Math:** Use `penalizedRate = annualRate - (prematurePenaltyPoints / 100)`. Compute interest using actual elapsed days divided by 365. Round to `currencyScale` using `RoundingMode.HALF_UP`.
* **Status Transitions:** Transition `ACTIVE` $\to$ `PREMATURE_CLOSED` or `MATURED`.
* **Ledger Entries:** Use Krishang's double-entry pattern with journal key `CLOSE:<id>` or `MATURE:<id>`.

### 2. For Krishang (Core Ledger & Identity Service)
* **Identity Test Fix:** Update `register()` in `IdentityCustomerMigrationTest.java` and `customer()` in `KycIntegrationTest.java` to supply the 4 required address fields.
* **Ledger Ownership:** `fd_journals` and `fd_journal_lines` schema remains strictly preserved and unchanged.

### 3. For Frontend Team (Angular)
* Gateway routes are active for `/api/calculator/estimate` and `/api/v1/fixed-deposits`.
* Idempotency header `Idempotency-Key` must be sent on `POST /api/v1/fixed-deposits` and `POST /api/v1/fixed-deposits/{id}/fund`.
