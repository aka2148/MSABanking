package com.bank.fd.application;

import com.bank.fd.api.dto.ProductOfferResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Customer-facing projection of active, server-authoritative FD offers. */
@Service
public class ProductOfferQueryService {
    private final ProductTermsPort termsPort;

    public ProductOfferQueryService(ProductTermsPort termsPort) {
        this.termsPort = termsPort;
    }

    public List<ProductOfferResponse> findEligibleOffers(LocalDate effectiveDate) {
        Map<Long, List<ProductTerms>> byProduct = termsPort.listEligibleTerms(effectiveDate).stream()
                .collect(Collectors.groupingBy(ProductTerms::productId));
        return byProduct.values().stream()
                .map(this::offer)
                .sorted(Comparator.comparing(ProductOfferResponse::productCode))
                .toList();
    }

    private ProductOfferResponse offer(List<ProductTerms> terms) {
        ProductTerms first = terms.get(0);
        List<ProductOfferResponse.CurrencyOffer> currencies = terms.stream()
                .collect(Collectors.groupingBy(ProductTerms::currencyCode))
                .entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> currencyOffer(entry.getKey(), entry.getValue()))
                .toList();
        return new ProductOfferResponse(first.productId(), first.productCode(), first.productName(), currencies,
                "Quarterly compounding, Actual/365, opening date included, maturity date excluded; payout at maturity");
    }

    private ProductOfferResponse.CurrencyOffer currencyOffer(String currencyCode, List<ProductTerms> tiers) {
        List<ProductTerms> ordered = tiers.stream()
                .sorted(Comparator.comparingInt(ProductTerms::minimumTenureMonths)).toList();
        ProductTerms first = ordered.get(0);
        ProductTerms last = ordered.get(ordered.size() - 1);
        return new ProductOfferResponse.CurrencyOffer(currencyCode, first.minimumAmount(), first.maximumAmount(),
                first.minimumTenureMonths(), last.maximumTenureMonths(), ordered.stream()
                .map(term -> new ProductOfferResponse.RateTier(term.minimumTenureMonths(),
                        term.maximumTenureMonths(), term.annualRate(), term.compoundingFrequency(),
                        term.payoutFrequency()))
                .toList());
    }
}
