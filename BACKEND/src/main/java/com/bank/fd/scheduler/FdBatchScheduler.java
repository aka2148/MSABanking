package com.bank.fd.scheduler;

import com.bank.fd.application.FdBatchProcessingService;
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

    // Run at midnight every day
    @Scheduled(cron = "0 0 0 * * ?", zone = "UTC")
    public void accrueDailyInterest() {
        logger.info("Starting scheduled task: Daily Interest Accrual...");
        try {
            batchService.runAccrual(null);
            logger.info("Daily Interest Accrual completed successfully.");
        } catch (Exception e) {
            logger.error("Error during scheduled Daily Interest Accrual", e);
        }
    }

    // Run at 1 AM every day
    @Scheduled(cron = "0 0 1 * * ?", zone = "UTC")
    public void processMaturedAccounts() {
        logger.info("Starting scheduled task: Maturity Handling...");
        try {
            batchService.runMaturity(null);
            logger.info("Maturity Handling completed successfully.");
        } catch (Exception e) {
            logger.error("Error during scheduled Maturity Handling", e);
        }
    }
}
