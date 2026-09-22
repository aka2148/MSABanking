package com.bank.fd.application;

import com.bank.common.exception.InvalidInputException;
import com.bank.fd.api.dto.AccrualResponse;
import com.bank.fd.domain.FdLifecycleStateMachine;
import com.bank.fd.domain.FdTimePolicy;
import com.bank.fd.entity.FdAccount;
import com.bank.fd.entity.FdCurrencyReference;
import com.bank.fd.entity.FdInterestAccrual;
import com.bank.fd.entity.FdStatus;
import com.bank.fd.entity.FdTransaction;
import com.bank.fd.entity.FdUserReference;
import com.bank.fd.exception.InvalidFdTransitionException;
import com.bank.fd.repository.FdAccountRepository;
import com.bank.fd.repository.FdCurrencyReferenceRepository;
import com.bank.fd.repository.FdInterestAccrualRepository;
import com.bank.fd.repository.FdTransactionRepository;
import com.bank.fd.repository.TransactionTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class AccrualMaturityServiceTest {
    private final FdAccountRepository accountRepository = mock(FdAccountRepository.class);
    private final FdInterestAccrualRepository accrualRepository = mock(FdInterestAccrualRepository.class);
    private final FdCurrencyReferenceRepository currencyRepository = mock(FdCurrencyReferenceRepository.class);
    private final TransactionTypeRepository transactionTypeRepository = mock(TransactionTypeRepository.class);
    private final FdTransactionRepository transactionRepository = mock(FdTransactionRepository.class);
    private final FdActorResolver actorResolver = mock(FdActorResolver.class);
    private AccrualService accrualService;
    private MaturityService maturityService;
    private FdAccount account;

    @BeforeEach
    void setUp() {
        FdTimePolicy timePolicy = new FdTimePolicy(
                Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneId.of("UTC")), ZoneId.of("UTC"));
        accrualService = new AccrualService(accountRepository, accrualRepository, currencyRepository,
                actorResolver, timePolicy);
        maturityService = new MaturityService(accountRepository, accrualRepository, currencyRepository,
                transactionTypeRepository, transactionRepository, actorResolver,
                new FdLifecycleStateMachine(), timePolicy);

        account = new FdAccount();
        account.setId(11L);
        account.setAccountNumber("FD000011");
        account.setPrimaryHolderId(41L);
        account.setProductId(7L);
        account.setCurrencyId(3L);
        account.setPrincipalAmount(new BigDecimal("100000"));
        account.setInterestRate(new BigDecimal("0.0650"));
        account.setBookingDate(LocalDate.of(2025, 1, 1));
        account.setMaturityDate(LocalDate.of(2026, 1, 1));
        account.setPolicyVersion("academic-v1");
        account.setStatus(FdStatus.ACTIVE);

        when(accountRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(account));
        when(accountRepository.findById(11L)).thenReturn(Optional.of(account));
        FdUserReference holder = new FdUserReference();
        holder.setId(41L);
        when(actorResolver.resolve("alice")).thenReturn(holder);
        FdCurrencyReference currency = new FdCurrencyReference();
        currency.setId(3L);
        currency.setIsoCode("USD");
        when(currencyRepository.findById(3L)).thenReturn(Optional.of(currency));
        when(transactionTypeRepository.findIdByCode("MATURITY")).thenReturn(Optional.of(10L));
        when(accrualRepository.save(any(FdInterestAccrual.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(FdTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void accrualOnActiveComputesKnownFullYearValue() {
        when(accrualRepository.findByAccountIdAndAccrualDate(eq(11L), any())).thenReturn(Optional.empty());

        AccrualResponse response = accrualService.runAccrual(11L, "alice", LocalDate.of(2026, 1, 1));

        assertEquals(0, response.accruedAmount().compareTo(new BigDecimal("6500.00")));
        assertEquals("USD", response.currencyCode());
        assertEquals("ACTIVE", response.status());
        assertEquals(LocalDate.of(2026, 1, 1), response.accrualDate());
        verify(accrualRepository).save(argThat(row -> row.getAccountId().equals(11L)
                && row.getAccruedAmount().compareTo(new BigDecimal("6500.00")) == 0
                && row.getCurrencyId().equals(3L)));
    }

    @Test
    void accrualOnPendingThrowsBeforeWriting() {
        account.setStatus(FdStatus.PENDING);

        assertThrows(InvalidInputException.class,
                () -> accrualService.runAccrual(11L, "alice", LocalDate.of(2026, 1, 1)));
        verify(accrualRepository, never()).save(any());
    }

    @Test
    void secondAccrualForSameDateReturnsExistingRowWithoutDuplicate() {
        FdInterestAccrual existing = new FdInterestAccrual();
        existing.setAccountId(11L);
        existing.setAccrualDate(OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        existing.setAccruedAmount(new BigDecimal("6500.0000"));
        existing.setCumulativeAccrued(new BigDecimal("6500.0000"));
        existing.setCurrencyId(3L);
        when(accrualRepository.findByAccountIdAndAccrualDate(eq(11L), any()))
                .thenReturn(Optional.empty(), Optional.of(existing));

        AccrualResponse first = accrualService.runAccrual(11L, "alice", LocalDate.of(2026, 1, 1));
        AccrualResponse second = accrualService.runAccrual(11L, "alice", LocalDate.of(2026, 1, 1));

        assertEquals(0, first.accruedAmount().compareTo(second.accruedAmount()));
        verify(accrualRepository, times(1)).save(any(FdInterestAccrual.class));
    }

    @Test
    void cumulativeSumsOrderedRowsAndEmptyReadsReturnZero() {
        FdInterestAccrual row = new FdInterestAccrual();
        row.setAccountId(11L);
        row.setAccrualDate(OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        row.setAccruedAmount(new BigDecimal("6500.0000"));
        row.setCumulativeAccrued(new BigDecimal("6500.0000"));
        row.setCurrencyId(3L);
        when(accrualRepository.findByAccountIdOrderByAccrualDateAscIdAsc(11L))
                .thenReturn(List.of(), List.of(row));

        AccrualResponse empty = accrualService.cumulative(11L, "alice");
        assertEquals(0, empty.cumulativeAccrued().compareTo(BigDecimal.ZERO));
        assertEquals("ACTIVE", empty.status());

        AccrualResponse total = accrualService.cumulative(11L, "alice");
        assertEquals(0, total.cumulativeAccrued().compareTo(new BigDecimal("6500.00")));
    }

    @Test
    void maturityBeforeMaturityDateThrows() {
        account.setMaturityDate(LocalDate.of(2026, 6, 1));

        assertThrows(InvalidInputException.class, () -> maturityService.processMaturity(11L, "alice"));
        assertEquals(FdStatus.ACTIVE, account.getStatus());
        verify(transactionRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void maturityOnActiveAtMaturityDateReturnsMaturedWithFinalAmount() {
        when(accrualRepository.findByAccountIdAndAccrualDate(eq(11L), any())).thenReturn(Optional.empty());

        AccrualResponse response = maturityService.processMaturity(11L, "alice");

        assertEquals("MATURED", response.status());
        assertEquals(FdStatus.MATURED, account.getStatus());
        assertEquals(0, response.maturityAmount().compareTo(new BigDecimal("106500.00")));
        verify(transactionRepository).save(argThat(tx -> tx.getAccountId().equals(11L)
                && tx.getTransactionTypeId().equals(10L)
                && tx.getAmount().compareTo(new BigDecimal("106500.00")) == 0
                && tx.getBalanceAfter().compareTo(new BigDecimal("106500.00")) == 0
                && tx.getRemarks().toLowerCase().contains("internal")));
        verify(accountRepository).save(account);
    }

    @Test
    void maturityOnPendingAndClosedThrowsInvalidTransition() {
        account.setStatus(FdStatus.PENDING);
        assertThrows(InvalidFdTransitionException.class, () -> maturityService.processMaturity(11L, "alice"));

        account.setStatus(FdStatus.CLOSED);
        assertThrows(InvalidFdTransitionException.class, () -> maturityService.processMaturity(11L, "alice"));

        verify(transactionRepository, never()).save(any());
    }
}
