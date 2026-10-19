package com.bank.fd.application;

import com.bank.common.exception.InvalidInputException;
import com.bank.common.exception.ResourceNotFoundException;
import com.bank.fd.api.dto.SavingsTransferRequest;
import com.bank.fd.domain.FdTimePolicy;
import com.bank.fd.entity.FdAccount;
import com.bank.fd.entity.FdCurrencyReference;
import com.bank.fd.entity.FdUserReference;
import com.bank.fd.entity.SavingsAccount;
import com.bank.fd.entity.SavingsTransaction;
import com.bank.fd.repository.FdAccountRepository;
import com.bank.fd.repository.FdCurrencyReferenceRepository;
import com.bank.fd.repository.SavingsAccountRepository;
import com.bank.fd.repository.SavingsTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SavingsTransferServiceTest {
    private final SavingsAccountRepository accounts = mock(SavingsAccountRepository.class);
    private final SavingsTransactionRepository transactions = mock(SavingsTransactionRepository.class);
    private final FdAccountRepository fdAccounts = mock(FdAccountRepository.class);
    private final FdCurrencyReferenceRepository currencies = mock(FdCurrencyReferenceRepository.class);
    private final FdActorResolver actors = mock(FdActorResolver.class);
    private final FdIdempotencyGuard idempotency = mock(FdIdempotencyGuard.class);
    private SavingsTransferService service;
    private SavingsAccount source;
    private SavingsAccount destination;

    @BeforeEach
    void setUp() {
        var time = new FdTimePolicy(Clock.fixed(Instant.parse("2026-01-15T00:00:00Z"), ZoneOffset.UTC), ZoneOffset.UTC);
        service = new SavingsTransferService(accounts, transactions, fdAccounts, currencies, actors, idempotency, time);
        var holder = new FdUserReference();
        holder.setId(41L);
        when(actors.resolve("alice")).thenReturn(holder);
        FdCurrencyReference currency = new FdCurrencyReference();
        currency.setId(3L);
        currency.setIsoCode("INR");
        currency.setDecimalPlaces(2);
        when(currencies.findById(3L)).thenReturn(Optional.of(currency));
        source = account(1L, "SA-SOURCE", 41L, "1500.00");
        destination = account(2L, "SA-DEST", 42L, "100.00");
        when(accounts.findById(1L)).thenReturn(Optional.of(source));
        when(accounts.findByAccountNumber("SA-DEST")).thenReturn(Optional.of(destination));
        when(accounts.findByIdForUpdate(1L)).thenReturn(Optional.of(source));
        when(accounts.findByIdForUpdate(2L)).thenReturn(Optional.of(destination));
        when(accounts.save(any(SavingsAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(transactions.saveAndFlush(any(SavingsTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void postsLinkedDebitAndCreditAndUpdatesBothBalances() {
        var response = service.transfer("alice", request("SA-DEST", "500.25"), "transfer-key");

        assertEquals("INR", response.currencyCode());
        assertEquals("customer payment", response.remark());
        assertEquals(2, response.postings().size());
        assertEquals("DEBIT", response.postings().get(0).entryType());
        assertEquals("SA-SOURCE", response.postings().get(0).accountNumber());
        assertEquals(0, new BigDecimal("999.75").compareTo(response.postings().get(0).balanceAfter()));
        assertEquals("CREDIT", response.postings().get(1).entryType());
        assertEquals("SA-DEST", response.postings().get(1).accountNumber());
        assertEquals(0, new BigDecimal("600.25").compareTo(response.postings().get(1).balanceAfter()));
        assertEquals(0, new BigDecimal("999.75").compareTo(source.getBalance()));
        assertEquals(0, new BigDecimal("600.25").compareTo(destination.getBalance()));

        ArgumentCaptor<SavingsTransaction> entries = ArgumentCaptor.forClass(SavingsTransaction.class);
        verify(transactions, times(2)).saveAndFlush(entries.capture());
        assertEquals(entries.getAllValues().get(0).getReferenceId(), entries.getAllValues().get(1).getReferenceId());
        assertEquals("DEBIT", entries.getAllValues().get(0).getEntryType());
        assertEquals("CREDIT", entries.getAllValues().get(1).getEntryType());
        verify(idempotency).complete(isNull(), isNull(), eq(response.transactionReference()));
    }

    @Test
    void refusesFdDestinationWithClearAccountTypeError() {
        when(accounts.findByAccountNumber("FD-DEST")).thenReturn(Optional.empty());
        FdAccount fd = new FdAccount();
        when(fdAccounts.findByAccountNumber("FD-DEST")).thenReturn(Optional.of(fd));

        InvalidInputException failure = assertThrows(InvalidInputException.class,
                () -> service.transfer("alice", request("FD-DEST", "10.00"), "transfer-key"));

        assertTrue(failure.getMessage().contains("FD accounts cannot receive transfers"));
        verify(transactions, never()).saveAndFlush(any());
    }

    @Test
    void refusesOtherAccountTypesAsNonSavingsDestinations() {
        when(accounts.findByAccountNumber("CA-DEST")).thenReturn(Optional.empty());
        when(fdAccounts.findByAccountNumber("CA-DEST")).thenReturn(Optional.empty());

        InvalidInputException failure = assertThrows(InvalidInputException.class,
                () -> service.transfer("alice", request("CA-DEST", "10.00"), "transfer-key"));

        assertTrue(failure.getMessage().contains("must belong to a savings account"));
        verify(transactions, never()).saveAndFlush(any());
    }

    @Test
    void refusesInsufficientBalanceInactiveAccountsAndNonOwnerSource() {
        assertThrows(InvalidInputException.class,
                () -> service.transfer("alice", request("SA-DEST", "1500.01"), "insufficient"));
        source.setStatus("INACTIVE");
        assertThrows(InvalidInputException.class,
                () -> service.transfer("alice", request("SA-DEST", "10.00"), "inactive"));
        source.setStatus("ACTIVE");
        source.setHolderId(42L);
        assertThrows(ResourceNotFoundException.class,
                () -> service.transfer("alice", request("SA-DEST", "10.00"), "owner"));
        verify(transactions, never()).saveAndFlush(any());
    }

    @Test
    void rejectsSelfTransferAndCurrencyMismatch() {
        when(accounts.findByAccountNumber("SA-SOURCE")).thenReturn(Optional.of(source));
        assertThrows(InvalidInputException.class,
                () -> service.transfer("alice", request("SA-SOURCE", "10.00"), "same"));
        destination.setCurrencyId(4L);
        assertThrows(InvalidInputException.class,
                () -> service.transfer("alice", request("SA-DEST", "10.00"), "currency"));
        verify(transactions, never()).saveAndFlush(any());
    }

    private static SavingsAccount account(Long id, String number, Long holder, String balance) {
        SavingsAccount account = new SavingsAccount();
        account.setId(id);
        account.setAccountNumber(number);
        account.setHolderId(holder);
        account.setCurrencyId(3L);
        account.setBalance(new BigDecimal(balance));
        account.setStatus("ACTIVE");
        return account;
    }

    private static SavingsTransferRequest request(String destination, String amount) {
        return new SavingsTransferRequest(1L, destination, new BigDecimal(amount), " customer payment ");
    }
}
