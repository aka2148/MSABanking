package com.bank.fd.scheduler;

import com.bank.fd.application.FdBatchProcessingService;
import com.bank.fd.application.FdBatchRunResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.SimpleTriggerContext;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class FdBatchSchedulerTest {

    private final FdBatchProcessingService batchService = mock(FdBatchProcessingService.class);
    private final FdBatchScheduler scheduler = new FdBatchScheduler(batchService);

    @Test
    void scheduledAccrualLogsPartialFailures(CapturedOutput output) {
        when(batchService.runAccrual(null)).thenReturn(new FdBatchRunResponse(
                LocalDate.of(2026, 4, 1), 2, 1, 0, 1, List.of(17L)));

        scheduler.accrueDailyInterest();

        verify(batchService).runAccrual(null);
        assertTrue(output.getOut().contains("Daily FD interest accrual finished"));
        assertTrue(output.getOut().contains("accounts_failed=1"));
        assertTrue(output.getOut().contains("failed_account_ids=[17]"));
    }

    @Test
    void scheduledMaturityUsesTheBatchRunner(CapturedOutput output) {
        when(batchService.runMaturity(null)).thenReturn(new FdBatchRunResponse(
                LocalDate.of(2026, 4, 1), 1, 0, 1, 0, List.of()));

        scheduler.processMaturedAccounts();

        verify(batchService).runMaturity(null);
        assertTrue(output.getOut().contains("FD maturity handling finished"));
        assertTrue(output.getOut().contains("maturities_processed=1"));
    }

    @Test
    void defaultSchedulesAccrueAtMidnightIstAndMatureOneHourLater() throws Exception {
        var environment = new StandardEnvironment();
        var context = new SimpleTriggerContext(Clock.fixed(
                Instant.parse("2026-01-14T18:29:59Z"), ZoneOffset.UTC));
        Scheduled accrual = FdBatchScheduler.class.getMethod("accrueDailyInterest").getAnnotation(Scheduled.class);
        Scheduled maturity = FdBatchScheduler.class.getMethod("processMaturedAccounts").getAnnotation(Scheduled.class);
        ZoneId accrualZone = ZoneId.of(environment.resolveRequiredPlaceholders(accrual.zone()));
        ZoneId maturityZone = ZoneId.of(environment.resolveRequiredPlaceholders(maturity.zone()));

        assertEquals(ZoneId.of("Asia/Kolkata"), accrualZone);
        assertEquals(accrualZone, maturityZone);
        assertEquals(Instant.parse("2026-01-14T18:30:00Z"),
                new CronTrigger(accrual.cron(), accrualZone).nextExecution(context));
        assertEquals(Instant.parse("2026-01-14T19:30:00Z"),
                new CronTrigger(maturity.cron(), maturityZone).nextExecution(context));
    }
}
