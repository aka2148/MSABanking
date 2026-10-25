package com.bank.fdservice;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Coordinates batches while each account's writes are committed or rolled back independently. */
@Service
public class FdBatchService {
    private static final Logger log = LoggerFactory.getLogger(FdBatchService.class);

    private final JdbcTemplate jdbc;
    private final DemoClockService clock;
    private final FdBatchAccountProcessor accountProcessor;

    public FdBatchService(JdbcTemplate jdbc, DemoClockService clock, FdBatchAccountProcessor accountProcessor) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.accountProcessor = accountProcessor;
    }

    public DailyBatchResult runDaily(LocalDate requestedDate) {
        LocalDate date = businessDate(requestedDate);
        List<Long> ids = jdbc.queryForList("SELECT fa_account_id FROM fd_accounts " +
                "WHERE fa_status IN ('ACTIVE','MATURED','CLOSED','PREMATURELY_CLOSED') ORDER BY fa_account_id", Long.class);
        List<Long> failed = new ArrayList<>();
        int accrualAccounts = 0;
        int maturities = 0;

        for (Long id : ids) {
            try {
                FdBatchAccountProcessor.DailyAccountResult result = accountProcessor.processDaily(id, date);
                if (result.accrualRowsCreated() > 0) accrualAccounts++;
                if (result.matured()) maturities++;
            } catch (RuntimeException e) {
                failed.add(id);
                log.error("FD daily batch failed for accountId={} businessDate={}; that account was rolled back", id, date, e);
            }
        }
        return new DailyBatchResult(date, ids.size(), accrualAccounts, maturities, failed.size(), List.copyOf(failed));
    }

    public BatchResult accrue(LocalDate requestedDate) {
        LocalDate date = businessDate(requestedDate);
        List<Long> ids = jdbc.queryForList("SELECT fa_account_id FROM fd_accounts WHERE fa_status='ACTIVE' ORDER BY fa_account_id", Long.class);
        List<Long> failed = new ArrayList<>();
        int accounts = 0;
        int rows = 0;
        for (Long id : ids) {
            try {
                int added = accountProcessor.accrueAccount(id, date);
                if (added > 0) {
                    accounts++;
                    rows += added;
                }
            } catch (RuntimeException e) {
                failed.add(id);
                log.error("FD accrual batch failed for accountId={} businessDate={}; that account was rolled back", id, date, e);
            }
        }
        return new BatchResult(date, accounts, rows, failed.size(), List.copyOf(failed));
    }

    public BatchResult mature(LocalDate requestedDate) {
        LocalDate date = businessDate(requestedDate);
        List<Long> ids = jdbc.queryForList("SELECT fa_account_id FROM fd_accounts WHERE fa_status='ACTIVE' AND fa_maturity_date<=? ORDER BY fa_account_id", Long.class, date);
        List<Long> failed = new ArrayList<>();
        int count = 0;
        for (Long id : ids) {
            try {
                if (accountProcessor.matureAccount(id, date)) count++;
            } catch (RuntimeException e) {
                failed.add(id);
                log.error("FD maturity batch failed for accountId={} businessDate={}; that account was rolled back", id, date, e);
            }
        }
        return new BatchResult(date, count, 0, failed.size(), List.copyOf(failed));
    }

    private LocalDate businessDate(LocalDate requestedDate) {
        LocalDate date = requestedDate == null ? clock.businessDate() : requestedDate;
        if (date.isAfter(clock.businessDate()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Business date cannot be later than the current business date");
        return date;
    }

    public record BatchResult(LocalDate businessDate, int accountsProcessed, int accrualRowsCreated,
                              int accountsFailed, List<Long> failedAccountIds) {}

    public record DailyBatchResult(@com.fasterxml.jackson.annotation.JsonProperty("business_date") LocalDate businessDate,
                                   @com.fasterxml.jackson.annotation.JsonProperty("accounts_examined") int accountsExamined,
                                   @com.fasterxml.jackson.annotation.JsonProperty("accruals_processed") int accrualsProcessed,
                                   @com.fasterxml.jackson.annotation.JsonProperty("maturities_processed") int maturitiesProcessed,
                                   @com.fasterxml.jackson.annotation.JsonProperty("accounts_failed") int accountsFailed,
                                   @com.fasterxml.jackson.annotation.JsonProperty("failed_account_ids") List<Long> failedAccountIds) {}
}
