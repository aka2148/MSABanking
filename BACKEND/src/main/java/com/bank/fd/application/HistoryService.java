package com.bank.fd.application;

import com.bank.common.exception.ResourceNotFoundException;
import com.bank.fd.api.dto.HistoryEntry;
import com.bank.fd.entity.FdAccount;
import com.bank.fd.entity.FdInterestAccrual;
import com.bank.fd.entity.FdTransaction;
import com.bank.fd.repository.FdAccountRepository;
import com.bank.fd.repository.FdCurrencyReferenceRepository;
import com.bank.fd.repository.FdInterestAccrualRepository;
import com.bank.fd.repository.FdTransactionRepository;
import com.bank.fd.repository.TransactionTypeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Unified full-detail timeline (D-05/D-06) merging {@code fd_transactions}
 * rows with {@code fd_interest_accrual} rows, time-ordered with amounts,
 * dates, and running balances. Ownership-checked read returning DTOs only.
 */
@Service
public class HistoryService {

    private static final String SOURCE_TRANSACTION = "TRANSACTION";
    private static final String SOURCE_ACCRUAL = "ACCRUAL";
    private static final String ACCRUAL_DESCRIPTION =
            "Internal academic interest accrual record; no external payment rail was called.";

    private final FdAccountRepository accountRepository;
    private final FdTransactionRepository transactionRepository;
    private final FdInterestAccrualRepository accrualRepository;
    private final TransactionTypeRepository transactionTypeRepository;
    private final FdCurrencyReferenceRepository currencyRepository;
    private final FdActorResolver actorResolver;
    private final FdAccessPolicy accessPolicy;

    @Autowired
    public HistoryService(FdAccountRepository accountRepository,
                          FdTransactionRepository transactionRepository,
                          FdInterestAccrualRepository accrualRepository,
                          TransactionTypeRepository transactionTypeRepository,
                          FdCurrencyReferenceRepository currencyRepository,
                          FdActorResolver actorResolver,
                          FdAccessPolicy accessPolicy) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.accrualRepository = accrualRepository;
        this.transactionTypeRepository = transactionTypeRepository;
        this.currencyRepository = currencyRepository;
        this.actorResolver = actorResolver;
        this.accessPolicy = accessPolicy;
    }

    @Transactional(readOnly = true)
    public List<HistoryEntry> timeline(Long accountId, String username) {
        FdAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("FD account not found"));
        accessPolicy.requireOwned(username, account,
                SecurityContextHolder.getContext().getAuthentication());
        String currencyCode = currencyRepository.findById(account.getCurrencyId())
                .orElseThrow(() -> new ResourceNotFoundException("FD account currency not found"))
                .getIsoCode();

        Map<Long, String> typeLabels = resolveTypeLabels();
        List<DatedEntry> merged = new ArrayList<>();

        for (FdTransaction transaction
                : transactionRepository.findByAccountIdOrderByTransactionDateAscIdAsc(accountId)) {
            String entryType = typeLabels.getOrDefault(transaction.getTransactionTypeId(), SOURCE_TRANSACTION);
            merged.add(new DatedEntry(transaction.getTransactionDate(), transaction.getId(),
                    new HistoryEntry(entryType, transaction.getTransactionDate(), transaction.getAmount(),
                            currencyCode, transaction.getBalanceAfter(), SOURCE_TRANSACTION,
                            transaction.getRemarks(), account.getFundingSavingsAccountId()), 0));
        }

        for (FdInterestAccrual accrual
                : accrualRepository.findByAccountIdOrderByAccrualDateAscIdAsc(accountId)) {
            BigDecimal cumulative = accrual.getCumulativeAccrued() != null
                    ? accrual.getCumulativeAccrued() : accrual.getAccruedAmount();
            boolean compounding = AccrualService.COMPOUND_POLICY_VERSION.equals(account.getPolicyVersion());
            BigDecimal runningBalance = compounding && accrual.getCompoundingBalance() != null
                    ? accrual.getCompoundingBalance()
                    : account.getPrincipalAmount() != null
                        ? account.getPrincipalAmount().add(cumulative).setScale(2, RoundingMode.HALF_UP)
                        : cumulative.setScale(2, RoundingMode.HALF_UP);
            BigDecimal amount = accrual.getAccruedAmount() == null ? null
                    : accrual.getAccruedAmount().setScale(2, RoundingMode.HALF_UP);
            merged.add(new DatedEntry(accrual.getAccrualDate(), accrual.getId(),
                    new HistoryEntry("ACCRUAL", accrual.getAccrualDate(), amount,
                            currencyCode, runningBalance, SOURCE_ACCRUAL,
                            compounding ? "Daily interest accrued; quarterly capitalization is recorded separately."
                                    : ACCRUAL_DESCRIPTION, account.getFundingSavingsAccountId()), 0));
            if (compounding && accrual.getCapitalizedAmount() != null
                    && accrual.getCapitalizedAmount().signum() > 0) {
                merged.add(new DatedEntry(accrual.getAccrualDate(), accrual.getId(),
                        new HistoryEntry("CAPITALIZATION", accrual.getAccrualDate(), accrual.getCapitalizedAmount(),
                                currencyCode, runningBalance, SOURCE_ACCRUAL,
                                "Quarterly interest capitalized into the FD balance.",
                                account.getFundingSavingsAccountId()), 1));
            }
        }

        merged.sort(Comparator.comparing(DatedEntry::date, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(DatedEntry::id, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparingInt(DatedEntry::sequence));
        return merged.stream().map(DatedEntry::entry).toList();
    }

    /** Resolves known transaction codes to timeline labels via the reference repository. */
    private Map<Long, String> resolveTypeLabels() {
        Map<Long, String> labels = new HashMap<>();
        labelFor("DEPOSIT", "FUNDING", labels);
        labelFor("INTEREST_ACCRUAL", "ACCRUAL", labels);
        labelFor("MATURITY", "MATURITY", labels);
        labelFor("CLOSURE", "CLOSURE", labels);
        labelFor("PENALTY", "PENALTY", labels);
        return labels;
    }

    private void labelFor(String code, String entryType, Map<Long, String> labels) {
        transactionTypeRepository.findIdByCode(code).ifPresent(id -> labels.put(id, entryType));
    }

    private record DatedEntry(OffsetDateTime date, Long id, HistoryEntry entry, int sequence) {
    }
}
