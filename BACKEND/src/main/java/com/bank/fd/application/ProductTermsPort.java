package com.bank.fd.application;

import java.time.LocalDate;
import java.util.List;

/** Server-authoritative boundary for active product and normalized rule terms. */
public interface ProductTermsPort {

    ProductTerms resolveActiveTerms(ProductTermsQuery query, LocalDate effectiveDate);

    default List<ProductTerms> listEligibleTerms(LocalDate effectiveDate) {
        throw new UnsupportedOperationException("Eligible product lookup is not available");
    }
}
