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
    private final SavingsInterestService savingsInterest;

    public FdBatchScheduler(DemoClockService clock, FdBatchService batches,SavingsInterestService savingsInterest) {
        this.clock = clock;
        this.batches = batches;
        this.savingsInterest=savingsInterest;
    }

    @Scheduled(cron = "${app.fd.batch.schedule.interest-cron:0 0 0 * * *}",
            zone = "${app.fd.batch.schedule.zone:Asia/Kolkata}")
    public void runInterestAccrual() {
        FdBatchService.BatchResult result = batches.accrue(clock.businessDate());
        if (result.accountsFailed() > 0) {
            log.error("Scheduled FD interest batch completed with {} failed accounts for {}: {}",
                    result.accountsFailed(), result.businessDate(), result.failedAccountIds());
        } else {
            log.info("Scheduled FD interest batch completed for {}: {} accounts and {} accrual rows",
                    result.businessDate(), result.accountsProcessed(), result.accrualRowsCreated());
        }
    }

    @Scheduled(cron = "${app.fd.batch.schedule.maturity-cron:0 0 1 * * *}",
            zone = "${app.fd.batch.schedule.zone:Asia/Kolkata}")
    public void runMaturityBatch() {
        FdBatchService.DailyBatchResult result = batches.runDaily(clock.businessDate());
        if (result.accountsFailed() > 0) {
            log.error("Scheduled FD maturity batch completed with {} failed accounts for {}: {}",
                    result.accountsFailed(), result.businessDate(), result.failedAccountIds());
        } else {
            log.info("Scheduled FD maturity batch completed for {}: {} accounts examined, {} maturities",
                    result.businessDate(), result.accountsExamined(), result.maturitiesProcessed());
        }
    }

    @Scheduled(cron="${app.savings.interest.schedule.cron:0 10 0 * * *}",zone="${app.fd.batch.schedule.zone:UTC}")
    public void runSavingsInterest(){SavingsInterestService.BatchResult result=savingsInterest.run(clock.businessDate());if(result.accountsFailed()>0)log.error("Scheduled savings interest batch completed with {} failed accounts for {}",result.accountsFailed(),result.businessDate());else log.info("Scheduled savings interest batch completed for {}: {} accounts, {} accrual rows, {} credits",result.businessDate(),result.accountsProcessed(),result.accrualRowsCreated(),result.creditsPosted());}
}
