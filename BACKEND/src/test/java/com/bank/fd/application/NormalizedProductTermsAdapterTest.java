package com.bank.fd.application;

import com.bank.fd.api.dto.BookingRequest;
import com.bank.fd.api.dto.FundingRequest;
import com.bank.fd.api.dto.ProductOfferResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NormalizedProductTermsAdapterTest {

    private static final NormalizedProductTermsAdapter.NormalizedOffer VALID_OFFER =
            new NormalizedProductTermsAdapter.NormalizedOffer(
                    7L, "FD-12", "Academic Fixed Deposit", "USD", true,
                    new BigDecimal("100.00"), new BigDecimal("100000.00"), 3, 24,
                    new BigDecimal("0.0650"), "ANNUAL", "AT_MATURITY", 42L,
                    "UNSUPPORTED");

    @Test
    void mapsOneActiveNormalizedOfferToValidatedProductTerms() {
        var adapter = new NormalizedProductTermsAdapter((product, currency, date) -> List.of(VALID_OFFER));

        ProductTerms terms = adapter.resolveActiveTerms(
                new ProductTermsQuery(7L, new BigDecimal("1000.00"), 12, "usd"),
                LocalDate.of(2026, 9, 19));

        assertEquals("FD-12", terms.productCode());
        assertEquals("USD", terms.currencyCode());
        assertEquals(new BigDecimal("0.0650"), terms.annualRate());
        assertEquals(42L, terms.appliedRateOutcomeId());
    }

    @Test
    void choosesTheSingleRateTierMatchingBookedTenure() {
        var shortTier = new NormalizedProductTermsAdapter.NormalizedOffer(
                7L, "FD-12", "Academic Fixed Deposit", "USD", true,
                new BigDecimal("100.00"), new BigDecimal("100000.00"), 3, 5,
                new BigDecimal("0.0400"), "QUARTERLY", "AT_MATURITY", 51L, "NONE");
        var longTier = new NormalizedProductTermsAdapter.NormalizedOffer(
                7L, "FD-12", "Academic Fixed Deposit", "USD", true,
                new BigDecimal("100.00"), new BigDecimal("100000.00"), 6, 12,
                new BigDecimal("0.0500"), "QUARTERLY", "AT_MATURITY", 52L, "NONE");
        var adapter = new NormalizedProductTermsAdapter((product, currency, date) -> List.of(shortTier, longTier));

        ProductTerms terms = adapter.resolveActiveTerms(
                new ProductTermsQuery(7L, new BigDecimal("1000.00"), 8, "USD"), LocalDate.of(2026, 1, 1));

        assertEquals(new BigDecimal("0.0500"), terms.annualRate());
        assertEquals(52L, terms.appliedRateOutcomeId());
        assertEquals(6, terms.minimumTenureMonths());
        assertEquals(12, terms.maximumTenureMonths());
    }

    @Test
    void rejectsUncoveredOrAmbiguousTenureBands() {
        var secondTier = new NormalizedProductTermsAdapter.NormalizedOffer(
                7L, "FD-12", "Academic Fixed Deposit", "USD", true,
                new BigDecimal("100.00"), new BigDecimal("100000.00"), 8, 12,
                new BigDecimal("0.0500"), "QUARTERLY", "AT_MATURITY", 52L, "NONE");
        var adapter = new NormalizedProductTermsAdapter((product, currency, date) -> List.of(VALID_OFFER, secondTier));

        assertThrows(NormalizedProductTermsAdapter.ProductTermsResolutionException.class,
                () -> adapter.resolveActiveTerms(new ProductTermsQuery(7L,
                        new BigDecimal("1000.00"), 8, "USD"), LocalDate.now()));
    }

    @Test
    void failsClosedForMissingAmbiguousInactiveAndOutOfRangeTerms() {
        assertThrows(NormalizedProductTermsAdapter.ProductTermsResolutionException.class,
                () -> adapter(List.of()).resolveActiveTerms(query(), LocalDate.now()));
        assertThrows(NormalizedProductTermsAdapter.ProductTermsResolutionException.class,
                () -> adapter(List.of(VALID_OFFER, VALID_OFFER)).resolveActiveTerms(query(), LocalDate.now()));
        assertThrows(NormalizedProductTermsAdapter.ProductTermsResolutionException.class,
                () -> adapter(List.of(VALID_OFFER.withActive(false))).resolveActiveTerms(query(), LocalDate.now()));
        assertThrows(NormalizedProductTermsAdapter.ProductTermsResolutionException.class,
                () -> adapter(List.of(VALID_OFFER)).resolveActiveTerms(
                        new ProductTermsQuery(7L, new BigDecimal("99.99"), 12, "USD"), LocalDate.now()));
    }

    @Test
    void contractsExcludeCallerControlledFinancialAndIdentityFields() {
        assertEquals(List.of("productId", "principalAmount", "tenureMonths", "currencyCode"),
                List.of(BookingRequest.class.getRecordComponents()).stream().map(java.lang.reflect.RecordComponent::getName).toList());
        assertEquals(List.of("amount", "currencyCode"),
                List.of(FundingRequest.class.getRecordComponents()).stream().map(java.lang.reflect.RecordComponent::getName).toList());
        assertEquals(5, ProductOfferResponse.class.getRecordComponents().length);
    }

    private static ProductTermsQuery query() {
        return new ProductTermsQuery(7L, new BigDecimal("1000.00"), 12, "USD");
    }

    private static NormalizedProductTermsAdapter adapter(
            List<NormalizedProductTermsAdapter.NormalizedOffer> offers) {
        return new NormalizedProductTermsAdapter((product, currency, date) -> offers);
    }
}
