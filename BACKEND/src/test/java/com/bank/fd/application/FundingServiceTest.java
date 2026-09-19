package com.bank.fd.application;

import com.bank.fd.api.dto.FundingRequest;
import com.bank.fd.api.dto.FundingResponse;
import com.bank.common.exception.InvalidInputException;
import com.bank.fd.domain.FdLifecycleStateMachine;
import com.bank.fd.entity.FdAccount;
import com.bank.fd.entity.FdCurrencyReference;
import com.bank.fd.entity.FdProductReference;
import com.bank.fd.entity.FdStatus;
import com.bank.fd.entity.FdTransaction;
import com.bank.fd.entity.FdUserReference;
import com.bank.fd.repository.FdAccountRepository;
import com.bank.fd.repository.FdCurrencyReferenceRepository;
import com.bank.fd.repository.FdProductReferenceRepository;
import com.bank.fd.repository.FdTransactionRepository;
import com.bank.fd.repository.TransactionTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import com.bank.fd.domain.FdTimePolicy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FundingServiceTest {
    private final FdAccountRepository accountRepository = mock(FdAccountRepository.class);
    private final FdTransactionRepository transactionRepository = mock(FdTransactionRepository.class);
    private final TransactionTypeRepository transactionTypeRepository = mock(TransactionTypeRepository.class);
    private final FdProductReferenceRepository productRepository = mock(FdProductReferenceRepository.class);
    private final FdCurrencyReferenceRepository currencyRepository = mock(FdCurrencyReferenceRepository.class);
    private final FdActorResolver actorResolver = mock(FdActorResolver.class);
    private final FdLifecycleStateMachine stateMachine = mock(FdLifecycleStateMachine.class);
    private FundingService service;
    private FdAccount account;

    @BeforeEach
    void setUp() {
        service = new FundingService(accountRepository, transactionRepository, transactionTypeRepository,
                productRepository, currencyRepository, actorResolver, stateMachine,
                new FdTimePolicy(Clock.fixed(Instant.parse("2026-01-15T10:15:30Z"), ZoneId.of("UTC")), ZoneId.of("UTC")));
        account = new FdAccount();
        account.setId(11L);
        account.setPrimaryHolderId(41L);
        account.setProductId(7L);
        account.setCurrencyId(3L);
        account.setPrincipalAmount(new BigDecimal("1000.0000"));
        account.setStatus(FdStatus.PENDING);
        when(accountRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(account));
        FdUserReference holder = new FdUserReference();
        holder.setId(41L);
        when(actorResolver.resolve("alice")).thenReturn(holder);
        when(transactionTypeRepository.findIdByCode("DEPOSIT")).thenReturn(Optional.of(9L));
        when(stateMachine.transition(FdStatus.PENDING, com.bank.fd.domain.FdLifecycleCommand.FUND))
                .thenReturn(new com.bank.fd.domain.FdTransition(FdStatus.PENDING, FdStatus.ACTIVE));
        FdProductReference product = mock(FdProductReference.class);
        when(product.getProductCode()).thenReturn("FD7");
        when(productRepository.findById(7L)).thenReturn(Optional.of(product));
        FdCurrencyReference currency = new FdCurrencyReference();
        currency.setId(3L);
        currency.setIsoCode("USD");
        when(currencyRepository.findById(3L)).thenReturn(Optional.of(currency));
        when(transactionRepository.save(any(FdTransaction.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void fundsExactPrincipalThroughLifecycleAndReturnsTruthfulActiveResponse() {
        FundingResponse response = service.fund(11L, "alice", new FundingRequest(new BigDecimal("1000.00"), "usd"));

        assertEquals("ACTIVE", response.status());
        assertTrue(response.fundingRecorded());
        assertTrue(response.fundingDescription().toLowerCase().contains("internal"));
        assertEquals(FdStatus.ACTIVE, account.getStatus());
        verify(stateMachine).transition(FdStatus.PENDING, com.bank.fd.domain.FdLifecycleCommand.FUND);
        verify(transactionRepository).save(argThat(tx -> tx.getAccountId().equals(11L)
                && tx.getTransactionTypeId().equals(9L)
                && tx.getAmount().compareTo(new BigDecimal("1000.00")) == 0
                && tx.getBalanceAfter().compareTo(new BigDecimal("1000.00")) == 0
                && tx.getCurrencyId().equals(3L)
                && tx.getRemarks().toLowerCase().contains("internal")));
        verify(accountRepository).save(account);
    }

    @Test
    void rejectsPartialFundingBeforeWritingTransaction() {
        assertThrows(InvalidInputException.class,
                () -> service.fund(11L, "alice", new FundingRequest(new BigDecimal("999.99"), "USD")));
        assertEquals(FdStatus.PENDING, account.getStatus());
        verify(transactionRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
        verifyNoInteractions(stateMachine);
    }

    @Test
    void rejectsWrongCurrencyAndNonOwner() {
        assertThrows(InvalidInputException.class,
                () -> service.fund(11L, "alice", new FundingRequest(new BigDecimal("1000.00"), "EUR")));
        verify(transactionRepository, never()).save(any());

        FdUserReference other = new FdUserReference();
        other.setId(99L);
        when(actorResolver.resolve("mallory")).thenReturn(other);
        assertThrows(RuntimeException.class,
                () -> service.fund(11L, "mallory", new FundingRequest(new BigDecimal("1000.00"), "USD")));
        verify(accountRepository, times(2)).findByIdForUpdate(11L);
    }
}
