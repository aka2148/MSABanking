package com.bank.product.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record ProductPricingPreviewRequest(
        @NotNull @Valid ProductConfigurationRequest configuration,
        Map<String, String> facts) { }
