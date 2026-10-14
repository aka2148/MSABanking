package com.bank.fd.scheduler;

import com.bank.fd.application.FdBatchProcessingService;
import com.bank.fd.application.FdBatchRunResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FdBatchScheduler {

    private static final Logger logger = LoggerFactory.getLogger(FdBatchScheduler.class);

    private final FdBatchProcessingService batchService;

    public FdBatchScheduler(FdBatchProcessingService batchService) {
        this.batchService = batchService;
    }

    // Run at midnight in the FD business zone (IST by default).
    @Scheduled(cron = "0 0 0 * * ?", zone = "${fd.policy.business-zone:Asia/Kolkata}")
    public void accrueDailyInterest() {
        logger.info("Starting scheduled task: Daily Interest Accrual...");
        try {
            logResult("Daily FD interest accrual", batchService.runAccrual(null));
        } catch (Exception e) {
            logger.error("Error during scheduled Daily Interest Accrual", e);
        }
    }

    // Run at 1 AM in the same FD business zone, after daily accrual.
    @Scheduled(cron = "0 0 1 * * ?", zone = "${fd.policy.business-zone:Asia/Kolkata}")
    public void processMaturedAccounts() {
        logger.info("Starting scheduled task: Maturity Handling...");
        try {
            logResult("FD maturity handling", batchService.runMaturity(null));
        } catch (Exception e) {
            logger.error("Error during scheduled Maturity Handling", e);
        }
    }

    private void logResult(String job, FdBatchRunResponse result) {
        String message = "{} finished: business_date={}, accounts_examined={}, accruals_processed={}, "
                + "maturities_processed={}, accounts_failed={}, failed_account_ids={}";
        if (result.accountsFailed() > 0) {
            logger.warn(message, job, result.businessDate(), result.accountsExamined(), result.accrualsProcessed(),
                    result.maturitiesProcessed(), result.accountsFailed(), result.failedAccountIds());
        } else {
            logger.info(message, job, result.businessDate(), result.accountsExamined(), result.accrualsProcessed(),
                    result.maturitiesProcessed(), result.accountsFailed(), result.failedAccountIds());
        }
    }
}
