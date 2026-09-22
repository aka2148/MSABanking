package com.bank.fd.application;

import com.bank.common.exception.InvalidInputException;
import com.bank.common.exception.ResourceNotFoundException;
import com.bank.fd.api.dto.PrematureClosureResponse;
import com.bank.fd.domain.FdLifecycleStateMachine;
import com.bank.fd.domain.FdTimePolicy;
import com.bank.fd.entity.FdAccount;
import com.bank.fd.entity.FdCurrencyReference;
import com.bank.fd.entity.FdInterestAccrual;
import com.bank.fd.entity.FdPrematureClosure;
import com.bank.fd.entity.FdRuleOutcomeReference;
import com.bank.fd.entity.FdStatus;
import com.bank.fd.entity.FdTransaction;
import com.bank.fd.entity.FdUserReference;
import com.bank.fd.exception.InvalidFdTransitionException;
import com.bank.fd.exception.PrematureClosureNotSupportedException;
import com.bank.fd.repository.FdAccountRepository;
import com.bank.fd.repository.FdCurrencyReferenceRepository;
import com.bank.fd.repository.FdInterestAccrualRepository;
import com.bank.fd.repository.FdPrematureClosureRepository;
import com.bank.fd.repository.FdRuleOutcomeReferenceRepository;
import com.bank.fd.repository.FdTransactionRepository;
import com.bank.fd.repository.TransactionTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PrematureClosureServiceTest {
    private final FdAccountRepository accountRepository = mock(FdAccountRepository.class);
    private final FdPrematureClosureRepository closureRepository = mock(FdPrematureClosureRepository.class);
    private final FdTransactionRepository transactionRepository = mock(FdTransactionRepository.class);
    private final FdInterestAccrualRepository accrualRepository = mock(FdInterestAccrualRepository.class);
    private final TransactionTypeRepository transactionTypeRepository = mock(TransactionTypeRepository.class);
    private final FdCurrencyReferenceRepository currencyRepository = mock(FdCurrencyReferenceRepository.class);
    private final FdRuleOutcomeReferenceRepository ruleOutcomeRepository =
            mock(FdRuleOutcomeReferenceRepository.class);
    private final FdActorResolver actorResolver = mock(FdActorResolver.class);
    private PrematureClosureService service;
    private FdAccount account;

    @BeforeEach
    void setUp() {
        service = new PrematureClosureService(accountRepository, closureRepository, transactionRepository,
                accrualRepository, transactionTypeRepository, currencyRepository, ruleOutcomeRepository,
                actorResolver, new FdLifecycleStateMachine(),
                new FdTimePolicy(Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneId.of("UTC")),
                        ZoneId.of("UTC")));
        account = new FdAccount();
        account.setId(11L);
        account.setAccountNumber("FD000011");
        account.setPrimaryHolderId(41L);
        account.setProductId(7L);
        account.setAppliedRateOutcomeId(77L);
        account.setCurrencyId(3L);
        account.setPrincipalAmount(new BigDecimal("100000.0000"));
        account.setInterestRate(new BigDecimal("0.0650"));
        account.setBookingDate(LocalDate.of(2025, 7, 3));
        account.setMaturityDate(LocalDate.of(2026, 7, 2));
        account.setPolicyVersion("academic-v1");
        account.setStatus(FdStatus.ACTIVE);
        when(accountRepository.findByIdForUpdate(11L)).thenReturn(Optional.of(account));
        FdUserReference holder = new FdUserReference();
        holder.setId(41L);
        when(actorResolver.resolve("alice")).thenReturn(holder);
        FdRuleOutcomeReference penaltyOutcome = mock(FdRuleOutcomeReference.class);
        when(penaltyOutcome.getId()).thenReturn(77L);
        when(penaltyOutcome.getOutcomeType()).thenReturn("CLOSURE_PENALTY");
        when(penaltyOutcome.getOutcomeValue()).thenReturn("1.00");
        when(ruleOutcomeRepository.findById(77L)).thenReturn(Optional.of(penaltyOutcome));
        FdCurrencyReference currency = new FdCurrencyReference();
        currency.setId(3L);
        currency.setIsoCode("USD");
        when(currencyRepository.findById(3L)).thenReturn(Optional.of(currency));
        when(transactionTypeRepository.findIdByCode("PENALTY")).thenReturn(Optional.of(13L));
        when(transactionTypeRepository.findIdByCode("CLOSURE")).thenReturn(Optional.of(12L));
        when(closureRepository.save(any(FdPrematureClosure.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(transactionRepository.save(any(FdTransaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(accrualRepository.save(any(FdInterestAccrual.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void requestPersistsPendingQuoteWithPenaltyPointMath() {
        // 182 elapsed days at contracted 0.0650 with a 1.00-point penalty:
        // full 3241.10, penalized at 0.0550 = 2742.47, foregone 498.63.
        PrematureClosureResponse response = service.requestPrematureClosure(11L, "alice", "need funds early");

        assertEquals("PENDING", response.status());
        assertEquals(11L, response.accountId());
        assertEquals("USD", response.currencyCode());
        assertNull(response.approvedBy());
        assertEquals(0, new BigDecimal("1.00").compareTo(response.penaltyPoints()));
        assertEquals(0, new BigDecimal("0.0550").compareTo(response.penalizedRate()));
        assertEquals(0, new BigDecimal("498.63").compareTo(response.penaltyAmount()));
        assertEquals(0, new BigDecimal("102742.47").compareTo(response.netPayableAmount()));

        ArgumentCaptor<FdPrematureClosure> captor = ArgumentCaptor.forClass(FdPrematureClosure.class);
        verify(closureRepository).save(captor.capture());
        FdPrematureClosure saved = captor.getValue();
        assertEquals(11L, saved.getAccountId());
        assertEquals("PENDING", saved.getStatus());
        assertEquals(0, new BigDecimal("1.00").compareTo(saved.getPenaltyPctApplied()));
        assertEquals(0, new BigDecimal("498.63").compareTo(saved.getPenaltyAmount()));
        assertEquals(0, new BigDecimal("102742.47").compareTo(saved.getNetPayableAmount()));
        assertEquals(77L, saved.getAppliedRuleOutcomeId());
        assertEquals("need funds early", saved.getReason());
        assertNotNull(saved.getUuid());
        assertEquals(FdStatus.ACTIVE, account.getStatus());
    }

    @Test
    void requestWithoutUsableRuleWritesNothing() {
        when(ruleOutcomeRepository.findById(77L)).thenReturn(Optional.empty());

        PrematureClosureNotSupportedException failure = assertThrows(
                PrematureClosureNotSupportedException.class,
                () -> service.requestPrematureClosure(11L, "alice", null));
        assertTrue(failure.getMessage().contains("CLOSURE_PENALTY"));
        assertEquals(FdStatus.ACTIVE, account.getStatus());
        verify(closureRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verify(accrualRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void requestWithUnparseableRuleValueWritesNothing() {
        FdRuleOutcomeReference badOutcome = mock(FdRuleOutcomeReference.class);
        when(badOutcome.getOutcomeType()).thenReturn("CLOSURE_PENALTY");
        when(badOutcome.getOutcomeValue()).thenReturn("waived");
        when(ruleOutcomeRepository.findById(77L)).thenReturn(Optional.of(badOutcome));

        assertThrows(PrematureClosureNotSupportedException.class,
                () -> service.requestPrematureClosure(11L, "alice", null));
        verify(closureRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void requestWithSecondProductPenaltyRowStillResolvesOwnProduct() {
        // A second product's CLOSURE_PENALTY row must not break this account:
        // resolution follows the booked outcome reference, never a global scan.
        FdRuleOutcomeReference otherProductPenalty = mock(FdRuleOutcomeReference.class);
        when(otherProductPenalty.getId()).thenReturn(99L);
        when(otherProductPenalty.getOutcomeType()).thenReturn("CLOSURE_PENALTY");
        when(otherProductPenalty.getOutcomeValue()).thenReturn("2.00");
        when(ruleOutcomeRepository.findById(99L)).thenReturn(Optional.of(otherProductPenalty));

        PrematureClosureResponse response = service.requestPrematureClosure(11L, "alice", null);

        assertEquals(0, new BigDecimal("1.00").compareTo(response.penaltyPoints()));
        assertEquals(0, new BigDecimal("0.0550").compareTo(response.penalizedRate()));
        assertEquals(0, new BigDecimal("498.63").compareTo(response.penaltyAmount()));
        assertEquals(0, new BigDecimal("102742.47").compareTo(response.netPayableAmount()));
        ArgumentCaptor<FdPrematureClosure> captor = ArgumentCaptor.forClass(FdPrematureClosure.class);
        verify(closureRepository).save(captor.capture());
        assertEquals(77L, captor.getValue().getAppliedRuleOutcomeId());
    }

    @Test
    void requestWithNonPenaltyBookedOutcomeWritesNothing() {
        FdRuleOutcomeReference rateOutcome = mock(FdRuleOutcomeReference.class);
        when(rateOutcome.getOutcomeType()).thenReturn("INTEREST_RATE");
        when(rateOutcome.getOutcomeValue()).thenReturn("0.0650");
        when(ruleOutcomeRepository.findById(77L)).thenReturn(Optional.of(rateOutcome));

        assertThrows(PrematureClosureNotSupportedException.class,
                () -> service.requestPrematureClosure(11L, "alice", null));
        assertEquals(FdStatus.ACTIVE, account.getStatus());
        verify(closureRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
        verify(accrualRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void requestOnNonActiveAccountWritesNothing() {
        account.setStatus(FdStatus.MATURED);

        assertThrows(InvalidFdTransitionException.class,
                () -> service.requestPrematureClosure(11L, "alice", null));
        assertEquals(FdStatus.MATURED, account.getStatus());
        verify(closureRepository, never()).save(any());
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void approveAppliesStoredSnapshotAndTransitions() {
        FdPrematureClosure pending = pendingClosure();
        when(closureRepository.findById(21L)).thenReturn(Optional.of(pending));
        when(accrualRepository.findByAccountIdAndAccrualDate(eq(11L),
                eq(LocalDate.of(2026, 1, 1).atStartOfDay().atOffset(ZoneOffset.UTC))))
                .thenReturn(Optional.empty());

        PrematureClosureResponse response = service.approvePrematureClosure(11L, 21L, "alice");

        assertEquals("APPROVED", response.status());
        assertEquals(21L, response.closureId());
        assertEquals(41L, response.approvedBy());
        assertEquals(0, new BigDecimal("498.63").compareTo(response.penaltyAmount()));
        assertEquals(0, new BigDecimal("102742.47").compareTo(response.netPayableAmount()));
        assertEquals(0, new BigDecimal("0.0550").compareTo(response.penalizedRate()));

        ArgumentCaptor<FdTransaction> transactions = ArgumentCaptor.forClass(FdTransaction.class);
        verify(transactionRepository, times(2)).save(transactions.capture());
        FdTransaction penalty = transactions.getAllValues().stream()
                .filter(tx -> tx.getTransactionTypeId().equals(13L)).findFirst().orElseThrow();
        FdTransaction settlement = transactions.getAllValues().stream()
                .filter(tx -> tx.getTransactionTypeId().equals(12L)).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("498.63").compareTo(penalty.getAmount()));
        assertNull(penalty.getBalanceAfter());
        assertTrue(penalty.getRemarks().toLowerCase().contains("penalty"));
        assertEquals(0, new BigDecimal("102742.47").compareTo(settlement.getAmount()));
        assertEquals(0, new BigDecimal("102742.47").compareTo(settlement.getBalanceAfter()));
        assertTrue(settlement.getRemarks().toLowerCase().contains("internal"));

        ArgumentCaptor<FdInterestAccrual> accruals = ArgumentCaptor.forClass(FdInterestAccrual.class);
        verify(accrualRepository).save(accruals.capture());
        assertEquals(0, new BigDecimal("2742.4700").compareTo(accruals.getValue().getAccruedAmount()));

        assertEquals("APPROVED", pending.getStatus());
        assertEquals(41L, pending.getApprovedBy());
        verify(closureRepository).save(pending);
        assertEquals(FdStatus.PREMATURELY_CLOSED, account.getStatus());
        verify(accountRepository).save(account);
    }

    @Test
    void doubleApproveThrowsBeforeWrites() {
        FdPrematureClosure approved = pendingClosure();
        approved.setStatus("APPROVED");
        when(closureRepository.findById(21L)).thenReturn(Optional.of(approved));

        assertThrows(InvalidFdTransitionException.class,
                () -> service.approvePrematureClosure(11L, 21L, "alice"));
        assertEquals(FdStatus.ACTIVE, account.getStatus());
        verify(transactionRepository, never()).save(any());
        verify(accrualRepository, never()).save(any());
        verify(closureRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void approveForDifferentAccountThrowsBeforeWrites() {
        FdPrematureClosure other = pendingClosure();
        other.setAccountId(12L);
        when(closureRepository.findById(21L)).thenReturn(Optional.of(other));

        assertThrows(InvalidFdTransitionException.class,
                () -> service.approvePrematureClosure(11L, 21L, "alice"));
        verify(transactionRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void approveMissingRequestThrowsNotFound() {
        when(closureRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> service.approvePrematureClosure(11L, 99L, "alice"));
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void approveWithNullSnapshotThrowsBadRequestBeforeWrites() {
        FdPrematureClosure corrupt = pendingClosure();
        corrupt.setPenaltyAmount(null);
        corrupt.setNetPayableAmount(null);
        corrupt.setPenaltyPctApplied(null);
        when(closureRepository.findById(21L)).thenReturn(Optional.of(corrupt));

        assertThrows(InvalidInputException.class,
                () -> service.approvePrematureClosure(11L, 21L, "alice"));
        assertEquals(FdStatus.ACTIVE, account.getStatus());
        assertEquals("PENDING", corrupt.getStatus());
        verify(transactionRepository, never()).save(any());
        verify(accrualRepository, never()).save(any());
        verify(closureRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
    }

    @Test
    void approveWithNullPrincipalThrowsBadRequestBeforeWrites() {
        account.setPrincipalAmount(null);
        when(closureRepository.findById(21L)).thenReturn(Optional.of(pendingClosure()));

        assertThrows(InvalidInputException.class,
                () -> service.approvePrematureClosure(11L, 21L, "alice"));
        assertEquals(FdStatus.ACTIVE, account.getStatus());
        verify(transactionRepository, never()).save(any());
        verify(accrualRepository, never()).save(any());
        verify(closureRepository, never()).save(any());
        verify(accountRepository, never()).save(any());
    }

    private static FdPrematureClosure pendingClosure() {
        FdPrematureClosure closure = new FdPrematureClosure();
        closure.setId(21L);
        closure.setAccountId(11L);
        closure.setClosureDate(OffsetDateTime.parse("2026-01-01T10:00:00Z"));
        closure.setPenaltyPctApplied(new BigDecimal("1.00"));
        closure.setPenaltyAmount(new BigDecimal("498.63"));
        closure.setNetPayableAmount(new BigDecimal("102742.47"));
        closure.setCurrencyId(3L);
        closure.setReason("need funds early");
        closure.setAppliedRuleOutcomeId(77L);
        closure.setStatus("PENDING");
        closure.setUuid("closure-uuid-21");
        return closure;
    }
}
