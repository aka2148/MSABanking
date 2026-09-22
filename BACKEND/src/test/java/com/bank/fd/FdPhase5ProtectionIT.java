package com.bank.fd;

import com.bank.fd.application.ProductTerms;
import com.bank.fd.application.ProductTermsPort;
import com.bank.fd.application.ProductTermsQuery;
import com.bank.fd.entity.FdAccount;
import com.bank.fd.entity.FdCurrencyReference;
import com.bank.fd.repository.FdAccountRepository;
import com.bank.fd.repository.FdCurrencyReferenceRepository;
import com.bank.fd.repository.FdInterestAccrualRepository;
import com.bank.fd.repository.FdOperationIdempotencyRepository;
import com.bank.fd.repository.FdTransactionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 5 exit-gate proof (D-09) against real PostgreSQL 16 Alpine.
 *
 * <p>Boot-managed datasource pointed at the Testcontainers database with the
 * Flyway migration chain applied (no H2, no DDL generation): end-to-end
 * book → fund → accrue → mature → close → timeline over MockMvc with fixed
 * {@code Idempotency-Key} values per operation, timeline linkage back to the
 * same account, and same-key replay safety for all five operations proven by
 * byte-identical bodies plus unchanged row counts in
 * {@code fd_operation_idempotency}, {@code fd_transaction}, and
 * {@code fd_interest_accrual}.</p>
 *
 * <p>Skips cleanly when Docker is unavailable (same mechanism as
 * {@code FdPostgresSchemaTest}). Only the normalized product-terms projection
 * is stubbed: migrations seed transaction types but no product/rule graph, so a
 * fixed academic-v1 projection (matching the MockMvc ownership contract test)
 * is served while every persistence, lifecycle, ownership, idempotency, and
 * history behavior stays real.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FdPhase5ProtectionIT {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void pgProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @TestConfiguration
    static class FixedTermsConfig {
        @Bean
        @Primary
        ProductTermsPort fixedProductTermsPort() {
            return new ProductTermsPort() {
                @Override
                public ProductTerms resolveActiveTerms(ProductTermsQuery query, LocalDate effectiveDate) {
                    return new ProductTerms(7L, "FD7", "Academic FD", "USD",
                            new BigDecimal("100"), new BigDecimal("10000"), 3, 12,
                            new BigDecimal("0.0650"), "SIMPLE", "AT_MATURITY", 9L, "NONE");
                }

                @Override
                public List<ProductTerms> listEligibleTerms(LocalDate effectiveDate) {
                    return List.of(resolveActiveTerms(null, effectiveDate));
                }
            };
        }
    }

    private static final String OWNER = "fdphase5owner";
    private static final String PASSWORD = "securepwd123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FdCurrencyReferenceRepository currencyRepository;

    @Autowired
    private FdAccountRepository accountRepository;

    @Autowired
    private FdTransactionRepository transactionRepository;

    @Autowired
    private FdInterestAccrualRepository accrualRepository;

    @Autowired
    private FdOperationIdempotencyRepository idempotencyRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String token;

    @BeforeAll
    void seedAndLogin() throws Exception {
        if (currencyRepository.findByIsoCode("USD").isEmpty()) {
            FdCurrencyReference currency = new FdCurrencyReference();
            currency.setIsoCode("USD");
            currencyRepository.save(currency);
        }
        Integer products = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.fd_product_ref WHERE fpr_product_id = 7", Integer.class);
        if (products == null || products == 0) {
            jdbcTemplate.execute("INSERT INTO public.fd_product_ref"
                    + " (fpr_product_id, fpr_product_code, fpr_product_name, fpr_status)"
                    + " VALUES (7, 'FD7', 'Academic FD', 'ACTIVE')");
        }
        register(OWNER, "phase5owner@bank.com");
        token = login(OWNER);
    }

    @Test
    @Order(1)
    void flywayMigrationsAppliedTheLifecycleBaseline() {
        Set<String> versions = new HashSet<>(jdbcTemplate.queryForList(
                "SELECT version FROM public.flyway_schema_history WHERE success", String.class));
        assertTrue(versions.containsAll(Set.of("1", "2", "3", "4", "5", "6", "7", "8", "9")),
                "Flyway must have applied V1..V9, saw " + versions);
        Integer deposit = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.transaction_types WHERE tt_txn_type_code = 'DEPOSIT'",
                Integer.class);
        assertEquals(1, deposit);
        assertTrue(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.fd_operation_idempotency", Integer.class) >= 0);
    }

    @Test
    @Order(2)
    void fullLifecycleWithFiveOperationDuplicateSafety() throws Exception {
        String bookBody = "{\"product_id\": 7, \"principal_amount\": 1000.00,"
                + " \"tenure_months\": 12, \"currency_code\": \"USD\"}";
        String fundBody = "{\"amount\": 1000.00, \"currency_code\": \"USD\"}";
        String accrualBody = "{\"business_date\": \"" + LocalDate.now() + "\"}";

        // BOOKING: 201 then byte-identical replay with zero new idempotency rows.
        long idemBefore = idempotencyRepository.count();
        String booked = mockMvc.perform(post("/fd/v1/accounts")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "it-book-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        long accountId = objectMapper.readTree(booked).get("account_id").asLong();
        assertEquals(idemBefore + 1, idempotencyRepository.count());
        String bookedReplay = mockMvc.perform(post("/fd/v1/accounts")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "it-book-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertEquals(booked, bookedReplay, "Booking replay must be byte-identical");
        assertEquals(idemBefore + 1, idempotencyRepository.count(),
                "Booking replay must not add idempotency rows");

        // FUNDING: 200 then byte-identical replay with exactly one DEPOSIT row.
        idemBefore = idempotencyRepository.count();
        String funded = mockMvc.perform(post("/fd/v1/accounts/" + accountId + "/fund")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "it-fund-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fundBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        assertEquals(1, transactionRepository.findByAccountIdOrderByTransactionDateAscIdAsc(accountId).size());
        String fundedReplay = mockMvc.perform(post("/fd/v1/accounts/" + accountId + "/fund")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "it-fund-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fundBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(funded, fundedReplay, "Funding replay must be byte-identical");
        assertEquals(1, transactionRepository.findByAccountIdOrderByTransactionDateAscIdAsc(accountId).size(),
                "Funding replay must not record a second transaction");
        assertEquals(idemBefore + 1, idempotencyRepository.count());

        // ACCRUAL: 200 then byte-identical replay with exactly one accrual row.
        idemBefore = idempotencyRepository.count();
        long accrualBefore = accrualRepository.count();
        String accrued = mockMvc.perform(post("/fd/v1/accounts/" + accountId + "/accrual/run")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "it-accrue-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accrualBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(1, accrualRepository.findByAccountIdOrderByAccrualDateAscIdAsc(accountId).size());
        String accruedReplay = mockMvc.perform(post("/fd/v1/accounts/" + accountId + "/accrual/run")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "it-accrue-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(accrualBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(accrued, accruedReplay, "Accrual replay must be byte-identical");
        assertEquals(accrualBefore + 1, accrualRepository.count(),
                "Accrual replay must not add accrual rows");
        assertEquals(idemBefore + 1, idempotencyRepository.count());

        // Time travel so maturity is reachable: backdate the maturity date.
        FdAccount account = accountRepository.findById(accountId).orElseThrow();
        account.setMaturityDate(LocalDate.now().minusDays(1));
        accountRepository.save(account);

        // MATURITY: 200 then byte-identical replay with unchanged row counts.
        idemBefore = idempotencyRepository.count();
        long txnBefore = transactionRepository.count();
        accrualBefore = accrualRepository.count();
        String matured = mockMvc.perform(post("/fd/v1/accounts/" + accountId + "/mature")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "it-mature-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MATURED"))
                .andReturn().getResponse().getContentAsString();
        String maturedReplay = mockMvc.perform(post("/fd/v1/accounts/" + accountId + "/mature")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "it-mature-1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(matured, maturedReplay, "Maturity replay must be byte-identical");
        assertEquals(txnBefore, transactionRepository.count(),
                "Maturity replay must not add transactions");
        assertEquals(accrualBefore, accrualRepository.count(),
                "Maturity replay must not add accrual rows");
        assertEquals(idemBefore + 1, idempotencyRepository.count());

        // CLOSURE: 200 then byte-identical replay with unchanged row counts.
        idemBefore = idempotencyRepository.count();
        txnBefore = transactionRepository.count();
        String closed = mockMvc.perform(post("/fd/v1/accounts/" + accountId + "/close")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "it-close-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andReturn().getResponse().getContentAsString();
        String closedReplay = mockMvc.perform(post("/fd/v1/accounts/" + accountId + "/close")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "it-close-1"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertEquals(closed, closedReplay, "Closure replay must be byte-identical");
        assertEquals(txnBefore, transactionRepository.count(),
                "Closure replay must not add transactions");
        assertEquals(idemBefore + 1, idempotencyRepository.count());

        // HISTORY: timeline links FUNDING, ACCRUAL, MATURITY, CLOSURE to this account.
        MvcResult timeline = mockMvc.perform(get("/fd/v1/accounts/" + accountId + "/transactions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        Set<String> entryTypes = new HashSet<>();
        for (JsonNode entry : objectMapper.readTree(timeline.getResponse().getContentAsString())) {
            entryTypes.add(entry.get("entry_type").asText());
        }
        assertTrue(entryTypes.containsAll(Set.of("FUNDING", "ACCRUAL", "MATURITY", "CLOSURE")),
                "Timeline must link all lifecycle entries, saw " + entryTypes);
        assertEquals(3, transactionRepository.findByAccountIdOrderByTransactionDateAscIdAsc(accountId).size(),
                "Exactly one transaction per money movement (fund/mature/close)");
        assertEquals(2, accrualRepository.findByAccountIdOrderByAccrualDateAscIdAsc(accountId).size(),
                "Accrual plus maturity-closing accrual rows");

        // Same key on a different funding body (different account) is a 409.
        MvcResult second = mockMvc.perform(post("/fd/v1/accounts")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "it-book-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bookBody))
                .andExpect(status().isCreated())
                .andReturn();
        long accountIdB = objectMapper.readTree(second.getResponse().getContentAsString())
                .get("account_id").asLong();
        mockMvc.perform(post("/fd/v1/accounts/" + accountIdB + "/fund")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "it-fund-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fundBody))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("existing")));

        // Key-less repeat close on a CLOSED account references the settlement.
        mockMvc.perform(post("/fd/v1/accounts/" + accountId + "/close")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("existing settlement recorded")));

        // fd_operation_idempotency proves durable claims for the five operations.
        assertEquals(6, idempotencyRepository.count(),
                "BOOKING/FUNDING/ACCRUAL/MATURITY/CLOSURE plus the second booking claim");
    }

    private void register(String username, String email) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("username", username);
        request.put("password", PASSWORD);
        request.put("email", email);
        request.put("role", "ROLE_CUSTOMER");
        request.put("firstName", "Phase");
        request.put("lastName", "Five");
        request.put("phone", "1234567890");
        request.put("address", "123 Banking St");
        request.put("category", "STANDARD");
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
        int responseStatus = result.getResponse().getStatus();
        assertTrue(responseStatus == 200 || responseStatus == 400,
                "Register must succeed or report the user exists, got " + responseStatus);
    }

    private String login(String username) throws Exception {
        Map<String, String> request = new HashMap<>();
        request.put("username", username);
        request.put("password", PASSWORD);
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String tokenValue = body.has("token") ? body.get("token").asText() : body.get("jwt").asText();
        assertTrue(tokenValue != null && !tokenValue.isBlank(), "Login must return a JWT");
        return tokenValue;
    }
}
