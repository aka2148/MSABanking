package com.bank.fd.application;

import com.bank.common.exception.ResourceNotFoundException;
import com.bank.fd.api.dto.ClosureResponse;
import com.bank.fd.domain.FdLifecycleCommand;
import com.bank.fd.domain.FdLifecycleStateMachine;
import com.bank.fd.domain.FdTimePolicy;
import com.bank.fd.domain.FdTransition;
import com.bank.fd.entity.FdAccount;
import com.bank.fd.entity.FdJournal;
import com.bank.fd.entity.FdOperationIdempotency;
import com.bank.fd.entity.FdCurrencyReference;
import com.bank.fd.entity.FdStatus;
import com.bank.fd.entity.FdTransaction;
import com.bank.fd.entity.FdUserReference;
import com.bank.fd.exception.InvalidFdTransitionException;
import com.bank.fd.repository.FdAccountRepository;
import com.bank.fd.repository.FdCurrencyReferenceRepository;
import com.bank.fd.repository.FdTransactionRepository;
import com.bank.fd.repository.TransactionTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ClosureServiceTest {
    private final FdAccountRepository accountRepository = mock(FdAccountRepository.class);
    private final FdTransactionRepository transactionRepository = mock(FdTransactionRepository.class);
    private final TransactionTypeRepository transactionTypeRepository = mock(TransactionTypeRepository.class);
    private final FdCurrencyReferenceRepository currencyRepository = mock(FdCurrencyReferenceRepository.class);
    private final FdActorResolver actorResolver = mock(FdActorResolver.class);
    private ClosureService service;
    private FdAccount account;

    @BeforeEach
    void setUp() {
        service = new ClosureService(accountRepository, transactionRepository, transactionTypeRepository,
                currencyRepository, actorResolver, new FdLifecycleStateMachine(),
                new FdTimePolicy(Clock.fixed(Instant.parse("2026-06-15T10:00:00Z"), ZoneId.of("UTC")),
                        ZoneId.of("UTC")),
                mock(FdIdempotencyGuard.class), new FdAccessPolicy(actorResolver));
        account = new FdAccount();
        account.setId(11L);
        account.setAccountNumber("FD000011");
        account.setPrimaryHolderId(41L);
        account.setCurrencyId(3L);
        account.setPrincipalAmount(new BigDecimal("100000.0000"));
        account.setMaturityAmount(new BigDecimal("106500.00"));
        account.setPolicyVersion("academic-v1");
        account.setStatus(FdStatus.MATURED);
        when(accountRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(account));
        FdUserReference holder = new FdUserReference();
        holder.setId(41L);
        when(actorResolver.resolve("alice")).thenReturn(holder);
        when(transactionTypeRepository.findIdByCode("CLOSURE")).thenReturn(Optional.of(12L));
        FdCurrencyReference currency = new FdCurrencyReference();
        currency.setId(3L);
        currency.setIsoCode("USD");
        when(currencyRepository.findById(3L)).thenReturn(Optional.of(currency));
        when(transactionRepository.save(any(FdTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void closesMaturedAccountWithOneClosureSettlementRow() {
        ClosureResponse response = service.close(11L, "alice");

        assertEquals("CLOSED", response.status());
        assertEquals(new BigDecimal("106500.00"), response.settlementAmount());
        assertEquals("USD", response.currencyCode());
        assertEquals(LocalDate.of(2026, 6, 15), response.closedDate());
        assertTrue(response.remark().toLowerCase().contains("internal"));
        assertEquals(FdStatus.CLOSED, account.getStatus());
        verify(transactionRepository).save(argThat(tx -> tx.getAccountId().equals(11L)
                && tx.getTransactionTypeId().equals(12L)
                && tx.getAmount().compareTo(new BigDecimal("106500.00")) == 0
                && tx.getBalanceAfter().signum() == 0
                && tx.getCurrencyId().equals(3L)
                && tx.getRemarks().toLowerCase().contains("internal")
                && tx.getUuid() != null));
        verify(accountRepository).save(account);
    }

    @Test
    void closureAndReplayUseOriginalIstSettlementDateAfterMidnight() {
        var time = new FdTimePolicy(Clock.fixed(Instant.parse("2026-06-15T19:30:00Z"), ZoneId.of("UTC")),
                ZoneId.of("Asia/Kolkata"));
        var guard = mock(FdIdempotencyGuard.class);
        var accounting = mock(FdAccountingService.class);
        var claim = new FdOperationIdempotency();
        var receipt = new FdJournal();
        receipt.setPostedAt(OffsetDateTime.parse("2026-06-15T19:30:00Z"));
        service = new ClosureService(accountRepository, transactionRepository, transactionTypeRepository,
                currencyRepository, actorResolver, new FdLifecycleStateMachine(), time,
                guard, new FdAccessPolicy(actorResolver));
        ReflectionTestUtils.setField(service, "accounting", accounting);
        when(guard.claim(any(), any(), any(), any())).thenReturn(claim);
        when(accounting.settle(account, account.getMaturityAmount())).thenReturn(receipt);

        assertEquals(LocalDate.of(2026, 6, 16), service.close(11L, "alice", "closure-key").closedDate());
        claim.setAggregateId(11L);
        when(accountRepository.findById(11L)).thenReturn(Optional.of(account));
        when(accounting.settlement(11L)).thenReturn(Optional.of(receipt));
        time.advanceDays(2);

        assertEquals(LocalDate.of(2026, 6, 16), service.close(11L, "alice", "closure-key").closedDate());
        verify(accounting, times(1)).settle(account, account.getMaturityAmount());
        verify(transactionRepository, times(1)).save(any());
    }

    @Test
    void rejectsActiveCloseWithoutWritingAnything() {
        account.setStatus(FdStatus.ACTIVE);

        assertThrows(InvalidFdTransitionException.class, () -> service.close(11L, "alice"));
        assertEquals(FdStatus.ACTIVE, account.getStatus());
        verify(transactionRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void rejectsAlreadyClosedAccountWithoutWritingAnything() {
        account.setStatus(FdStatus.CLOSED);

        assertThrows(InvalidFdTransitionException.class, () -> service.close(11L, "alice"));
        verify(transactionRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void rejectsMissingAccountAndNonOwner() {
        when(accountRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.close(99L, "alice"));

        FdUserReference other = new FdUserReference();
        other.setId(99L);
        when(actorResolver.resolve("mallory")).thenReturn(other);
        assertThrows(RuntimeException.class, () -> service.close(11L, "mallory"));
        assertEquals(FdStatus.MATURED, account.getStatus());
        verify(transactionRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void stateMachineEncodesMaturedCloseTransition() {
        FdLifecycleStateMachine stateMachine = new FdLifecycleStateMachine();
        FdTransition transition = stateMachine.transition(FdStatus.MATURED, FdLifecycleCommand.CLOSE);
        assertEquals(FdStatus.CLOSED, transition.to());
    }
}
