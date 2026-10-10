package com.bank.fd.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/** Customer-facing product offer with explicit per-currency tenor rate bands. */
public record ProductOfferResponse(
        @JsonProperty("product_id") Long productId,
        @JsonProperty("product_code") String productCode,
        @JsonProperty("product_name") String productName,
        @JsonProperty("currency_offers") List<CurrencyOffer> currencyOffers,
        @JsonProperty("maturity_estimate_basis") String maturityEstimateBasis) {

    public record CurrencyOffer(
            @JsonProperty("currency_code") String currencyCode,
            @JsonProperty("minimum_amount") BigDecimal minimumAmount,
            @JsonProperty("maximum_amount") BigDecimal maximumAmount,
            @JsonProperty("minimum_tenure_months") int minimumTenureMonths,
            @JsonProperty("maximum_tenure_months") int maximumTenureMonths,
            @JsonProperty("rate_tiers") List<RateTier> rateTiers) {
    }

    public record RateTier(
            @JsonProperty("minimum_tenure_months") int minimumTenureMonths,
            @JsonProperty("maximum_tenure_months") int maximumTenureMonths,
            @JsonProperty("annual_rate") BigDecimal annualRate,
            @JsonProperty("compounding_frequency") String compoundingFrequency,
            @JsonProperty("payout_frequency") String payoutFrequency) {
    }
}
