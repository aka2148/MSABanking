package com.bank.product.service;

import com.bank.common.exception.InvalidInputException;
import com.bank.common.exception.ResourceNotFoundException;
import com.bank.fd.domain.FdCompoundCalculator;
import com.bank.fd.entity.FdProductReference;
import com.bank.fd.repository.FdProductReferenceRepository;
import com.bank.product.dto.request.CreateFdOfferRequest;
import com.bank.product.dto.request.ProductConfigurationRequest;
import com.bank.product.dto.request.ProductPricingPreviewRequest;
import com.bank.product.dto.response.FdOfferAdminResponse;
import com.bank.product.dto.response.ProductBuilderMetadataResponse;
import com.bank.product.dto.response.ProductConfigurationResponse;
import com.bank.product.dto.response.ProductPricingPreviewResponse;
import com.bank.product.dto.response.ValidationResponse;
import com.bank.product.entity.AttributeDefinition;
import com.bank.product.entity.ChargeConfig;
import com.bank.product.entity.ChargeSlab;
import com.bank.product.entity.Currency;
import com.bank.product.entity.Product;
import com.bank.product.entity.ProductAttributeValue;
import com.bank.product.entity.ProductCategory;
import com.bank.product.entity.ProductConfigurationRevision;
import com.bank.product.entity.ProductCurrencyConfig;
import com.bank.product.entity.ProductRoleConfig;
import com.bank.product.entity.ProductRule;
import com.bank.product.entity.RoleType;
import com.bank.product.entity.RuleCondition;
import com.bank.product.entity.RuleOutcome;
import com.bank.product.entity.RuleType;
import com.bank.product.repository.AttributeDefinitionRepository;
import com.bank.product.repository.ChargeConfigRepository;
import com.bank.product.repository.ChargeSlabRepository;
import com.bank.product.repository.CurrencyRepository;
import com.bank.product.repository.ProductAttributeValueRepository;
import com.bank.product.repository.ProductCategoryRepository;
import com.bank.product.repository.ProductConfigurationRevisionRepository;
import com.bank.product.repository.ProductCurrencyConfigRepository;
import com.bank.product.repository.ProductRepository;
import com.bank.product.repository.ProductRoleConfigRepository;
import com.bank.product.repository.ProductRuleRepository;
import com.bank.product.repository.RoleTypeRepository;
import com.bank.product.repository.RuleConditionRepository;
import com.bank.product.repository.RuleOutcomeRepository;
import com.bank.product.repository.RuleTypeRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProductConfigurationService {
    private static final String CREATOR = "SYSTEM";
    private static final Set<String> FAMILIES = Set.of("LOAN", "CASA", "FIXED_DEPOSIT", "FOREX");
    private static final Set<String> OPERATORS = Set.of(
            "EQ", "NE", "GT", "GTE", "LT", "LTE", "BETWEEN_INCLUSIVE", "IN");
    private static final Map<String, FactDefinition> FACTS = Map.ofEntries(
            Map.entry("CUSTOMER_AGE", new FactDefinition("NUMBER", true, null)),
            Map.entry("KYC_STATUS", new FactDefinition("STRING", true, null)),
            Map.entry("RISK_CATEGORY", new FactDefinition("STRING", true, null)),
            Map.entry("VERIFIED_IDENTITY", new FactDefinition("BOOLEAN", true, null)),
            Map.entry("ANNUAL_INCOME", new FactDefinition("NUMBER", false, "Customer API does not provide verified income")),
            Map.entry("RESIDENCY", new FactDefinition("STRING", false, "Customer API does not provide residency")),
            Map.entry("PAN_VERIFIED", new FactDefinition("BOOLEAN", false, "Customer API does not expose a verified PAN fact")));
    private static final Map<String, FamilyPricing> FAMILY_PRICING = Map.of(
            "LOAN", new FamilyPricing("TENURE_MONTHS", "INTEREST_RATE", "LOAN_REDUCING_BALANCE"),
            "CASA", new FamilyPricing("BALANCE", "CREDIT_INTEREST_RATE", "CASA_DAILY_BALANCE"),
            "FIXED_DEPOSIT", new FamilyPricing("TENURE_MONTHS", "INTEREST_RATE", "FD_QUARTERLY"),
            "FOREX", new FamilyPricing("TRANSFER_AMOUNT", "MARKUP_RATE", "FOREX_REFERENCE_MARKUP"));

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
    private final RoleTypeRepository roleTypes;
    private final ProductRoleConfigRepository roleConfigs;
    private final ProductConfigurationRevisionRepository revisions;
    private final FdProductReferenceRepository fdProducts;
    private final FdOfferAdminService fdOffers;
    private final ObjectMapper objectMapper;
    private final ProductConfigurationIntegrityService integrity;

    public ProductConfigurationService(ProductRepository products,
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
                                       RoleTypeRepository roleTypes,
                                       ProductRoleConfigRepository roleConfigs,
                                       ProductConfigurationRevisionRepository revisions,
                                       FdProductReferenceRepository fdProducts,
                                       FdOfferAdminService fdOffers,
                                       ProductConfigurationIntegrityService integrity,
                                       ObjectMapper objectMapper) {
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
        this.roleTypes = roleTypes;
        this.roleConfigs = roleConfigs;
        this.revisions = revisions;
        this.fdProducts = fdProducts;
        this.fdOffers = fdOffers;
        this.integrity = integrity;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ProductBuilderMetadataResponse metadata(Integer categoryId) {
        Map<Integer, ProductCategory> categoryMap = categories.findAll().stream()
                .collect(Collectors.toMap(ProductCategory::getCategoryId, Function.identity()));
        List<ProductBuilderMetadataResponse.CategoryOption> categoryOptions = categoryMap.values().stream()
                .sorted(Comparator.comparing(ProductCategory::getCategoryName))
                .map(category -> new ProductBuilderMetadataResponse.CategoryOption(category.getCategoryId(),
                        category.getCategoryCode(), category.getCategoryName(), category.getParentCategoryId(),
                        family(category, categoryMap))).toList();
        List<Integer> inheritedIds = categoryId == null ? List.of() : lineage(categoryId, categoryMap).stream()
                .map(ProductCategory::getCategoryId).toList();
        List<AttributeDefinition> definitions = categoryId == null
                ? attributeDefinitions.findAll()
                : attributeDefinitions.findAll().stream().filter(value -> inheritedIds.contains(value.getCategoryId())).toList();
        List<ProductBuilderMetadataResponse.AttributeOption> attributes = definitions.stream()
                .sorted(Comparator.comparing(AttributeDefinition::getDisplayOrder)
                        .thenComparing(AttributeDefinition::getAttributeKey))
                .map(value -> new ProductBuilderMetadataResponse.AttributeOption(value.getId(), value.getCategoryId(),
                        value.getAttributeKey(), value.getDataType(), value.getDisplayName(),
                        Boolean.TRUE.equals(value.getRequired()), split(value.getChoiceValues()), value.getUnit(),
                        value.getDefaultValue(), value.getMinValue(), value.getMaxValue(), value.getDisplayOrder(),
                        value.getBehaviorKey(), categoryId != null && !Objects.equals(categoryId, value.getCategoryId())))
                .toList();
        List<ProductBuilderMetadataResponse.CurrencyOption> currencyOptions = currencies.findAll().stream()
                .filter(value -> Boolean.TRUE.equals(value.getIsActive()))
                .sorted(Comparator.comparing(Currency::getIsoCode))
                .map(value -> new ProductBuilderMetadataResponse.CurrencyOption(value.getCurrencyId(), value.getIsoCode(),
                        value.getCurrencyName(), value.getCurrencySymbol(), value.getDecimalPlaces())).toList();
        List<ProductBuilderMetadataResponse.RoleOption> roles = roleTypes.findAll().stream()
                .map(value -> new ProductBuilderMetadataResponse.RoleOption(value.getRoleTypeId(),
                        value.getRoleTypeCode(), value.getDescription())).toList();
        List<ProductBuilderMetadataResponse.FactOption> facts = FACTS.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> new ProductBuilderMetadataResponse.FactOption(entry.getKey(), entry.getValue().dataType(),
                        operatorsFor(entry.getValue().dataType()), entry.getValue().executable(), entry.getValue().dependency()))
                .toList();
        return new ProductBuilderMetadataResponse(categoryOptions, currencyOptions, attributes, roles, facts,
                OPERATORS.stream().sorted().toList(), List.of("INTEREST_RATE", "CREDIT_INTEREST_RATE", "MARKUP_RATE"),
                FAMILY_PRICING.values().stream().map(FamilyPricing::calculationMethod).distinct().sorted().toList());
    }

    @Transactional(readOnly = true)
    public List<ProductConfigurationResponse> list() {
        Map<Integer, ProductCategory> categoryMap = categories.findAll().stream()
                .collect(Collectors.toMap(ProductCategory::getCategoryId, Function.identity()));
        return products.findAll().stream().map(product -> response(product, categoryMap)).toList();
    }

    @Transactional(readOnly = true)
    public ValidationResponse validate(ProductConfigurationRequest request) {
        List<String> errors = validationErrors(request, true);
        return new ValidationResponse(errors.isEmpty(), errors);
    }

    @Transactional
    public ProductConfigurationResponse create(ProductConfigurationRequest request) {
        List<String> errors = validationErrors(request, true);  if (!errors.isEmpty()) throw new InvalidInputException(String.join("; ", errors));
        String family = family(request.categoryId());
        Product product;
        Long fdProductId = null;
        if ("FIXED_DEPOSIT".equals(family)) {
            FdOfferAdminResponse fd = fdOffers.create(fdRequest(request));
            product = products.findById(fd.catalogProductId()).orElseThrow();
            fdProductId = fd.fdProductId();
            product.setBaseCurrencyCode(normalize(request.baseCurrencyCode()));
            product.setEffectiveFrom(date(request.effectiveFrom()));
            product.setEffectiveTo(date(request.effectiveTo()));
            products.save(product);
        } else {
            product = createProduct(request);
            saveCurrencies(product, request);
            savePricing(product, request);
        }
        saveAttributes(product, request);
        saveEligibility(product, request);
        saveCharges(product, request);
        saveRoles(product, request);
        saveRevision(product.getProductId(), "DRAFT", request);
        Map<Integer, ProductCategory> categoryMap = categories.findAll().stream()
                .collect(Collectors.toMap(ProductCategory::getCategoryId, Function.identity()));
        ProductConfigurationResponse response = response(product, categoryMap);
        return new ProductConfigurationResponse(response.productId(), fdProductId, response.productCode(),
                response.productName(), response.family(), response.categoryCode(), response.status(),
                response.baseCurrencyCode(), response.currencyCodes(), response.revision());
    }

    @Transactional
    public ProductConfigurationResponse activate(Integer productId) {
        Product product = products.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
        ProductConfigurationRevision latest = revisions.findTopByProductIdOrderByRevisionNoDesc(productId).orElse(null);
        String family = family(product.getCategoryId());
        if (latest == null) {
            FdProductReference reference = fdProducts.findByProductCodeIgnoreCase(product.getProductCode())
                    .orElseThrow(() -> new InvalidInputException("Product has no product-builder configuration revision"));
            fdOffers.activate(reference.getId());
            Map<Integer, ProductCategory> categoryMap = categories.findAll().stream()
                    .collect(Collectors.toMap(ProductCategory::getCategoryId, Function.identity()));
            return response(products.findById(productId).orElseThrow(), categoryMap);
        }
        ProductConfigurationRequest request = readSnapshot(latest.getSnapshotJson());
        List<String> errors = new ArrayList<>(validationErrors(request, false));
        errors.addAll(persistedConfigurationErrors(product, request));
        if (!errors.isEmpty()) throw new InvalidInputException(String.join("; ", errors));
        if ("ACTIVE".equalsIgnoreCase(product.getStatus()) && "ACTIVE".equalsIgnoreCase(latest.getStatus())) {
            Map<Integer, ProductCategory> categoryMap = categories.findAll().stream()
                    .collect(Collectors.toMap(ProductCategory::getCategoryId, Function.identity()));
            return response(product, categoryMap);
        }
        if ("FIXED_DEPOSIT".equals(family)) {
            FdProductReference reference = fdProducts.findByProductCodeIgnoreCase(product.getProductCode())
                    .orElseThrow(() -> new InvalidInputException("FD reference synchronization is missing"));
            fdOffers.activate(reference.getId());
            product = products.findById(productId).orElseThrow();
        } else {
            product.setStatus("ACTIVE");
            product.setUpdatedBy(CREATOR);
            product.setUpdatedDate(LocalDateTime.now());
            product.setCrudValue("U");
            product = products.save(product);
        }
        saveRevision(productId, "ACTIVE", request);
        Map<Integer, ProductCategory> categoryMap = categories.findAll().stream()
                .collect(Collectors.toMap(ProductCategory::getCategoryId, Function.identity()));
        return response(product, categoryMap);
    }

    @Transactional(readOnly = true)
    public ProductPricingPreviewResponse preview(ProductPricingPreviewRequest previewRequest) {
        ProductConfigurationRequest request = previewRequest.configuration();
        List<String> errors = validationErrors(request, false);
        String family = safeFamily(request.categoryId());
        if (!errors.isEmpty()) {
            return new ProductPricingPreviewResponse("UNSUPPORTED", family, request.baseCurrencyCode(), request.baseCurrencyCode(), null, null,
                    Map.of(), errors);
        }
        Map<String, String> facts = previewRequest.facts() == null ? Map.of() : previewRequest.facts();
        List<String> missing = request.eligibilityRules() == null ? new ArrayList<>() : request.eligibilityRules().stream()
                .map(rule -> normalize(rule.fact())).filter(fact -> !facts.containsKey(fact)).distinct().toList();
        if (!missing.isEmpty()) {
            return new ProductPricingPreviewResponse("INCOMPLETE", family, request.baseCurrencyCode(), request.baseCurrencyCode(), null, null,
                    Map.of(), List.of("Missing customer/application facts: " + String.join(", ", missing)));
        }
        List<ProductConfigurationRequest.EligibilityRule> eligibility = request.eligibilityRules() == null
                ? List.of() : request.eligibilityRules();
        boolean andRulesMatch = eligibility.stream()
                .filter(rule -> !"OR".equals(normalize(rule.combineLogic())))
                .allMatch(rule -> matches(rule, facts.get(normalize(rule.fact()))));
        List<ProductConfigurationRequest.EligibilityRule> orRules = eligibility.stream()
                .filter(rule -> "OR".equals(normalize(rule.combineLogic()))).toList();
        boolean eligible = andRulesMatch && (orRules.isEmpty() || orRules.stream()
                .anyMatch(rule -> matches(rule, facts.get(normalize(rule.fact())))));
        if (!eligible) {
            return new ProductPricingPreviewResponse("SUPPORTED", family, request.baseCurrencyCode(), request.baseCurrencyCode(), null, null,
                    Map.of("eligible", BigDecimal.ZERO), List.of("Sample facts do not satisfy eligibility rules"));
        }
        return pricingPreview(request, facts);
    }

    private List<String> validationErrors(ProductConfigurationRequest request, boolean checkDuplicateCode) {
        List<String> errors = new ArrayList<>();
        if (request == null) return List.of("Product configuration is required");
        String family = safeFamily(request.categoryId());
        if (!FAMILIES.contains(family)) errors.add("Category must inherit from Loan, CASA, Fixed Deposit or Forex");
        if (checkDuplicateCode && products.existsByProductCodeIgnoreCase(normalize(request.productCode()))) errors.add("Product code is already in use");
        if (request.effectiveFrom() != null && request.effectiveTo() != null
                && request.effectiveTo().isBefore(request.effectiveFrom())) errors.add("Effective-to date must not precede effective-from date");
        if (request.effectiveTo() != null && request.effectiveTo().isBefore(LocalDate.now())) errors.add("Effective-to date must not be in the past");
        validateCurrencies(request, errors);
        validateAttributes(request, errors);
        validateEligibility(request, errors);
        validatePricing(request, family, errors);
        validateCharges(request, family, errors);
        validateRoles(request, errors);
        return errors.stream().distinct().toList();
    }

    private void validateCurrencies(ProductConfigurationRequest request, List<String> errors) {
        if (request.currencies() == null || request.currencies().isEmpty()) {
            errors.add("Select at least one supported currency");
            return;
        }
        Set<String> seen = new HashSet<>();
        for (ProductConfigurationRequest.CurrencyConfig config : request.currencies()) {
            String code = normalize(config.currencyCode());
            if (!seen.add(code)) errors.add("Currency appears more than once: " + code);
            Currency currency = currencies.findByIsoCode(code).filter(value -> Boolean.TRUE.equals(value.getIsActive())).orElse(null);
            if (currency == null) errors.add("Active currency is not configured: " + code);
            if (config.minimumAmount() == null || config.maximumAmount() == null
                    || config.minimumAmount().signum() < 0 || config.maximumAmount().signum() <= 0
                    || config.minimumAmount().compareTo(config.maximumAmount()) > 0) {
                errors.add("Currency amount limits are invalid for " + code);
            } else if (currency != null && (config.minimumAmount().scale() > currency.getDecimalPlaces()
                    || config.maximumAmount().scale() > currency.getDecimalPlaces())) {
                errors.add(code + " amounts support " + currency.getDecimalPlaces() + " decimal places");
            }
        }
        String family = safeFamily(request.categoryId());
        if (!"CASA".equals(family) && request.currencies().stream().anyMatch(value -> value.minimumAmount() != null && value.minimumAmount().signum() <= 0)) {
            errors.add(family + " minimum amounts must be greater than zero");
        }
        if (!seen.contains(normalize(request.baseCurrencyCode()))) errors.add("Base currency must be one of the selected currencies");
        if ("FOREX".equals(safeFamily(request.categoryId())) && seen.size() < 2) errors.add("Forex requires a funding and destination currency");
    }

    private void validateAttributes(ProductConfigurationRequest request, List<String> errors) {
        Map<Integer, ProductCategory> categoryMap = categories.findAll().stream()
                .collect(Collectors.toMap(ProductCategory::getCategoryId, Function.identity()));
        if (!categoryMap.containsKey(request.categoryId())) return;
        Set<Integer> owners = lineage(request.categoryId(), categoryMap).stream().map(ProductCategory::getCategoryId).collect(Collectors.toSet());
        Map<Integer, String> supplied = request.attributes() == null ? Map.of() : request.attributes().stream()
                .filter(value -> value.definitionId() != null)
                .collect(Collectors.toMap(ProductConfigurationRequest.AttributeValue::definitionId,
                        ProductConfigurationRequest.AttributeValue::value, (left, right) -> right));
        for (AttributeDefinition definition : attributeDefinitions.findAll()) {
            if (!owners.contains(definition.getCategoryId())) continue;
            String value = supplied.get(definition.getId());
            if (Boolean.TRUE.equals(definition.getRequired()) && (value == null || value.isBlank())) {
                errors.add((definition.getDisplayName() == null ? definition.getAttributeKey() : definition.getDisplayName()) + " is required");
                continue;
            }
            if (value == null || value.isBlank()) continue;
            validateAttributeValue(definition, value, errors);
        }
        for (Integer definitionId : supplied.keySet()) {
            AttributeDefinition definition = attributeDefinitions.findById(definitionId).orElse(null);
            if (definition == null || !owners.contains(definition.getCategoryId())) {
                errors.add("Attribute definition " + definitionId + " does not belong to the selected category hierarchy");
            }
        }
    }

    private void validateAttributeValue(AttributeDefinition definition, String value, List<String> errors) {
        try {
            if ("NUMBER".equals(definition.getDataType())) {
                BigDecimal number = new BigDecimal(value);
                if (definition.getMinValue() != null && number.compareTo(definition.getMinValue()) < 0) errors.add(definition.getAttributeKey() + " is below its minimum");
                if (definition.getMaxValue() != null && number.compareTo(definition.getMaxValue()) > 0) errors.add(definition.getAttributeKey() + " exceeds its maximum");
            } else if ("DATE".equals(definition.getDataType())) {
                LocalDate.parse(value);
            } else if ("BOOLEAN".equals(definition.getDataType()) && !Set.of("true", "false").contains(value.toLowerCase(Locale.ROOT))) {
                errors.add(definition.getAttributeKey() + " must be true or false");
            }
            List<String> choices = split(definition.getChoiceValues());
            if (!choices.isEmpty() && !choices.contains(normalize(value))) errors.add(definition.getAttributeKey() + " must be one of " + choices);
        } catch (RuntimeException exception) {
            errors.add(definition.getAttributeKey() + " has an invalid " + definition.getDataType().toLowerCase(Locale.ROOT) + " value");
        }
    }

    private void validateEligibility(ProductConfigurationRequest request, List<String> errors) {
        if (request.eligibilityRules() == null) return;
        for (ProductConfigurationRequest.EligibilityRule rule : request.eligibilityRules()) {
            FactDefinition fact = FACTS.get(normalize(rule.fact()));
            if (fact == null) errors.add("Unrecognized eligibility fact: " + rule.fact());
            else if (!fact.executable()) errors.add("Unsupported eligibility fact " + rule.fact() + ": " + fact.dependency());
            else if (!fact.dataType().equals(normalize(rule.dataType()))) errors.add("Eligibility fact data type does not match " + rule.fact());
            if (!OPERATORS.contains(normalize(rule.operator()))) errors.add("Unsupported eligibility operator: " + rule.operator());
            if (!Set.of("AND", "OR").contains(normalize(rule.combineLogic() == null ? "AND" : rule.combineLogic()))) errors.add("Rule combination must be AND or OR");
        }
    }

    private void validatePricing(ProductConfigurationRequest request, String family, List<String> errors) {
        FamilyPricing expected = FAMILY_PRICING.get(family);
        if (expected == null || request.pricingBands() == null || request.pricingBands().isEmpty()) return;
        Set<String> currencyCodes = request.currencies().stream().map(value -> normalize(value.currencyCode())).collect(Collectors.toSet());
        Map<String, List<ProductConfigurationRequest.PricingBand>> byCurrency = new HashMap<>();
        for (ProductConfigurationRequest.PricingBand band : request.pricingBands()) {
            String code = normalize(band.currencyCode());
            if (!currencyCodes.contains(code)) errors.add("Pricing currency is not selected: " + code);
            if (!expected.fact().equals(normalize(band.fact())) || !expected.outcomeType().equals(normalize(band.outcomeType()))
                    || !expected.calculationMethod().equals(normalize(band.calculationMethod()))) {
                errors.add("Unsupported pricing behavior for " + family + ": use " + expected.fact() + ", "
                        + expected.outcomeType() + " and " + expected.calculationMethod());
            }
            if (!"DECIMAL_FRACTION".equals(normalize(band.rateUnit())) || band.rate() == null
                    || band.rate().signum() < 0 || band.rate().compareTo(BigDecimal.ONE) > 0 || band.rate().scale() > 8) {
                errors.add("Rates and markups use DECIMAL_FRACTION between 0 and 1 with at most 8 decimals");
            }
            if (band.valueFrom() == null || band.valueTo() == null || band.valueFrom().signum() < 0
                    || band.valueFrom().compareTo(band.valueTo()) > 0) errors.add("Pricing band bounds are invalid for " + code);
            byCurrency.computeIfAbsent(code, ignored -> new ArrayList<>()).add(band);
        }
        Set<String> requiredPricing = "FOREX".equals(family)
                ? currencyCodes.stream().filter(code -> !code.equals(normalize(request.baseCurrencyCode()))).collect(Collectors.toSet())
                : currencyCodes;
        if (!byCurrency.keySet().containsAll(requiredPricing)) errors.add("Each applicable currency requires pricing bands");
        for (Map.Entry<String, List<ProductConfigurationRequest.PricingBand>> entry : byCurrency.entrySet()) {
            List<ProductConfigurationRequest.PricingBand> sorted = entry.getValue().stream()
                    .sorted(Comparator.comparing(ProductConfigurationRequest.PricingBand::valueFrom)).toList();
            String bandBasisCurrency = "FOREX".equals(family) ? normalize(request.baseCurrencyCode()) : entry.getKey();
            Currency bandCurrency = currencies.findByIsoCode(bandBasisCurrency).orElse(null);
            BigDecimal step = "TENURE_MONTHS".equals(expected.fact()) ? BigDecimal.ONE
                    : BigDecimal.ONE.movePointLeft(bandCurrency == null ? 2 : bandCurrency.getDecimalPlaces());
            for (int index = 1; index < sorted.size(); index++) {
                BigDecimal expectedStart = sorted.get(index - 1).valueTo().add(step);
                int comparison = sorted.get(index).valueFrom().compareTo(expectedStart);
                if (comparison < 0) errors.add("Pricing bands overlap or are ambiguous for " + entry.getKey());
                if (comparison > 0) errors.add("Pricing bands leave an unsupported coverage gap for " + entry.getKey());
            }
        }
    }

    private void validateCharges(ProductConfigurationRequest request, String family, List<String> errors) {
        if (request.charges() == null) return;
        Set<String> selectedCurrencies = request.currencies().stream()
                .map(value -> normalize(value.currencyCode())).collect(Collectors.toSet());
        for (ProductConfigurationRequest.Charge charge : request.charges()) {
            if (!Set.of("FIXED", "PERCENTAGE").contains(normalize(charge.calculationBasis()))) errors.add("Charge basis must be FIXED or PERCENTAGE");
            for (ProductConfigurationRequest.ChargeSlabConfig slab : charge.slabs()) {
                if (slab.valueFrom().compareTo(slab.valueTo()) > 0) errors.add("Charge slab minimum exceeds maximum");
                String unit = normalize(slab.unit());
                if (!Set.of("FIXED_AMOUNT", "DECIMAL_FRACTION").contains(unit)) errors.add("Charge unit must be FIXED_AMOUNT or DECIMAL_FRACTION");
                if ("DECIMAL_FRACTION".equals(unit) && slab.value().compareTo(BigDecimal.ONE) > 0) errors.add("Percentage charges use decimal fractions");
                if (slab.floor() != null && slab.cap() != null && slab.floor().compareTo(slab.cap()) > 0) errors.add("Charge floor must not exceed cap");
                String chargeCurrency = normalize(slab.currencyCode());
                if (!selectedCurrencies.contains(chargeCurrency)) errors.add("Charge currency is not selected: " + chargeCurrency);
                if ("FOREX".equals(family) && !chargeCurrency.equals(normalize(request.baseCurrencyCode()))) {
                    errors.add("Forex charges must use the funding/base currency; fee-currency conversion is unsupported");
                }
                if ("FOREX".equals(family) && !Set.of("TRANSFER_AMOUNT", "FUNDING_AMOUNT").contains(normalize(slab.fact()))) {
                    errors.add("Forex charges must use TRANSFER_AMOUNT or FUNDING_AMOUNT as their calculation fact");
                }
            }
            Map<String, List<ProductConfigurationRequest.ChargeSlabConfig>> slabGroups = charge.slabs().stream()
                    .collect(Collectors.groupingBy(slab -> normalize(slab.currencyCode()) + ":" + normalize(slab.fact())));
            for (List<ProductConfigurationRequest.ChargeSlabConfig> group : slabGroups.values()) {
                List<ProductConfigurationRequest.ChargeSlabConfig> sorted = group.stream()
                        .sorted(Comparator.comparing(ProductConfigurationRequest.ChargeSlabConfig::valueFrom)).toList();
                for (int index = 1; index < sorted.size(); index++) {
                    if (sorted.get(index).valueFrom().compareTo(sorted.get(index - 1).valueTo()) <= 0) {
                        errors.add("Charge slabs overlap for " + charge.chargeCode());
                    }
                }
            }
        }
    }

    private void validateRoles(ProductConfigurationRequest request, List<String> errors) {
        if (request.roleConstraints() == null) return;
        Set<Integer> seen = new HashSet<>();
        for (ProductConfigurationRequest.RoleConstraint role : request.roleConstraints()) {
            if (!seen.add(role.roleTypeId())) errors.add("Role constraint appears more than once");
            if (!roleTypes.existsById(role.roleTypeId())) errors.add("Role type not found: " + role.roleTypeId());
            if (role.minimumCount() != null && role.maximumCount() != null && role.minimumCount() > role.maximumCount()) errors.add("Role minimum count exceeds maximum count");
            if (role.mandatory() && (role.minimumCount() == null || role.minimumCount() < 1)) errors.add("Mandatory roles require a minimum count of at least one");
        }
    }

    private List<String> persistedConfigurationErrors(Product product, ProductConfigurationRequest request) {
        return integrity.persistedConfigurationErrors(product, request);
    }

    @Transactional(readOnly = true)
    public void assertEditable(Integer productId) {
        integrity.assertEditable(productId);
    }

    private Product createProduct(ProductConfigurationRequest request) {
        Product product = new Product();
        product.setProductCode(normalize(request.productCode()));
        product.setProductName(request.productName().trim());
        product.setCategoryId(request.categoryId());
        product.setBaseCurrencyCode(normalize(request.baseCurrencyCode()));
        product.setDescription(request.description());
        product.setEffectiveFrom(date(request.effectiveFrom()));
        product.setEffectiveTo(date(request.effectiveTo()));
        product.setStatus("DRAFT");
        product.setUuid(UUID.randomUUID().toString());
        product.setCreatedBy(CREATOR);
        product.setCreatedDate(LocalDateTime.now());
        product.setCrudValue("C");
        return products.save(product);
    }

    private void saveCurrencies(Product product, ProductConfigurationRequest request) {
        for (ProductConfigurationRequest.CurrencyConfig value : request.currencies()) {
            Currency currency = currency(value.currencyCode());
            ProductCurrencyConfig config = new ProductCurrencyConfig();
            config.setProductId(product.getProductId());
            config.setCurrencyId(currency.getCurrencyId());
            config.setIsBaseCurrency(normalize(value.currencyCode()).equals(normalize(request.baseCurrencyCode())));
            config.setMinimumAmount(value.minimumAmount());
            config.setMaximumAmount(value.maximumAmount());
            config.setWalletEnabled(false);
            audit(config);
            currencyConfigs.save(config);
        }
    }

    private void saveAttributes(Product product, ProductConfigurationRequest request) {
        if (request.attributes() == null) return;
        for (ProductConfigurationRequest.AttributeValue configured : request.attributes()) {
            if (configured.value() == null || configured.value().isBlank()) continue;
            AttributeDefinition definition = attributeDefinitions.findById(configured.definitionId()).orElseThrow();
            ProductAttributeValue value = new ProductAttributeValue();
            value.setProductId(product.getProductId());
            value.setAttributeDefId(definition.getId());
            if ("NUMBER".equals(definition.getDataType())) value.setValueNumber(new BigDecimal(configured.value()));
            else if ("DATE".equals(definition.getDataType())) value.setValueDate(LocalDate.parse(configured.value()).atStartOfDay().atOffset(ZoneOffset.UTC));
            else value.setValueString(configured.value());
            value.setCreatedBy(CREATOR);
            value.setCreatedDate(LocalDateTime.now());
            value.setUuid(UUID.randomUUID().toString());
            value.setCrudValue("C");
            attributeValues.save(value);
        }
    }

    private void saveEligibility(Product product, ProductConfigurationRequest request) {
        if (request.eligibilityRules() == null) return;
        RuleType type = ruleType("ELIGIBILITY");
        int priority = 1;
        for (ProductConfigurationRequest.EligibilityRule configured : request.eligibilityRules()) {
            ProductRule rule = rule(product.getProductId(), type, configured.name(), priority++, configured.combineLogic());
            RuleCondition condition = new RuleCondition();
            condition.setRuleId(rule.getRuleId());
            condition.setConditionAttribute(normalize(configured.fact()));
            condition.setOperator(normalize(configured.operator()));
            condition.setValueFrom(configured.valueFrom());
            condition.setValueTo(configured.valueTo());
            condition.setValueSet(configured.valueSet());
            audit(condition);
            conditions.save(condition);
            RuleOutcome outcome = outcome(rule.getRuleId(), "ELIGIBILITY", BigDecimal.ONE, "BOOLEAN", null);
            outcomes.save(outcome);
        }
    }

    private void savePricing(Product product, ProductConfigurationRequest request) {
        RuleType type = ruleType("PRICING_BAND");
        int priority = 1;
        for (ProductConfigurationRequest.PricingBand configured : request.pricingBands()) {
            Currency currency = currency(configured.currencyCode());
            ProductRule rule = rule(product.getProductId(), type, "PRICING_BAND", priority++, "AND");
            RuleCondition condition = new RuleCondition();
            condition.setRuleId(rule.getRuleId());
            condition.setConditionAttribute(normalize(configured.fact()));
            condition.setOperator("BETWEEN_INCLUSIVE");
            condition.setValueFrom(configured.valueFrom().toPlainString());
            condition.setValueTo(configured.valueTo().toPlainString());
            audit(condition);
            conditions.save(condition);
            RuleOutcome outcome = outcome(rule.getRuleId(), normalize(configured.outcomeType()),
                    configured.rate().setScale(8, RoundingMode.HALF_UP), "DECIMAL_FRACTION", currency.getCurrencyId());
            outcome.setCompoundingBasis(normalize(configured.calculationMethod()));
            outcomes.save(outcome);
        }
    }

    private void saveCharges(Product product, ProductConfigurationRequest request) {
        if (request.charges() == null) return;
        for (ProductConfigurationRequest.Charge configured : request.charges()) {
            ChargeConfig charge = new ChargeConfig();
            charge.setProductId(product.getProductId());
            charge.setChargeCode(normalize(configured.chargeCode()));
            charge.setCalculationBasis(normalize(configured.calculationBasis()));
            charge.setIsActive(true);
            audit(charge);
            charge = charges.save(charge);
            for (ProductConfigurationRequest.ChargeSlabConfig configuredSlab : configured.slabs()) {
                ChargeSlab slab = new ChargeSlab();
                slab.setChargeId(charge.getChargeId());
                slab.setSlabAttribute(normalize(configuredSlab.fact()));
                slab.setValueFrom(configuredSlab.valueFrom());
                slab.setValueTo(configuredSlab.valueTo());
                slab.setChargeAmount(configuredSlab.value());
                slab.setChargeUnit(normalize(configuredSlab.unit()));
                slab.setMinimumCharge(configuredSlab.floor());
                slab.setMaximumCharge(configuredSlab.cap());
                slab.setCurrencyId(currency(configuredSlab.currencyCode()).getCurrencyId());
                audit(slab);
                chargeSlabs.save(slab);
            }
        }
    }

    private void saveRoles(Product product, ProductConfigurationRequest request) {
        if (request.roleConstraints() == null) return;
        for (ProductConfigurationRequest.RoleConstraint configured : request.roleConstraints()) {
            ProductRoleConfig role = new ProductRoleConfig();
            role.setProductId(product.getProductId());
            role.setRoleTypeId(configured.roleTypeId());
            role.setMandatory(configured.mandatory());
            role.setMinCount(configured.minimumCount());
            role.setMaxCount(configured.maximumCount());
            role.setCreatedBy(CREATOR);
            role.setCreatedDate(LocalDateTime.now());
            role.setUuid(UUID.randomUUID().toString());
            role.setCrudValue("C");
            roleConfigs.save(role);
        }
    }

    private ProductPricingPreviewResponse pricingPreview(ProductConfigurationRequest request, Map<String, String> facts) {
        String family = family(request.categoryId());
        if ("FOREX".equals(family)) return forexPricingPreview(request, facts);
        String currencyCode = normalize(facts.getOrDefault("CURRENCY", request.baseCurrencyCode()));
        Currency currency = currency(currencyCode);
        FamilyPricing pricing = FAMILY_PRICING.get(family);
        BigDecimal basis = decimalFact(facts, pricing.fact());
        ProductConfigurationRequest.PricingBand band = request.pricingBands().stream()
                .filter(value -> currencyCode.equals(normalize(value.currencyCode())))
                .filter(value -> basis.compareTo(value.valueFrom()) >= 0 && basis.compareTo(value.valueTo()) <= 0)
                .findFirst().orElseThrow(() -> new InvalidInputException("No pricing band covers the sample input"));
        BigDecimal amount = decimalFact(facts, "AMOUNT");
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        values.put("eligible", BigDecimal.ONE);
        values.put("rateDecimalFraction", band.rate());
        if ("FIXED_DEPOSIT".equals(family)) {
            int months = integerFact(facts, "TENURE_MONTHS");
            FdCompoundCalculator.Result result = FdCompoundCalculator.maturityAmount(amount, band.rate(),
                    LocalDate.now(), LocalDate.now().plusMonths(months), currency.getDecimalPlaces());
            values.put("interest", result.interestAmount());
            values.put("maturityAmount", result.maturityAmount());
        } else if ("LOAN".equals(family)) {
            int months = integerFact(facts, "TENURE_MONTHS");
            BigDecimal monthlyRate = band.rate().divide(BigDecimal.valueOf(12), MathContext.DECIMAL128);
            BigDecimal factor = BigDecimal.ONE.add(monthlyRate).pow(months, MathContext.DECIMAL128);
            BigDecimal emi = monthlyRate.signum() == 0 ? amount.divide(BigDecimal.valueOf(months), MathContext.DECIMAL128)
                    : amount.multiply(monthlyRate, MathContext.DECIMAL128).multiply(factor, MathContext.DECIMAL128)
                    .divide(factor.subtract(BigDecimal.ONE), MathContext.DECIMAL128);
            values.put("monthlyInstallment", emi.setScale(currency.getDecimalPlaces(), RoundingMode.HALF_UP));
            values.put("totalRepayment", emi.multiply(BigDecimal.valueOf(months)).setScale(currency.getDecimalPlaces(), RoundingMode.HALF_UP));
        } else if ("CASA".equals(family)) {
            BigDecimal interest = amount.multiply(band.rate()).multiply(BigDecimal.valueOf(30))
                    .divide(BigDecimal.valueOf(365), 12, RoundingMode.HALF_UP)
                    .setScale(currency.getDecimalPlaces(), RoundingMode.HALF_UP);
            values.put("thirtyDayCreditInterest", interest);
        }
        BigDecimal totalCharges = calculateCharges(request, amount, currencyCode, currency.getDecimalPlaces());
        values.put("charges", totalCharges);
        return new ProductPricingPreviewResponse("SUPPORTED", family, currencyCode, currencyCode, null,
                pricing.calculationMethod(), values, List.of());
    }

    private ProductPricingPreviewResponse forexPricingPreview(ProductConfigurationRequest request, Map<String, String> facts) {
        String fundingCode = requiredCodeFact(facts, "FUNDING_CURRENCY");
        String destinationCode = requiredCodeFact(facts, "DESTINATION_CURRENCY");
        String direction = requiredCodeFact(facts, "REFERENCE_RATE_DIRECTION");
        String mode = requiredCodeFact(facts, "FOREX_AMOUNT_MODE");
        if (!fundingCode.equals(normalize(request.baseCurrencyCode()))) {
            throw new InvalidInputException("Forex funding currency must equal the configured base currency");
        }
        if (fundingCode.equals(destinationCode)) throw new InvalidInputException("Forex destination currency must differ from the funding currency");
        Set<String> selected = request.currencies().stream().map(value -> normalize(value.currencyCode())).collect(Collectors.toSet());
        if (!selected.contains(destinationCode)) throw new InvalidInputException("Forex destination currency is not configured");
        if (!"FUNDING_PER_DESTINATION".equals(direction)) {
            throw new InvalidInputException("Only FUNDING_PER_DESTINATION reference rates are supported");
        }
        if (!Set.of("CONVERSION_PRINCIPAL", "TOTAL_BUDGET").contains(mode)) {
            throw new InvalidInputException("FOREX_AMOUNT_MODE must be CONVERSION_PRINCIPAL or TOTAL_BUDGET");
        }
        Currency fundingCurrency = currency(fundingCode);
        Currency destinationCurrency = currency(destinationCode);
        BigDecimal inputAmount = decimalFact(facts, "TRANSFER_AMOUNT");
        if (inputAmount.signum() <= 0) throw new InvalidInputException("TRANSFER_AMOUNT must be greater than zero");
        ProductConfigurationRequest.PricingBand band = request.pricingBands().stream()
                .filter(value -> destinationCode.equals(normalize(value.currencyCode())))
                .filter(value -> inputAmount.compareTo(value.valueFrom()) >= 0 && inputAmount.compareTo(value.valueTo()) <= 0)
                .findFirst().orElseThrow(() -> new InvalidInputException("No Forex pricing band covers the funding amount"));
        BigDecimal referenceRate = decimalFact(facts, "REFERENCE_RATE");
        if (referenceRate.signum() <= 0) throw new InvalidInputException("REFERENCE_RATE must be greater than zero");
        BigDecimal customerRate = referenceRate.multiply(BigDecimal.ONE.add(band.rate()), MathContext.DECIMAL128)
                .setScale(12, RoundingMode.HALF_UP);
        BigDecimal fundingFees = calculateForexCharges(request, inputAmount, fundingCode, fundingCurrency.getDecimalPlaces());
        BigDecimal conversionPrincipal = "TOTAL_BUDGET".equals(mode) ? inputAmount.subtract(fundingFees) : inputAmount;
        if (conversionPrincipal.signum() <= 0) throw new InvalidInputException("Forex fees must be less than the total funding budget");
        BigDecimal totalFundingDebit = "TOTAL_BUDGET".equals(mode) ? inputAmount : conversionPrincipal.add(fundingFees);
        BigDecimal destinationAmount = conversionPrincipal.divide(customerRate,
                destinationCurrency.getDecimalPlaces(), RoundingMode.HALF_UP);
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        values.put("eligible", BigDecimal.ONE);
        values.put("markupRateDecimalFraction", band.rate());
        values.put("referenceRateFundingPerDestination", referenceRate);
        values.put("customerRateFundingPerDestination", customerRate);
        values.put("conversionPrincipal", conversionPrincipal.setScale(fundingCurrency.getDecimalPlaces(), RoundingMode.HALF_UP));
        values.put("fundingCurrencyFees", fundingFees);
        values.put("totalFundingDebit", totalFundingDebit.setScale(fundingCurrency.getDecimalPlaces(), RoundingMode.HALF_UP));
        values.put("destinationAmount", destinationAmount);
        return new ProductPricingPreviewResponse("SUPPORTED", "FOREX", destinationCode, fundingCode, destinationCode,
                "FOREX_REFERENCE_MARKUP", values, List.of(
                "Reference rate direction is " + fundingCode + " per one " + destinationCode + " unit",
                "The supplied reference rate is for preview only and is not a live quote"));
    }

    private BigDecimal calculateForexCharges(ProductConfigurationRequest request, BigDecimal amount,
                                               String fundingCode, int scale) {
        if (request.charges() == null) return BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP);
        BigDecimal total = BigDecimal.ZERO;
        for (ProductConfigurationRequest.Charge charge : request.charges()) {
            List<ProductConfigurationRequest.ChargeSlabConfig> covering = charge.slabs().stream()
                    .filter(value -> amount.compareTo(value.valueFrom()) >= 0 && amount.compareTo(value.valueTo()) <= 0)
                    .toList();
            List<ProductConfigurationRequest.ChargeSlabConfig> fundingSlabs = covering.stream()
                    .filter(value -> fundingCode.equals(normalize(value.currencyCode()))).toList();
            if (fundingSlabs.isEmpty()) {
                if (!covering.isEmpty()) {
                    throw new InvalidInputException("Charge " + charge.chargeCode()
                            + " requires unsupported fee-currency conversion to " + fundingCode);
                }
                continue;
            }
            if (fundingSlabs.size() != 1) throw new InvalidInputException("Charge " + charge.chargeCode() + " is ambiguous");
            ProductConfigurationRequest.ChargeSlabConfig slab = fundingSlabs.get(0);
            BigDecimal computed = "DECIMAL_FRACTION".equals(normalize(slab.unit()))
                    ? amount.multiply(slab.value(), MathContext.DECIMAL128) : slab.value();
            if (slab.floor() != null && computed.compareTo(slab.floor()) < 0) computed = slab.floor();
            if (slab.cap() != null && computed.compareTo(slab.cap()) > 0) computed = slab.cap();
            total = total.add(computed);
        }
        return total.setScale(scale, RoundingMode.HALF_UP);
    }

    private BigDecimal calculateCharges(ProductConfigurationRequest request, BigDecimal amount, String currencyCode, int scale) {
        if (request.charges() == null) return BigDecimal.ZERO.setScale(scale, RoundingMode.HALF_UP);
        BigDecimal total = BigDecimal.ZERO;
        for (ProductConfigurationRequest.Charge charge : request.charges()) {
            ProductConfigurationRequest.ChargeSlabConfig slab = charge.slabs().stream()
                    .filter(value -> currencyCode.equals(normalize(value.currencyCode())))
                    .filter(value -> amount.compareTo(value.valueFrom()) >= 0 && amount.compareTo(value.valueTo()) <= 0)
                    .findFirst().orElse(null);
            if (slab == null) continue;
            BigDecimal computed = "DECIMAL_FRACTION".equals(normalize(slab.unit())) ? amount.multiply(slab.value()) : slab.value();
            if (slab.floor() != null && computed.compareTo(slab.floor()) < 0) computed = slab.floor();
            if (slab.cap() != null && computed.compareTo(slab.cap()) > 0) computed = slab.cap();
            total = total.add(computed);
        }
        return total.setScale(scale, RoundingMode.HALF_UP);
    }

    private CreateFdOfferRequest fdRequest(ProductConfigurationRequest request) {
        Map<String, List<ProductConfigurationRequest.PricingBand>> bands = request.pricingBands().stream()
                .collect(Collectors.groupingBy(value -> normalize(value.currencyCode())));
        List<CreateFdOfferRequest.CurrencySchedule> schedules = request.currencies().stream().map(config ->
                new CreateFdOfferRequest.CurrencySchedule(normalize(config.currencyCode()), config.minimumAmount(),
                        config.maximumAmount(), bands.getOrDefault(normalize(config.currencyCode()), List.of()).stream()
                        .map(band -> new CreateFdOfferRequest.TenureRateBand(band.valueFrom().intValueExact(),
                                band.valueTo().intValueExact(), band.rate())).toList())).toList();
        return new CreateFdOfferRequest(request.productCode(), request.productName(), request.categoryId(),
                request.description(), request.baseCurrencyCode(), schedules);
    }

    private ProductConfigurationResponse response(Product product, Map<Integer, ProductCategory> categoryMap) {
        ProductCategory category = categoryMap.get(product.getCategoryId());
        List<String> codes = currencyConfigs.findByProductId(product.getProductId()).stream()
                .map(config -> currencies.findById(config.getCurrencyId()).map(Currency::getIsoCode).orElse(null))
                .filter(Objects::nonNull).sorted().toList();
        Long fdProductId = fdProducts.findByProductCodeIgnoreCase(product.getProductCode()).map(FdProductReference::getId).orElse(null);
        int revision = revisions.findTopByProductIdOrderByRevisionNoDesc(product.getProductId())
                .map(ProductConfigurationRevision::getRevisionNo).orElse(0);
        return new ProductConfigurationResponse(product.getProductId(), fdProductId, product.getProductCode(),
                product.getProductName(), family(category, categoryMap), category == null ? null : category.getCategoryCode(),
                product.getStatus(), product.getBaseCurrencyCode(), codes, revision);
    }

    private void saveRevision(Integer productId, String status, ProductConfigurationRequest request) {
        ProductConfigurationRevision revision = new ProductConfigurationRevision();
        revision.setProductId(productId);
        revision.setRevisionNo(revisions.findTopByProductIdOrderByRevisionNoDesc(productId)
                .map(value -> value.getRevisionNo() + 1).orElse(1));
        revision.setStatus(status);
        revision.setSnapshotJson(writeSnapshot(request));
        revision.setCreatedBy(CREATOR);
        revision.setCreatedDate(LocalDateTime.now());
        revisions.save(revision);
    }

    private String writeSnapshot(ProductConfigurationRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize product configuration snapshot", exception);
        }
    }

    private ProductConfigurationRequest readSnapshot(String json) {
        try {
            return objectMapper.readValue(json, ProductConfigurationRequest.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not read product configuration snapshot", exception);
        }
    }

    private ProductRule rule(Integer productId, RuleType type, String name, int priority, String combineLogic) {
        ProductRule rule = new ProductRule();
        rule.setProductId(productId);
        rule.setRuleTypeId(type.getRuleTypeId());
        rule.setRuleName(name);
        rule.setPriority(priority);
        rule.setCombineLogic(normalize(combineLogic == null ? "AND" : combineLogic));
        rule.setIsActive(true);
        audit(rule);
        return rules.save(rule);
    }

    private RuleOutcome outcome(Integer ruleId, String type, BigDecimal value, String unit, Integer currencyId) {
        RuleOutcome outcome = new RuleOutcome();
        outcome.setRuleId(ruleId);
        outcome.setOutcomeType(type);
        outcome.setOutcomeValue(value);
        outcome.setUnit(unit);
        outcome.setCurrencyId(currencyId);
        audit(outcome);
        return outcome;
    }

    private RuleType ruleType(String code) {
        return ruleTypes.findByRuleTypeCode(code)
                .orElseThrow(() -> new IllegalStateException(code + " rule type migration is missing"));
    }

    private Currency currency(String code) {
        return currencies.findByIsoCode(normalize(code)).filter(value -> Boolean.TRUE.equals(value.getIsActive()))
                .orElseThrow(() -> new ResourceNotFoundException("Active currency not found: " + code));
    }

    private String family(Integer categoryId) {
        ProductCategory category = categories.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Product category not found: " + categoryId));
        Map<Integer, ProductCategory> categoryMap = categories.findAll().stream()
                .collect(Collectors.toMap(ProductCategory::getCategoryId, Function.identity()));
        return family(category, categoryMap);
    }

    private String safeFamily(Integer categoryId) {
        if (categoryId == null) return "UNKNOWN";
        return categories.findById(categoryId).map(category -> {
            Map<Integer, ProductCategory> categoryMap = categories.findAll().stream()
                    .collect(Collectors.toMap(ProductCategory::getCategoryId, Function.identity()));
            return family(category, categoryMap);
        }).orElse("UNKNOWN");
    }

    private String family(ProductCategory category, Map<Integer, ProductCategory> categoryMap) {
        ProductCategory current = category;
        Set<Integer> visited = new HashSet<>();
        while (current != null && visited.add(current.getCategoryId())) {
            String code = normalize(current.getCategoryCode());
            if (FAMILIES.contains(code)) return code;
            current = current.getParentCategoryId() == null ? null : categoryMap.get(current.getParentCategoryId());
        }
        return "UNKNOWN";
    }

    private List<ProductCategory> lineage(Integer categoryId, Map<Integer, ProductCategory> categoryMap) {
        List<ProductCategory> lineage = new ArrayList<>();
        ProductCategory current = categoryMap.get(categoryId);
        Set<Integer> visited = new HashSet<>();
        while (current != null && visited.add(current.getCategoryId())) {
            lineage.add(current);
            current = current.getParentCategoryId() == null ? null : categoryMap.get(current.getParentCategoryId());
        }
        return lineage;
    }

    private boolean matches(ProductConfigurationRequest.EligibilityRule rule, String supplied) {
        if (supplied == null) return false;
        String operator = normalize(rule.operator());
        if ("NUMBER".equals(normalize(rule.dataType()))) {
            BigDecimal actual = new BigDecimal(supplied);
            BigDecimal from = rule.valueFrom() == null ? null : new BigDecimal(rule.valueFrom());
            BigDecimal to = rule.valueTo() == null ? null : new BigDecimal(rule.valueTo());
            return switch (operator) {
                case "EQ" -> actual.compareTo(from) == 0;
                case "NE" -> actual.compareTo(from) != 0;
                case "GT" -> actual.compareTo(from) > 0;
                case "GTE" -> actual.compareTo(from) >= 0;
                case "LT" -> actual.compareTo(from) < 0;
                case "LTE" -> actual.compareTo(from) <= 0;
                case "BETWEEN_INCLUSIVE" -> actual.compareTo(from) >= 0 && actual.compareTo(to) <= 0;
                default -> false;
            };
        }
        if ("IN".equals(operator)) return split(rule.valueSet()).contains(normalize(supplied));
        boolean equal = normalize(supplied).equals(normalize(rule.valueFrom()));
        return "NE".equals(operator) ? !equal : equal;
    }

    private BigDecimal decimalFact(Map<String, String> facts, String key) {
        String value = facts.get(key);
        if (value == null || value.isBlank()) throw new InvalidInputException("Preview fact is required: " + key);
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw new InvalidInputException("Preview fact must be numeric: " + key);
        }
    }

    private String requiredCodeFact(Map<String, String> facts, String key) {
        String value = normalize(facts.get(key));
        if (value.isBlank()) throw new InvalidInputException("Preview fact is required: " + key);
        return value;
    }

    private int integerFact(Map<String, String> facts, String key) {
        return decimalFact(facts, key).intValueExact();
    }

    private static List<String> split(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split(",")).map(ProductConfigurationService::normalize)
                .filter(item -> !item.isBlank()).toList();
    }

    private static List<String> operatorsFor(String dataType) {
        return "NUMBER".equals(dataType) ? List.of("EQ", "NE", "GT", "GTE", "LT", "LTE", "BETWEEN_INCLUSIVE")
                : List.of("EQ", "NE", "IN");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private static String date(LocalDate value) {
        return value == null ? null : value.toString();
    }

    private static void audit(ProductCurrencyConfig value) {
        value.setCreatedBy(CREATOR); value.setCreatedDate(LocalDateTime.now());
        value.setUuid(UUID.randomUUID().toString()); value.setCrudValue("C");
    }
    private static void audit(ProductRule value) {
        value.setCreatedBy(CREATOR); value.setCreatedDate(LocalDateTime.now());
        value.setUuid(UUID.randomUUID().toString()); value.setCrudValue("C");
    }
    private static void audit(RuleCondition value) {
        value.setCreatedBy(CREATOR); value.setCreatedDate(LocalDateTime.now());
        value.setUuid(UUID.randomUUID().toString()); value.setCrudValue("C");
    }
    private static void audit(RuleOutcome value) {
        value.setCreatedBy(CREATOR); value.setCreatedDate(LocalDateTime.now());
        value.setUuid(UUID.randomUUID().toString()); value.setCrudValue("C");
    }
    private static void audit(ChargeConfig value) {
        value.setCreatedBy(CREATOR); value.setCreatedDate(LocalDateTime.now());
        value.setUuid(UUID.randomUUID().toString()); value.setCrudValue("C");
    }
    private static void audit(ChargeSlab value) {
        value.setCreatedBy(CREATOR); value.setCreatedDate(LocalDateTime.now());
        value.setUuid(UUID.randomUUID().toString()); value.setCrudValue("C");
    }

    private record FactDefinition(String dataType, boolean executable, String dependency) { }
    private record FamilyPricing(String fact, String outcomeType, String calculationMethod) { }
}
