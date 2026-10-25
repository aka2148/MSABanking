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

import java.util.UUID;
import java.util.Date;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        properties.add("app.demo.opening-max-amount", () -> "5000.00");
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    MockMvc mvc;

    @Autowired
    FdBatchService batches;

    @Test
    void appliesAllFdMigrationsAndCreatesWorkflowSchema() {
        assertEquals(14, jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1", Integer.class));
        assertEquals(14, jdbc.queryForObject("SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1", Integer.class));

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
        assertTrue(table("loan_applications"));
        assertTrue(table("loan_application_events"));
        assertTrue(table("forex_requests"));
        assertTrue(table("chart_of_accounts"));
        assertTrue(table("fd_ledger_cutover"));
        assertTrue(table("fd_mpin_proof_use"));

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
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/savings-accounts/demo-options")
                        .header("Authorization", "Bearer " + adminToken))
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
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/savings-accounts/{id}/demo-opening", openingSavingsId)
                        .header("Authorization", "Bearer " + adminToken).header("Idempotency-Key", "opening-cap")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":5000.01}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/savings-accounts/{id}/demo-opening", openingSavingsId)
                        .header("Authorization", "Bearer " + adminToken).header("Idempotency-Key", "opening-valid")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":123.45}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.balance").value(123.45));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/savings-accounts/{id}/demo-opening", openingSavingsId)
                        .header("Authorization", "Bearer " + adminToken).header("Idempotency-Key", "opening-again")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":123.45}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/savings-accounts/{id}", openingSavingsId)
                        .header("Authorization", "Bearer " + token(799, "ROLE_CUSTOMER")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM savings_transactions WHERE st_account_id=?", Integer.class, openingSavingsId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM fd_journals WHERE fj_key=? AND fj_operation='DEMO_OPENING' AND fj_created_by=?", Integer.class, "DEMO_OPENING:"+openingSavingsId, "702"));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM fd_journal_lines WHERE jl_journal_id=(SELECT fj_id FROM fd_journals WHERE fj_key=?)", Integer.class, "DEMO_OPENING:"+openingSavingsId));

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

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/fixed-deposits/{id}/fund", fdId)
                        .header("Authorization", "Bearer " + customerToken).header("Idempotency-Key", "fund-wrong-account")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"savingsAccountId\":999999}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());

        var funded = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/fixed-deposits/{id}/fund", fdId)
                        .header("Authorization", "Bearer " + customerToken).header("X-MPIN-Proof",proof(701,"FD_FUNDING",MpinProofVerifier.canonical("FD_FUNDING",fdId,new BigDecimal("100.00")))).header("Idempotency-Key", "fund-701-1"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk()).andReturn();
        org.junit.jupiter.api.Assertions.assertEquals("ACTIVE", new com.fasterxml.jackson.databind.ObjectMapper().readTree(funded.getResponse().getContentAsString()).get("status").asText());
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM fd_transactions WHERE ft_account_id=? AND ft_source_savings_account_id=?",Integer.class,fdId,1));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/fixed-deposits/{id}", fdId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.funding_savings_account_id").value(1));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/fixed-deposits/{id}/fund", fdId)
                        .header("Authorization", "Bearer " + customerToken).header("Idempotency-Key", "fund-701-1"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM savings_transactions WHERE st_remarks=?", Integer.class, "FD funding #" + fdId));
        assertEquals(new BigDecimal("900.00000000"), jdbc.queryForObject("SELECT sa_balance FROM savings_accounts WHERE sa_account_id=1", BigDecimal.class));

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/admin/clock/advance")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"days\":7}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/fixed-deposits/accrual-batch")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/admin/clock/advance")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"days\":25}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/fixed-deposits/maturity-batch")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        assertEquals("MATURED", jdbc.queryForObject("SELECT fa_status FROM fd_accounts WHERE fa_account_id=?", String.class, fdId));
        assertEquals(new BigDecimal("900.00000000"), jdbc.queryForObject("SELECT sa_balance FROM savings_accounts WHERE sa_account_id=1", BigDecimal.class),
                "maturity recognition must not pay savings until the customer closes the FD");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM fd_notices WHERE fn_account_id=? AND fn_notice_type='FD_MATURED'", Integer.class, fdId));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM fd_interest_accrual WHERE fia_account_id=?", Integer.class, fdId) >= 28);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/fixed-deposits/{id}/accruals", fdId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].currency_code").value("USD"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/fixed-deposits/{id}/journals", fdId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].lines.length()").value(2));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/fixed-deposits/{id}/accruals/cumulative", fdId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.cumulative_accrued").isNumber());

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/fixed-deposits/{id}/close", fdId)
                        .header("Authorization", "Bearer " + customerToken).header("X-MPIN-Proof",proof(701,"FD_CLOSE",MpinProofVerifier.canonical("FD_CLOSE",fdId))).header("Idempotency-Key", "close-701-1"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("CLOSED"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/fixed-deposits/{id}/transactions", fdId)
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[?(@.event_type=='FD_FUNDING')]").exists())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].currency_code").value("USD"));
        assertEquals("CLOSED", jdbc.queryForObject("SELECT fa_status FROM fd_accounts WHERE fa_account_id=?", String.class, fdId));
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM fd_journals WHERE fj_fd_account_id=?", Integer.class, fdId));
        BigDecimal savingsAfterClose = jdbc.queryForObject("SELECT sa_balance FROM savings_accounts WHERE sa_account_id=1", BigDecimal.class);
        assertTrue(savingsAfterClose.compareTo(new BigDecimal("900.00000000")) > 0);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/fixed-deposits/{id}/close", fdId)
                        .header("Authorization", "Bearer " + customerToken).header("Idempotency-Key", "close-701-1"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("CLOSED"));
        assertEquals(0, savingsAfterClose.compareTo(jdbc.queryForObject("SELECT sa_balance FROM savings_accounts WHERE sa_account_id=1", BigDecimal.class)));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM fd_journals WHERE fj_key=?", Integer.class, "SETTLEMENT:" + fdId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM fd_notices WHERE fn_account_id=? AND fn_notice_type='FD_CLOSED'", Integer.class, fdId));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/admin/clock/reset")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());

        var secondBooking = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/fixed-deposits")
                        .header("Authorization", "Bearer " + customerToken)
                        .header("Idempotency-Key", "booking-701-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":11,\"savingsAccountId\":1,\"principal\":200.00,\"currencyCode\":\"USD\",\"tenureMonths\":1}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andReturn();
        long secondFdId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(secondBooking.getResponse().getContentAsString()).get("account_id").asLong();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/fixed-deposits/{id}/fund", secondFdId)
                        .header("Authorization", "Bearer " + customerToken).header("X-MPIN-Proof",proof(701,"FD_FUNDING",MpinProofVerifier.canonical("FD_FUNDING",secondFdId,new BigDecimal("200.00")))).header("Idempotency-Key", "fund-701-2"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        jdbc.update("UPDATE fd_accounts SET fa_open_date=DATE_SUB(UTC_TIMESTAMP(6), INTERVAL 30 DAY), fa_maturity_date=DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 30 DAY) WHERE fa_account_id=?", secondFdId);
        var quote = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/fixed-deposits/{id}/premature-closure", secondFdId)
                        .header("Authorization", "Bearer " + customerToken).header("X-MPIN-Proof",proof(701,"FD_PREMATURE_REQUEST",MpinProofVerifier.canonical("FD_PREMATURE_REQUEST",secondFdId))).header("Idempotency-Key", "premature-701-2")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Demo premature withdrawal\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("PENDING"))
                .andReturn();
        long closureId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(quote.getResponse().getContentAsString()).get("closure_id").asLong();
        var quoteJson = new com.fasterxml.jackson.databind.ObjectMapper().readTree(quote.getResponse().getContentAsString());
        BigDecimal penalty = quoteJson.get("penalty_amount").decimalValue();
        BigDecimal paidInterest = quoteJson.get("paid_interest").decimalValue();
        BigDecimal netPayable = quoteJson.get("net_payable_amount").decimalValue();
        assertTrue(penalty.signum() > 0);
        assertEquals(0, netPayable.compareTo(new BigDecimal("200.00").add(paidInterest)));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/admin/fixed-deposits/premature-closures")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.content[0].quote.closure_id").value(closureId));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/fixed-deposits/{id}/premature-closure/{closureId}/approve", secondFdId, closureId)
                        .header("Authorization", "Bearer " + token(701, "ROLE_BANK_OFFICER")).header("Idempotency-Key", "self-approve-701"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        BigDecimal savingsBeforeInjectedFailure = jdbc.queryForObject("SELECT sa_balance FROM savings_accounts WHERE sa_account_id=1", BigDecimal.class);
        long savingsVersionBeforeInjectedFailure = jdbc.queryForObject("SELECT sa_row_version FROM savings_accounts WHERE sa_account_id=1", Long.class);
        jdbc.update("UPDATE savings_accounts SET sa_row_version=? WHERE sa_account_id=1", Long.MAX_VALUE);
        try {
            assertThrows(jakarta.servlet.ServletException.class, () -> mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/fixed-deposits/{id}/premature-closure/{closureId}/approve", secondFdId, closureId)
                    .header("Authorization", "Bearer " + adminToken).header("Idempotency-Key", "approve-premature-failure")));
        } finally {
            jdbc.update("UPDATE savings_accounts SET sa_row_version=? WHERE sa_account_id=1", savingsVersionBeforeInjectedFailure);
        }
        assertEquals("PENDING", jdbc.queryForObject("SELECT fpc_status FROM fd_premature_closure WHERE fpc_closure_id=?", String.class, closureId));
        assertEquals("ACTIVE", jdbc.queryForObject("SELECT fa_status FROM fd_accounts WHERE fa_account_id=?", String.class, secondFdId));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM fd_journals WHERE fj_key=?", Integer.class, "INTEREST:" + secondFdId + ":PREMATURE"));
        assertEquals(0, savingsBeforeInjectedFailure.compareTo(jdbc.queryForObject("SELECT sa_balance FROM savings_accounts WHERE sa_account_id=1", BigDecimal.class)));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/fixed-deposits/{id}/premature-closure/{closureId}/approve", secondFdId, closureId)
                        .header("Authorization", "Bearer " + adminToken).header("Idempotency-Key", "approve-premature-702-2"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.status").value("PREMATURELY_CLOSED"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.settledAmount").value(netPayable.doubleValue()));
        assertEquals("APPROVED", jdbc.queryForObject("SELECT fpc_status FROM fd_premature_closure WHERE fpc_closure_id=?", String.class, closureId));
        assertEquals(702L, jdbc.queryForObject("SELECT fpc_decision_by FROM fd_premature_closure WHERE fpc_closure_id=?", Long.class, closureId));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM fd_transactions WHERE ft_account_id=? AND ft_txn_type_id=(SELECT tt_txn_type_id FROM transaction_types WHERE tt_txn_type_code='FD_PENALTY')", Integer.class, secondFdId) > 0);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM fd_journal_lines l JOIN fd_journals j ON j.fj_id=l.jl_journal_id WHERE j.fj_key=? AND l.jl_account_code='INTEREST_EXPENSE' AND l.jl_debit=?", Integer.class, "INTEREST:" + secondFdId + ":PREMATURE", paidInterest));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM fd_journal_lines l JOIN fd_journals j ON j.fj_id=l.jl_journal_id WHERE j.fj_key=? AND l.jl_account_code='FIXED_DEPOSIT' AND l.jl_credit=?", Integer.class, "INTEREST:" + secondFdId + ":PREMATURE", paidInterest));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM fd_journal_lines l JOIN fd_journals j ON j.fj_id=l.jl_journal_id WHERE j.fj_key=? AND ((l.jl_account_code='FIXED_DEPOSIT' AND l.jl_debit=?) OR (l.jl_account_code='SAVINGS' AND l.jl_credit=?))", Integer.class, "SETTLEMENT:" + secondFdId, netPayable, netPayable));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM fd_journals WHERE fj_fd_account_id=? AND fj_operation='SETTLEMENT'", Integer.class, secondFdId));
        BigDecimal fdLiability = jdbc.queryForObject("SELECT COALESCE(SUM(l.jl_credit-l.jl_debit),0) FROM fd_journal_lines l JOIN fd_journals j ON j.fj_id=l.jl_journal_id WHERE j.fj_fd_account_id=? AND l.jl_account_code='FIXED_DEPOSIT'", BigDecimal.class, secondFdId);
        assertEquals(0, BigDecimal.ZERO.compareTo(fdLiability));
        assertEquals(0, savingsBeforeInjectedFailure.add(netPayable).compareTo(jdbc.queryForObject("SELECT sa_balance FROM savings_accounts WHERE sa_account_id=1", BigDecimal.class)));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM savings_transactions WHERE st_account_id=1 AND st_remarks=?", Integer.class, "FD settlement #" + secondFdId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM fd_transactions WHERE ft_account_id=? AND ft_txn_type_id=(SELECT tt_txn_type_id FROM transaction_types WHERE tt_txn_type_code='FD_CLOSURE')", Integer.class, secondFdId));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM fd_account_events WHERE fae_account_id=? AND fae_event_type='PREMATURELY_CLOSED'", Integer.class, secondFdId));
        BigDecimal savingsAfter = jdbc.queryForObject("SELECT sa_balance FROM savings_accounts WHERE sa_account_id=1", BigDecimal.class);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/admin/fixed-deposits/{id}/premature-closure/{closureId}/approve", secondFdId, closureId)
                        .header("Authorization", "Bearer " + adminToken).header("Idempotency-Key", "approve-premature-702-2"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.settledAmount").value(netPayable.doubleValue()));
        assertEquals(0, savingsAfter.compareTo(jdbc.queryForObject("SELECT sa_balance FROM savings_accounts WHERE sa_account_id=1", BigDecimal.class)));
    }

    @Test
    void persistedAccrualUsesEachStoredCompoundingBasis() {
        jdbc.update("INSERT INTO fd_currency_ref(fcr_currency_id,fcr_iso_code,fcr_currency_name,fcr_decimal_places) VALUES(980,'GBP','Pound',2)");
        jdbc.update("INSERT INTO fd_user_ref(fur_user_id,fur_user_uuid,fur_user_status) VALUES(980,?,'ACTIVE')",UUID.randomUUID().toString());
        jdbc.update("INSERT INTO fd_product_ref(fpr_product_id,fpr_product_code,fpr_product_name,fpr_status) VALUES(980,'FD-COMPOUNDING','Compounding Test','ACTIVE')");
        LocalDate start=LocalDate.of(2025,1,31),end=start.plusYears(1);
        for(String basis:new String[]{"MONTHLY","QUARTERLY","YEARLY"}) {
            jdbc.update("INSERT INTO fd_accounts(fa_account_number,fa_primary_holder_id,fa_product_id,fa_principal_amount,fa_currency_id,fa_tenure_months,fa_interest_rate,fa_compounding_frequency,fa_payout_frequency,fa_open_date,fa_maturity_date,fa_status) VALUES(?,980,980,10000,980,12,0.06,?,'AT_MATURITY',?,?,'ACTIVE')","COMPOUND-"+basis,basis,java.sql.Timestamp.valueOf(start.atStartOfDay()),java.sql.Date.valueOf(end));
            long id=jdbc.queryForObject("SELECT fa_account_id FROM fd_accounts WHERE fa_account_number=?",Long.class,"COMPOUND-"+basis);
            batches.accrue(end);
            BigDecimal expected=FdCompounding.maturity(new BigDecimal("10000"),new BigDecimal("0.06"),start,end,2,basis);
            assertEquals(0,expected.compareTo(jdbc.queryForObject("SELECT fa_maturity_amount FROM fd_accounts WHERE fa_account_id=?",BigDecimal.class,id)));
            assertEquals(0,batches.accrue(end).accrualRowsCreated());
            jdbc.update("UPDATE fd_accounts SET fa_status='CLOSED' WHERE fa_account_id=?",id);
        }
    }

    private void seedDemoData() {
        jdbc.update("INSERT INTO fd_user_ref(fur_user_id,fur_user_uuid,fur_full_name,fur_user_status,fur_kyc_status) VALUES(701,?,'Demo Customer','ACTIVE','VERIFIED'),(702,?,'Demo Admin','ACTIVE','VERIFIED')", UUID.randomUUID().toString(), UUID.randomUUID().toString());
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

    private String proof(long userId,String purpose,String canonical) {
        try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(Decoders.BASE64.decode(JWT_SECRET),"HmacSHA256"));byte[] key=mac.doFinal("mpin-proof-v1".getBytes(StandardCharsets.UTF_8));java.time.Instant now=java.time.Instant.now();
            return Jwts.builder().setIssuer("identity").setSubject(Long.toString(userId)).claim("typ","mpin-proof").claim("act",Long.toString(userId)).claim("purpose",purpose).claim("bind",MpinProofVerifier.hash(canonical)).claim("jti",UUID.randomUUID().toString()).setIssuedAt(Date.from(now)).setExpiration(Date.from(now.plusSeconds(90))).signWith(Keys.hmacShaKeyFor(key),SignatureAlgorithm.HS256).compact();
        }catch(Exception e){throw new IllegalStateException(e);}
    }

    private boolean table(String name) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?", Integer.class, name) == 1;
    }

    private int decimalScale(String table, String column) {
        return jdbc.queryForObject("SELECT numeric_scale FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name=? AND column_name=?", Integer.class, table, column);
    }
}
