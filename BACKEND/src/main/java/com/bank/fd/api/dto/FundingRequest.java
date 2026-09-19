package com.bank.fd.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/** Exact-principal internal funding command; it does not represent external settlement. */
public record FundingRequest(
        @NotNull @DecimalMin("0.01") @Digits(integer = 14, fraction = 4) BigDecimal amount,
        @NotBlank @JsonProperty("currency_code") String currencyCode) {
}
