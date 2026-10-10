package com.bank.fd.application;

import com.bank.fd.entity.FdAccount;
import com.bank.fd.entity.FdStatus;
import com.bank.fd.repository.FdAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Sequential, per-account scheduled/manual batch runner; each account transaction is independently repeat-safe. */
@Service
public class FdBatchProcessingService {
    private static final Logger logger = LoggerFactory.getLogger(FdBatchProcessingService.class);
    private final FdAccountRepository accounts;
    private final AccrualService accrualService;
    private final MaturityService maturityService;
    private final com.bank.fd.domain.FdTimePolicy timePolicy;

    public FdBatchProcessingService(FdAccountRepository accounts, AccrualService accrualService,
                                    MaturityService maturityService,
                                    com.bank.fd.domain.FdTimePolicy timePolicy) {
        this.accounts = accounts;
        this.accrualService = accrualService;
        this.maturityService = maturityService;
        this.timePolicy = timePolicy;
    }

    public FdBatchRunResponse runDaily(LocalDate requestedDate) {
        LocalDate date = requestedDate == null ? timePolicy.businessDate() : requestedDate;
        List<FdAccount> active = accounts.findByStatus(FdStatus.ACTIVE);
        List<Long> failed = new ArrayList<>();
        int accruals = 0;
        int maturities = 0;
        for (FdAccount account : active) {
            try {
                accrualService.accrueForBatch(account.getId(), date);
                accruals++;
            } catch (RuntimeException exception) {
                logger.error("FD daily accrual failed for account id {} on {}", account.getId(), date, exception);
                failed.add(account.getId());
                continue;
            }
            if (account.getMaturityDate() != null && !date.isBefore(account.getMaturityDate())) {
                try {
                    if (maturityService.matureForBatch(account.getId(), date)) maturities++;
                } catch (RuntimeException exception) {
                    logger.error("FD maturity failed for account id {} on {}", account.getId(), date, exception);
                    failed.add(account.getId());
                }
            }
        }
        return new FdBatchRunResponse(date, active.size(), accruals, maturities, failed.size(), List.copyOf(failed));
    }

    public FdBatchRunResponse runAccrual(LocalDate requestedDate) {
        LocalDate date = requestedDate == null ? timePolicy.businessDate() : requestedDate;
        List<FdAccount> active = accounts.findByStatus(FdStatus.ACTIVE);
        List<Long> failed = new ArrayList<>();
        int processed = 0;
        for (FdAccount account : active) {
            try {
                accrualService.accrueForBatch(account.getId(), date);
                processed++;
            } catch (RuntimeException exception) {
                logger.error("FD daily accrual failed for account id {} on {}", account.getId(), date, exception);
                failed.add(account.getId());
            }
        }
        return new FdBatchRunResponse(date, active.size(), processed, 0, failed.size(), List.copyOf(failed));
    }

    public FdBatchRunResponse runMaturity(LocalDate requestedDate) {
        LocalDate date = requestedDate == null ? timePolicy.businessDate() : requestedDate;
        List<FdAccount> active = accounts.findByStatus(FdStatus.ACTIVE);
        List<Long> failed = new ArrayList<>();
        int processed = 0;
        int due = 0;
        for (FdAccount account : active) {
            if (account.getMaturityDate() == null || date.isBefore(account.getMaturityDate())) continue;
            due++;
            try {
                if (maturityService.matureForBatch(account.getId(), date)) processed++;
            } catch (RuntimeException exception) {
                logger.error("FD maturity failed for account id {} on {}", account.getId(), date, exception);
                failed.add(account.getId());
            }
        }
        return new FdBatchRunResponse(date, due, 0, processed, failed.size(), List.copyOf(failed));
    }
}
