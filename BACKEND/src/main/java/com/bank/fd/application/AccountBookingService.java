package com.bank.fd.application;

import com.bank.fd.api.dto.AccountResponse;
import com.bank.fd.api.dto.BookingRequest;
import com.bank.fd.domain.FdCalculationPolicy;
import com.bank.fd.domain.FdTimePolicy;
import com.bank.fd.entity.FdAccount;
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
    private final ProductTermsPort termsPort;
    private final FdActorResolver actorResolver;
    private final FdAccountRepository accountRepository;
    private final AccountNumberGenerator accountNumberGenerator;
    private final FdTimePolicy timePolicy;
    private final FdCalculationPolicy calculationPolicy;
    @Autowired
    private FdCurrencyReferenceRepository currencyReferenceRepository;

    public AccountBookingService(ProductTermsPort termsPort, FdActorResolver actorResolver,
                                 FdAccountRepository accountRepository, AccountNumberGenerator accountNumberGenerator,
                                 FdTimePolicy timePolicy) {
        this(termsPort, actorResolver, accountRepository, accountNumberGenerator, timePolicy,
                new FdCalculationPolicy(POLICY_VERSION, "DECIMAL_FRACTION", "ACTUAL_365", 4, 2,
                        RoundingMode.HALF_UP, true, false));
    }

    public AccountBookingService(ProductTermsPort termsPort, FdActorResolver actorResolver,
                                 FdAccountRepository accountRepository, AccountNumberGenerator accountNumberGenerator,
                                 FdTimePolicy timePolicy, FdCalculationPolicy calculationPolicy) {
        this.termsPort = termsPort;
        this.actorResolver = actorResolver;
        this.accountRepository = accountRepository;
        this.accountNumberGenerator = accountNumberGenerator;
        this.timePolicy = timePolicy;
        this.calculationPolicy = calculationPolicy;
    }

    @Transactional
    public AccountResponse book(String username, BookingRequest request) {
        if (request == null) throw new IllegalArgumentException("Booking request is required");
        ProductTerms terms = termsPort.resolveActiveTerms(
                new ProductTermsQuery(request.productId(), request.principalAmount(), request.tenureMonths(),
                        request.currencyCode()), timePolicy.businessDate());
        validateCompounding(terms.compoundingFrequency());
        var holder = actorResolver.resolve(username);

        LocalDate openDate = timePolicy.businessDate();
        LocalDate maturityDate = openDate.plusMonths(request.tenureMonths());
        BigDecimal estimate = maturityEstimate(request.principalAmount(), terms.annualRate(), openDate, maturityDate);

        FdAccount account = new FdAccount();
        account.setAccountNumber(accountNumberGenerator.generateNextAccountNumber(BRANCH_CODE));
        account.setPrimaryHolderId(holder.getId());
        account.setProductId(terms.productId());
        account.setAppliedRateOutcomeId(terms.appliedRateOutcomeId());
        account.setPrincipalAmount(request.principalAmount().setScale(calculationPolicy.currencyScale(), RoundingMode.HALF_UP));
        account.setCurrencyId(resolveCurrencyReferenceId(terms.currencyCode()));
        account.setTenureMonths(request.tenureMonths());
        account.setInterestRate(terms.annualRate());
        account.setCompoundingFrequency(terms.compoundingFrequency());
        account.setPayoutFrequency(terms.payoutFrequency());
        account.setAutoRenewal(false);
        account.setOpenDate(openDate.atStartOfDay().atOffset(ZoneOffset.UTC));
        account.setMaturityDate(maturityDate);
        account.setMaturityAmount(estimate);
        account.setPolicyVersion(calculationPolicy.version());
        FdAccount saved = accountRepository.save(account);
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

    static BigDecimal maturityEstimate(BigDecimal principal, BigDecimal annualRate,
                                      LocalDate openDate, LocalDate maturityDate) {
        long days = ChronoUnit.DAYS.between(openDate, maturityDate);
        return principal.add(principal.multiply(annualRate)
                        .multiply(BigDecimal.valueOf(days))
                        .divide(BigDecimal.valueOf(365), 12, RoundingMode.HALF_UP))
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static void validateCompounding(String frequency) {
        if (frequency == null || !(frequency.equalsIgnoreCase("SIMPLE")
                || frequency.equalsIgnoreCase("SIMPLE_INTEREST")
                || frequency.equalsIgnoreCase("NONE"))) {
            throw new IllegalArgumentException("Only simple Actual/365 interest is supported for booking");
        }
    }
}
