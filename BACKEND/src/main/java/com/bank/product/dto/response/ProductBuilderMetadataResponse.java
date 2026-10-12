package com.bank.product.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record ProductBuilderMetadataResponse(
        List<CategoryOption> categories,
        List<CurrencyOption> currencies,
        List<AttributeOption> attributes,
        List<RoleOption> roles,
        List<FactOption> facts,
        List<String> operators,
        List<String> outcomeTypes,
        List<String> calculationMethods) {

    public record CategoryOption(Integer id, String code, String name, Integer parentId, String family) { }
    public record CurrencyOption(Integer id, String code, String name, String symbol, Integer decimalPlaces) { }
    public record AttributeOption(Integer id, Integer categoryId, String key, String dataType, String displayName,
                                  boolean required, List<String> choices, String unit, String defaultValue,
                                  BigDecimal minimumValue, BigDecimal maximumValue, Integer displayOrder,
                                  String behaviorKey, boolean inherited) { }
    public record RoleOption(Integer id, String code, String description) { }
    public record FactOption(String code, String dataType, List<String> operators, boolean executable,
                             String dependency) { }
}
