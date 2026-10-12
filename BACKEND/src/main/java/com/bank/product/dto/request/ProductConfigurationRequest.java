package com.bank.product.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ProductConfigurationRequest(
        @NotBlank @Size(max = 32) String productCode,
        @NotBlank @Size(max = 255) String productName,
        @NotNull @Min(1) Integer categoryId,
        @Size(max = 1000) String description,
        LocalDate effectiveFrom,
        LocalDate effectiveTo,
        @NotBlank @Size(max = 3) String baseCurrencyCode,
        @NotEmpty @Valid List<CurrencyConfig> currencies,
        @Valid List<AttributeValue> attributes,
        @Valid List<EligibilityRule> eligibilityRules,
        @NotEmpty @Valid List<PricingBand> pricingBands,
        @Valid List<Charge> charges,
        @Valid List<RoleConstraint> roleConstraints) {

    public record CurrencyConfig(
            @NotBlank @Size(max = 3) String currencyCode,
            @NotNull @DecimalMin("0.00") BigDecimal minimumAmount,
            @NotNull @DecimalMin("0.01") BigDecimal maximumAmount) { }

    public record AttributeValue(@NotNull @Min(1) Integer definitionId, String value) { }

    public record EligibilityRule(
            @NotBlank @Size(max = 150) String name,
            @NotBlank String fact,
            @NotBlank String dataType,
            @NotBlank String operator,
            String valueFrom,
            String valueTo,
            String valueSet,
            String combineLogic) { }

    public record PricingBand(
            @NotBlank @Size(max = 3) String currencyCode,
            @NotBlank String fact,
            @NotNull BigDecimal valueFrom,
            @NotNull BigDecimal valueTo,
            @NotNull @DecimalMin("0.0") BigDecimal rate,
            @NotBlank String outcomeType,
            @NotBlank String rateUnit,
            @NotBlank String calculationMethod) { }

    public record Charge(
            @NotBlank @Size(max = 50) String chargeCode,
            @NotBlank String calculationBasis,
            @Valid @NotEmpty List<ChargeSlabConfig> slabs) { }

    public record ChargeSlabConfig(
            @NotBlank String fact,
            @NotNull BigDecimal valueFrom,
            @NotNull BigDecimal valueTo,
            @NotNull @DecimalMin("0.0") BigDecimal value,
            @NotBlank String unit,
            @NotBlank @Size(max = 3) String currencyCode,
            BigDecimal floor,
            BigDecimal cap) { }

    public record RoleConstraint(
            @NotNull @Min(1) Integer roleTypeId,
            boolean mandatory,
            @Min(0) Integer minimumCount,
            @Min(0) Integer maximumCount) { }
}
