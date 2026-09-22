package com.bank.fd.application;

import com.bank.common.exception.InvalidInputException;
import com.bank.common.exception.ResourceNotFoundException;
import com.bank.fd.api.dto.AccrualResponse;
import com.bank.fd.domain.FdInterestCalculator;
import com.bank.fd.domain.FdTimePolicy;
import com.bank.fd.entity.FdAccount;
import com.bank.fd.entity.FdInterestAccrual;
import com.bank.fd.entity.FdStatus;
import com.bank.fd.repository.FdAccountRepository;
import com.bank.fd.repository.FdCurrencyReferenceRepository;
import com.bank.fd.repository.FdInterestAccrualRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Runs Academic-v1 interest accrual for ACTIVE FD accounts with a per-date repeat guard. */
@Service
public class AccrualService {

    private final FdAccountRepository accountRepository;
    private final FdInterestAccrualRepository accrualRepository;
    private final FdCurrencyReferenceRepository currencyRepository;
    private final FdActorResolver actorResolver;
    private final FdTimePolicy timePolicy;

    @Autowired
    public AccrualService(FdAccountRepository accountRepository,
                          FdInterestAccrualRepository accrualRepository,
                          FdCurrencyReferenceRepository currencyRepository,
                          FdActorResolver actorResolver,
                          FdTimePolicy timePolicy) {
        this.accountRepository = accountRepository;
        this.accrualRepository = accrualRepository;
        this.currencyRepository = currencyRepository;
        this.actorResolver = actorResolver;
        this.timePolicy = timePolicy;
    }

    @Transactional
    public AccrualResponse runAccrual(Long accountId, String username, LocalDate businessDate) {
        LocalDate effectiveBusinessDate = businessDate == null ? timePolicy.businessDate() : businessDate;
        FdAccount account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("FD account not found"));
        var holder = actorResolver.resolve(username);
        if (!holder.getId().equals(account.getPrimaryHolderId())) {
            throw new AccessDeniedException("Authenticated customer does not own this FD account");
        }
        if (account.getStatus() != FdStatus.ACTIVE) {
            throw new InvalidInputException("Only ACTIVE FD accounts can accrue interest");
        }

        LocalDate endDate = effectiveBusinessDate;
        if (account.getMaturityDate() != null && endDate.isAfter(account.getMaturityDate())) {
            endDate = account.getMaturityDate();
        }

        BigDecimal interest = FdInterestCalculator.interestForPeriod(
                account.getPrincipalAmount(), account.getInterestRate(),
                account.getBookingDate(), endDate)
                .setScale(4, RoundingMode.HALF_UP);
        OffsetDateTime accrualDate = endDate.atStartOfDay().atOffset(ZoneOffset.UTC);

        var existing = accrualRepository.findByAccountIdAndAccrualDate(accountId, accrualDate);
        String currencyCode = resolveCurrencyCode(account);
        if (existing.isPresent()) {
            FdInterestAccrual row = existing.get();
            BigDecimal cumulative = row.getCumulativeAccrued() == null ? row.getAccruedAmount() : row.getCumulativeAccrued();
            return AccrualResponse.accrued(account, row, cumulative, currencyCode);
        }

        FdInterestAccrual row = new FdInterestAccrual();
        row.setAccountId(account.getId());
        row.setAccrualDate(accrualDate);
        row.setAccruedAmount(interest);
        row.setCumulativeAccrued(interest);
        row.setCurrencyId(account.getCurrencyId());
        row.setUuid(UUID.randomUUID().toString());
        FdInterestAccrual saved = accrualRepository.save(row);
        return AccrualResponse.accrued(account, saved, saved.getCumulativeAccrued(), currencyCode);
    }

    @Transactional(readOnly = true)
    public List<AccrualResponse> listAccruals(Long accountId, String username) {
        FdAccount account = loadOwnedAccount(accountId, username);
        String currencyCode = resolveCurrencyCode(account);
        return accrualRepository.findByAccountIdOrderByAccrualDateAscIdAsc(accountId).stream()
                .map(row -> {
                    BigDecimal cumulative = row.getCumulativeAccrued() == null
                            ? row.getAccruedAmount() : row.getCumulativeAccrued();
                    return AccrualResponse.accrued(account, row, cumulative, currencyCode);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public AccrualResponse cumulative(Long accountId, String username) {
        FdAccount account = loadOwnedAccount(accountId, username);
        String currencyCode = resolveCurrencyCode(account);
        List<FdInterestAccrual> rows = accrualRepository.findByAccountIdOrderByAccrualDateAscIdAsc(accountId);
        if (rows.isEmpty()) {
            return new AccrualResponse(account.getId(), account.getAccountNumber(), timePolicy.businessDate(),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
                    currencyCode, account.getMaturityAmount(), account.getPolicyVersion(),
                    account.getStatus().name());
        }
        BigDecimal total = rows.stream()
                .map(FdInterestAccrual::getAccruedAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        FdInterestAccrual latest = rows.get(rows.size() - 1);
        return AccrualResponse.accrued(account, latest, total, currencyCode);
    }

    private FdAccount loadOwnedAccount(Long accountId, String username) {
        FdAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("FD account not found"));
        var holder = actorResolver.resolve(username);
        if (!holder.getId().equals(account.getPrimaryHolderId())) {
            throw new AccessDeniedException("Authenticated customer does not own this FD account");
        }
        return account;
    }

    private String resolveCurrencyCode(FdAccount account) {
        return currencyRepository.findById(account.getCurrencyId())
                .orElseThrow(() -> new ResourceNotFoundException("FD account currency not found"))
                .getIsoCode();
    }
}
