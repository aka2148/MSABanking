package com.bank.fd;

import com.bank.fd.application.*;
import com.bank.fd.api.dto.FundingRequest;
import com.bank.fd.domain.*;
import com.bank.fd.entity.*;
import com.bank.fd.repository.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real transactional services and JPA persistence with an explicitly isolated in-memory datasource. */
@SpringBootTest(classes=FdSavingsSettlementIntegrationTest.Config.class, webEnvironment=SpringBootTest.WebEnvironment.NONE,
    properties={"spring.datasource.url=jdbc:h2:mem:fd-settlement;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false", "spring.sql.init.mode=never"})
class FdSavingsSettlementIntegrationTest {
    @Configuration @EnableAutoConfiguration
    @EntityScan("com.bank.fd.entity") @EnableJpaRepositories("com.bank.fd.repository")
    @Import({FundingService.class, ClosureService.class, MaturityService.class, AccrualService.class,
        FdAccountingService.class, FdIdempotencyGuard.class, FdAccessPolicy.class,
        FdLifecycleStateMachine.class, FdBatchProcessingService.class})
    static class Config {
        @Bean FdTimePolicy time() { return new FdTimePolicy(Clock.fixed(Instant.parse("2026-01-15T00:00:00Z"),ZoneOffset.UTC),ZoneOffset.UTC); }
    }
    @MockitoBean FdActorResolver actors;
    @MockitoBean FdProductReferenceRepository products;
    @MockitoBean TransactionTypeRepository types;
    @MockitoSpyBean FdJournalRepository journals;
    @Autowired SavingsAccountRepository savings;
    @Autowired FdAccountRepository accounts;
    @Autowired FdCurrencyReferenceRepository currencies;
    @Autowired FundingService funding;
    @Autowired MaturityService maturity;
    @Autowired ClosureService closure;
    @Autowired FdAccountingService accounting;
    @Autowired FdBatchProcessingService batch;
    @Autowired FdTimePolicy time;
    @Autowired PlatformTransactionManager manager;
    @Autowired JdbcTemplate sql;
    Long fdId, savingsId;

    @BeforeEach void setup() {
        sql.update("DELETE FROM public.fd_journal_lines"); sql.update("DELETE FROM public.fd_journals");
        sql.update("DELETE FROM public.fd_operation_idempotency"); sql.update("DELETE FROM public.fd_interest_accrual");
        sql.update("DELETE FROM public.fd_transactions"); sql.update("DELETE FROM public.fd_accounts");
        sql.update("DELETE FROM public.savings_accounts"); sql.update("DELETE FROM public.fd_currency_ref");
        time.reset();
        FdUserReference user=new FdUserReference();user.setId(41L);when(actors.resolve("alice")).thenReturn(user);
        var product=new FdProductReference();product.setId(7L);product.setProductCode("FD7");
        when(products.findById(7L)).thenReturn(Optional.of(product));
        when(types.findIdByCode("DEPOSIT")).thenReturn(Optional.of(1L));
        when(types.findIdByCode("MATURITY")).thenReturn(Optional.of(2L));
        when(types.findIdByCode("CLOSURE")).thenReturn(Optional.of(3L));
        var currency=new FdCurrencyReference();currency.setIsoCode("INR");currency.setDecimalPlaces(2);
        currency=currencies.save(currency);
        var sa=new SavingsAccount();sa.setAccountNumber("SA-INTEGRATION");sa.setDisplayName("Test savings");
        sa.setHolderId(41L);sa.setCurrencyId(currency.getId());sa.setBalance(new BigDecimal("0.00"));
        sa.setCreatedDate(OffsetDateTime.ofInstant(time.now(),ZoneOffset.UTC));sa=savings.save(sa);savingsId=sa.getId();
        new TransactionTemplate(manager).executeWithoutResult(status -> accounting.provisionDemoOpening(
            savings.findByIdForUpdate(savingsId).orElseThrow(),new BigDecimal("5000.00")));
        var fd=new FdAccount();fd.setAccountNumber("FD-INTEGRATION");fd.setPrimaryHolderId(41L);fd.setProductId(7L);
        fd.setCurrencyId(currency.getId());fd.setFundingSavingsAccountId(savingsId);fd.setPrincipalAmount(new BigDecimal("1000.00"));
        fd.setInterestRate(new BigDecimal("0.0650"));fd.setTenureMonths(3);fd.setBookingDate(time.businessDate());
        fd.setMaturityDate(time.businessDate().plusMonths(3));fd.setCompoundingFrequency("SIMPLE");fd.setPayoutFrequency("AT_MATURITY");
        fdId=accounts.save(fd).getId();
    }
    private FundingRequest request(){return new FundingRequest(new BigDecimal("1000.00"),"INR");}
    private BigDecimal balance(){return savings.findById(savingsId).orElseThrow().getBalance();}
    private void mature(){time.advanceDays(90);maturity.processMaturity(fdId,"alice","maturity-key");}
    private long count(String operation){return sql.queryForObject("SELECT COUNT(*) FROM public.fd_journals WHERE fj_fd_account_id=? AND fj_operation=?",Long.class,fdId,operation);}
    private void balanced(){
        Integer invalid=sql.queryForObject("SELECT COUNT(*) FROM (SELECT jl_journal_id FROM public.fd_journal_lines GROUP BY jl_journal_id HAVING SUM(jl_debit)<>SUM(jl_credit))",Integer.class);
        assertEquals(0,invalid);
    }
    @Test void fundingDebitsSavingsOnceAndActivatesWithBalancedJournal(){
        funding.fund(fdId,"alice",request(),"fund-key");
        assertEquals(0,new BigDecimal("4000.00").compareTo(balance()));assertEquals(FdStatus.ACTIVE,accounts.findById(fdId).orElseThrow().getStatus());
        funding.fund(fdId,"alice",request(),"fund-key");
        assertThrows(RuntimeException.class,()->funding.fund(fdId,"alice",request(),"another-key"));
        assertEquals(1,count("FUNDING"));assertEquals(0,new BigDecimal("4000.00").compareTo(balance()));balanced();
    }
    @Test void maturityRecognizesInterestAndClosureReturnsFundsOnceWithStableReceiptDate(){
        funding.fund(fdId,"alice",request(),"fund-key");mature();
        assertEquals(FdStatus.MATURED,accounts.findById(fdId).orElseThrow().getStatus());
        assertEquals(0,new BigDecimal("4000.00").compareTo(balance()));assertEquals(1,count("INTEREST"));
        var result=closure.close(fdId,"alice","close-key");
        assertEquals(FdStatus.CLOSED,accounts.findById(fdId).orElseThrow().getStatus());
        assertEquals(0,new BigDecimal("5016.03").compareTo(balance()));
        assertEquals(0,new BigDecimal("1016.03").compareTo(result.settlementAmount()));
        assertEquals(0,new BigDecimal("1000.00").compareTo(sql.queryForObject(
            "SELECT l.jl_debit FROM public.fd_journal_lines l JOIN public.fd_journals j ON j.fj_id=l.jl_journal_id WHERE j.fj_fd_account_id=? AND j.fj_operation='SETTLEMENT' AND l.jl_account_code='FD_PRINCIPAL_LIABILITY'",BigDecimal.class,fdId)));
        assertEquals(0,new BigDecimal("16.03").compareTo(sql.queryForObject(
            "SELECT l.jl_debit FROM public.fd_journal_lines l JOIN public.fd_journals j ON j.fj_id=l.jl_journal_id WHERE j.fj_fd_account_id=? AND j.fj_operation='SETTLEMENT' AND l.jl_account_code='ACCRUED_INTEREST_PAYABLE'",BigDecimal.class,fdId)));
        time.advanceDays(1);assertEquals(result.closedDate(),closure.close(fdId,"alice","close-key").closedDate());
        assertThrows(RuntimeException.class,()->closure.close(fdId,"alice","other-close"));
        assertEquals(1,count("SETTLEMENT"));assertEquals(0,new BigDecimal("5016.03").compareTo(balance()));
        assertEquals(0,sql.queryForObject("SELECT ft_balance_after FROM public.fd_transactions WHERE ft_account_id=? AND ft_txn_type_id=3",BigDecimal.class,fdId).signum());
        balanced();
    }
    @Test void failedSettlementRollsBackSavingsCreditReceiptClosureAndIdempotency(){
        funding.fund(fdId,"alice",request(),"fund-key");mature();
        doThrow(new IllegalStateException("Injected journal failure after savings credit"))
            .when(journals).saveAndFlush(argThat(j -> "SETTLEMENT".equals(j.getOperation())));
        assertThrows(IllegalStateException.class,()->closure.close(fdId,"alice","close-key"));
        assertEquals(FdStatus.MATURED,accounts.findById(fdId).orElseThrow().getStatus());
        assertEquals(0,new BigDecimal("4000.00").compareTo(balance()));assertEquals(0,count("SETTLEMENT"));
        assertEquals(0,sql.queryForObject("SELECT COUNT(*) FROM public.fd_transactions WHERE ft_account_id=? AND ft_txn_type_id=3",Integer.class,fdId));
        reset(journals);closure.close(fdId,"alice","close-key");assertEquals(0,new BigDecimal("5016.03").compareTo(balance()));
    }
    @Test void insufficientFundsAndInvalidClosureLeavePendingAccountUnchanged(){
        var sa=savings.findById(savingsId).orElseThrow();sa.setBalance(new BigDecimal("10.00"));savings.save(sa);
        assertThrows(RuntimeException.class,()->funding.fund(fdId,"alice",request(),"fund-key"));
        assertThrows(RuntimeException.class,()->closure.close(fdId,"alice","close-key"));
        assertEquals(FdStatus.PENDING,accounts.findById(fdId).orElseThrow().getStatus());assertEquals(0,count("FUNDING"));
        assertEquals(0,new BigDecimal("10.00").compareTo(balance()));
    }
    @Test void repeatedEodDoesNotDuplicateInterestOrTransferMoney(){
        funding.fund(fdId,"alice",request(),"fund-key");time.advanceDays(90);
        batch.runDaily(null);batch.runDaily(null);
        assertEquals(FdStatus.MATURED,accounts.findById(fdId).orElseThrow().getStatus());assertEquals(1,count("INTEREST"));
        assertEquals(0,count("SETTLEMENT"));assertEquals(0,new BigDecimal("4000.00").compareTo(balance()));
        closure.close(fdId,"alice","close-key");batch.runDaily(null);assertEquals(1,count("SETTLEMENT"));balanced();
    }
    @Test void concurrentClosuresCreditSavingsOnlyOnce() throws Exception {
        funding.fund(fdId,"alice",request(),"fund-key");mature();
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var a=pool.submit(() -> { try { closure.close(fdId,"alice","concurrent-a");return true; } catch(RuntimeException e) { return false; } });
            var b=pool.submit(() -> { try { closure.close(fdId,"alice","concurrent-b");return true; } catch(RuntimeException e) { return false; } });
            assertNotEquals(a.get(),b.get());
        } finally { pool.shutdownNow(); }
        assertEquals(1,count("SETTLEMENT"));assertEquals(0,new BigDecimal("5016.03").compareTo(balance()));
    }
    @Test void quarterlyCompoundingSettlesFinalInterestWithoutSummingCumulativeSnapshots() {
        var fd=accounts.findById(fdId).orElseThrow();fd.setInterestRate(new BigDecimal("0.1200"));
        fd.setCompoundingFrequency("QUARTERLY");fd.setPolicyVersion(AccrualService.COMPOUND_POLICY_VERSION);accounts.save(fd);
        funding.fund(fdId,"alice",request(),"fund-key");time.advanceDays(90);batch.runDaily(null);
        assertEquals(FdStatus.MATURED,accounts.findById(fdId).orElseThrow().getStatus());
        var result=closure.close(fdId,"alice","close-key");
        assertEquals(0,new BigDecimal("1029.59").compareTo(result.settlementAmount()));
        assertEquals(0,new BigDecimal("5029.59").compareTo(balance()));assertEquals(1,count("INTEREST"));balanced();
    }
    @Test void existingFundedFdIsNotDebitedAgainAndUnmappedHistoryCannotBePaid() {
        var fd=accounts.findById(fdId).orElseThrow();fd.setStatus(FdStatus.ACTIVE);fd.setFundingSavingsAccountId(null);accounts.save(fd);
        assertThrows(RuntimeException.class,()->funding.fund(fdId,"alice",request(),"fund-key"));
        mature();assertThrows(RuntimeException.class,()->closure.close(fdId,"alice","close-key"));
        assertEquals(0,new BigDecimal("5000.00").compareTo(balance()));assertEquals(0,count("FUNDING"));assertEquals(0,count("SETTLEMENT"));
        assertEquals(FdStatus.MATURED,accounts.findById(fdId).orElseThrow().getStatus());
    }
}
