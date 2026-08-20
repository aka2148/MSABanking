package com.bank.fd.scheduler;

import com.bank.fd.service.FdAccountService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FdBatchScheduler {

    private static final Logger logger = LoggerFactory.getLogger(FdBatchScheduler.class);

    @Autowired
    private FdAccountService fdAccountService;

    // Run at midnight every day
    @Scheduled(cron = "0 0 0 * * ?")
    public void accrueDailyInterest() {
        logger.info("Starting scheduled task: Daily Interest Accrual...");
        try {
            fdAccountService.accrueDailyInterest();
            logger.info("Daily Interest Accrual completed successfully.");
        } catch (Exception e) {
            logger.error("Error during scheduled Daily Interest Accrual", e);
        }
    }

    // Run at 1 AM every day
    @Scheduled(cron = "0 0 1 * * ?")
    public void processMaturedAccounts() {
        logger.info("Starting scheduled task: Maturity Handling...");
        try {
            fdAccountService.processMaturedAccounts();
            logger.info("Maturity Handling completed successfully.");
        } catch (Exception e) {
            logger.error("Error during scheduled Maturity Handling", e);
        }
    }
}
