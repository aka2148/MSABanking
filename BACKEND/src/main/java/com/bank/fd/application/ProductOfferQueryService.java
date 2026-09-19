package com.bank.fd.application;

import com.bank.fd.api.dto.ProductOfferResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/** Customer-facing projection of active, server-authoritative FD offers. */
@Service
public class ProductOfferQueryService {
    private final ProductTermsPort termsPort;

    public ProductOfferQueryService(ProductTermsPort termsPort) {
        this.termsPort = termsPort;
    }

    public List<ProductOfferResponse> findEligibleOffers(LocalDate effectiveDate) {
        return termsPort.listEligibleTerms(effectiveDate).stream()
                .map(terms -> new ProductOfferResponse(
                        terms.productId(), terms.productCode(), terms.productName(), terms.currencyCode(),
                        terms.minimumAmount(), terms.maximumAmount(), terms.minimumTenureMonths(),
                        terms.maximumTenureMonths(), terms.annualRate(), terms.compoundingFrequency(),
                        terms.payoutFrequency(), terms.closurePenaltyRule(),
                        "Simple interest, Actual/365, opening date included, maturity date excluded"))
                .toList();
    }
}
