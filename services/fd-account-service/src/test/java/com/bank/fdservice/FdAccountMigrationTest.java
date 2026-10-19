package com.bank.fdservice;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class FdAccountMigrationTest {
    private static final String JWT_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWYwMTIzNDU2Nzg5YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZg==";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("moneypal_fd")
            .withUsername("fd_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20))
            .withPassword(UUID.randomUUID().toString());

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", MYSQL::getJdbcUrl);
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
        properties.add("app.jwt.secret", () -> JWT_SECRET);
        properties.add("app.kafka.enabled", () -> false);
        properties.add("app.demo.opening-balance.enabled", () -> true);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockMvc mvc;

    @Test
    void appliesAllFdMigrationsAndCreatesWorkflowSchema() {
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1", Integer.class));
        assertEquals(3, jdbc.queryForObject("SELECT MAX(version) FROM flyway_schema_history WHERE success=1", Integer.class));

        assertTrue(table("fd_accounts"));
        assertTrue(table("savings_accounts"));
        assertTrue(table("fd_user_ref"));
        assertTrue(table("fd_product_ref"));
        assertTrue(table("fd_currency_ref"));
        assertTrue(table("fd_product_currency_ref"));
        assertTrue(table("fd_product_rate_ref"));
        assertTrue(table("fd_operation_idempotency"));
        assertTrue(table("fd_journals"));
        assertTrue(table("fd_journal_lines"));

        assertEquals(8, decimalScale("fd_accounts", "fa_principal_amount"));
        assertEquals(8, decimalScale("savings_accounts", "sa_balance"));
        assertEquals(6, jdbc.queryForObject("SELECT COUNT(DISTINCT tt_txn_type_code) FROM transaction_types WHERE tt_txn_type_code IN ('FD_FUNDING','FD_SETTLEMENT','FD_INTEREST','FD_MATURITY','FD_CLOSURE','FD_PENALTY')", Integer.class));
    }

    @Test
    void customerCanBookFundAccrueMatureAndCloseWithIdempotentRequests() throws Exception {
        seedDemoData();

        String customerToken = token(701, "ROLE_CUSTOMER");
        String adminToken = token(702, "ROLE_ADMIN");

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/savings-accounts/demo-options")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.enabled").value(true));
        var openingAccount = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/savings-accounts")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currencyCode\":\"USD\",\"displayName\":\"Demo opening account\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andReturn();
        long openingSavingsId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(openingAccount.getResponse().getContentAsString()).get("account_id").asLong();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/savings-accounts/{id}/demo-opening", openingSavingsId)
                        .header("Authorization", "Bearer " + customerToken).header("Idempotency-Key", "opening-precision-reject")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":123.456}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/savings-accounts/{id}/demo-opening", openingSavingsId)
                        .header("Authorization", "Bearer " + customerToken).header("Idempotency-Key", "opening-valid")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":123.45}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.balance").value(123.45));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/savings-accounts/{id}/demo-opening", openingSavingsId)
                        .header("Authorization", "Bearer " + customerToken).header("Idempotency-Key", "opening-again")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":123.45}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/savings-accounts/{id}", openingSavingsId)
                        .header("Authorization", "Bearer " + token(799, "ROLE_CUSTOMER")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM savings_transactions WHERE st_account_id=?", Integer.class, openingSavingsId));

        String booking = """
                {"productId":11,"savingsAccountId":1,"principal":100.00,"currencyCode":"USD","tenureMonths":1}
                """;

        var booked = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/fixed-deposits")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", "booking-701-1")
                        .contentType(MediaType.APPLICATION_JSON).content(booking))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andReturn();
        long fdId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(booked.getResponse().getContentAsString()).get("account_id").asLong();
        org.junit.jupiter.api.Assertions.assertEquals("PENDING", new com.fasterxml.jackson.databind.ObjectMapper().readTree(booked.getResponse().getContentAsString()).get("status").asText());

        var funded = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/fixed-deposits/{id}/fund", fdId)
                        .header("Authorization", "Bearer " + customerToken).header("Idempotency-Key", "fund-701-1"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn();
        org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", new com.fasterxml.jackson.databind.ObjectMapper().readTree(funded.getResponse().getContentAsString()).get("status").asText());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/fixed-deposits/{id}", fdId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.funding_savings_account_id").value(1));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/fixed-deposits/{id}/fund", fdId)
                        .header("Authorization", "Bearer " + customerToken).header("Idempotency-Key", "fund-701-1"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM savings_transactions WHERE st_remarks=?", Integer.class, "FD funding #" + fdId));
        assertEquals(new BigDecimal("900.00000000"), jdbc.queryForObject("SELECT sa_balance FROM savings_accounts WHERE sa_account_id=1", BigDecimal.class));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/fixed-deposits/accrual-batch")
                        .param("businessDate", LocalDate.now(ZoneOffset.UTC).plusDays(7).toString())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/fixed-deposits/maturity-batch")
                        .param("businessDate", LocalDate.now(ZoneOffset.UTC).plusMonths(1).plusDays(1).toString())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        assertEquals("MATURED", jdbc.queryForObject("SELECT fa_status FROM fd_accounts WHERE fa_account_id=?", String.class, fdId));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM fd_interest_accrual WHERE fia_account_id=?", Integer.class, fdId) >= 28);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/fixed-deposits/{id}/accruals", fdId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].currency_code").value("USD"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/fixed-deposits/{id}/journals", fdId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].lines.length()").value(2));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/fixed-deposits/{id}/accruals/cumulative", fdId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.cumulative_accrued").isNumber());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/fixed-deposits/{id}/close", fdId)
                        .header("Authorization", "Bearer " + customerToken).header("Idempotency-Key", "close-701-1"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("CLOSED"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/fixed-deposits/{id}/transactions", fdId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[?(@.event_type=='FD_FUNDING')]").exists())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].currency_code").value("USD"));
        assertEquals("CLOSED", jdbc.queryForObject("SELECT fa_status FROM fd_accounts WHERE fa_account_id=?", String.class, fdId));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM fd_journals WHERE fj_fd_account_id=?", Integer.class, fdId));

        var secondBooking = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/fixed-deposits")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", "booking-701-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":11,\"savingsAccountId\":1,\"principal\":200.00,\"currencyCode\":\"USD\",\"tenureMonths\":1}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andReturn();
        long secondFdId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(secondBooking.getResponse().getContentAsString()).get("account_id").asLong();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/fixed-deposits/{id}/fund", secondFdId)
                        .header("Authorization", "Bearer " + customerToken).header("Idempotency-Key", "fund-701-2"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        jdbc.update("UPDATE fd_accounts SET fa_open_date=DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 30 DAY), fa_maturity_date=DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 30 DAY) WHERE fa_account_id=?", secondFdId);
        var quote = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/fixed-deposits/{id}/premature-closure", secondFdId)
                        .header("Authorization", "Bearer " + customerToken).header("Idempotency-Key", "premature-701-2")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Demo premature withdrawal\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("PENDING"))
                .andReturn();
        long closureId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(quote.getResponse().getContentAsString()).get("closure_id").asLong();
        BigDecimal penalty = new com.fasterxml.jackson.databind.ObjectMapper().readTree(quote.getResponse().getContentAsString()).get("penalty_amount").decimalValue();
        assertTrue(penalty.signum() > 0);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/fixed-deposits/{id}/premature-closure/{closureId}/approve", secondFdId, closureId)
                        .header("Authorization", "Bearer " + adminToken).header("Idempotency-Key", "approve-premature-702-2"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("PREMATURELY_CLOSED"));
        assertEquals("APPROVED", jdbc.queryForObject("SELECT fpc_status FROM fd_premature_closure WHERE fpc_closure_id=?", String.class, closureId));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM fd_transactions WHERE ft_account_id=? AND ft_txn_type_id=(SELECT tt_txn_type_id FROM transaction_types WHERE tt_txn_type_code='FD_PENALTY')", Integer.class, secondFdId) > 0);
    }

    private void seedDemoData() {
        jdbc.update("INSERT INTO fd_user_ref(fur_user_id,fur_user_uuid,fur_full_name,fur_user_status) VALUES(701,?,'Demo Customer','ACTIVE'),(702,?,'Demo Admin','ACTIVE')", UUID.randomUUID().toString(), UUID.randomUUID().toString());
        jdbc.update("INSERT INTO fd_currency_ref(fcr_currency_id,fcr_iso_code,fcr_currency_name,fcr_decimal_places) VALUES(1,'USD','US Dollar',2)");
        jdbc.update("INSERT INTO fd_product_ref(fpr_product_id,fpr_product_code,fpr_product_name,fpr_status) VALUES(11,'FD-DEMO','Demo Fixed Deposit','ACTIVE')");
        jdbc.update("INSERT INTO fd_product_currency_ref(product_id,currency_id,minimum_amount,maximum_amount,is_base_currency,source_version,premature_penalty_points) VALUES(11,1,10,100000,1,1,1.0000)");
        jdbc.update("INSERT INTO fd_product_rate_ref(product_id,currency_id,minimum_tenure_months,maximum_tenure_months,annual_rate,compounding_basis,payout_frequency,source_version) VALUES(11,1,1,12,0.0500,'QUARTERLY','AT_MATURITY',1)");
        jdbc.update("INSERT INTO savings_accounts(sa_account_number,sa_holder_id,sa_currency_id,sa_display_name,sa_balance,sa_status,sa_created_date) VALUES('SAV-DEMO-701',701,1,'Demo Savings',1000,'ACTIVE',UTC_TIMESTAMP(6))");
    }

    private String token(long userId, String role) {
        return Jwts.builder().setSubject(Long.toString(userId)).claim("userId", userId).claim("roles", role)
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(JWT_SECRET)), SignatureAlgorithm.HS512).compact();
    }

    private boolean table(String name) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?", Integer.class, name) == 1;
    }

    private int decimalScale(String table, String column) {
        return jdbc.queryForObject("SELECT numeric_scale FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? AND column_name=?", Integer.class, table, column);
    }
}
