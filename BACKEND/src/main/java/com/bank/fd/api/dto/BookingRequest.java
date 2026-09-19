package com.bank.fd.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Caller-authoritative booking choices; identity and financial terms are server-resolved. */
public record BookingRequest(
        @NotNull @Min(1) @JsonProperty("product_id") Long productId,
        @NotNull @DecimalMin("0.01") @Digits(integer = 14, fraction = 4)
        @JsonProperty("principal_amount") BigDecimal principalAmount,
        @NotNull @Min(1) @JsonProperty("tenure_months") Integer tenureMonths,
        @NotBlank @JsonProperty("currency_code") String currencyCode) {
}
