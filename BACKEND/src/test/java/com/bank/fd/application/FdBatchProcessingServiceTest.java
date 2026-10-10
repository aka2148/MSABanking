package com.bank.fd.application;

import com.bank.fd.domain.FdTimePolicy;
import com.bank.fd.entity.FdAccount;
import com.bank.fd.entity.FdStatus;
import com.bank.fd.repository.FdAccountRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FdBatchProcessingServiceTest {
    @Test
    void dailyBatchContinuesAfterAnAccountFailureAndMaturesDueAccountsOnce() {
        FdAccountRepository accounts = mock(FdAccountRepository.class);
        AccrualService accruals = mock(AccrualService.class);
        MaturityService maturities = mock(MaturityService.class);
        FdTimePolicy time = new FdTimePolicy(Clock.fixed(Instant.parse("2026-04-01T02:00:00Z"),
                ZoneId.of("UTC")), ZoneId.of("UTC"));
        FdBatchProcessingService service = new FdBatchProcessingService(accounts, accruals, maturities, time);
        FdAccount failed = account(1L, LocalDate.of(2026, 3, 1));
        FdAccount due = account(2L, LocalDate.of(2026, 4, 1));
        FdAccount notDue = account(3L, LocalDate.of(2026, 8, 1));
        when(accounts.findByStatus(FdStatus.ACTIVE)).thenReturn(List.of(failed, due, notDue));
        when(accruals.accrueForBatch(1L, LocalDate.of(2026, 4, 1))).thenThrow(new IllegalStateException("test failure"));
        when(maturities.matureForBatch(2L, LocalDate.of(2026, 4, 1))).thenReturn(true);
        when(maturities.matureForBatch(3L, LocalDate.of(2026, 4, 1))).thenReturn(false);

        FdBatchRunResponse result = service.runDaily(null);

        assertEquals(3, result.accountsExamined());
        assertEquals(2, result.accrualsProcessed());
        assertEquals(1, result.maturitiesProcessed());
        assertEquals(List.of(1L), result.failedAccountIds());
    }

    private static FdAccount account(Long id, LocalDate maturity) {
        FdAccount account = new FdAccount();
        account.setId(id);
        account.setStatus(FdStatus.ACTIVE);
        account.setMaturityDate(maturity);
        return account;
    }
}
