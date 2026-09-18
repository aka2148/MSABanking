package com.bank.fd.application;

import java.time.LocalDate;

/** Server-authoritative boundary for active product and normalized rule terms. */
public interface ProductTermsPort {

    ProductTerms resolveActiveTerms(ProductTermsQuery query, LocalDate effectiveDate);
}
