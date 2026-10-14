package com.bank.fd.api;

import com.bank.fd.api.controller.FdV1ContractController;
import com.bank.fd.api.dto.BookingRequest;
import com.bank.fd.api.dto.FundingRequest;
import com.bank.fd.api.dto.FundingResponse;
import com.bank.fd.application.FdActorResolver;
import com.bank.fd.application.ProductOfferQueryService;
import com.bank.fd.application.ProductTerms;
import com.bank.fd.application.ProductTermsPort;
import com.bank.fd.domain.FdTimePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FdV1BookingContractTest {
    @Test
    void bookingRequestContainsOnlyCallerChoices() {
        assertEquals(5, BookingRequest.class.getRecordComponents().length);
        assertEquals("fundingSavingsAccountId", BookingRequest.class.getRecordComponents()[4].getName());
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
        assertEquals("USD", offers.get(0).currencyOffers().get(0).currencyCode());
        assertEquals(0, offers.get(0).currencyOffers().get(0).rateTiers().get(0).annualRate()
                .compareTo(new BigDecimal("0.0650")));
        assertTrue(offers.get(0).maturityEstimateBasis().contains("Actual/365"));
    }

    @Test
    void controllerKeepsAuthenticatedBoundaryAndActorResolverIsAService() {
        assertEquals("isAuthenticated()", FdV1ContractController.class
                .getAnnotation(PreAuthorize.class).value());
        assertNotNull(FdActorResolver.class.getAnnotation(org.springframework.stereotype.Service.class));
    }

    @Test
    void fundingRouteIsAuthenticatedAndTyped() throws Exception {
        var method = FdV1ContractController.class.getDeclaredMethod("fundAccount", Long.class,
                FundingRequest.class, String.class, org.springframework.security.core.Authentication.class);
        assertEquals("/accounts/{accountId}/fund", method.getAnnotation(PostMapping.class).value()[0]);
        assertEquals("hasAnyRole('CUSTOMER','BANK_OFFICER','ADMIN')", method.getAnnotation(PreAuthorize.class).value());
        assertEquals(2, FundingResponse.class.getRecordComponents().length - 15);
    }
}
