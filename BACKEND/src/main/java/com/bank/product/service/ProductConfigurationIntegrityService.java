package com.bank.product.service;

import com.bank.common.exception.InvalidInputException;
import com.bank.common.exception.ResourceNotFoundException;
import com.bank.product.dto.request.ProductConfigurationRequest;
import com.bank.product.entity.AttributeDefinition;
import com.bank.product.entity.ChargeConfig;
import com.bank.product.entity.ChargeSlab;
import com.bank.product.entity.Currency;
import com.bank.product.entity.Product;
import com.bank.product.entity.ProductAttributeValue;
import com.bank.product.entity.ProductCategory;
import com.bank.product.entity.ProductCurrencyConfig;
import com.bank.product.entity.ProductRoleConfig;
import com.bank.product.entity.ProductRule;
import com.bank.product.entity.RuleCondition;
import com.bank.product.entity.RuleOutcome;
import com.bank.product.repository.AttributeDefinitionRepository;
import com.bank.product.repository.ChargeConfigRepository;
import com.bank.product.repository.ChargeSlabRepository;
import com.bank.product.repository.CurrencyRepository;
import com.bank.product.repository.ProductAttributeValueRepository;
import com.bank.product.repository.ProductCategoryRepository;
import com.bank.product.repository.ProductCurrencyConfigRepository;
import com.bank.product.repository.ProductRepository;
import com.bank.product.repository.ProductRoleConfigRepository;
import com.bank.product.repository.ProductRuleRepository;
import com.bank.product.repository.RuleConditionRepository;
import com.bank.product.repository.RuleOutcomeRepository;
import com.bank.product.repository.RuleTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProductConfigurationIntegrityService {
    private final ProductRepository products;
    private final ProductCategoryRepository categories;
    private final CurrencyRepository currencies;
    private final ProductCurrencyConfigRepository currencyConfigs;
    private final AttributeDefinitionRepository attributeDefinitions;
    private final ProductAttributeValueRepository attributeValues;
    private final ProductRuleRepository rules;
    private final RuleTypeRepository ruleTypes;
    private final RuleConditionRepository conditions;
    private final RuleOutcomeRepository outcomes;
    private final ChargeConfigRepository charges;
    private final ChargeSlabRepository chargeSlabs;
    private final ProductRoleConfigRepository roleConfigs;

    public ProductConfigurationIntegrityService(ProductRepository products,
                                                ProductCategoryRepository categories,
                                                CurrencyRepository currencies,
                                                ProductCurrencyConfigRepository currencyConfigs,
                                                AttributeDefinitionRepository attributeDefinitions,
                                                ProductAttributeValueRepository attributeValues,
                                                ProductRuleRepository rules,
                                                RuleTypeRepository ruleTypes,
                                                RuleConditionRepository conditions,
                                                RuleOutcomeRepository outcomes,
                                                ChargeConfigRepository charges,
                                                ChargeSlabRepository chargeSlabs,
                                                ProductRoleConfigRepository roleConfigs) {
        this.products = products;
        this.categories = categories;
        this.currencies = currencies;
        this.currencyConfigs = currencyConfigs;
        this.attributeDefinitions = attributeDefinitions;
        this.attributeValues = attributeValues;
        this.rules = rules;
        this.ruleTypes = ruleTypes;
        this.conditions = conditions;
        this.outcomes = outcomes;
        this.charges = charges;
        this.chargeSlabs = chargeSlabs;
        this.roleConfigs = roleConfigs;
    }

    @Transactional(readOnly = true)
    public void assertEditable(Integer productId) {
        Product product = products.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        if ("ACTIVE".equals(normalize(product.getStatus()))) {
            throw new InvalidInputException(
                    "Active product terms are immutable; create a supported revision before editing");
        }
    }

    @Transactional(readOnly = true)
    public List<String> persistedConfigurationErrors(Product product, ProductConfigurationRequest request) {
        List<String> errors = new ArrayList<>();
        if (!productState(product).equals(expectedProductState(request))) {
            errors.add("Persisted product identity or effective dates differ from the saved revision");
        }
        if (!actualCurrencies(product).equals(expectedCurrencies(request))) {
            errors.add("Persisted currencies, limits, or base-currency flags differ from the saved revision");
        }
        if (!actualAttributes(product).equals(expectedAttributes(request))) {
            errors.add("Persisted attribute values differ from the saved revision");
        }
        if (!actualRules(product).equals(expectedRules(product, request))) {
            errors.add("Persisted rule conditions, outcomes, rates, or relationships differ from the saved revision");
        }
        if (!actualCharges(product).equals(expectedCharges(request))) {
            errors.add("Persisted charges or charge slabs differ from the saved revision");
        }
        if (!actualRoles(product).equals(expectedRoles(request))) {
            errors.add("Persisted role constraints differ from the saved revision");
        }
        return errors;
    }

    private ProductState productState(Product product) {
        return new ProductState(normalize(product.getProductCode()), text(product.getProductName()),
                product.getCategoryId(), normalize(product.getBaseCurrencyCode()), text(product.getDescription()),
                text(product.getEffectiveFrom()), text(product.getEffectiveTo()));
    }

    private ProductState expectedProductState(ProductConfigurationRequest request) {
        return new ProductState(normalize(request.productCode()), text(request.productName().trim()), request.categoryId(),
                normalize(request.baseCurrencyCode()), text(request.description()),
                request.effectiveFrom() == null ? null : request.effectiveFrom().toString(),
                request.effectiveTo() == null ? null : request.effectiveTo().toString());
    }

    private List<CurrencyState> actualCurrencies(Product product) {
        return currencyConfigs.findByProductId(product.getProductId()).stream()
                .map(config -> new CurrencyState(currencyCode(config.getCurrencyId()), decimal(config.getMinimumAmount()),
                        decimal(config.getMaximumAmount()), Boolean.TRUE.equals(config.getIsBaseCurrency()),
                        Boolean.TRUE.equals(config.getWalletEnabled())))
                .sorted(Comparator.comparing(CurrencyState::currencyCode)).toList();
    }

    private List<CurrencyState> expectedCurrencies(ProductConfigurationRequest request) {
        return request.currencies().stream().map(config -> new CurrencyState(normalize(config.currencyCode()),
                        decimal(config.minimumAmount()), decimal(config.maximumAmount()),
                        normalize(config.currencyCode()).equals(normalize(request.baseCurrencyCode())), false))
                .sorted(Comparator.comparing(CurrencyState::currencyCode)).toList();
    }

    private List<AttributeState> actualAttributes(Product product) {
        return attributeValues.findByProductId(product.getProductId()).stream()
                .map(value -> new AttributeState(value.getAttributeDefId(), attributeValue(value)))
                .sorted(Comparator.comparing(AttributeState::definitionId)).toList();
    }

    private List<AttributeState> expectedAttributes(ProductConfigurationRequest request) {
        if (request.attributes() == null) return List.of();
        return request.attributes().stream().filter(value -> value.value() != null && !value.value().isBlank())
                .map(value -> new AttributeState(value.definitionId(), expectedAttributeValue(value)))
                .sorted(Comparator.comparing(AttributeState::definitionId)).toList();
    }

    private String attributeValue(ProductAttributeValue value) {
        AttributeDefinition definition = attributeDefinitions.findById(value.getAttributeDefId()).orElse(null);
        if (definition == null) return "<missing-definition>";
        return switch (normalize(definition.getDataType())) {
            case "NUMBER" -> decimal(value.getValueNumber());
            case "DATE" -> value.getValueDate() == null ? null : value.getValueDate().toLocalDate().toString();
            default -> value.getValueString();
        };
    }

    private String expectedAttributeValue(ProductConfigurationRequest.AttributeValue value) {
        AttributeDefinition definition = attributeDefinitions.findById(value.definitionId()).orElse(null);
        if (definition == null) return "<missing-definition>";
        return "NUMBER".equals(normalize(definition.getDataType()))
                ? decimal(new BigDecimal(value.value())) : value.value();
    }

    private List<RuleState> actualRules(Product product) {
        Map<Integer, String> typeCodes = ruleTypes.findAll().stream()
                .collect(Collectors.toMap(value -> value.getRuleTypeId(), value -> normalize(value.getRuleTypeCode())));
        return rules.findByProductId(product.getProductId()).stream().map(rule -> new RuleState(
                        typeCodes.getOrDefault(rule.getRuleTypeId(), "<missing-rule-type>"), normalize(rule.getRuleName()),
                        rule.getPriority(), normalize(rule.getCombineLogic()), Boolean.TRUE.equals(rule.getIsActive()),
                        rule.getEffectiveFrom() == null ? null : rule.getEffectiveFrom().toString(),
                        rule.getEffectiveTo() == null ? null : rule.getEffectiveTo().toString(),
                        conditions.findByRuleId(rule.getRuleId()).stream().map(this::conditionState)
                                .sorted(Comparator.comparing(ConditionState::sortKey)).toList(),
                        outcomes.findByRuleId(rule.getRuleId()).stream().map(this::outcomeState)
                                .sorted(Comparator.comparing(OutcomeState::sortKey)).toList()))
                .sorted(Comparator.comparing(RuleState::sortKey)).toList();
    }

    private List<RuleState> expectedRules(Product product, ProductConfigurationRequest request) {
        List<RuleState> expected = new ArrayList<>();
        int priority = 1;
        if (request.eligibilityRules() != null) {
            for (ProductConfigurationRequest.EligibilityRule rule : request.eligibilityRules()) {
                expected.add(new RuleState("ELIGIBILITY", normalize(rule.name()), priority++,
                        normalize(rule.combineLogic() == null ? "AND" : rule.combineLogic()), true, null, null,
                        List.of(new ConditionState(normalize(rule.fact()), normalize(rule.operator()),
                                eligibilityValue(rule.dataType(), rule.valueFrom()),
                                eligibilityValue(rule.dataType(), rule.valueTo()), text(rule.valueSet()))),
                        List.of(new OutcomeState("ELIGIBILITY", decimal(BigDecimal.ONE), "BOOLEAN", null,
                                null, null))));
            }
        }
        priority = 1;
        String family = family(product.getCategoryId());
        Map<String, ProductConfigurationRequest.CurrencyConfig> requestCurrencies = request.currencies().stream()
                .collect(Collectors.toMap(value -> normalize(value.currencyCode()), Function.identity()));
        for (ProductConfigurationRequest.PricingBand band : request.pricingBands()) {
            boolean fixedDeposit = "FIXED_DEPOSIT".equals(family);
            List<ConditionState> expectedConditions = new ArrayList<>();
            expectedConditions.add(new ConditionState(normalize(band.fact()), "BETWEEN_INCLUSIVE",
                    decimal(band.valueFrom()), decimal(band.valueTo()), null));
            if (fixedDeposit) {
                ProductConfigurationRequest.CurrencyConfig config = requestCurrencies.get(normalize(band.currencyCode()));
                expectedConditions.add(new ConditionState("AMOUNT_RANGE", "BETWEEN_INCLUSIVE",
                        decimal(config.minimumAmount()), decimal(config.maximumAmount()), null));
            }
            expected.add(new RuleState(fixedDeposit ? "FD_TENURE_RATE" : "PRICING_BAND",
                    fixedDeposit ? "TENURE_RATE" : "PRICING_BAND", priority++, "AND", true, null, null,
                    expectedConditions.stream().sorted(Comparator.comparing(ConditionState::sortKey)).toList(),
                    List.of(new OutcomeState(normalize(band.outcomeType()), decimal(band.rate()),
                            "DECIMAL_FRACTION", fixedDeposit ? "QUARTERLY" : normalize(band.calculationMethod()),
                            fixedDeposit ? "AT_MATURITY" : null, normalize(band.currencyCode())))));
        }
        return expected.stream().sorted(Comparator.comparing(RuleState::sortKey)).toList();
    }

    private ConditionState conditionState(RuleCondition condition) {
        return new ConditionState(normalize(condition.getConditionAttribute()), normalize(condition.getOperator()),
                numericText(condition.getValueFrom()), numericText(condition.getValueTo()), text(condition.getValueSet()));
    }

    private OutcomeState outcomeState(RuleOutcome outcome) {
        return new OutcomeState(normalize(outcome.getOutcomeType()), decimal(outcome.getOutcomeValue()),
                normalizeNullable(outcome.getUnit()), normalizeNullable(outcome.getCompoundingBasis()),
                normalizeNullable(outcome.getPayoutFrequency()),
                outcome.getCurrencyId() == null ? null : currencyCode(outcome.getCurrencyId()));
    }

    private List<ChargeState> actualCharges(Product product) {
        return charges.findByProductId(product.getProductId()).stream().map(charge -> new ChargeState(
                        normalize(charge.getChargeCode()), normalize(charge.getCalculationBasis()),
                        Boolean.TRUE.equals(charge.getIsActive()), text(charge.getGlCode()),
                        chargeSlabs.findByChargeId(charge.getChargeId()).stream().map(this::slabState)
                                .sorted(Comparator.comparing(SlabState::sortKey)).toList()))
                .sorted(Comparator.comparing(ChargeState::sortKey)).toList();
    }

    private List<ChargeState> expectedCharges(ProductConfigurationRequest request) {
        if (request.charges() == null) return List.of();
        return request.charges().stream().map(charge -> new ChargeState(normalize(charge.chargeCode()),
                        normalize(charge.calculationBasis()), true, null,
                        charge.slabs().stream().map(slab -> new SlabState(normalize(slab.fact()),
                                        decimal(slab.valueFrom()), decimal(slab.valueTo()), decimal(slab.value()),
                                        normalize(slab.unit()), normalize(slab.currencyCode()),
                                        decimal(slab.floor()), decimal(slab.cap())))
                                .sorted(Comparator.comparing(SlabState::sortKey)).toList()))
                .sorted(Comparator.comparing(ChargeState::sortKey)).toList();
    }

    private SlabState slabState(ChargeSlab slab) {
        return new SlabState(normalize(slab.getSlabAttribute()), decimal(slab.getValueFrom()), decimal(slab.getValueTo()),
                decimal(slab.getChargeAmount()), normalize(slab.getChargeUnit()), currencyCode(slab.getCurrencyId()),
                decimal(slab.getMinimumCharge()), decimal(slab.getMaximumCharge()));
    }

    private List<RoleState> actualRoles(Product product) {
        return roleConfigs.findByProductId(product.getProductId()).stream()
                .map(role -> new RoleState(role.getRoleTypeId(), Boolean.TRUE.equals(role.getMandatory()),
                        role.getMinCount(), role.getMaxCount()))
                .sorted(Comparator.comparing(RoleState::roleTypeId)).toList();
    }

    private List<RoleState> expectedRoles(ProductConfigurationRequest request) {
        if (request.roleConstraints() == null) return List.of();
        return request.roleConstraints().stream()
                .map(role -> new RoleState(role.roleTypeId(), role.mandatory(), role.minimumCount(), role.maximumCount()))
                .sorted(Comparator.comparing(RoleState::roleTypeId)).toList();
    }

    private String family(Integer categoryId) {
        Map<Integer, ProductCategory> categoryMap = categories.findAll().stream()
                .collect(Collectors.toMap(ProductCategory::getCategoryId, Function.identity()));
        ProductCategory current = categoryMap.get(categoryId);
        while (current != null) {
            String code = normalize(current.getCategoryCode());
            if (List.of("LOAN", "CASA", "FIXED_DEPOSIT", "FOREX").contains(code)) return code;
            current = current.getParentCategoryId() == null ? null : categoryMap.get(current.getParentCategoryId());
        }
        return "";
    }

    private String currencyCode(Integer currencyId) {
        if (currencyId == null) return null;
        return currencies.findById(currencyId).map(Currency::getIsoCode).map(ProductConfigurationIntegrityService::normalize)
                .orElse("<missing-currency>");
    }

    private static String eligibilityValue(String dataType, String value) {
        if (!"NUMBER".equals(normalize(dataType)) || value == null || value.isBlank()) return text(value);
        return decimal(new BigDecimal(value));
    }

    private static String numericText(String value) {
        if (value == null || value.isBlank()) return text(value);
        try {
            return decimal(new BigDecimal(value));
        } catch (NumberFormatException ignored) {
            return value;
        }
    }

    private static String decimal(BigDecimal value) {
        if (value == null) return null;
        if (value.signum() == 0) return "0";
        return value.stripTrailingZeros().toPlainString();
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizeNullable(String value) {
        return value == null ? null : normalize(value);
    }

    private static String text(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private record ProductState(String productCode, String productName, Integer categoryId, String baseCurrencyCode,
                                String description, String effectiveFrom, String effectiveTo) { }
    private record CurrencyState(String currencyCode, String minimumAmount, String maximumAmount,
                                 boolean baseCurrency, boolean walletEnabled) { }
    private record AttributeState(Integer definitionId, String value) { }
    private record ConditionState(String fact, String operator, String valueFrom, String valueTo, String valueSet) {
        String sortKey() { return fact + "|" + operator + "|" + Objects.toString(valueFrom, "") + "|"
                + Objects.toString(valueTo, "") + "|" + Objects.toString(valueSet, ""); }
    }
    private record OutcomeState(String type, String value, String unit, String method, String payout,
                                String currencyCode) {
        String sortKey() { return type + "|" + Objects.toString(value, "") + "|" + Objects.toString(unit, "") + "|"
                + Objects.toString(method, "") + "|" + Objects.toString(payout, "") + "|"
                + Objects.toString(currencyCode, ""); }
    }
    private record RuleState(String type, String name, Integer priority, String combineLogic, boolean active,
                             String effectiveFrom, String effectiveTo, List<ConditionState> conditions,
                             List<OutcomeState> outcomes) {
        String sortKey() { return type + "|" + priority + "|" + name; }
    }
    private record SlabState(String fact, String valueFrom, String valueTo, String value, String unit,
                             String currencyCode, String floor, String cap) {
        String sortKey() { return fact + "|" + currencyCode + "|" + valueFrom + "|" + valueTo; }
    }
    private record ChargeState(String code, String calculationBasis, boolean active, String glCode,
                               List<SlabState> slabs) {
        String sortKey() { return code + "|" + calculationBasis; }
    }
    private record RoleState(Integer roleTypeId, boolean mandatory, Integer minimumCount, Integer maximumCount) { }
}
