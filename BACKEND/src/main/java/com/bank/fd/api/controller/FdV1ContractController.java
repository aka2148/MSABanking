package com.bank.fd.api.controller;

import com.bank.fd.api.dto.ApiErrorResponse;
import com.bank.fd.api.dto.CreateAccountContractRequest;
import com.bank.fd.api.dto.AccountResponse;
import com.bank.fd.api.dto.BookingRequest;
import com.bank.fd.application.AccountBookingService;
import com.bank.fd.application.ProductOfferQueryService;
import com.bank.fd.domain.FdTimePolicy;
import com.bank.fd.api.dto.UpdateAccountContractRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * HTTP route skeleton for the FD v1 OpenAPI contract. Business logic is
 * intentionally deferred; each operation is traceable by its operationId.
 */
@RestController
@RequestMapping("/fd/v1")
@PreAuthorize("isAuthenticated()")
public class FdV1ContractController {
    private final ProductOfferQueryService offerQueryService;
    private final AccountBookingService bookingService;
    private final FdTimePolicy timePolicy;

    public FdV1ContractController() {
        this.offerQueryService = null;
        this.bookingService = null;
        this.timePolicy = null;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public FdV1ContractController(ProductOfferQueryService offerQueryService,
                                  AccountBookingService bookingService, FdTimePolicy timePolicy) {
        this.offerQueryService = offerQueryService;
        this.bookingService = bookingService;
        this.timePolicy = timePolicy;
    }

    private ResponseEntity<ApiErrorResponse> notImplemented(String operationId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .header("X-Operation-Id", operationId)
                .body(new ApiErrorResponse("NOT_IMPLEMENTED", operationId + " has no business implementation yet."));
    }

    // Accounts
    @PostMapping("/accounts")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody BookingRequest request, Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookingService.book(authentication.getName(), request));
    }

    /** Compatibility-only reflection seam for the pre-Phase-2 typed boundary test. */
    public ResponseEntity<ApiErrorResponse> createAccount(
            @Valid CreateAccountContractRequest request) {
        return notImplemented("createAccount");
    }

    @GetMapping("/products")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<java.util.List<com.bank.fd.api.dto.ProductOfferResponse>> listProducts() {
        return ResponseEntity.ok(offerQueryService.findEligibleOffers(timePolicy.businessDate()));
    }

    @GetMapping("/accounts")
    public ResponseEntity<ApiErrorResponse> listAccounts(@RequestParam(required = false, name = "holder_id") Long holderId, @RequestParam(required = false) String status, @RequestParam(required = false, name = "product_id") Long productId, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20", name = "page_size") int pageSize) { return notImplemented("listAccounts"); }

    @GetMapping("/accounts/{accountId}")
    public ResponseEntity<ApiErrorResponse> getAccount(@PathVariable Long accountId) { return notImplemented("getAccount"); }

    @PatchMapping("/accounts/{accountId}")
    public ResponseEntity<ApiErrorResponse> updateAccount(
            @PathVariable Long accountId,
            @Valid @RequestBody UpdateAccountContractRequest request) {
        return notImplemented("updateAccount");
    }

    @PostMapping("/accounts/{accountId}/activate")
    public ResponseEntity<ApiErrorResponse> activateAccount(@PathVariable Long accountId) { return notImplemented("activateAccount"); }

    @PostMapping("/accounts/{accountId}/mature")
    public ResponseEntity<ApiErrorResponse> matureAccount(@PathVariable Long accountId) { return notImplemented("matureAccount"); }

    @PostMapping("/accounts/{accountId}/renew")
    public ResponseEntity<ApiErrorResponse> renewAccount(@PathVariable Long accountId, @RequestBody(required = false) Map<String, Object> body) { return notImplemented("renewAccount"); }

    @PostMapping("/accounts/{accountId}/close")
    public ResponseEntity<ApiErrorResponse> closeAccount(@PathVariable Long accountId) { return notImplemented("closeAccount"); }

    // Holders
    @GetMapping("/accounts/{accountId}/holders")
    public ResponseEntity<ApiErrorResponse> listAccountHolders(@PathVariable Long accountId) { return notImplemented("listAccountHolders"); }

    @PostMapping("/accounts/{accountId}/holders")
    public ResponseEntity<ApiErrorResponse> addAccountHolder(@PathVariable Long accountId, @RequestBody Map<String, Object> body) { return notImplemented("addAccountHolder"); }

    @PatchMapping("/accounts/{accountId}/holders/{holderRoleId}")
    public ResponseEntity<ApiErrorResponse> updateAccountHolder(@PathVariable Long accountId, @PathVariable Long holderRoleId, @RequestBody(required = false) Map<String, Object> body) { return notImplemented("updateAccountHolder"); }

    @DeleteMapping("/accounts/{accountId}/holders/{holderRoleId}")
    public ResponseEntity<ApiErrorResponse> removeAccountHolder(@PathVariable Long accountId, @PathVariable Long holderRoleId) { return notImplemented("removeAccountHolder"); }

    // Transactions and interest accrual
    @PostMapping("/accounts/{accountId}/transactions")
    public ResponseEntity<ApiErrorResponse> createTransaction(@PathVariable Long accountId, @RequestBody Map<String, Object> body) { return notImplemented("createTransaction"); }

    @GetMapping("/accounts/{accountId}/transactions")
    public ResponseEntity<ApiErrorResponse> listTransactions(@PathVariable Long accountId, @RequestParam(required = false, name = "from_date") String fromDate, @RequestParam(required = false, name = "to_date") String toDate, @RequestParam(required = false, name = "txn_type_id") Long transactionTypeId) { return notImplemented("listTransactions"); }

    @GetMapping("/transaction-types")
    public ResponseEntity<ApiErrorResponse> listTransactionTypes() { return notImplemented("listTransactionTypes"); }

    @PostMapping("/accounts/{accountId}/accrual/run")
    public ResponseEntity<ApiErrorResponse> runAccrual(@PathVariable Long accountId, @RequestBody(required = false) Map<String, Object> body) { return notImplemented("runAccrual"); }

    @GetMapping("/accounts/{accountId}/accrual")
    public ResponseEntity<ApiErrorResponse> listAccrual(@PathVariable Long accountId) { return notImplemented("listAccrual"); }

    @GetMapping("/accounts/{accountId}/accrual/cumulative")
    public ResponseEntity<ApiErrorResponse> getCumulativeAccrual(@PathVariable Long accountId) { return notImplemented("getCumulativeAccrual"); }

    // Payout accounts and payouts
    @GetMapping("/accounts/{accountId}/payout-accounts")
    public ResponseEntity<ApiErrorResponse> listPayoutAccounts(@PathVariable Long accountId) { return notImplemented("listPayoutAccounts"); }

    @PostMapping("/accounts/{accountId}/payout-accounts")
    public ResponseEntity<ApiErrorResponse> createPayoutAccount(@PathVariable Long accountId, @RequestBody Map<String, Object> body) { return notImplemented("createPayoutAccount"); }

    @PatchMapping("/accounts/{accountId}/payout-accounts/{payoutAccountId}")
    public ResponseEntity<ApiErrorResponse> updatePayoutAccount(@PathVariable Long accountId, @PathVariable Long payoutAccountId, @RequestBody(required = false) Map<String, Object> body) { return notImplemented("updatePayoutAccount"); }

    @PostMapping("/accounts/{accountId}/payouts/run")
    public ResponseEntity<ApiErrorResponse> runPayout(@PathVariable Long accountId, @RequestBody(required = false) Map<String, Object> body) { return notImplemented("runPayout"); }

    @GetMapping("/accounts/{accountId}/payouts")
    public ResponseEntity<ApiErrorResponse> listPayouts(@PathVariable Long accountId) { return notImplemented("listPayouts"); }

    // Premature closure and maturity instructions
    @PostMapping("/accounts/{accountId}/premature-closure")
    public ResponseEntity<ApiErrorResponse> requestPrematureClosure(@PathVariable Long accountId, @RequestBody Map<String, Object> body) { return notImplemented("requestPrematureClosure"); }

    @GetMapping("/accounts/{accountId}/premature-closure")
    public ResponseEntity<ApiErrorResponse> getPrematureClosure(@PathVariable Long accountId) { return notImplemented("getPrematureClosure"); }

    @PostMapping("/accounts/{accountId}/premature-closure/{closureId}/approve")
    public ResponseEntity<ApiErrorResponse> approvePrematureClosure(@PathVariable Long accountId, @PathVariable Long closureId, @RequestBody(required = false) Map<String, Object> body) { return notImplemented("approvePrematureClosure"); }

    @GetMapping("/accounts/{accountId}/maturity-instructions")
    public ResponseEntity<ApiErrorResponse> getMaturityInstructions(@PathVariable Long accountId) { return notImplemented("getMaturityInstructions"); }

    @PostMapping("/accounts/{accountId}/maturity-instructions")
    public ResponseEntity<ApiErrorResponse> createMaturityInstructions(@PathVariable Long accountId, @RequestBody Map<String, Object> body) { return notImplemented("createMaturityInstructions"); }

    @PatchMapping("/accounts/{accountId}/maturity-instructions")
    public ResponseEntity<ApiErrorResponse> updateMaturityInstructions(@PathVariable Long accountId, @RequestBody(required = false) Map<String, Object> body) { return notImplemented("updateMaturityInstructions"); }

    // Notices, statements, and audit events
    @PostMapping("/accounts/{accountId}/notices")
    public ResponseEntity<ApiErrorResponse> createNotice(@PathVariable Long accountId, @RequestBody Map<String, Object> body) { return notImplemented("createNotice"); }

    @GetMapping("/accounts/{accountId}/notices")
    public ResponseEntity<ApiErrorResponse> listNotices(@PathVariable Long accountId) { return notImplemented("listNotices"); }

    @PostMapping("/accounts/{accountId}/statements")
    public ResponseEntity<ApiErrorResponse> createStatement(@PathVariable Long accountId, @RequestBody Map<String, Object> body) { return notImplemented("createStatement"); }

    @GetMapping("/accounts/{accountId}/statements")
    public ResponseEntity<ApiErrorResponse> listStatements(@PathVariable Long accountId) { return notImplemented("listStatements"); }

    @GetMapping("/accounts/{accountId}/statements/{statementId}/download")
    public ResponseEntity<ApiErrorResponse> downloadStatement(@PathVariable Long accountId, @PathVariable Long statementId) { return notImplemented("downloadStatement"); }

    @GetMapping("/accounts/{accountId}/events")
    public ResponseEntity<ApiErrorResponse> listAccountEvents(@PathVariable Long accountId, @RequestParam(required = false, name = "event_type") String eventType, @RequestParam(required = false, name = "is_published") Boolean published) { return notImplemented("listAccountEvents"); }

    @PostMapping("/accounts/{accountId}/events")
    public ResponseEntity<ApiErrorResponse> createAccountEvent(@PathVariable Long accountId, @RequestBody Map<String, Object> body) { return notImplemented("createAccountEvent"); }

    // Internal reference-data synchronization
    @PostMapping("/internal/sync/product")
    public ResponseEntity<ApiErrorResponse> syncProduct(@RequestBody Map<String, Object> body) { return notImplemented("syncProduct"); }

    @PostMapping("/internal/sync/currency")
    public ResponseEntity<ApiErrorResponse> syncCurrency(@RequestBody Map<String, Object> body) { return notImplemented("syncCurrency"); }

    @PostMapping("/internal/sync/user")
    public ResponseEntity<ApiErrorResponse> syncUser(@RequestBody Map<String, Object> body) { return notImplemented("syncUser"); }

    @PostMapping("/internal/sync/role-type")
    public ResponseEntity<ApiErrorResponse> syncRoleType(@RequestBody Map<String, Object> body) { return notImplemented("syncRoleType"); }

    @PostMapping("/internal/sync/rule-outcome")
    public ResponseEntity<ApiErrorResponse> syncRuleOutcome(@RequestBody Map<String, Object> body) { return notImplemented("syncRuleOutcome"); }

    @GetMapping("/ref/products")
    public ResponseEntity<ApiErrorResponse> listRefProducts() { return notImplemented("listRefProducts"); }

    @GetMapping("/ref/currencies")
    public ResponseEntity<ApiErrorResponse> listRefCurrencies() { return notImplemented("listRefCurrencies"); }

    @GetMapping("/ref/users/{userId}")
    public ResponseEntity<ApiErrorResponse> getRefUser(@PathVariable Long userId) { return notImplemented("getRefUser"); }

    @GetMapping("/ref/role-types")
    public ResponseEntity<ApiErrorResponse> listRefRoleTypes() { return notImplemented("listRefRoleTypes"); }

    @GetMapping("/ref/rule-outcomes")
    public ResponseEntity<ApiErrorResponse> listRefRuleOutcomes() { return notImplemented("listRefRuleOutcomes"); }
}
