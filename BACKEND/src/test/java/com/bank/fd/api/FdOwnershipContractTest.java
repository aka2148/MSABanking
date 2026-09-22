package com.bank.fd.api;

import com.bank.fd.application.ProductTerms;
import com.bank.fd.application.ProductTermsPort;
import com.bank.fd.application.ProductTermsQuery;
import com.bank.fd.entity.FdCurrencyReference;
import com.bank.fd.repository.FdCurrencyReferenceRepository;
import com.bank.fd.repository.FdTransactionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Over-HTTP ownership and error-contract proof (D-05, D-06, D-11).
 *
 * <p>Full Spring Boot + MockMvc against the default H2 test profile — no Docker.
 * Real authentication (register/login), real actor resolution, real funding,
 * ownership, idempotency, and error-mapping paths. Only the normalized product
 * terms projection is stubbed: seeding the full product/rule/currency graph in
 * H2 is out of scope for this contract test (PostgreSQL persistence of that
 * graph is proven by the Flyway-backed suites), so a fixed academic-v1
 * projection matching the {@code AccountBookingServiceTest} fixture is served
 * instead.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FdOwnershipContractTest {

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

    private static final String OWNER_A = "fdownerA";
    private static final String OWNER_B = "fdownerB";
    private static final String STAFF = "fdofficer";
    private static final String PASSWORD = "securepwd123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    private FdCurrencyReferenceRepository currencyRepository;

    @Autowired
    private FdTransactionRepository transactionRepository;

    @BeforeAll
    void seedUsersAndReferences() throws Exception {
        if (currencyRepository.findByIsoCode("USD").isEmpty()) {
            FdCurrencyReference currency = new FdCurrencyReference();
            currency.setIsoCode("USD");
            currencyRepository.save(currency);
        }
        Integer depositTypes = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transaction_types WHERE tt_txn_type_code = 'DEPOSIT'", Integer.class);
        if (depositTypes == null || depositTypes == 0) {
            jdbcTemplate.execute("INSERT INTO transaction_types (tt_txn_type_code) VALUES ('DEPOSIT')");
        }
        Integer productRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fd_product_ref WHERE fpr_product_id = 7", Integer.class);
        if (productRows == null || productRows == 0) {
            jdbcTemplate.execute("INSERT INTO fd_product_ref"
                    + " (fpr_product_id, fpr_product_code, fpr_product_name, fpr_status)"
                    + " VALUES (7, 'FD7', 'Academic FD', 'ACTIVE')");
        }
        register(OWNER_A, "owner.a@bank.com", "ROLE_CUSTOMER");
        register(OWNER_B, "owner.b@bank.com", "ROLE_CUSTOMER");
        // Staff principal exercises the D-06 BANK_OFFICER read bypass.
        register(STAFF, "officer@bank.com", "ROLE_BANK_OFFICER");
    }

    @Test
    void sameKeyFundReplayIsByteIdenticalWithOneDepositRow() throws Exception {
        String token = login(OWNER_A);
        long accountId = book(token, "own-replay-book-1");

        String fundBody = "{\"amount\": 1000.00, \"currency_code\": \"USD\"}";
        String first = mockMvc.perform(post("/fd/v1/accounts/" + accountId + "/fund")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "own-replay-fund-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fundBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();

        String replay = mockMvc.perform(post("/fd/v1/accounts/" + accountId + "/fund")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", "own-replay-fund-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fundBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();

        assertEquals(first, replay, "Same-key funding replay must return the identical body");
        assertEquals(1, transactionRepository.findByAccountIdOrderByTransactionDateAscIdAsc(accountId).size(),
                "Replay must not record a second DEPOSIT row");
    }

    @Test
    void crossCustomerReadsReturn404WithoutLeakingExistence() throws Exception {
        String tokenA = login(OWNER_A);
        String tokenB = login(OWNER_B);
        long accountId = book(tokenA, "own-404-book-1");
        fund(tokenA, accountId, "own-404-fund-1");

        mockMvc.perform(get("/fd/v1/accounts/" + accountId)
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mockMvc.perform(get("/fd/v1/accounts/" + accountId + "/transactions")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mockMvc.perform(get("/fd/v1/accounts/" + accountId + "/accrual")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));

        mockMvc.perform(get("/fd/v1/accounts/" + accountId + "/premature-closure")
                        .header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void staffOfficerCanReadAnyFdWhileCustomersStaySelfService() throws Exception {
        String tokenA = login(OWNER_A);
        String staffToken = login(STAFF);
        long accountId = book(tokenA, "own-staff-book-1");

        // BANK_OFFICER bypass (D-06): staff reads another holder's FD.
        mockMvc.perform(get("/fd/v1/accounts/" + accountId)
                        .header("Authorization", "Bearer " + staffToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account_id").value(accountId));

        // Owner still sees their own FD.
        mockMvc.perform(get("/fd/v1/accounts/" + accountId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account_id").value(accountId));
    }

    @Test
    void errorContractIsConsistentAcross409And422And400() throws Exception {
        String tokenA = login(OWNER_A);
        long accountId = book(tokenA, "own-contract-book-1");
        fund(tokenA, accountId, "own-contract-fund-1");

        // Key-less repeat funding on an ACTIVE account: 409 referencing the existing record.
        mockMvc.perform(post("/fd/v1/accounts/" + accountId + "/fund")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 1000.00, \"currency_code\": \"USD\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("existing")));

        // Unsupported premature-closure rule (no stored CLOSURE_PENALTY outcome): 422.
        mockMvc.perform(post("/fd/v1/accounts/" + accountId + "/premature-closure")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"need funds\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message", containsString("CLOSURE_PENALTY")));

        // The 422 must not close the FD.
        mockMvc.perform(get("/fd/v1/accounts/" + accountId)
                        .header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        // Malformed booking (negative principal): 400.
        mockMvc.perform(post("/fd/v1/accounts")
                        .header("Authorization", "Bearer " + tokenA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product_id\": 7, \"principal_amount\": -5.00,"
                                + " \"tenure_months\": 12, \"currency_code\": \"USD\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void authenticationContractHoldsForAnonymousAndBadCredentials() throws Exception {
        String tokenA = login(OWNER_A);
        long accountId = book(tokenA, "own-auth-book-1");

        // No token on a secured FD read: the stateless JWT filter passes the
        // anonymous request through and method security denies it — pinned as
        // 403 by SecurityFlowTest (not 401); 401 applies to failed logins.
        mockMvc.perform(get("/fd/v1/accounts/" + accountId))
                .andExpect(status().isForbidden());

        // Bad credentials: 401 with a truthful body.
        Map<String, String> badLogin = new HashMap<>();
        badLogin.put("username", OWNER_A);
        badLogin.put("password", "wrong-password");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badLogin)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    private void register(String username, String email, String role) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("username", username);
        request.put("password", PASSWORD);
        request.put("email", email);
        request.put("role", role);
        request.put("firstName", "Fd");
        request.put("lastName", "Owner");
        request.put("phone", "1234567890");
        request.put("address", "123 Banking St");
        request.put("category", "STANDARD");
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andReturn();
        int status = result.getResponse().getStatus();
        assertTrue(status == 200 || status == 400,
                "Register must succeed or report the user exists, got " + status);
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
        String token = body.has("token") ? body.get("token").asText() : body.get("jwt").asText();
        assertTrue(token != null && !token.isBlank(), "Login must return a JWT");
        return token;
    }

    private long book(String token, String idempotencyKey) throws Exception {
        MvcResult result = mockMvc.perform(post("/fd/v1/accounts")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"product_id\": 7, \"principal_amount\": 1000.00,"
                                + " \"tenure_months\": 12, \"currency_code\": \"USD\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("account_id").asLong();
    }

    private void fund(String token, long accountId, String idempotencyKey) throws Exception {
        mockMvc.perform(post("/fd/v1/accounts/" + accountId + "/fund")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amount\": 1000.00, \"currency_code\": \"USD\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }
}
