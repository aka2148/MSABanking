package com.bank.product.dto.response;

import java.util.List;

public record ProductConfigurationResponse(
        Integer productId,
        Long fdProductId,
        String productCode,
        String productName,
        String family,
        String categoryCode,
        String status,
        String baseCurrencyCode,
        List<String> currencyCodes,
        Integer revision) { }
