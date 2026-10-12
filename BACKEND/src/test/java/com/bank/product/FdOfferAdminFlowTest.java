package com.bank.product;

import com.bank.product.entity.Currency;
import com.bank.product.entity.ProductCategory;
import com.bank.product.entity.RuleType;
import com.bank.product.repository.CurrencyRepository;
import com.bank.product.repository.ProductCategoryRepository;
import com.bank.product.repository.ProductRepository;
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

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class FdOfferAdminFlowTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private CurrencyRepository currencies;
    @Autowired private ProductCategoryRepository categories;
    @Autowired private RuleTypeRepository ruleTypes;
    @Autowired private ProductRepository products;
    @Autowired private ObjectMapper objectMapper;

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotCreateAnFdProduct() throws Exception {
        mockMvc.perform(post("/api/v1/fd-products")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/products")
                        .contentType("application/json")
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "BANK_OFFICER")
    void staffCreatesAndActivatesUsdAndInrTenureRateSchedules() throws Exception {
        ProductCategory category = new ProductCategory();
        category.setCategoryCode("FD_TEST");
        category.setCategoryName("Fixed deposits");
        category = categories.save(category);
        addCurrency("USD", "US Dollar", "$", 2);
        addCurrency("INR", "Indian Rupee", "₹", 2);
        if (ruleTypes.findByRuleTypeCode("FD_TENURE_RATE").isEmpty()) {
            RuleType type = new RuleType();
            type.setRuleTypeCode("FD_TENURE_RATE");
            type.setDescription("FD rates by tenure");
            ruleTypes.save(type);
        }

        String request = """
                {
                  "productCode":"LAB_GLOBAL_FD",
                  "productName":"Lab Global Fixed Deposit",
                  "categoryId":%d,
                  "description":"Configurable academic term deposit",
                  "currencies":[
                    {"currencyCode":"USD","minimumAmount":100,"maximumAmount":100000,
                     "rateBands":[
                       {"minimumTenureMonths":3,"maximumTenureMonths":5,"annualRate":0.0400},
                       {"minimumTenureMonths":6,"maximumTenureMonths":12,"annualRate":0.0500}]},
                    {"currencyCode":"INR","minimumAmount":1000,"maximumAmount":1000000,
                     "rateBands":[
                       {"minimumTenureMonths":3,"maximumTenureMonths":5,"annualRate":0.0500},
                       {"minimumTenureMonths":6,"maximumTenureMonths":12,"annualRate":0.0600}]}
                  ]
                }
                """.formatted(category.getCategoryId());

        String response = mockMvc.perform(post("/api/v1/fd-products")
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.currency_codes", hasSize(2)))
                .andReturn().getResponse().getContentAsString();
        long fdProductId = objectMapper.readTree(response).get("fd_product_id").asLong();
        int catalogProductId = objectMapper.readTree(response).get("catalog_product_id").asInt();

        mockMvc.perform(patch("/api/v1/products/{id}/status", catalogProductId)
                        .contentType("application/json")
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(post("/api/v1/fd-products/{id}/activate", fdProductId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        org.junit.jupiter.api.Assertions.assertEquals("ACTIVE",
                products.findByProductCode("LAB_GLOBAL_FD").orElseThrow().getStatus());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void staffCannotCreateOverlappingOrGappedRateBands() throws Exception {
        ProductCategory category = new ProductCategory();
        category.setCategoryCode("FD_INVALID_TEST");
        category.setCategoryName("Invalid test category");
        category = categories.save(category);
        addCurrency("USD", "US Dollar", "$", 2);
        addCurrency("INR", "Indian Rupee", "₹", 2);

        String request = """
                {"productCode":"INVALID_FD","productName":"Invalid FD","categoryId":%d,
                 "currencies":[
                   {"currencyCode":"USD","minimumAmount":100,"maximumAmount":10000,
                    "rateBands":[{"minimumTenureMonths":3,"maximumTenureMonths":6,"annualRate":0.04},
                                 {"minimumTenureMonths":6,"maximumTenureMonths":12,"annualRate":0.05}]},
                   {"currencyCode":"INR","minimumAmount":100,"maximumAmount":10000,
                    "rateBands":[{"minimumTenureMonths":3,"maximumTenureMonths":12,"annualRate":0.05}]}
                 ]}
                """.formatted(category.getCategoryId());

        mockMvc.perform(post("/api/v1/fd-products")
                        .contentType("application/json")
                        .content(request))
                .andExpect(status().isBadRequest());
        org.junit.jupiter.api.Assertions.assertFalse(products.existsByProductCode("INVALID_FD"));
    }

    private void addCurrency(String code, String name, String symbol, int decimals) {
        if (currencies.findByIsoCode(code).isPresent()) return;
        Currency currency = new Currency();
        currency.setIsoCode(code);
        currency.setCurrencyName(name);
        currency.setCurrencySymbol(symbol);
        currency.setDecimalPlaces(decimals);
        currency.setIsActive(true);
        currencies.save(currency);
    }
}
