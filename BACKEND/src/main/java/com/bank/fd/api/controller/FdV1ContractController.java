package com.bank.fd.api.controller;

import com.bank.common.exception.InvalidInputException;
import com.bank.common.exception.ResourceNotFoundException;
import com.bank.fd.api.dto.ApiErrorResponse;
import com.bank.fd.api.dto.AccrualResponse;
import com.bank.fd.api.dto.ClosureResponse;
import com.bank.fd.api.dto.CreateAccountContractRequest;
import com.bank.fd.api.dto.AccountResponse;
import com.bank.fd.api.dto.BookingRequest;
import com.bank.fd.api.dto.FundingRequest;
import com.bank.fd.api.dto.FundingResponse;
import com.bank.fd.api.dto.HistoryEntry;
import com.bank.fd.api.dto.PrematureClosureResponse;
import com.bank.fd.application.AccrualService;
import com.bank.fd.application.AccountBookingService;
import com.bank.fd.application.ClosureService;
import com.bank.fd.application.FdAccessPolicy;
import com.bank.fd.application.FdActorResolver;
import com.bank.fd.application.FundingService;
import com.bank.fd.application.HistoryService;
import com.bank.fd.application.MaturityService;
import com.bank.fd.application.PrematureClosureService;
import com.bank.fd.application.ProductOfferQueryService;
import com.bank.fd.domain.FdTimePolicy;
import com.bank.fd.domain.PrematurePenalty;
import com.bank.fd.entity.FdAccount;
import com.bank.fd.entity.FdPrematureClosure;
import com.bank.fd.repository.FdAccountRepository;
import com.bank.fd.repository.FdCurrencyReferenceRepository;
import com.bank.fd.repository.FdPrematureClosureRepository;
import com.bank.fd.repository.FdProductReferenceRepository;
import com.bank.fd.repository.TransactionTypeRepository;
import com.bank.fd.api.dto.UpdateAccountContractRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * FD v1 contract boundary: Phase 2 product, booking, and funding routes are
 * implemented while later lifecycle operations remain explicit placeholders.
 */
@RestController
@RequestMapping("/fd/v1")
@PreAuthorize("isAuthenticated()")
public class FdV1ContractController {
    private final ProductOfferQueryService offerQueryService;
    private final AccountBookingService bookingService;
    private final FundingService fundingService;
    private final AccrualService accrualService;
    private final MaturityService maturityService;
    private final ClosureService closureService;
    private final PrematureClosureService prematureClosureService;
    private final HistoryService historyService;
    private final FdAccountRepository accountRepository;
    private final FdProductReferenceRepository productRepository;
    private final FdCurrencyReferenceRepository currencyRepository;
    private final FdPrematureClosureRepository prematureClosureRepository;
    private final TransactionTypeRepository transactionTypeRepository;
    private final FdActorResolver actorResolver;
    private final FdAccessPolicy accessPolicy;
    private final FdTimePolicy timePolicy;

    public FdV1ContractController() {
        this.offerQueryService = null;
        this.bookingService = null;
        this.fundingService = null;
        this.accrualService = null;
        this.maturityService = null;
        this.closureService = null;
        this.prematureClosureService = null;
        this.historyService = null;
        this.accountRepository = null;
        this.productRepository = null;
        this.currencyRepository = null;
        this.prematureClosureRepository = null;
        this.transactionTypeRepository = null;
        this.actorResolver = null;
        this.accessPolicy = null;
        this.timePolicy = null;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public FdV1ContractController(ProductOfferQueryService offerQueryService,
                                  AccountBookingService bookingService, FundingService fundingService,
                                  AccrualService accrualService, MaturityService maturityService,
                                  ClosureService closureService,
                                  PrematureClosureService prematureClosureService,
                                  HistoryService historyService,
                                  FdAccountRepository accountRepository,
                                  FdProductReferenceRepository productRepository,
                                  FdCurrencyReferenceRepository currencyRepository,
                                  FdPrematureClosureRepository prematureClosureRepository,
                                  TransactionTypeRepository transactionTypeRepository,
                                  FdActorResolver actorResolver,
                                  FdAccessPolicy accessPolicy,
                                  FdTimePolicy timePolicy) {
        this.offerQueryService = offerQueryService;
        this.bookingService = bookingService;
        this.fundingService = fundingService;
        this.accrualService = accrualService;
        this.maturityService = maturityService;
        this.closureService = closureService;
        this.prematureClosureService = prematureClosureService;
        this.historyService = historyService;
        this.accountRepository = accountRepository;
        this.productRepository = productRepository;
        this.currencyRepository = currencyRepository;
        this.prematureClosureRepository = prematureClosureRepository;
        this.transactionTypeRepository = transactionTypeRepository;
        this.actorResolver = actorResolver;
        this.accessPolicy = accessPolicy;
        this.timePolicy = timePolicy;
    }

    private ResponseEntity<ApiErrorResponse> notImplemented(String operationId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .header("X-Operation-Id", operationId)
                .body(new ApiErrorResponse("NOT_IMPLEMENTED", operationId + " has no business implementation yet."));
    }

    // Accounts
    @PostMapping("/accounts")
    @PreAuthorize("hasAnyRole('CUSTOMER','BANK_OFFICER','ADMIN')")
    public ResponseEntity<AccountResponse> createAccount(
            @Valid @RequestBody BookingRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            Authentication authentication) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(bookingService.book(authentication.getName(), request, idempotencyKey));
    }

    @PostMapping("/accounts/{accountId}/fund")
    @PreAuthorize("hasAnyRole('CUSTOMER','BANK_OFFICER','ADMIN')")
    public ResponseEntity<FundingResponse> fundAccount(@PathVariable Long accountId,
                                                        @Valid @RequestBody FundingRequest request,
                                                        @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                        Authentication authentication) {
        return ResponseEntity.ok(fundingService.fund(accountId, authentication.getName(), request, idempotencyKey));
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
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AccountResponse> getAccount(@PathVariable Long accountId,
                                                      Authentication authentication) {
        FdAccount account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("FD account not found"));
        accessPolicy.requireOwned(authentication.getName(), account, authentication);
        String productCode = productRepository.findById(account.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("FD product reference not found"))
                .getProductCode();
        String currencyCode = currencyRepository.findById(account.getCurrencyId())
                .orElseThrow(() -> new ResourceNotFoundException("FD account currency not found"))
                .getIsoCode();
        return ResponseEntity.ok(AccountResponse.status(account, productCode, currencyCode));
    }

    @PatchMapping("/accounts/{accountId}")
    public ResponseEntity<ApiErrorResponse> updateAccount(
            @PathVariable Long accountId,
            @Valid @RequestBody UpdateAccountContractRequest request) {
        return notImplemented("updateAccount");
    }

    @PostMapping("/accounts/{accountId}/activate")
    public ResponseEntity<ApiErrorResponse> activateAccount(@PathVariable Long accountId) { return notImplemented("activateAccount"); }

    @PostMapping("/accounts/{accountId}/mature")
    @PreAuthorize("hasAnyRole('CUSTOMER','BANK_OFFICER','ADMIN')")
    public ResponseEntity<AccrualResponse> matureAccount(@PathVariable Long accountId,
                                                          @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                          Authentication authentication) {
        return ResponseEntity.ok(maturityService.processMaturity(accountId, authentication.getName(), idempotencyKey));
    }

    @PostMapping("/accounts/{accountId}/renew")
    public ResponseEntity<ApiErrorResponse> renewAccount(@PathVariable Long accountId, @RequestBody(required = false) Map<String, Object> body) { return notImplemented("renewAccount"); }

    @PostMapping("/accounts/{accountId}/close")
    @PreAuthorize("hasAnyRole('CUSTOMER','BANK_OFFICER','ADMIN')")
    public ResponseEntity<ClosureResponse> closeAccount(@PathVariable Long accountId,
                                                         @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                         Authentication authentication) {
        return ResponseEntity.ok(closureService.close(accountId, authentication.getName(), idempotencyKey));
    }

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
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<HistoryEntry>> listTransactions(@PathVariable Long accountId,
                                                               @RequestParam(required = false, name = "from_date") String fromDate,
                                                               @RequestParam(required = false, name = "to_date") String toDate,
                                                               @RequestParam(required = false, name = "txn_type_id") Long transactionTypeId,
                                                               Authentication authentication) {
        List<HistoryEntry> timeline = historyService.timeline(accountId, authentication.getName());
        LocalDate from = parseDateParam(fromDate, "from_date");
        LocalDate to = parseDateParam(toDate, "to_date");
        String wantedType = transactionTypeId == null ? null : resolveEntryTypeForTxnTypeId(transactionTypeId);
        List<HistoryEntry> filtered = new ArrayList<>();
        for (HistoryEntry entry : timeline) {
            LocalDate entryDay = entry.entryDate() == null ? null : entry.entryDate().toLocalDate();
            if (from != null && (entryDay == null || entryDay.isBefore(from))) {
                continue;
            }
            if (to != null && (entryDay == null || entryDay.isAfter(to))) {
                continue;
            }
            if (wantedType != null && !wantedType.equals(entry.entryType())) {
                continue;
            }
            if (transactionTypeId != null && wantedType == null) {
                continue;
            }
            filtered.add(entry);
        }
        return ResponseEntity.ok(filtered);
    }

    @GetMapping("/transaction-types")
    public ResponseEntity<ApiErrorResponse> listTransactionTypes() { return notImplemented("listTransactionTypes"); }

    @PostMapping("/accounts/{accountId}/accrual/run")
    @PreAuthorize("hasAnyRole('CUSTOMER','BANK_OFFICER','ADMIN')")
    public ResponseEntity<AccrualResponse> runAccrual(@PathVariable Long accountId,
                                                       @RequestBody(required = false) Map<String, Object> body,
                                                       @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                       Authentication authentication) {
        return ResponseEntity.ok(accrualService.runAccrual(accountId, authentication.getName(),
                extractBusinessDate(body), idempotencyKey));
    }

    @GetMapping("/accounts/{accountId}/accrual")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<java.util.List<AccrualResponse>> listAccrual(@PathVariable Long accountId,
                                                                       Authentication authentication) {
        return ResponseEntity.ok(accrualService.listAccruals(accountId, authentication.getName()));
    }

    @GetMapping("/accounts/{accountId}/accrual/cumulative")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AccrualResponse> getCumulativeAccrual(@PathVariable Long accountId,
                                                                Authentication authentication) {
        return ResponseEntity.ok(accrualService.cumulative(accountId, authentication.getName()));
    }

    private java.time.LocalDate extractBusinessDate(Map<String, Object> body) {
        if (body != null) {
            Object raw = body.containsKey("business_date") ? body.get("business_date") : body.get("accrual_date");
            if (raw instanceof String text && !text.isBlank()) {
                try {
                    return java.time.LocalDate.parse(text.trim());
                } catch (java.time.format.DateTimeParseException ex) {
                    throw new InvalidInputException("business_date must be ISO-8601 (yyyy-MM-dd)");
                }
            }
        }
        return timePolicy.businessDate();
    }

    /** Parses an optional yyyy-MM-dd query filter; malformed values are 400, never 500. */
    private LocalDate parseDateParam(String raw, String paramName) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (java.time.format.DateTimeParseException ex) {
            throw new InvalidInputException(paramName + " must be ISO-8601 (yyyy-MM-dd)");
        }
    }

    /** Extracts optional free-form reason text; non-text values are 400. Amounts stay server-computed. */
    private String extractReason(Map<String, Object> body) {
        if (body == null || !body.containsKey("reason") || body.get("reason") == null) {
            return null;
        }
        Object reason = body.get("reason");
        if (!(reason instanceof String text)) {
            throw new InvalidInputException("reason must be text");
        }
        return text;
    }

    /** Maps one stored closure row to its typed response; PENDING reads use the request shape. */
    private PrematureClosureResponse toPrematureClosureResponse(FdAccount account,
                                                                FdPrematureClosure closure,
                                                                String currencyCode) {
        java.math.BigDecimal penalizedRate = null;
        if (account.getInterestRate() != null && closure.getPenaltyPctApplied() != null) {
            penalizedRate = PrematurePenalty.penalizedRate(
                    account.getInterestRate(), closure.getPenaltyPctApplied());
        }
        if ("PENDING".equalsIgnoreCase(closure.getStatus())) {
            return PrematureClosureResponse.requested(closure, penalizedRate, currencyCode);
        }
        return PrematureClosureResponse.approved(closure, penalizedRate, currencyCode);
    }

    /**
     * Maps a transaction-type id to the HistoryEntry label HistoryService
     * produces for it, or null when the id matches no known code (the
     * timeline then filters to an empty list). HistoryEntry carries no
     * txn_type_id, so the mapping reuses the same code-to-label table.
     */
    private String resolveEntryTypeForTxnTypeId(Long transactionTypeId) {
        String[][] codesToLabels = {
                {"DEPOSIT", "FUNDING"},
                {"INTEREST_ACCRUAL", "ACCRUAL"},
                {"MATURITY", "MATURITY"},
                {"CLOSURE", "CLOSURE"},
                {"PENALTY", "PENALTY"},
        };
        for (String[] mapping : codesToLabels) {
            if (transactionTypeRepository.findIdByCode(mapping[0])
                    .map(transactionTypeId::equals).orElse(false)) {
                return mapping[1];
            }
        }
        return null;
    }

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
    @PreAuthorize("hasAnyRole('CUSTOMER','BANK_OFFICER','ADMIN')")
    public ResponseEntity<PrematureClosureResponse> requestPrematureClosure(@PathVariable Long accountId,
                                                                             @RequestBody(required = false) Map<String, Object> body,
                                                                             @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                                             Authentication authentication) {
        if (body != null) {
            extractBusinessDate(body);
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(prematureClosureService
                .requestPrematureClosure(accountId, authentication.getName(), extractReason(body), idempotencyKey));
    }

    @GetMapping("/accounts/{accountId}/premature-closure")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PrematureClosureResponse>> getPrematureClosure(@PathVariable Long accountId,
                                                                              Authentication authentication) {
        FdAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("FD account not found"));
        accessPolicy.requireOwned(authentication.getName(), account, authentication);
        String currencyCode = currencyRepository.findById(account.getCurrencyId())
                .orElseThrow(() -> new ResourceNotFoundException("FD account currency not found"))
                .getIsoCode();
        List<PrematureClosureResponse> responses = new ArrayList<>();
        for (FdPrematureClosure closure
                : prematureClosureRepository.findByAccountIdOrderByClosureDateAscIdAsc(accountId)) {
            responses.add(toPrematureClosureResponse(account, closure, currencyCode));
        }
        return ResponseEntity.ok(responses);
    }

    @PostMapping("/accounts/{accountId}/premature-closure/{closureId}/approve")
    @PreAuthorize("hasAnyRole('CUSTOMER','BANK_OFFICER','ADMIN')")
    public ResponseEntity<PrematureClosureResponse> approvePrematureClosure(@PathVariable Long accountId,
                                                                             @PathVariable Long closureId,
                                                                             @RequestBody(required = false) Map<String, Object> body,
                                                                             @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
                                                                             Authentication authentication) {
        if (body != null) {
            extractBusinessDate(body);
        }
        return ResponseEntity.ok(prematureClosureService
                .approvePrematureClosure(accountId, closureId, authentication.getName(), idempotencyKey));
    }

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
