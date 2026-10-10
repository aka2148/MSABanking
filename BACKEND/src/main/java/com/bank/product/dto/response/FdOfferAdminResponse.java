package com.bank.product.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record FdOfferAdminResponse(
        @JsonProperty("fd_product_id") Long fdProductId,
        @JsonProperty("catalog_product_id") Integer catalogProductId,
        @JsonProperty("product_code") String productCode,
        @JsonProperty("product_name") String productName,
        String status,
        @JsonProperty("currency_codes") List<String> currencyCodes) {
}
