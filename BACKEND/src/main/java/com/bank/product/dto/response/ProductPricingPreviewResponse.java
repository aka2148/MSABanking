package com.bank.product.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ProductPricingPreviewResponse(
        String status,
        String family,
        String currencyCode,
        String fundingCurrencyCode,
        String destinationCurrencyCode,
        String calculationMethod,
        Map<String, BigDecimal> values,
        List<String> messages) { }
