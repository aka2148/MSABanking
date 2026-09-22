package com.bank.fd.application;

import com.bank.common.exception.ResourceNotFoundException;
import com.bank.fd.api.dto.HistoryEntry;
import com.bank.fd.entity.FdAccount;
import com.bank.fd.entity.FdCurrencyReference;
import com.bank.fd.entity.FdInterestAccrual;
import com.bank.fd.entity.FdStatus;
import com.bank.fd.entity.FdTransaction;
import com.bank.fd.entity.FdUserReference;
import com.bank.fd.repository.FdAccountRepository;
import com.bank.fd.repository.FdCurrencyReferenceRepository;
import com.bank.fd.repository.FdInterestAccrualRepository;
import com.bank.fd.repository.FdTransactionRepository;
import com.bank.fd.repository.TransactionTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HistoryServiceTest {
    private final FdAccountRepository accountRepository = mock(FdAccountRepository.class);
    private final FdTransactionRepository transactionRepository = mock(FdTransactionRepository.class);
    private final FdInterestAccrualRepository accrualRepository = mock(FdInterestAccrualRepository.class);
    private final TransactionTypeRepository transactionTypeRepository = mock(TransactionTypeRepository.class);
    private final FdCurrencyReferenceRepository currencyRepository = mock(FdCurrencyReferenceRepository.class);
    private final FdActorResolver actorResolver = mock(FdActorResolver.class);
    private HistoryService service;

    @BeforeEach
    void setUp() {
        service = new HistoryService(accountRepository, transactionRepository, accrualRepository,
                transactionTypeRepository, currencyRepository, actorResolver,
                new FdAccessPolicy(actorResolver));
        FdAccount account = new FdAccount();
        account.setId(11L);
        account.setAccountNumber("FD000011");
        account.setPrimaryHolderId(41L);
        account.setCurrencyId(3L);
        account.setPrincipalAmount(new BigDecimal("100000.0000"));
        account.setStatus(FdStatus.MATURED);
        when(accountRepository.findById(11L)).thenReturn(Optional.of(account));
        FdUserReference holder = new FdUserReference();
        holder.setId(41L);
        when(actorResolver.resolve("alice")).thenReturn(holder);
        FdCurrencyReference currency = new FdCurrencyReference();
        currency.setId(3L);
        currency.setIsoCode("USD");
        when(currencyRepository.findById(3L)).thenReturn(Optional.of(currency));
        when(transactionTypeRepository.findIdByCode("DEPOSIT")).thenReturn(Optional.of(9L));
        when(transactionTypeRepository.findIdByCode("INTEREST_ACCRUAL")).thenReturn(Optional.of(8L));
        when(transactionTypeRepository.findIdByCode("MATURITY")).thenReturn(Optional.of(10L));
        when(transactionTypeRepository.findIdByCode("CLOSURE")).thenReturn(Optional.of(12L));
        when(transactionTypeRepository.findIdByCode("PENALTY")).thenReturn(Optional.of(13L));
    }

    @Test
    void mergesTransactionsAndAccrualsInDateOrderWithRunningBalances() {
        FdTransaction funding = transaction(1L, 9L, "100000.0000", "100000.0000",
                OffsetDateTime.of(2025, 7, 2, 10, 0, 0, 0, ZoneOffset.UTC),
                "Internal academic funding record; no external payment rail was called.");
        FdTransaction maturity = transaction(2L, 10L, "106500.0000", "106500.0000",
                OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC),
                "Internal academic maturity record; no external payment rail was called.");
        // Stored out of order on purpose: the service must still return date order.
        when(transactionRepository.findByAccountIdOrderByTransactionDateAscIdAsc(11L))
                .thenReturn(List.of(maturity, funding));
        FdInterestAccrual accrual = new FdInterestAccrual();
        accrual.setId(5L);
        accrual.setAccountId(11L);
        accrual.setAccrualDate(LocalDate.of(2025, 10, 1).atStartOfDay().atOffset(ZoneOffset.UTC));
        accrual.setAccruedAmount(new BigDecimal("1500.0000"));
        accrual.setCumulativeAccrued(new BigDecimal("1500.0000"));
        accrual.setCurrencyId(3L);
        when(accrualRepository.findByAccountIdOrderByAccrualDateAscIdAsc(11L)).thenReturn(List.of(accrual));

        List<HistoryEntry> timeline = service.timeline(11L, "alice");

        assertEquals(3, timeline.size());
        assertEquals("FUNDING", timeline.get(0).entryType());
        assertEquals("TRANSACTION", timeline.get(0).source());
        assertEquals(0, new BigDecimal("100000.00").compareTo(timeline.get(0).amount()));
        assertEquals(0, new BigDecimal("100000.00").compareTo(timeline.get(0).runningBalance()));
        assertEquals("ACCRUAL", timeline.get(1).entryType());
        assertEquals("ACCRUAL", timeline.get(1).source());
        assertEquals(0, new BigDecimal("1500.00").compareTo(timeline.get(1).amount()));
        assertEquals(0, new BigDecimal("101500.00").compareTo(timeline.get(1).runningBalance()));
        assertEquals("MATURITY", timeline.get(2).entryType());
        assertEquals(0, new BigDecimal("106500.00").compareTo(timeline.get(2).amount()));
        assertEquals(0, new BigDecimal("106500.00").compareTo(timeline.get(2).runningBalance()));
        assertTrue(timeline.stream().allMatch(entry -> "USD".equals(entry.currencyCode())));
        assertTrue(timeline.get(0).entryDate().isBefore(timeline.get(1).entryDate()));
        assertTrue(timeline.get(1).entryDate().isBefore(timeline.get(2).entryDate()));
    }

    @Test
    void mapsPenaltyRowWithNullRunningBalance() {
        FdTransaction penalty = transaction(3L, 13L, "498.6300", null,
                OffsetDateTime.of(2026, 1, 1, 10, 0, 0, 0, ZoneOffset.UTC),
                "Internal academic premature-closure penalty record; no external payment rail was called.");
        when(transactionRepository.findByAccountIdOrderByTransactionDateAscIdAsc(11L))
                .thenReturn(List.of(penalty));
        when(accrualRepository.findByAccountIdOrderByAccrualDateAscIdAsc(11L)).thenReturn(List.of());

        List<HistoryEntry> timeline = service.timeline(11L, "alice");

        assertEquals(1, timeline.size());
        assertEquals("PENALTY", timeline.get(0).entryType());
        assertNull(timeline.get(0).runningBalance());
    }

    @Test
    void rejectsMissingAccountAndNonOwnerBeforeMerging() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> service.timeline(99L, "alice"));

        FdUserReference other = new FdUserReference();
        other.setId(99L);
        when(actorResolver.resolve("mallory")).thenReturn(other);
        assertThrows(RuntimeException.class, () -> service.timeline(11L, "mallory"));
        verify(transactionRepository, never()).findByAccountIdOrderByTransactionDateAscIdAsc(any());
        verify(accrualRepository, never()).findByAccountIdOrderByAccrualDateAscIdAsc(any());
    }

    private static FdTransaction transaction(Long id, Long typeId, String amount, String balanceAfter,
                                             OffsetDateTime date, String remarks) {
        FdTransaction transaction = new FdTransaction();
        transaction.setId(id);
        transaction.setAccountId(11L);
        transaction.setTransactionTypeId(typeId);
        transaction.setAmount(new BigDecimal(amount));
        transaction.setTransactionDate(date);
        transaction.setBalanceAfter(balanceAfter == null ? null : new BigDecimal(balanceAfter));
        transaction.setCurrencyId(3L);
        transaction.setRemarks(remarks);
        transaction.setUuid("tx-uuid-" + id);
        return transaction;
    }
}
