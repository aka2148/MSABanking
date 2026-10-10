package com.bank.product.service;

import com.bank.common.exception.InvalidInputException;
import com.bank.common.exception.ResourceNotFoundException;
import com.bank.fd.entity.FdCurrencyReference;
import com.bank.fd.entity.FdProductReference;
import com.bank.fd.entity.FdRuleOutcomeReference;
import com.bank.fd.repository.FdCurrencyReferenceRepository;
import com.bank.fd.repository.FdProductReferenceRepository;
import com.bank.fd.repository.FdRuleOutcomeReferenceRepository;
import com.bank.product.dto.request.CreateFdOfferRequest;
import com.bank.product.dto.response.FdOfferAdminResponse;
import com.bank.product.entity.Currency;
import com.bank.product.entity.Product;
import com.bank.product.entity.ProductCurrencyConfig;
import com.bank.product.entity.ProductRule;
import com.bank.product.entity.RuleCondition;
import com.bank.product.entity.RuleOutcome;
import com.bank.product.entity.RuleType;
import com.bank.product.repository.CurrencyRepository;
import com.bank.product.repository.ProductCategoryRepository;
import com.bank.product.repository.ProductCurrencyConfigRepository;
import com.bank.product.repository.ProductRepository;
import com.bank.product.repository.ProductRuleRepository;
import com.bank.product.repository.RuleConditionRepository;
import com.bank.product.repository.RuleOutcomeRepository;
import com.bank.product.repository.RuleTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/** Creates the small, typed FD product/rate vocabulary on top of the existing product-rule tables. */
@Service
public class FdOfferAdminService {
    private static final String CREATOR = "SYSTEM";
    private static final String TENURE_RATE = "TENURE_RATE";
    private static final String AMOUNT_RANGE = "AMOUNT_RANGE";
    private static final String INTEREST_RATE = "INTEREST_RATE";
    private static final String QUARTERLY = "QUARTERLY";
    private static final String AT_MATURITY = "AT_MATURITY";

    private final ProductRepository products;
    private final ProductCategoryRepository categories;
    private final CurrencyRepository currencies;
    private final ProductCurrencyConfigRepository currencyConfigs;
    private final ProductRuleRepository rules;
    private final RuleConditionRepository conditions;
    private final RuleOutcomeRepository outcomes;
    private final RuleTypeRepository ruleTypes;
    private final FdProductReferenceRepository fdProducts;
    private final FdCurrencyReferenceRepository fdCurrencies;
    private final FdRuleOutcomeReferenceRepository fdOutcomes;

    public FdOfferAdminService(ProductRepository products,
                               ProductCategoryRepository categories,
                               CurrencyRepository currencies,
                               ProductCurrencyConfigRepository currencyConfigs,
                               ProductRuleRepository rules,
                               RuleConditionRepository conditions,
                               RuleOutcomeRepository outcomes,
                               RuleTypeRepository ruleTypes,
                               FdProductReferenceRepository fdProducts,
                               FdCurrencyReferenceRepository fdCurrencies,
                               FdRuleOutcomeReferenceRepository fdOutcomes) {
        this.products = products;
        this.categories = categories;
        this.currencies = currencies;
        this.currencyConfigs = currencyConfigs;
        this.rules = rules;
        this.conditions = conditions;
        this.outcomes = outcomes;
        this.ruleTypes = ruleTypes;
        this.fdProducts = fdProducts;
        this.fdCurrencies = fdCurrencies;
        this.fdOutcomes = fdOutcomes;
    }

    @Transactional
    public FdOfferAdminResponse create(CreateFdOfferRequest request) {
        validateSchedule(request);
        String code = request.productCode().trim().toUpperCase(Locale.ROOT);
        if (products.existsByProductCodeIgnoreCase(code) || fdProducts.findByProductCodeIgnoreCase(code).isPresent()) {
            throw new InvalidInputException("Product code is already in use: " + code);
        }
        if (!categories.existsById(request.categoryId())) {
            throw new ResourceNotFoundException("Product category not found: " + request.categoryId());
        }

        Product product = new Product();
        product.setProductCode(code);
        product.setProductName(request.productName().trim());
        product.setCategoryId(request.categoryId());
        product.setBaseCurrencyCode("USD");
        product.setDescription(request.description());
        product.setStatus("DRAFT");
        product.setUuid(UUID.randomUUID().toString());
        product.setCreatedBy(CREATOR);
        product.setCreatedDate(LocalDateTime.now());
        product.setCrudValue("C");
        Product savedProduct = products.save(product);

        FdProductReference reference = new FdProductReference();
        reference.setProductCode(code);
        reference.setProductName(product.getProductName());
        reference.setStatus("DRAFT");
        FdProductReference savedReference = fdProducts.save(reference);

        int priority = 1;
        for (CreateFdOfferRequest.CurrencySchedule schedule : request.currencies()) {
            String iso = schedule.currencyCode().trim().toUpperCase(Locale.ROOT);
            Currency currency = currencies.findByIsoCode(iso)
                    .filter(value -> Boolean.TRUE.equals(value.getIsActive()))
                    .orElseThrow(() -> new ResourceNotFoundException("Active currency not configured: " + iso));
            ensureLocalCurrency(currency);

            ProductCurrencyConfig config = new ProductCurrencyConfig();
            config.setProductId(savedProduct.getProductId());
            config.setCurrencyId(currency.getCurrencyId());
            config.setIsBaseCurrency("USD".equals(iso));
            config.setWalletEnabled(false);
            config.setCreatedBy(CREATOR);
            config.setCreatedDate(LocalDateTime.now());
            config.setUuid(UUID.randomUUID().toString());
            config.setCrudValue("C");
            currencyConfigs.save(config);

            RuleType rateType = ruleTypes.findByRuleTypeCode("FD_TENURE_RATE")
                    .orElseThrow(() -> new IllegalStateException("FD_TENURE_RATE rule type migration is missing"));
            for (CreateFdOfferRequest.TenureRateBand band : schedule.rateBands().stream()
                    .sorted(Comparator.comparing(CreateFdOfferRequest.TenureRateBand::minimumTenureMonths)).toList()) {
                ProductRule rule = createRateRule(savedProduct.getProductId(), rateType, priority++);
                createCondition(rule, "TENURE_MONTHS", band.minimumTenureMonths().toString(),
                        band.maximumTenureMonths().toString());
                createCondition(rule, AMOUNT_RANGE, schedule.minimumAmount().toPlainString(),
                        schedule.maximumAmount().toPlainString());
                RuleOutcome outcome = createRateOutcome(rule, currency, band.annualRate());
                createLocalOutcomeReference(outcome, band.annualRate());
            }
        }
        return response(savedReference, savedProduct);
    }

    @Transactional
    public FdOfferAdminResponse activate(Long fdProductId) {
        FdProductReference reference = fdProducts.findById(fdProductId)
                .orElseThrow(() -> new ResourceNotFoundException("FD product not found: " + fdProductId));
        Product product = products.findByProductCode(reference.getProductCode())
                .orElseThrow(() -> new ResourceNotFoundException("Catalog product is not linked to the FD product"));
        List<String> currencyCodes = currencyConfigs.findByProductId(product.getProductId()).stream()
                .map(config -> currencies.findById(config.getCurrencyId())
                        .map(Currency::getIsoCode).orElse(null))
                .filter(code -> code != null)
                .sorted()
                .toList();
        if (!currencyCodes.containsAll(List.of("USD", "INR"))) {
            throw new InvalidInputException("An FD offer must be configured for both USD and INR before activation");
        }
        for (String currencyCode : currencyCodes) {
            Currency currency = currencies.findByIsoCode(currencyCode)
                    .orElseThrow(() -> new ResourceNotFoundException("Currency not found: " + currencyCode));
            ensureLocalCurrency(currency);
            validatePersistedBands(product.getProductId(), currency.getCurrencyId());
        }
        product.setStatus("ACTIVE");
        product.setUpdatedBy(CREATOR);
        product.setUpdatedDate(LocalDateTime.now());
        product.setCrudValue("U");
        products.save(product);
        reference.setStatus("ACTIVE");
        reference.setProductName(product.getProductName());
        FdProductReference savedReference = fdProducts.save(reference);
        return response(savedReference, product);
    }

    @Transactional(readOnly = true)
    public List<FdOfferAdminResponse> list() {
        return fdProducts.findAll().stream().map(reference -> products.findByProductCode(reference.getProductCode())
                .map(product -> response(reference, product))
                .orElse(new FdOfferAdminResponse(reference.getId(), null, reference.getProductCode(),
                        reference.getProductName(), reference.getStatus(), List.of())))
                .toList();
    }

    private void validatePersistedBands(Integer productId, Integer currencyId) {
        List<ProductRule> rateRules = rules.findByProductIdAndRuleNameAndIsActiveTrue(productId, TENURE_RATE);
        List<Band> bands = new ArrayList<>();
        for (ProductRule rule : rateRules) {
            List<RuleOutcome> matching = outcomes.findByRuleId(rule.getRuleId()).stream()
                    .filter(outcome -> INTEREST_RATE.equalsIgnoreCase(outcome.getOutcomeType())
                            && currencyId.equals(outcome.getCurrencyId())).toList();
            if (matching.isEmpty()) continue;
            if (matching.size() != 1) throw new InvalidInputException("Each FD rate tier must have one rate outcome");
            RuleOutcome rate = matching.get(0);
            if (rate.getOutcomeValue() == null || rate.getOutcomeValue().signum() < 0
                    || rate.getOutcomeValue().compareTo(BigDecimal.ONE) > 0
                    || !"DECIMAL_FRACTION".equalsIgnoreCase(rate.getUnit())
                    || !QUARTERLY.equalsIgnoreCase(rate.getCompoundingBasis())
                    || !AT_MATURITY.equalsIgnoreCase(rate.getPayoutFrequency())
                    || fdOutcomes.findBySourceOutcomeId(rate.getRoOutcomeId()).isEmpty()) {
                throw new InvalidInputException("FD rate tier has an invalid rate, reference, or interest convention");
            }
            List<RuleCondition> ruleConditions = conditions.findByRuleId(rule.getRuleId());
            RuleCondition tenure = condition(ruleConditions, "TENURE_MONTHS");
            RuleCondition amount = condition(ruleConditions, AMOUNT_RANGE);
            if (tenure == null || amount == null) throw new InvalidInputException("FD rate tier is missing eligibility bounds");
            try {
                bands.add(new Band(Integer.parseInt(tenure.getValueFrom()), Integer.parseInt(tenure.getValueTo()),
                        new BigDecimal(amount.getValueFrom()), new BigDecimal(amount.getValueTo())));
            } catch (RuntimeException exception) {
                throw new InvalidInputException("FD rate tier has invalid tenure or amount bounds");
            }
        }
        bands.sort(Comparator.comparingInt(Band::minimumMonth));
        if (bands.isEmpty()) throw new InvalidInputException("Currency has no active FD rate tiers");
        for (int i = 1; i < bands.size(); i++) {
            if (bands.get(i).minimumMonth() != bands.get(i - 1).maximumMonth() + 1) {
                throw new InvalidInputException("FD rate tiers must not overlap or leave uncovered tenure months");
            }
            if (bands.get(i).minimumAmount().compareTo(bands.get(i - 1).minimumAmount()) != 0
                    || bands.get(i).maximumAmount().compareTo(bands.get(i - 1).maximumAmount()) != 0) {
                throw new InvalidInputException("All tenure tiers for a currency must use the same amount bounds");
            }
        }
    }

    private ProductRule createRateRule(Integer productId, RuleType ruleType, int priority) {
        ProductRule rule = new ProductRule();
        rule.setProductId(productId);
        rule.setRuleTypeId(ruleType.getRuleTypeId());
        rule.setRuleName(TENURE_RATE);
        rule.setPriority(priority);
        rule.setCombineLogic("AND");
        rule.setIsActive(true);
        rule.setCreatedBy(CREATOR);
        rule.setCreatedDate(LocalDateTime.now());
        rule.setCrudValue("C");
        rule.setUuid(UUID.randomUUID().toString());
        return rules.save(rule);
    }

    private void createCondition(ProductRule rule, String attribute, String from, String to) {
        RuleCondition condition = new RuleCondition();
        condition.setRuleId(rule.getRuleId());
        condition.setConditionAttribute(attribute);
        condition.setOperator("BETWEEN_INCLUSIVE");
        condition.setValueFrom(from);
        condition.setValueTo(to);
        condition.setCreatedBy(CREATOR);
        condition.setCreatedDate(LocalDateTime.now());
        condition.setCrudValue("C");
        condition.setUuid(UUID.randomUUID().toString());
        conditions.save(condition);
    }

    private RuleOutcome createRateOutcome(ProductRule rule, Currency currency, BigDecimal annualRate) {
        RuleOutcome outcome = new RuleOutcome();
        outcome.setRuleId(rule.getRuleId());
        outcome.setOutcomeType(INTEREST_RATE);
        outcome.setOutcomeValue(annualRate.setScale(4, java.math.RoundingMode.HALF_UP));
        outcome.setUnit("DECIMAL_FRACTION");
        outcome.setCompoundingBasis(QUARTERLY);
        outcome.setPayoutFrequency(AT_MATURITY);
        outcome.setCurrencyId(currency.getCurrencyId());
        outcome.setCreatedBy(CREATOR);
        outcome.setCreatedDate(LocalDateTime.now());
        outcome.setCrudValue("C");
        outcome.setUuid(UUID.randomUUID().toString());
        return outcomes.save(outcome);
    }

    private void createLocalOutcomeReference(RuleOutcome outcome, BigDecimal rate) {
        FdRuleOutcomeReference reference = new FdRuleOutcomeReference();
        reference.setOutcomeType(INTEREST_RATE);
        reference.setOutcomeValue(rate.setScale(4, java.math.RoundingMode.HALF_UP).toPlainString());
        reference.setUnit("DECIMAL_FRACTION");
        reference.setSourceOutcomeId(outcome.getRoOutcomeId());
        fdOutcomes.save(reference);
    }

    private void ensureLocalCurrency(Currency currency) {
        fdCurrencies.findByIsoCode(currency.getIsoCode()).orElseGet(() -> {
            FdCurrencyReference reference = new FdCurrencyReference();
            reference.setIsoCode(currency.getIsoCode());
            reference.setCurrencyName(currency.getCurrencyName());
            reference.setDecimalPlaces(currency.getDecimalPlaces());
            return fdCurrencies.save(reference);
        });
    }

    private static RuleCondition condition(List<RuleCondition> values, String attribute) {
        List<RuleCondition> matching = values.stream()
                .filter(value -> attribute.equalsIgnoreCase(value.getConditionAttribute())).toList();
        return matching.size() == 1 ? matching.get(0) : null;
    }

    private void validateSchedule(CreateFdOfferRequest request) {
        if (request == null) throw new InvalidInputException("FD offer request is required");
        Set<String> codes = new HashSet<>();
        for (CreateFdOfferRequest.CurrencySchedule schedule : request.currencies()) {
            String code = schedule.currencyCode().trim().toUpperCase(Locale.ROOT);
            if (!codes.add(code)) throw new InvalidInputException("Currency appears more than once: " + code);
            if (schedule.minimumAmount().compareTo(schedule.maximumAmount()) > 0) {
                throw new InvalidInputException("Minimum amount must not exceed maximum amount for " + code);
            }
            List<CreateFdOfferRequest.TenureRateBand> bands = schedule.rateBands().stream()
                    .sorted(Comparator.comparing(CreateFdOfferRequest.TenureRateBand::minimumTenureMonths)).toList();
            for (int i = 0; i < bands.size(); i++) {
                var band = bands.get(i);
                if (band.minimumTenureMonths() > band.maximumTenureMonths()) {
                    throw new InvalidInputException("Minimum tenure must not exceed maximum tenure");
                }
                if (band.annualRate().scale() > 4) {
                    throw new InvalidInputException("Annual rates support at most four decimal places");
                }
                if (i > 0 && band.minimumTenureMonths() != bands.get(i - 1).maximumTenureMonths() + 1) {
                    throw new InvalidInputException("Tenure bands must not overlap or leave uncovered months");
                }
            }
        }
        if (!codes.containsAll(List.of("USD", "INR"))) {
            throw new InvalidInputException("Configure both USD and INR schedules for the initial FD product");
        }
    }

    private FdOfferAdminResponse response(FdProductReference reference, Product product) {
        List<String> currencyCodes = currencyConfigs.findByProductId(product.getProductId()).stream()
                .map(config -> currencies.findById(config.getCurrencyId()).map(Currency::getIsoCode).orElse(null))
                .filter(code -> code != null).sorted().toList();
        return new FdOfferAdminResponse(reference.getId(), product.getProductId(), product.getProductCode(),
                product.getProductName(), reference.getStatus(), currencyCodes);
    }

    private record Band(int minimumMonth, int maximumMonth, BigDecimal minimumAmount, BigDecimal maximumAmount) { }
}
