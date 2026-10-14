package com.bank.product;

import com.bank.fd.repository.FdProductReferenceRepository;
import com.bank.product.entity.Currency;
import com.bank.product.entity.ProductCategory;
import com.bank.product.entity.RuleType;
import com.bank.product.repository.CurrencyRepository;
import com.bank.product.repository.ProductCategoryRepository;
import com.bank.product.repository.ProductRepository;
import com.bank.product.repository.RuleTypeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProductConfigurationFlowTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProductCategoryRepository categories;
    @Autowired private CurrencyRepository currencies;
    @Autowired private RuleTypeRepository ruleTypes;
    @Autowired private ProductRepository products;
    @Autowired private FdProductReferenceRepository fdProducts;

    @Test
    @WithMockUser(roles = "BANK_OFFICER")
    void staffBuildsAllFamiliesAndOnlyFdCreatesFdReferences() throws Exception {
        ProductCategory loan = category("LOAN_CFG", "Loans", null, "LOAN");
        ProductCategory casa = category("CASA_CFG", "CASA", null, "CASA");
        ProductCategory savings = category("SAVINGS_CFG", "Savings", casa, null);
        ProductCategory current = category("CURRENT_CFG", "Current", casa, null);
        ProductCategory fd = category("FD_CFG", "Fixed deposits", null, "FIXED_DEPOSIT");
        ProductCategory forex = category("FOREX_CFG", "Forex", null, "FOREX");
        currency("INR", 2);
        currency("USD", 2);
        currency("KWD", 3);
        ruleType("PRICING_BAND");
        ruleType("ELIGIBILITY");
        ruleType("FD_TENURE_RATE");

        int firstLoan = create(loan.getCategoryId(), "HOME_LOAN_A", "Home Loan A", "INR",
                currencyJson("INR", "10000", "5000000"), pricingJson("INR", "TENURE_MONTHS", 6, 360, "0.085", "INTEREST_RATE", "LOAN_REDUCING_BALANCE"));
        int secondLoan = create(loan.getCategoryId(), "HOME_LOAN_B", "Home Loan B", "INR",
                currencyJson("INR", "25000", "7500000"), pricingJson("INR", "TENURE_MONTHS", 12, 240, "0.0795", "INTEREST_RATE", "LOAN_REDUCING_BALANCE"));
        create(savings.getCategoryId(), "SAVINGS_PLUS", "Savings Plus", "INR",
                currencyJson("INR", "0", "10000000"), pricingJson("INR", "BALANCE", 0, 10000000, "0.035", "CREDIT_INTEREST_RATE", "CASA_DAILY_BALANCE"));
        create(current.getCategoryId(), "CURRENT_START", "Current Start", "INR",
                currencyJson("INR", "0", "10000000"), pricingJson("INR", "BALANCE", 0, 10000000, "0.00", "CREDIT_INTEREST_RATE", "CASA_DAILY_BALANCE"));

        long fdCountBefore = fdProducts.count();
        int fdProduct = create(fd.getCategoryId(), "KWD_FD", "KWD Fixed Deposit", "KWD",
                currencyJson("KWD", "100.000", "100000.000"), pricingJson("KWD", "TENURE_MONTHS", 3, 24, "0.0625", "INTEREST_RATE", "FD_QUARTERLY"));
        assertEquals(fdCountBefore + 1, fdProducts.count());

        String forexCurrencies = """
                [{"currencyCode":"INR","minimumAmount":1000,"maximumAmount":10000000},
                 {"currencyCode":"USD","minimumAmount":10,"maximumAmount":100000},
                 {"currencyCode":"KWD","minimumAmount":10.000,"maximumAmount":100000.000}]
                """;
        String forexPricing = """
                [{"currencyCode":"USD","fact":"TRANSFER_AMOUNT","valueFrom":1000,"valueTo":10000000,
                  "rate":0.0125,"outcomeType":"MARKUP_RATE","rateUnit":"DECIMAL_FRACTION","calculationMethod":"FOREX_REFERENCE_MARKUP"},
                 {"currencyCode":"KWD","fact":"TRANSFER_AMOUNT","valueFrom":1000,"valueTo":10000000,
                  "rate":0.015,"outcomeType":"MARKUP_RATE","rateUnit":"DECIMAL_FRACTION","calculationMethod":"FOREX_REFERENCE_MARKUP"}]
                """;
        create(forex.getCategoryId(), "INR_FOREX", "INR Outward Transfer", "INR", forexCurrencies, forexPricing);
        assertEquals(fdCountBefore + 1, fdProducts.count(), "Loan, CASA and Forex must not create fd_* references");

        mockMvc.perform(post("/api/v1/product-configurations/preview")
                        .contentType("application/json")
                        .content(previewPayload(loan.getCategoryId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUPPORTED"))
                .andExpect(jsonPath("$.values.monthlyInstallment", greaterThan(0.0)));

        String unsupportedIncome = previewPayload(loan.getCategoryId()).replace(
                "\"eligibilityRules\":[]",
                "\"eligibilityRules\":[{\"name\":\"Income minimum\",\"fact\":\"ANNUAL_INCOME\",\"dataType\":\"NUMBER\",\"operator\":\"GTE\",\"expectedValue\":\"500000\",\"combineLogic\":\"AND\"}]");
        mockMvc.perform(post("/api/v1/product-configurations/preview")
                        .contentType("application/json").content(unsupportedIncome))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UNSUPPORTED"))
                .andExpect(jsonPath("$.messages[0]", containsString("Customer API does not provide verified income")));

        String missingAge = previewPayload(loan.getCategoryId()).replace(
                "\"eligibilityRules\":[]",
                "\"eligibilityRules\":[{\"name\":\"Adult customer\",\"fact\":\"CUSTOMER_AGE\",\"dataType\":\"NUMBER\",\"operator\":\"GTE\",\"expectedValue\":\"18\",\"combineLogic\":\"AND\"}]");
        mockMvc.perform(post("/api/v1/product-configurations/preview")
                        .contentType("application/json").content(missingAge))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INCOMPLETE"))
                .andExpect(jsonPath("$.messages[0]", containsString("CUSTOMER_AGE")));

        mockMvc.perform(post("/api/v1/product-configurations/{id}/activate", firstLoan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(patch("/api/v1/products/{id}/status", secondLoan)
                        .contentType("application/json").content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        mockMvc.perform(post("/api/v1/product-configurations/{id}/activate", fdProduct))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseCurrencyCode").value("KWD"))
                .andExpect(jsonPath("$.currencyCodes", hasSize(1)));
        assertTrue(products.findByProductCode("HOME_LOAN_A").isPresent());
    }

    private int create(int categoryId, String code, String name, String baseCurrency,
                       String currencyJson, String pricingJson) throws Exception {
        String payload = """
                {"productCode":"%s","productName":"%s","categoryId":%d,"description":"Configured in GUI",
                 "baseCurrencyCode":"%s","currencies":%s,"attributes":[],"eligibilityRules":[],
                 "pricingBands":%s,"charges":[],"roleConstraints":[]}
                """.formatted(code, name, categoryId, baseCurrency, currencyJson, pricingJson);
        String response = mockMvc.perform(post("/api/v1/product-configurations")
                        .contentType("application/json").content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("productId").asInt();
    }

    private String previewPayload(int categoryId) throws Exception {
        JsonNode configuration = objectMapper.readTree("""
                {"productCode":"PREVIEW_ONLY","productName":"Preview","categoryId":%d,"baseCurrencyCode":"INR",
                 "currencies":[{"currencyCode":"INR","minimumAmount":10000,"maximumAmount":5000000}],
                 "attributes":[],"eligibilityRules":[],
                 "pricingBands":[{"currencyCode":"INR","fact":"TENURE_MONTHS","valueFrom":6,"valueTo":360,
                 "rate":0.085,"outcomeType":"INTEREST_RATE","rateUnit":"DECIMAL_FRACTION","calculationMethod":"LOAN_REDUCING_BALANCE"}],
                 "charges":[],"roleConstraints":[]}
                """.formatted(categoryId));
        var payload = objectMapper.createObjectNode();
        payload.set("configuration", configuration);
        payload.set("facts", objectMapper.readTree("{\"AMOUNT\":\"100000\",\"TENURE_MONTHS\":\"12\",\"CURRENCY\":\"INR\"}"));
        return objectMapper.writeValueAsString(payload);
    }

    private String currencyJson(String code, String minimum, String maximum) {
        return "[{\"currencyCode\":\"%s\",\"minimumAmount\":%s,\"maximumAmount\":%s}]"
                .formatted(code, minimum, maximum);
    }

    private String pricingJson(String code, String fact, int from, int to, String rate,
                               String outcome, String method) {
        return ("[{\"currencyCode\":\"%s\",\"fact\":\"%s\",\"valueFrom\":%d,\"valueTo\":%d,"
                + "\"rate\":%s,\"outcomeType\":\"%s\",\"rateUnit\":\"DECIMAL_FRACTION\","
                + "\"calculationMethod\":\"%s\"}]").formatted(code, fact, from, to, rate, outcome, method);
    }

    private ProductCategory category(String code, String name, ProductCategory parent, String familyCode) {
        ProductCategory category = new ProductCategory();
        category.setCategoryCode(familyCode == null ? code : familyCode);
        category.setCategoryName(name);
        category.setParentCategoryId(parent == null ? null : parent.getCategoryId());
        return categories.save(category);
    }

    private void currency(String code, int decimals) {
        Currency currency = new Currency();
        currency.setIsoCode(code);
        currency.setCurrencyName(code);
        currency.setCurrencySymbol(code);
        currency.setDecimalPlaces(decimals);
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
