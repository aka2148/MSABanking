package com.bank.fd.api;

import com.bank.fd.api.controller.FdV1ContractController;
import com.bank.fd.api.dto.BookingRequest;
import com.bank.fd.application.FdActorResolver;
import com.bank.fd.application.ProductOfferQueryService;
import com.bank.fd.application.ProductTerms;
import com.bank.fd.application.ProductTermsPort;
import com.bank.fd.domain.FdTimePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FdV1BookingContractTest {
    @Test
    void bookingRequestContainsOnlyCallerChoices() {
        assertEquals(4, BookingRequest.class.getRecordComponents().length);
        assertEquals("productId", BookingRequest.class.getRecordComponents()[0].getName());
    }

    @Test
    void productsAreProjectedFromServerTerms() {
        ProductTermsPort port = new ProductTermsPort() {
            public ProductTerms resolveActiveTerms(com.bank.fd.application.ProductTermsQuery q, LocalDate d) { return null; }
            public List<ProductTerms> listEligibleTerms(LocalDate date) {
                return List.of(new ProductTerms(7L, "FD7", "Academic FD", "USD", new BigDecimal("100"),
                        new BigDecimal("10000"), 3, 12, new BigDecimal("0.0650"), "SIMPLE", "AT_MATURITY", 9L, "NONE"));
            }
        };
        var offers = new ProductOfferQueryService(port).findEligibleOffers(LocalDate.of(2026, 9, 19));
        assertEquals(1, offers.size());
        assertEquals("USD", offers.get(0).currencyCode());
        assertTrue(offers.get(0).maturityEstimateBasis().contains("Actual/365"));
    }

    @Test
    void controllerKeepsAuthenticatedBoundaryAndActorResolverIsAService() {
        assertEquals("isAuthenticated()", FdV1ContractController.class
                .getAnnotation(PreAuthorize.class).value());
        assertNotNull(FdActorResolver.class.getAnnotation(org.springframework.stereotype.Service.class));
    }
}
