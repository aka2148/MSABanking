package com.bank.fdservice;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.Date;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class LedgerInvariantIntegrationTest {
    private static final String SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWYwMTIzNDU2Nzg5YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZg==";
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4").withDatabaseName("moneypal_fd").withUsername("ledger_test").withPassword(UUID.randomUUID().toString());
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", MYSQL::getJdbcUrl); p.add("spring.datasource.username", MYSQL::getUsername); p.add("spring.datasource.password", MYSQL::getPassword);
        p.add("app.jwt.secret", () -> SECRET); p.add("app.kafka.enabled", () -> false); p.add("app.demo.opening-balance.enabled", () -> false);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @MockBean IdentitySessionClient identitySessionClient;
    @Autowired PostingService postings;
    @Autowired PlatformTransactionManager transactionManager;
    long customer = 77101, other = 77102, staff = 77103, usd = 1, jpy = 2, source, destination, otherSource, jpyAccount;
    String customerToken, otherToken, staffToken, adminToken;

    @BeforeEach void seed() {
        IdentitySessionTestSupport.stub(identitySessionClient, SECRET);
        jdbc.update("DELETE FROM fd_operation_idempotency WHERE foi_actor_key IN (?,?,?,?)", "77101", "77102", "77103", "77104");
        jdbc.update("DELETE FROM fd_journal_lines WHERE jl_journal_id IN (SELECT fj_id FROM fd_journals WHERE fj_key LIKE 'TELLER_%' OR fj_key LIKE 'TRANSFER:TRF-%')");
        jdbc.update("DELETE FROM fd_journals WHERE fj_key LIKE 'TELLER_%' OR fj_key LIKE 'TRANSFER:TRF-%'");
        jdbc.update("DELETE FROM savings_transactions WHERE st_reference_id LIKE 'STAFF-%' OR st_reference_id LIKE 'TRF-%'");
        jdbc.update("DELETE FROM savings_accounts WHERE sa_account_number LIKE 'LEDGER-%'");
        jdbc.update("DELETE FROM fd_user_ref WHERE fur_user_id IN (?,?,?)", customer, other, staff);
        jdbc.update("INSERT INTO fd_currency_ref(fcr_currency_id,fcr_iso_code,fcr_currency_name,fcr_decimal_places) VALUES(1,'USD','US Dollar',2),(2,'JPY','Yen',0) ON DUPLICATE KEY UPDATE fcr_iso_code=VALUES(fcr_iso_code)");
        jdbc.update("INSERT INTO fd_user_ref(fur_user_id,fur_user_uuid,fur_full_name,fur_user_status,fur_kyc_status) VALUES(?,?,'Ledger Customer','ACTIVE','VERIFIED'),(?,?,'Other Customer','ACTIVE','VERIFIED'),(?,?,'Ledger Staff','ACTIVE','VERIFIED')",
                customer,UUID.randomUUID().toString(),other,UUID.randomUUID().toString(),staff,UUID.randomUUID().toString());
        source = account("LEDGER-A-" + UUID.randomUUID(), customer, usd, "100.00");
        destination = account("LEDGER-B-" + UUID.randomUUID(), customer, usd, "25.00");
        otherSource = account("LEDGER-C-" + UUID.randomUUID(), other, usd, "40.00");
        jpyAccount = account("LEDGER-J-" + UUID.randomUUID(), customer, jpy, "100");
        customerToken = token(customer,"ROLE_CUSTOMER"); otherToken = token(other,"ROLE_CUSTOMER"); staffToken = token(staff,"ROLE_BANK_OFFICER"); adminToken = token(77104,"ROLE_ADMIN");
    }

    @Test void transferHasPairedHistoryBalancedJournalAndStableReplay() throws Exception {
        String target = accountNumber(destination);
        mvc.perform(transfer("same-transfer", source, target, "10.00", null)).andExpect(status().isOk())
                .andExpect(jsonPath("$.reference").isString()).andExpect(jsonPath("$.fromBalanceAfter").value(90));
        mvc.perform(transfer("same-transfer", source, target, "10", null)).andExpect(status().isOk());
        assertMoney("90.00", source); assertMoney("35.00", destination);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM savings_transactions WHERE st_reference_id LIKE 'TRF-%'", Integer.class));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM fd_journals WHERE fj_operation='TRANSFER'", Integer.class));
        assertEquals(0, jdbc.queryForObject("SELECT fj_total_debit-fj_total_credit FROM fd_journals WHERE fj_operation='TRANSFER'", java.math.BigDecimal.class).signum());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM fd_journal_lines WHERE jl_account_code='SAVINGS' AND jl_savings_account_id IS NOT NULL", Integer.class));
        mvc.perform(transfer("same-transfer", source, target, "11.00", null)).andExpect(status().isConflict());
        mvc.perform(get("/api/v1/admin/ledger/reconciliation").header("Authorization", adminToken)).andExpect(status().isOk())
                .andExpect(jsonPath("$.journalHeaderMismatches.length()").value(0));
    }

    @Test void transferRejectsInvalidOwnershipCurrencyFundsAndScaleWithoutPartialEffects() throws Exception {
        mvc.perform(transfer("insufficient", source, accountNumber(destination), "101.00", null)).andExpect(status().isConflict());
        mvc.perform(transfer("self", source, accountNumber(source), "1.00", null)).andExpect(status().isBadRequest());
        mvc.perform(transfer("currency", source, accountNumber(jpyAccount), "1.00", null)).andExpect(status().isBadRequest());
        mvc.perform(transfer("ownership", otherSource, accountNumber(destination), "1.00", null).header("Authorization", customerToken)).andExpect(status().isNotFound());
        mvc.perform(transfer("unknown", source, "NO-SUCH-ACCOUNT", "1.00", null)).andExpect(status().isNotFound());
        jdbc.update("UPDATE savings_accounts SET sa_status='INACTIVE' WHERE sa_account_id=?", destination);
        mvc.perform(transfer("inactive", source, accountNumber(destination), "1.00", null)).andExpect(status().isConflict());
        jdbc.update("UPDATE savings_accounts SET sa_status='ACTIVE' WHERE sa_account_id=?", destination);
        mvc.perform(transfer("scale", jpyAccount, accountNumber(source), "1.5", null)).andExpect(status().isBadRequest());
        mvc.perform(transfer("zero", source, accountNumber(destination), "0", null)).andExpect(status().isBadRequest());
        mvc.perform(transfer("remark", source, accountNumber(destination), "1.00", "bad <remark>")).andExpect(status().isBadRequest());
        assertMoney("100.00", source); assertMoney("25.00", destination); assertMoney("100", jpyAccount);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM savings_transactions WHERE st_reference_id LIKE 'TRF-%'", Integer.class));
        jdbc.update("UPDATE fd_user_ref SET fur_kyc_status='PENDING' WHERE fur_user_id=?", customer);
        org.mockito.Mockito.doReturn(new IdentitySessionClient.Session(customer, "test-" + customer,
                java.util.List.of("ROLE_CUSTOMER"), "PENDING", "LOW"))
                .when(identitySessionClient).validate(org.mockito.ArgumentMatchers.anyString());
        mvc.perform(transfer("kyc", source, accountNumber(destination), "1.00", null)).andExpect(status().isForbidden());
        assertMoney("100.00", source);
    }

    @Test void tellerOperationsAreJournaledAndOpeningDefaultsOff() throws Exception {
        mvc.perform(get("/api/v1/savings-accounts/demo-options").header("Authorization", customerToken)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/savings-accounts/{id}/demo-opening", source).header("Authorization", customerToken).header("Idempotency-Key","demo-off").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":10.00}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/savings-accounts/demo-options").header("Authorization", staffToken)).andExpect(status().isOk()).andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(post("/api/v1/savings-accounts/{id}/demo-opening", source).header("Authorization", staffToken).header("Idempotency-Key","demo-off-staff").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":10.00}"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/fd/v1/staff/savings-accounts/{id}/deposit", source).header("Authorization", staffToken).header("Idempotency-Key","teller-deposit").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":10.00,\"remarks\":\"cash\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(110));
        mvc.perform(post("/fd/v1/staff/savings-accounts/{id}/deposit", source).header("Authorization", staffToken).header("Idempotency-Key","teller-deposit").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":10.00,\"remarks\":\"cash\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/fd/v1/staff/savings-accounts/{id}/withdraw", source).header("Authorization", staffToken).header("X-MPIN-Proof",proof(customer,Long.toString(staff),"WITHDRAWAL",MpinProofVerifier.canonical("WITHDRAWAL",source,new java.math.BigDecimal("20.00")))).header("Idempotency-Key","teller-withdraw").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":20.00}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(90));
        mvc.perform(post("/fd/v1/staff/savings-accounts/{id}/withdraw", source).header("Authorization", staffToken).header("X-MPIN-Proof",proof(customer,Long.toString(staff),"WITHDRAWAL",MpinProofVerifier.canonical("WITHDRAWAL",source,new java.math.BigDecimal("1000.00")))).header("Idempotency-Key","teller-overdraft").contentType(MediaType.APPLICATION_JSON).content("{\"amount\":1000.00}"))
                .andExpect(status().isConflict());
        assertMoney("90.00", source);
        mvc.perform(post("/fd/v1/staff/savings-accounts/{id}/deposit", source)
                        .header("Authorization", staffToken).header("Idempotency-Key", "teller-out-of-range")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"amount\":10000000000000000.00}"))
                .andExpect(status().isBadRequest());
        assertMoney("90.00", source);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM fd_journals WHERE fj_operation IN ('TELLER_DEPOSIT','TELLER_WITHDRAWAL')", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM savings_transactions WHERE st_reference_id LIKE 'STAFF-%'", Integer.class));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM fd_journals WHERE fj_created_by=?", Integer.class, Long.toString(staff)));
    }

    @Test void twentyConcurrentTransfersCannotOverdraw() throws Exception {
        jdbc.update("UPDATE savings_accounts SET sa_balance=100.00 WHERE sa_account_id=?", source);
        var pool = Executors.newFixedThreadPool(20);
        try {
            var tasks = new java.util.ArrayList<Callable<Integer>>();
            for (int i=0;i<20;i++) { final int n=i; tasks.add(() -> mvc.perform(transfer("parallel-"+n, source, accountNumber(destination), "10.00", null)).andReturn().getResponse().getStatus()); }
            var results = pool.invokeAll(tasks, 90, TimeUnit.SECONDS);
            long success=0;
            for (var result : results) { assertFalse(result.isCancelled()); int code=result.get(); if(code==200)success++; else assertEquals(409,code); }
            assertEquals(10,success); assertMoney("0.00", source); assertMoney("125.00", destination);
            assertEquals(10,jdbc.queryForObject("SELECT COUNT(*) FROM fd_journals WHERE fj_operation='TRANSFER'",Integer.class));
        } finally { pool.shutdownNow(); }
    }

    @Test void concurrentSameKeyProducesOneTransferAndNoServerError() throws Exception {
        var pool=Executors.newFixedThreadPool(12);
        try {
            var tasks=new java.util.ArrayList<Callable<Integer>>();
            for(int i=0;i<12;i++)tasks.add(()->mvc.perform(transfer("concurrent-same-key",source,accountNumber(destination),"10.00",null)).andReturn().getResponse().getStatus());
            var results=pool.invokeAll(tasks,90,TimeUnit.SECONDS);
            for(var result:results){assertFalse(result.isCancelled());int code=result.get();assertTrue(code==200||code==409,"unexpected HTTP "+code);}
            assertMoney("90.00",source);assertMoney("35.00",destination);
            assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM fd_journals WHERE fj_operation='TRANSFER'",Integer.class));
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM savings_transactions WHERE st_reference_id LIKE 'TRF-%'",Integer.class));
        }finally{pool.shutdownNow();}
    }

    @Test void injectedPostingFailureRollsBackBalancesHistoryJournalAndIdempotency() throws Exception {
        var request = new SavingsTransferController.TransferRequest(source,accountNumber(destination),new java.math.BigDecimal("10.00"),null);
        String hash = SavingsTransferController.requestHash(request);
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.update("INSERT INTO fd_operation_idempotency(foi_operation_scope,foi_actor_key,foi_idempotency_key,foi_request_hash,foi_status) VALUES('SAVINGS_TRANSFER',?,?,?,'PENDING')",Long.toString(customer),"fault-transfer",hash);
            postings.postSavingsTransfer(source,destination,usd,new java.math.BigDecimal("10.00"),"TRF-FAULT-ROLLBACK","Savings transfer",Long.toString(customer));
            jdbc.update("UPDATE fd_operation_idempotency SET foi_status='COMPLETED',foi_aggregate_id=?,foi_response_reference='TRF-FAULT-ROLLBACK' WHERE foi_operation_scope='SAVINGS_TRANSFER' AND foi_actor_key=? AND foi_idempotency_key=?",source,Long.toString(customer),"fault-transfer");
            throw new IllegalStateException("injected posting failure after history and journal writes");
        }));
        assertMoney("100.00",source);assertMoney("25.00",destination);
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM savings_transactions WHERE st_reference_id LIKE 'TRF-%'",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM fd_journals WHERE fj_operation='TRANSFER'",Integer.class));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM fd_operation_idempotency WHERE foi_operation_scope='SAVINGS_TRANSFER' AND foi_idempotency_key='fault-transfer'",Integer.class));
    }

    @Test void oppositeDirectionTransfersLockRowsInStableOrder() throws Exception {
        jdbc.update("UPDATE savings_accounts SET sa_balance=100.00 WHERE sa_account_id=?",destination);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var tasks=java.util.List.of(
                    (Callable<Integer>)()->mvc.perform(transfer("ab",source,accountNumber(destination),"10.00",null)).andReturn().getResponse().getStatus(),
                    ()->mvc.perform(transfer("ba",destination,accountNumber(source),"10.00",null)).andReturn().getResponse().getStatus());
            var results=pool.invokeAll(tasks,90,TimeUnit.SECONDS);
            for(var result:results){assertFalse(result.isCancelled());assertEquals(200,result.get());}
            assertMoney("100.00",source);assertMoney("100.00",destination);
            assertEquals(2,jdbc.queryForObject("SELECT COUNT(*) FROM fd_journals WHERE fj_operation='TRANSFER'",Integer.class));
        }finally{pool.shutdownNow();}
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder transfer(String key,long from,String to,String amount,String remark) {
        String body="{\"fromAccountId\":"+from+",\"toAccountNumber\":\""+to+"\",\"amount\":"+amount+(remark==null?"":",\"remark\":\""+remark+"\"")+"}";
        String canonical=MpinProofVerifier.canonical("TRANSFER",from,to,new java.math.BigDecimal(amount));
        return post("/api/v1/savings-accounts/transfers").header("Authorization",customerToken).header("X-MPIN-Proof",proof(customer,Long.toString(customer),"TRANSFER",canonical)).header("Idempotency-Key",key).contentType(MediaType.APPLICATION_JSON).content(body);
    }
    private long account(String number,long owner,long currency,String balance) {
        jdbc.update("INSERT INTO savings_accounts(sa_account_number,sa_holder_id,sa_currency_id,sa_display_name,sa_balance,sa_status,sa_created_date) VALUES(?,?,?, 'Test account',?,'ACTIVE',UTC_TIMESTAMP(6))",number,owner,currency,new java.math.BigDecimal(balance));
        return jdbc.queryForObject("SELECT sa_account_id FROM savings_accounts WHERE sa_account_number=?",Long.class,number);
    }
    private String accountNumber(long id){return jdbc.queryForObject("SELECT sa_account_number FROM savings_accounts WHERE sa_account_id=?",String.class,id);}
    private void assertMoney(String expected,long id){assertEquals(0,new java.math.BigDecimal(expected).compareTo(jdbc.queryForObject("SELECT sa_balance FROM savings_accounts WHERE sa_account_id=?",java.math.BigDecimal.class,id)));}
    private static String token(long id,String role){return "Bearer "+Jwts.builder().setSubject(Long.toString(id)).claim("userId",id).claim("roles",role).signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)),SignatureAlgorithm.HS512).compact();}
    private static String proof(long owner,String actor,String purpose,String canonical){
        try{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(Decoders.BASE64.decode(SECRET),"HmacSHA256"));byte[] key=mac.doFinal("mpin-proof-v1".getBytes(StandardCharsets.UTF_8));java.time.Instant now=java.time.Instant.now();
            return Jwts.builder().setIssuer("identity").setSubject(Long.toString(owner)).claim("typ","mpin-proof").claim("act",actor).claim("purpose",purpose).claim("bind",MpinProofVerifier.hash(canonical)).claim("jti",UUID.randomUUID().toString()).setIssuedAt(Date.from(now)).setExpiration(Date.from(now.plusSeconds(90))).signWith(Keys.hmacShaKeyFor(key),SignatureAlgorithm.HS256).compact();
        }catch(Exception e){throw new IllegalStateException(e);}
    }
}
