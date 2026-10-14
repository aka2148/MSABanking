package com.bank.fd.application;

import com.bank.common.exception.InvalidInputException;
import com.bank.common.exception.ResourceNotFoundException;
import com.bank.fd.api.dto.AccountResponse;
import com.bank.fd.api.dto.BookingRequest;
import com.bank.fd.domain.FdCompoundCalculator;
import com.bank.fd.domain.FdCalculationPolicy;
import com.bank.fd.domain.FdTimePolicy;
import com.bank.fd.entity.FdAccount;
import com.bank.fd.entity.FdOperationIdempotency;
import com.bank.fd.repository.FdAccountRepository;
import com.bank.fd.repository.FdCurrencyReferenceRepository;
import com.bank.fd.service.AccountNumberGenerator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/** Owns the authenticated, server-authoritative pending booking transaction. */
@Service
public class AccountBookingService {
    private static final String BRANCH_CODE = "000";
    private static final String POLICY_VERSION = "academic-v1";
    private static final String COMPOUND_POLICY_VERSION = "academic-v2-quarterly";
    private final ProductTermsPort termsPort;
    private final FdActorResolver actorResolver;
    private final FdAccountRepository accountRepository;
    private final AccountNumberGenerator accountNumberGenerator;
    private final FdTimePolicy timePolicy;
    private final FdCalculationPolicy calculationPolicy;
    private final FdIdempotencyGuard idempotencyGuard;
    @Autowired
    private SavingsAccountService savingsAccountService;
    @Autowired
    private FdCurrencyReferenceRepository currencyReferenceRepository;

    @Autowired
    public AccountBookingService(ProductTermsPort termsPort, FdActorResolver actorResolver,
                                 FdAccountRepository accountRepository, AccountNumberGenerator accountNumberGenerator,
                                 FdTimePolicy timePolicy, FdIdempotencyGuard idempotencyGuard) {
        this(termsPort, actorResolver, accountRepository, accountNumberGenerator, timePolicy,
                new FdCalculationPolicy(POLICY_VERSION, "DECIMAL_FRACTION", "ACTUAL_365", 4, 2,
                        RoundingMode.HALF_UP, true, false),
                idempotencyGuard);
    }

    public AccountBookingService(ProductTermsPort termsPort, FdActorResolver actorResolver,
                                 FdAccountRepository accountRepository, AccountNumberGenerator accountNumberGenerator,
                                 FdTimePolicy timePolicy, FdCalculationPolicy calculationPolicy,
                                 FdIdempotencyGuard idempotencyGuard) {
        this.termsPort = termsPort;
        this.actorResolver = actorResolver;
        this.accountRepository = accountRepository;
        this.accountNumberGenerator = accountNumberGenerator;
        this.timePolicy = timePolicy;
        this.calculationPolicy = calculationPolicy;
        this.idempotencyGuard = idempotencyGuard;
    }

    @Transactional
    public AccountResponse book(String username, BookingRequest request) {
        return book(username, request, null);
    }

    @Transactional
    public AccountResponse book(String username, BookingRequest request, String idempotencyKey) {
        if (request == null) throw new IllegalArgumentException("Booking request is required");
        var holder = actorResolver.resolve(username);
        ProductTerms terms = termsPort.resolveActiveTerms(
                new ProductTermsQuery(request.productId(), request.principalAmount(), request.tenureMonths(),
                        request.currencyCode()), timePolicy.businessDate());
        boolean quarterlyCompounding = validateCompounding(terms.compoundingFrequency(), terms.payoutFrequency());

        String actorKey = "fd-user-" + holder.getId();
        String canonical = request.productId() + "|"
                + request.principalAmount().stripTrailingZeros().toPlainString() + "|"
                + request.tenureMonths() + "|" + request.currencyCode().toUpperCase()
                + (request.fundingSavingsAccountId() == null ? "" : "|" + request.fundingSavingsAccountId());
        FdOperationIdempotency claim = idempotencyGuard.claim("BOOKING", actorKey, idempotencyKey,
                FdIdempotencyGuard.sha256Hex(canonical));
        if (claim != null && claim.getAggregateId() != null) {
            FdAccount existing = accountRepository.findById(claim.getAggregateId())
                    .orElseThrow(() -> new ResourceNotFoundException("FD account not found"));
            String[] parts = claim.getResponseReference() == null ? new String[0]
                    : claim.getResponseReference().split("\\|", -1);
            String productCode = parts.length > 0 && !parts[0].isBlank() ? parts[0] : terms.productCode();
            String currencyCode = parts.length > 1 && !parts[1].isBlank() ? parts[1] : terms.currencyCode();
            return AccountResponse.pending(existing, productCode, currencyCode);
        }

        LocalDate openDate = timePolicy.businessDate();
        LocalDate maturityDate = openDate.plusMonths(request.tenureMonths());
        int currencyScale = quarterlyCompounding ? resolveCurrencyScale(terms.currencyCode())
                : calculationPolicy.currencyScale();
        BigDecimal principal;
        try {
            principal = request.principalAmount().setScale(currencyScale, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new InvalidInputException("Principal amount exceeds the supported minor units for " + terms.currencyCode());
        }
        BigDecimal estimate = quarterlyCompounding
                ? FdCompoundCalculator.maturityAmount(principal, terms.annualRate(), openDate, maturityDate,
                        currencyScale).maturityAmount()
                : maturityEstimate(principal, terms.annualRate(), openDate, maturityDate);

        FdAccount account = new FdAccount();
        account.setAccountNumber(accountNumberGenerator.generateNextAccountNumber(BRANCH_CODE));
        account.setPrimaryHolderId(holder.getId());
        account.setProductId(terms.productId());
        account.setAppliedRateOutcomeId(terms.appliedRateOutcomeId());
        account.setPrincipalAmount(principal);
        account.setCurrencyId(resolveCurrencyReferenceId(terms.currencyCode()));
        if (request.fundingSavingsAccountId() != null) {
            savingsAccountService.requireFundingAccount(request.fundingSavingsAccountId(), holder.getId(), account.getCurrencyId());
            account.setFundingSavingsAccountId(request.fundingSavingsAccountId());
        }
        account.setTenureMonths(request.tenureMonths());
        account.setInterestRate(terms.annualRate());
        account.setCompoundingFrequency(terms.compoundingFrequency());
        account.setPayoutFrequency(terms.payoutFrequency());
        account.setAutoRenewal(false);
        account.setOpenDate(openDate.atStartOfDay().atOffset(ZoneOffset.UTC));
        account.setMaturityDate(maturityDate);
        account.setMaturityAmount(estimate);
        account.setPolicyVersion(quarterlyCompounding ? COMPOUND_POLICY_VERSION : calculationPolicy.version());
        FdAccount saved = accountRepository.save(account);
        idempotencyGuard.complete(claim, saved.getId(), terms.productCode() + "|" + terms.currencyCode());
        return AccountResponse.pending(saved, terms.productCode(), terms.currencyCode());
    }

    private long resolveCurrencyReferenceId(String currencyCode) {
        if (currencyReferenceRepository == null) {
            throw new IllegalStateException("Currency reference repository is required for booking");
        }
        return currencyReferenceRepository.findByIsoCode(currencyCode.toUpperCase())
                .orElseThrow(() -> new IllegalArgumentException("Currency reference is not available"))
                .getId();
    }

    private int resolveCurrencyScale(String currencyCode) {
        if (currencyReferenceRepository == null) {
            throw new IllegalStateException("Currency reference repository is required for booking");
        }
        return currencyReferenceRepository.findByIsoCode(currencyCode.toUpperCase())
                .map(reference -> reference.getDecimalPlaces() == null ? 2 : reference.getDecimalPlaces())
                .orElseThrow(() -> new IllegalArgumentException("Currency reference is not available"));
    }

    static BigDecimal maturityEstimate(BigDecimal principal, BigDecimal annualRate,
                                      LocalDate openDate, LocalDate maturityDate) {
        long days = ChronoUnit.DAYS.between(openDate, maturityDate);
        return principal.add(principal.multiply(annualRate)
                        .multiply(BigDecimal.valueOf(days))
                        .divide(BigDecimal.valueOf(365), 12, RoundingMode.HALF_UP))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static boolean validateCompounding(String frequency, String payoutFrequency) {
        if (frequency != null && frequency.equalsIgnoreCase(FdCompoundCalculator.QUARTERLY)) {
            if (payoutFrequency == null || !payoutFrequency.equalsIgnoreCase("AT_MATURITY")) {
                throw new IllegalArgumentException("Quarterly compounding requires payout at maturity");
            }
            return true;
        }
        if (frequency == null || !(frequency.equalsIgnoreCase("SIMPLE")
                || frequency.equalsIgnoreCase("SIMPLE_INTEREST") || frequency.equalsIgnoreCase("NONE"))) {
            throw new IllegalArgumentException("Unsupported FD compounding frequency");
        }
        return false;
    }
}
