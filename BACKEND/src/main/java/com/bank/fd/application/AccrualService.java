package com.bank.fd.application;

import com.bank.common.exception.InvalidInputException;
import com.bank.common.exception.ResourceNotFoundException;
import com.bank.fd.api.dto.AccrualResponse;
import com.bank.fd.domain.FdInterestCalculator;
import com.bank.fd.domain.FdCompoundCalculator;
import com.bank.fd.domain.FdTimePolicy;
import com.bank.fd.entity.FdAccount;
import com.bank.fd.entity.FdInterestAccrual;
import com.bank.fd.entity.FdOperationIdempotency;
import com.bank.fd.entity.FdStatus;
import com.bank.fd.repository.FdAccountRepository;
import com.bank.fd.repository.FdCurrencyReferenceRepository;
import com.bank.fd.repository.FdInterestAccrualRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
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
    public static final String COMPOUND_POLICY_VERSION = "academic-v2-quarterly";

    private final FdAccountRepository accountRepository;
    private final FdInterestAccrualRepository accrualRepository;
    private final FdCurrencyReferenceRepository currencyRepository;
    private final FdActorResolver actorResolver;
    private final FdTimePolicy timePolicy;
    private final FdIdempotencyGuard idempotencyGuard;
    private final FdAccessPolicy accessPolicy;

    @Autowired
    public AccrualService(FdAccountRepository accountRepository,
                          FdInterestAccrualRepository accrualRepository,
                          FdCurrencyReferenceRepository currencyRepository,
                          FdActorResolver actorResolver,
                          FdTimePolicy timePolicy,
                          FdIdempotencyGuard idempotencyGuard,
                          FdAccessPolicy accessPolicy) {
        this.accountRepository = accountRepository;
        this.accrualRepository = accrualRepository;
        this.currencyRepository = currencyRepository;
        this.actorResolver = actorResolver;
        this.timePolicy = timePolicy;
        this.idempotencyGuard = idempotencyGuard;
        this.accessPolicy = accessPolicy;
    }

    @Transactional
    public AccrualResponse runAccrual(Long accountId, String username, LocalDate businessDate) {
        return runAccrual(accountId, username, businessDate, null);
    }

    @Transactional
    public AccrualResponse runAccrual(Long accountId, String username, LocalDate businessDate, String idempotencyKey) {
        LocalDate effectiveBusinessDate = businessDate == null ? timePolicy.businessDate() : businessDate;
        FdAccount account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("FD account not found"));
        var holder = actorResolver.resolve(username);
        accessPolicy.requireOwned(username, account,
                SecurityContextHolder.getContext().getAuthentication());
        if (account.getStatus() != FdStatus.ACTIVE) {
            throw new InvalidInputException("Only ACTIVE FD accounts can accrue interest");
        }

        LocalDate endDate = effectiveBusinessDate;
        if (account.getMaturityDate() != null && endDate.isAfter(account.getMaturityDate())) {
            endDate = account.getMaturityDate();
        }

        String canonical = accountId + "|" + effectiveBusinessDate;
        FdOperationIdempotency claim = idempotencyGuard.claim("ACCRUAL", "fd-user-" + holder.getId(), idempotencyKey,
                FdIdempotencyGuard.sha256Hex(canonical));
        if (claim != null && claim.getAggregateId() != null) {
            FdAccount stored = accountRepository.findById(claim.getAggregateId())
                    .orElseThrow(() -> new ResourceNotFoundException("FD account not found"));
            LocalDate replayEnd = effectiveBusinessDate;
            if (stored.getMaturityDate() != null && replayEnd.isAfter(stored.getMaturityDate())) {
                replayEnd = stored.getMaturityDate();
            }
            OffsetDateTime replayDate = replayEnd.atStartOfDay().atOffset(ZoneOffset.UTC);
            FdInterestAccrual replayed = accrualRepository.findByAccountIdAndAccrualDate(stored.getId(), replayDate)
                    .orElseThrow(() -> new ResourceNotFoundException("FD interest accrual not found"));
            BigDecimal replayedCumulative = replayed.getCumulativeAccrued() == null
                    ? replayed.getAccruedAmount() : replayed.getCumulativeAccrued();
            return AccrualResponse.accrued(stored, replayed, replayedCumulative, resolveCurrencyCode(stored));
        }

        AccrualResponse response = isQuarterlyAccount(account)
                ? accrueQuarterly(account, endDate)
                : accrueSimpleSnapshot(account, endDate);
        idempotencyGuard.complete(claim, account.getId(), "accrual:" + account.getId() + ":" + endDate);
        return response;
    }

    /** System batch entry point: same per-account transaction and calculations, without a customer principal. */
    @Transactional
    public AccrualResponse accrueForBatch(Long accountId, LocalDate businessDate) {
        FdAccount account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("FD account not found"));
        if (account.getStatus() != FdStatus.ACTIVE) {
            throw new InvalidInputException("Only ACTIVE FD accounts can accrue interest");
        }
        LocalDate endDate = businessDate == null ? timePolicy.businessDate() : businessDate;
        if (account.getMaturityDate() != null && endDate.isAfter(account.getMaturityDate())) {
            endDate = account.getMaturityDate();
        }
        return isQuarterlyAccount(account) ? accrueQuarterly(account, endDate) : accrueSimpleSnapshot(account, endDate);
    }

    private AccrualResponse accrueSimpleSnapshot(FdAccount account, LocalDate endDate) {
        OffsetDateTime accrualDate = endDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        String currencyCode = resolveCurrencyCode(account);
        var existing = accrualRepository.findByAccountIdAndAccrualDate(account.getId(), accrualDate);
        if (existing.isPresent()) {
            FdInterestAccrual row = existing.get();
            BigDecimal cumulative = row.getCumulativeAccrued() == null ? row.getAccruedAmount() : row.getCumulativeAccrued();
            return AccrualResponse.accrued(account, row, cumulative, currencyCode);
        }
        BigDecimal interest = FdInterestCalculator.interestForPeriod(account.getPrincipalAmount(),
                account.getInterestRate(), account.getBookingDate(), endDate).setScale(8, RoundingMode.HALF_UP);
        FdInterestAccrual row = new FdInterestAccrual();
        row.setAccountId(account.getId());
        row.setAccrualDate(accrualDate);
        row.setAccruedAmount(interest);
        row.setCumulativeAccrued(interest);
        row.setCurrencyId(account.getCurrencyId());
        row.setUuid(UUID.randomUUID().toString());
        return AccrualResponse.accrued(account, accrualRepository.save(row), interest, currencyCode);
    }

    /** Accrues each missing business day and capitalizes the balance on quarterly anniversaries. */
    private AccrualResponse accrueQuarterly(FdAccount account, LocalDate endDate) {
        if (account.getBookingDate() == null || account.getInterestRate() == null || account.getPrincipalAmount() == null) {
            throw new InvalidInputException("FD account is missing its booked terms for compounding");
        }
        String currencyCode = resolveCurrencyCode(account);
        int scale = resolveCurrencyScale(account);
        OffsetDateTime targetDate = endDate.atStartOfDay().atOffset(ZoneOffset.UTC);
        var targetExisting = accrualRepository.findByAccountIdAndAccrualDate(account.getId(), targetDate);
        if (targetExisting.isPresent()) {
            FdInterestAccrual row = targetExisting.get();
            return AccrualResponse.accrued(account, row,
                    row.getCumulativeAccrued() == null ? row.getAccruedAmount() : row.getCumulativeAccrued(),
                    currencyCode);
        }

        List<FdInterestAccrual> previousRows = accrualRepository
                .findByAccountIdOrderByAccrualDateAscIdAsc(account.getId());
        LocalDate lastDate = account.getBookingDate();
        BigDecimal balance = account.getPrincipalAmount();
        BigDecimal cumulative = BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        BigDecimal pendingQuarterInterest = BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        if (!previousRows.isEmpty()) {
            FdInterestAccrual last = previousRows.get(previousRows.size() - 1);
            lastDate = last.getAccrualDate().toLocalDate();
            if (lastDate.isAfter(endDate)) {
                throw new InvalidInputException("Accrual dates must be processed in chronological order");
            }
            balance = last.getCompoundingBalance() == null ? account.getPrincipalAmount() : last.getCompoundingBalance();
            cumulative = last.getCumulativeAccrued() == null ? sumAccrued(previousRows) : last.getCumulativeAccrued();
            boolean capitalized = false;
            for (int i = previousRows.size() - 1; i >= 0; i--) {
                FdInterestAccrual row = previousRows.get(i);
                if (row.getCapitalizedAmount() != null && row.getCapitalizedAmount().signum() > 0) {
                    capitalized = true;
                    break;
                }
                if (row.getAccruedAmount() != null) pendingQuarterInterest = pendingQuarterInterest.add(row.getAccruedAmount());
            }
            if (!capitalized && last.getCompoundingBalance() == null) {
                pendingQuarterInterest = sumAccrued(previousRows);
            }
        }

        FdInterestAccrual target = null;
        for (LocalDate accrualDate = lastDate.plusDays(1); !accrualDate.isAfter(endDate); accrualDate = accrualDate.plusDays(1)) {
            OffsetDateTime storedDate = accrualDate.atStartOfDay().atOffset(ZoneOffset.UTC);
            var existing = accrualRepository.findByAccountIdAndAccrualDate(account.getId(), storedDate);
            if (existing.isPresent()) {
                FdInterestAccrual row = existing.get();
                balance = row.getCompoundingBalance() == null ? balance : row.getCompoundingBalance();
                cumulative = row.getCumulativeAccrued() == null ? cumulative.add(row.getAccruedAmount()) : row.getCumulativeAccrued();
                if (row.getCapitalizedAmount() != null && row.getCapitalizedAmount().signum() > 0) {
                    pendingQuarterInterest = BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
                } else {
                    pendingQuarterInterest = pendingQuarterInterest.add(row.getAccruedAmount());
                }
                target = row;
                continue;
            }
            BigDecimal dailyInterest = FdCompoundCalculator.dailyInterest(balance, account.getInterestRate());
            cumulative = cumulative.add(dailyInterest);
            pendingQuarterInterest = pendingQuarterInterest.add(dailyInterest);
            BigDecimal capitalized = BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
            if (FdCompoundCalculator.isCapitalizationDate(account.getBookingDate(), accrualDate,
                    account.getMaturityDate() == null ? endDate : account.getMaturityDate())) {
                capitalized = pendingQuarterInterest.setScale(scale, RoundingMode.HALF_UP).setScale(8, RoundingMode.HALF_UP);
                balance = balance.add(capitalized);
                pendingQuarterInterest = BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
            }
            FdInterestAccrual row = new FdInterestAccrual();
            row.setAccountId(account.getId());
            row.setAccrualDate(storedDate);
            row.setAccruedAmount(dailyInterest);
            row.setCumulativeAccrued(cumulative);
            row.setCompoundingBalance(balance);
            row.setCapitalizedAmount(capitalized);
            row.setCurrencyId(account.getCurrencyId());
            row.setUuid(UUID.randomUUID().toString());
            target = accrualRepository.save(row);
        }
        if (target == null) {
            return new AccrualResponse(account.getId(), account.getAccountNumber(), endDate,
                    BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP), cumulative.setScale(scale, RoundingMode.HALF_UP),
                    currencyCode, account.getMaturityAmount(), account.getPolicyVersion(), account.getStatus().name());
        }
        return AccrualResponse.accrued(account, target,
                target.getCumulativeAccrued() == null ? cumulative : target.getCumulativeAccrued(), currencyCode);
    }

    private static BigDecimal sumAccrued(List<FdInterestAccrual> rows) {
        return rows.stream().map(FdInterestAccrual::getAccruedAmount).filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(8, RoundingMode.HALF_UP);
    }

    private static boolean isQuarterlyAccount(FdAccount account) {
        return COMPOUND_POLICY_VERSION.equals(account.getPolicyVersion())
                && FdCompoundCalculator.QUARTERLY.equalsIgnoreCase(account.getCompoundingFrequency());
    }

    private int resolveCurrencyScale(FdAccount account) {
        return currencyRepository.findById(account.getCurrencyId())
                .map(reference -> reference.getDecimalPlaces() == null ? 2 : reference.getDecimalPlaces())
                .orElse(2);
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
        FdInterestAccrual latest = rows.get(rows.size() - 1);
        BigDecimal total = latest.getCumulativeAccrued() == null
                ? sumAccrued(rows) : latest.getCumulativeAccrued();
        return AccrualResponse.accrued(account, latest, total, currencyCode);
    }

    private FdAccount loadOwnedAccount(Long accountId, String username) {
        FdAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("FD account not found"));
        accessPolicy.requireOwned(username, account,
                SecurityContextHolder.getContext().getAuthentication());
        return account;
    }

    private String resolveCurrencyCode(FdAccount account) {
        return currencyRepository.findById(account.getCurrencyId())
                .orElseThrow(() -> new ResourceNotFoundException("FD account currency not found"))
                .getIsoCode();
    }
}
