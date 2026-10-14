package com.bank.product;

import com.bank.product.entity.ChargeSlab;
import com.bank.product.entity.Currency;
import com.bank.product.entity.ProductCategory;
import com.bank.product.entity.RuleOutcome;
import com.bank.product.entity.RuleType;
import com.bank.product.repository.ChargeConfigRepository;
import com.bank.product.repository.ChargeSlabRepository;
import com.bank.product.repository.CurrencyRepository;
import com.bank.product.repository.ProductCategoryRepository;
import com.bank.product.repository.ProductRuleRepository;
import com.bank.product.repository.RuleOutcomeRepository;
import com.bank.product.repository.RuleTypeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductConfigurationRegressionTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProductCategoryRepository categories;
    @Autowired private CurrencyRepository currencies;
    @Autowired private RuleTypeRepository ruleTypes;
    @Autowired private ProductRuleRepository rules;
    @Autowired private RuleOutcomeRepository outcomes;
    @Autowired private ChargeConfigRepository charges;
    @Autowired private ChargeSlabRepository slabs;

    @Test
    @WithMockUser(roles = "BANK_OFFICER")
    void ordinaryCreationIsDraftAndArbitraryStatusIsRejected() throws Exception {
        ProductCategory loan = category("LOAN");
        String response = mockMvc.perform(post("/api/v1/products")
                        .contentType("application/json")
                        .content("""
                                {"productCode":"GENERIC_ACTIVE","productName":"Generic active attempt",
                                 "categoryId":%d,"baseCurrencyCode":"INR","status":"ACTIVE"}
                                """.formatted(loan.getCategoryId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        int productId = objectMapper.readTree(response).get("productId").asInt();

        mockMvc.perform(patch("/api/v1/products/{id}/status", productId)
                        .contentType("application/json").content("{\"status\":\"SUSPENDED\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Unsupported product status transition")));
        mockMvc.perform(patch("/api/v1/products/{id}/status", productId)
                        .contentType("application/json").content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("no product-builder configuration revision")));
    }

    @Test
    @WithMockUser(roles = "BANK_OFFICER")
    void legacyFdProjectionCannotBypassValidatedActivation() throws Exception {
        String response = mockMvc.perform(post("/api/product")
                        .contentType("application/json")
                        .content("{\"productCode\":\"LEGACY_FD_BYPASS\",\"productName\":\"Legacy FD\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        long productId = objectMapper.readTree(response).get("id").asLong();

        mockMvc.perform(put("/api/product/{id}", productId)
                        .contentType("application/json")
                        .content("{\"productCode\":\"LEGACY_FD_BYPASS\",\"productName\":\"Legacy FD\",\"status\":\"ACTIVE\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("validated /api/v1/fd-products activation endpoint")));
    }

    @Test
    @WithMockUser(roles = "BANK_OFFICER")
    void activationRejectsRateAndChargeDriftWithoutRowCountChanges() throws Exception {
        ProductCategory loan = category("LOAN");
        currency("INR", 2);
        ruleType("PRICING_BAND");
        ruleType("ELIGIBILITY");
        int productId = createLoan(loan.getCategoryId());

        var pricingRule = rules.findByProductId(productId).stream()
                .filter(rule -> "PRICING_BAND".equals(rule.getRuleName())).findFirst().orElseThrow();
        RuleOutcome outcome = outcomes.findByRuleId(pricingRule.getRuleId()).get(0);
        long outcomeCount = outcomes.count();
        outcome.setOutcomeValue(new BigDecimal("0.095"));
        outcomes.saveAndFlush(outcome);

        mockMvc.perform(post("/api/v1/product-configurations/{id}/activate", productId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("conditions, outcomes, rates")));
        assertEquals(outcomeCount, outcomes.count());

        outcome.setOutcomeValue(new BigDecimal("0.085"));
        outcomes.saveAndFlush(outcome);
        var charge = charges.findByProductId(productId).get(0);
        ChargeSlab slab = slabs.findByChargeId(charge.getChargeId()).get(0);
        long slabCount = slabs.count();
        slab.setChargeAmount(new BigDecimal("1001"));
        slabs.saveAndFlush(slab);

        mockMvc.perform(post("/api/v1/product-configurations/{id}/activate", productId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("charges or charge slabs")));
        assertEquals(slabCount, slabs.count());

        slab.setChargeAmount(new BigDecimal("1000"));
        slabs.saveAndFlush(slab);
        mockMvc.perform(post("/api/v1/product-configurations/{id}/activate", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(put("/api/v1/products/{id}", productId)
                        .contentType("application/json").content("{\"productName\":\"Silent edit\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("immutable")));
    }

    @Test
    @WithMockUser(roles = "BANK_OFFICER")
    void forexPreviewKeepsInrFeesSeparateForUsdAndKwd() throws Exception {
        ProductCategory forex = category("FOREX");
        currency("INR", 2);
        currency("USD", 2);
        currency("KWD", 3);
        ruleType("PRICING_BAND");
        ruleType("ELIGIBILITY");

        mockMvc.perform(post("/api/v1/product-configurations/preview")
                        .contentType("application/json").content(forexPreview(forex.getCategoryId(), "USD", "83.25", "INR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUPPORTED"))
                .andExpect(jsonPath("$.fundingCurrencyCode").value("INR"))
                .andExpect(jsonPath("$.destinationCurrencyCode").value("USD"))
                .andExpect(jsonPath("$.values.fundingCurrencyFees").value(1000.00))
                .andExpect(jsonPath("$.values.totalFundingDebit").value(101000.00))
                .andExpect(jsonPath("$.values.destinationAmount").value(1186.37));

        String totalBudgetPreview = forexPreview(forex.getCategoryId(), "USD", "83.25", "INR")
                .replace("CONVERSION_PRINCIPAL", "TOTAL_BUDGET");
        mockMvc.perform(post("/api/v1/product-configurations/preview")
                        .contentType("application/json").content(totalBudgetPreview))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.values.conversionPrincipal").value(99000.00))
                .andExpect(jsonPath("$.values.fundingCurrencyFees").value(1000.00))
                .andExpect(jsonPath("$.values.totalFundingDebit").value(100000.00))
                .andExpect(jsonPath("$.values.destinationAmount").value(1174.51));

        mockMvc.perform(post("/api/v1/product-configurations/preview")
                        .contentType("application/json").content(forexPreview(forex.getCategoryId(), "KWD", "270", "INR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destinationCurrencyCode").value("KWD"))
                .andExpect(jsonPath("$.values.fundingCurrencyFees").value(1000.00))
                .andExpect(jsonPath("$.values.totalFundingDebit").value(101000.00))
                .andExpect(jsonPath("$.values.destinationAmount").value(364.897));

        mockMvc.perform(post("/api/v1/product-configurations/preview")
                        .contentType("application/json").content(forexPreview(forex.getCategoryId(), "USD", "83.25", "USD")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNSUPPORTED"))
                .andExpect(jsonPath("$.messages[0]", containsString("funding/base currency")));
    }

    private int createLoan(int categoryId) throws Exception {
        String payload = """
                {"productCode":"INTEGRITY_LOAN","productName":"Integrity Loan","categoryId":%d,
                 "description":"Revision integrity regression","baseCurrencyCode":"INR",
                 "currencies":[{"currencyCode":"INR","minimumAmount":10000,"maximumAmount":5000000}],
                 "attributes":[],"eligibilityRules":[],
                 "pricingBands":[{"currencyCode":"INR","fact":"TENURE_MONTHS","valueFrom":6,"valueTo":360,
                   "rate":0.085,"outcomeType":"INTEREST_RATE","rateUnit":"DECIMAL_FRACTION",
                   "calculationMethod":"LOAN_REDUCING_BALANCE"}],
                 "charges":[{"chargeCode":"PROCESSING_FEE","calculationBasis":"FIXED","slabs":[
                   {"fact":"AMOUNT","valueFrom":10000,"valueTo":5000000,"value":1000,
                    "unit":"FIXED_AMOUNT","currencyCode":"INR"}]}],"roleConstraints":[]}
                """.formatted(categoryId);
        String response = mockMvc.perform(post("/api/v1/product-configurations")
                        .contentType("application/json").content(payload))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("productId").asInt();
    }

    private String forexPreview(int categoryId, String destination, String referenceRate, String feeCurrency) throws Exception {
        String configuration = """
                {"productCode":"FOREX_PREVIEW","productName":"Forex Preview","categoryId":%d,
                 "baseCurrencyCode":"INR","currencies":[
                   {"currencyCode":"INR","minimumAmount":1000,"maximumAmount":10000000},
                   {"currencyCode":"USD","minimumAmount":10,"maximumAmount":100000},
                   {"currencyCode":"KWD","minimumAmount":10.000,"maximumAmount":100000.000}],
                 "attributes":[],"eligibilityRules":[],"pricingBands":[
                   {"currencyCode":"USD","fact":"TRANSFER_AMOUNT","valueFrom":1000,"valueTo":10000000,
                    "rate":0.0125,"outcomeType":"MARKUP_RATE","rateUnit":"DECIMAL_FRACTION","calculationMethod":"FOREX_REFERENCE_MARKUP"},
                   {"currencyCode":"KWD","fact":"TRANSFER_AMOUNT","valueFrom":1000,"valueTo":10000000,
                    "rate":0.015,"outcomeType":"MARKUP_RATE","rateUnit":"DECIMAL_FRACTION","calculationMethod":"FOREX_REFERENCE_MARKUP"}],
                 "charges":[{"chargeCode":"TRANSFER_FEE","calculationBasis":"FIXED","slabs":[
                   {"fact":"TRANSFER_AMOUNT","valueFrom":1000,"valueTo":10000000,"value":1000,
                    "unit":"FIXED_AMOUNT","currencyCode":"%s"}]}],"roleConstraints":[]}
                """.formatted(categoryId, feeCurrency);
        String facts = """
                {"TRANSFER_AMOUNT":"100000","REFERENCE_RATE":"%s","FUNDING_CURRENCY":"INR",
                 "DESTINATION_CURRENCY":"%s","REFERENCE_RATE_DIRECTION":"FUNDING_PER_DESTINATION",
                 "FOREX_AMOUNT_MODE":"CONVERSION_PRINCIPAL"}
                """.formatted(referenceRate, destination);
        var payload = objectMapper.createObjectNode();
        payload.set("configuration", objectMapper.readTree(configuration));
        payload.set("facts", objectMapper.readTree(facts));
        return objectMapper.writeValueAsString(payload);
    }

    private ProductCategory category(String code) {
        ProductCategory category = new ProductCategory();
        category.setCategoryCode(code);
        category.setCategoryName(code);
        return categories.save(category);
    }

    private void currency(String code, int decimalPlaces) {
        Currency currency = new Currency();
        currency.setIsoCode(code);
        currency.setCurrencyName(code);
        currency.setCurrencySymbol(code);
        currency.setDecimalPlaces(decimalPlaces);
        currency.setIsActive(true);
        currencies.save(currency);
    }

    private void ruleType(String code) {
        RuleType type = new RuleType();
        type.setRuleTypeCode(code);
        type.setDescription(code);
        ruleTypes.save(type);
    }
}
