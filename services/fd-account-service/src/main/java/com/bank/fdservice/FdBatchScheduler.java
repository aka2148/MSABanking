package com.bank.fdservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@EnableScheduling
@ConditionalOnProperty(name = "app.fd.batch.schedule.enabled", havingValue = "true")
public class FdBatchScheduler {
    private static final Logger log = LoggerFactory.getLogger(FdBatchScheduler.class);
    private final DemoClockService clock;
    private final FdBatchService batches;

    public FdBatchScheduler(DemoClockService clock, FdBatchService batches) {
        this.clock = clock;
        this.batches = batches;
    }

    @Scheduled(cron = "${app.fd.batch.schedule.cron:0 0 0 * * *}", zone = "UTC")
    public void runDaily() {
        FdBatchService.DailyBatchResult result = batches.runDaily(clock.businessDate());
        if (result.accountsFailed() > 0) {
            log.error("Scheduled FD batch completed with {} failed accounts for {}: {}",
                    result.accountsFailed(), result.businessDate(), result.failedAccountIds());
        } else {
            log.info("Scheduled FD batch completed for {}: {} accounts examined, {} accrual accounts, {} maturities",
                    result.businessDate(), result.accountsExamined(), result.accrualsProcessed(), result.maturitiesProcessed());
        }
    }
}
