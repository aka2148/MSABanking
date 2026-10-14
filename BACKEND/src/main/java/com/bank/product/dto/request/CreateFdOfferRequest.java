package com.bank.product.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** Staff-authored FD offer configuration. Rates are decimal fractions (0.0650 = 6.50%). */
public record CreateFdOfferRequest(
        @NotBlank @Size(max = 32) String productCode,
        @NotBlank @Size(max = 255) String productName,
        @NotNull @Min(1) Integer categoryId,
        @Size(max = 1000) String description,
        @Pattern(regexp = "USD|INR|KWD") String baseCurrencyCode,
        @NotEmpty @Valid List<CurrencySchedule> currencies) {

    public record CurrencySchedule(
            @NotBlank @Pattern(regexp = "USD|INR|KWD") String currencyCode,
            @NotNull @DecimalMin(value = "0.01") BigDecimal minimumAmount,
            @NotNull @DecimalMin(value = "0.01") BigDecimal maximumAmount,
            @NotEmpty @Valid List<TenureRateBand> rateBands) {
    }

    public record TenureRateBand(
            @NotNull @Min(1) Integer minimumTenureMonths,
            @NotNull @Min(1) Integer maximumTenureMonths,
            @NotNull @DecimalMin(value = "0.0000") @DecimalMax(value = "1.0000") BigDecimal annualRate) {
    }
}
