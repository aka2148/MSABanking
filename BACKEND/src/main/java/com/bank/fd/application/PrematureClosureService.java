package com.bank.fd.application;

import com.bank.common.exception.InvalidInputException;
import com.bank.common.exception.ResourceNotFoundException;
import com.bank.fd.api.dto.PrematureClosureResponse;
import com.bank.fd.domain.FdInterestCalculator;
import com.bank.fd.domain.FdLifecycleCommand;
import com.bank.fd.domain.FdLifecycleStateMachine;
import com.bank.fd.domain.FdTimePolicy;
import com.bank.fd.domain.PrematurePenalty;
import com.bank.fd.entity.FdAccount;
import com.bank.fd.entity.FdInterestAccrual;
import com.bank.fd.entity.FdPrematureClosure;
import com.bank.fd.entity.FdRuleOutcomeReference;
import com.bank.fd.entity.FdStatus;
import com.bank.fd.entity.FdTransaction;
import com.bank.fd.exception.InvalidFdTransitionException;
import com.bank.fd.exception.PrematureClosureNotSupportedException;
import com.bank.fd.repository.FdAccountRepository;
import com.bank.fd.repository.FdCurrencyReferenceRepository;
import com.bank.fd.repository.FdInterestAccrualRepository;
import com.bank.fd.repository.FdPrematureClosureRepository;
import com.bank.fd.repository.FdRuleOutcomeReferenceRepository;
import com.bank.fd.repository.FdTransactionRepository;
import com.bank.fd.repository.TransactionTypeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Self-service two-step premature closure (D-03/D-04): the same authenticated
 * owning customer requests a penalized-rate quote and then approves it.
 * Amounts are server-computed from the locked FD row plus the stored
 * {@code CLOSURE_PENALTY} outcome snapshot; the request body carries only
 * optional free-form reason text.
 */
@Service
public class PrematureClosureService {

    private static final String PENALTY = "PENALTY";
    private static final String CLOSURE = "CLOSURE";
    private static final String CLOSURE_PENALTY_RULE = "CLOSURE_PENALTY";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_APPROVED = "APPROVED";
    private static final String PENALTY_REMARK =
            "Internal academic premature-closure penalty record; no external payment rail was called.";
    private static final String SETTLEMENT_REMARK =
            "Internal academic premature-closure settlement; no external payment rail was called.";
    private static final int MAX_REASON_LENGTH = 500;

    private final FdAccountRepository accountRepository;
    private final FdPrematureClosureRepository closureRepository;
    private final FdTransactionRepository transactionRepository;
    private final FdInterestAccrualRepository accrualRepository;
    private final TransactionTypeRepository transactionTypeRepository;
    private final FdCurrencyReferenceRepository currencyRepository;
    private final FdRuleOutcomeReferenceRepository ruleOutcomeRepository;
    private final FdActorResolver actorResolver;
    private final FdLifecycleStateMachine stateMachine;
    private final FdTimePolicy timePolicy;

    @Autowired
    public PrematureClosureService(FdAccountRepository accountRepository,
                                   FdPrematureClosureRepository closureRepository,
                                   FdTransactionRepository transactionRepository,
                                   FdInterestAccrualRepository accrualRepository,
                                   TransactionTypeRepository transactionTypeRepository,
                                   FdCurrencyReferenceRepository currencyRepository,
                                   FdRuleOutcomeReferenceRepository ruleOutcomeRepository,
                                   FdActorResolver actorResolver,
                                   FdLifecycleStateMachine stateMachine,
                                   FdTimePolicy timePolicy) {
        this.accountRepository = accountRepository;
        this.closureRepository = closureRepository;
        this.transactionRepository = transactionRepository;
        this.accrualRepository = accrualRepository;
        this.transactionTypeRepository = transactionTypeRepository;
        this.currencyRepository = currencyRepository;
        this.ruleOutcomeRepository = ruleOutcomeRepository;
        this.actorResolver = actorResolver;
        this.stateMachine = stateMachine;
        this.timePolicy = timePolicy;
    }

    @Transactional
    public PrematureClosureResponse requestPrematureClosure(Long accountId, String username, String reason) {
        FdAccount account = loadOwnedAccount(accountId, username);
        if (account.getStatus() != FdStatus.ACTIVE) {
            throw new InvalidFdTransitionException(account.getStatus(), FdLifecycleCommand.PREMATURE_CLOSE);
        }

        // Stored-snapshot penalty: the booked CLOSURE_PENALTY outcome row for
        // THIS account, resolved before any write (D-02).
        FdRuleOutcomeReference penaltyOutcome = resolvePenaltyOutcome(account);
        BigDecimal penaltyPoints = PrematurePenalty.parsePenaltyPoints(penaltyOutcome.getOutcomeValue())
                .orElseThrow(() -> new PrematureClosureNotSupportedException(
                        "Premature closure is not supported for FD account " + accountId
                                + ": the stored CLOSURE_PENALTY rule value '"
                                + penaltyOutcome.getOutcomeValue() + "' is unusable"));

        LocalDate startInclusive = account.getBookingDate();
        if (startInclusive == null) {
            throw new InvalidInputException("FD account has no booking date for premature-closure quote");
        }
        if (account.getInterestRate() == null) {
            throw new InvalidInputException("FD account has no contracted rate for premature-closure quote");
        }
        LocalDate endExclusive = elapsedEndExclusive(account);
        BigDecimal principal = account.getPrincipalAmount();

        BigDecimal penalizedRate = PrematurePenalty.penalizedRate(account.getInterestRate(), penaltyPoints);
        BigDecimal penalizedInterest =
                PrematurePenalty.prematureInterest(principal, account.getInterestRate(), penaltyPoints,
                        startInclusive, endExclusive);
        BigDecimal fullInterest = FdInterestCalculator.interestForPeriod(
                principal, account.getInterestRate(), startInclusive, endExclusive);
        BigDecimal foregone = PrematurePenalty.foregoneInterest(fullInterest, penalizedInterest);
        BigDecimal settlement = PrematurePenalty.prematureSettlement(
                principal, account.getInterestRate(), penaltyPoints, startInclusive, endExclusive);

        String currencyCode = resolveCurrencyCode(account);

        FdPrematureClosure closure = new FdPrematureClosure();
        closure.setAccountId(account.getId());
        closure.setClosureDate(OffsetDateTime.ofInstant(timePolicy.now(), ZoneOffset.UTC));
        closure.setPenaltyPctApplied(penaltyPoints);
        closure.setPenaltyAmount(foregone);
        closure.setNetPayableAmount(settlement);
        closure.setCurrencyId(account.getCurrencyId());
        closure.setReason(truncateReason(reason));
        closure.setAppliedRuleOutcomeId(penaltyOutcome.getId());
        closure.setStatus(STATUS_PENDING);
        closure.setUuid(UUID.randomUUID().toString());
        FdPrematureClosure saved = closureRepository.save(closure);

        return PrematureClosureResponse.requested(saved, penalizedRate, currencyCode);
    }

    @Transactional
    public PrematureClosureResponse approvePrematureClosure(Long accountId, Long closureId, String username) {
        FdAccount account = loadOwnedAccount(accountId, username);
        FdPrematureClosure closure = closureRepository.findById(closureId)
                .orElseThrow(() -> new ResourceNotFoundException("Premature closure request not found"));
        if (!accountId.equals(closure.getAccountId()) || !STATUS_PENDING.equalsIgnoreCase(closure.getStatus())) {
            throw new InvalidFdTransitionException(account.getStatus(), FdLifecycleCommand.PREMATURE_CLOSE);
        }
        if (account.getStatus() != FdStatus.ACTIVE) {
            throw new InvalidFdTransitionException(account.getStatus(), FdLifecycleCommand.PREMATURE_CLOSE);
        }

        // Corrupt-row guards: the request path rejects a null booking
        // date/rate with 400, so approve holds the same bar for the stored
        // snapshot and live account before writing anything (never 500).
        if (account.getPrincipalAmount() == null) {
            throw new InvalidInputException(
                    "FD account has no principal amount for premature-closure approval");
        }
        if (account.getInterestRate() == null) {
            throw new InvalidInputException(
                    "FD account has no contracted rate for premature-closure approval");
        }
        if (closure.getPenaltyAmount() == null || closure.getNetPayableAmount() == null
                || closure.getPenaltyPctApplied() == null) {
            throw new InvalidInputException(
                    "Premature closure request has no usable quoted amounts for approval");
        }

        Long penaltyTypeId = transactionTypeRepository.findIdByCode(PENALTY)
                .orElseThrow(() -> new ResourceNotFoundException("PENALTY transaction type is not configured"));
        Long closureTypeId = transactionTypeRepository.findIdByCode(CLOSURE)
                .orElseThrow(() -> new ResourceNotFoundException("CLOSURE transaction type is not configured"));
        String currencyCode = resolveCurrencyCode(account);

        // Apply the stored request-time snapshot verbatim: no requote, so
        // request, approve, and history agree on the same amounts.
        BigDecimal foregone = closure.getPenaltyAmount();
        BigDecimal settlement = closure.getNetPayableAmount();
        OffsetDateTime now = OffsetDateTime.ofInstant(timePolicy.now(), ZoneOffset.UTC);

        FdTransaction penalty = new FdTransaction();
        penalty.setAccountId(account.getId());
        penalty.setTransactionTypeId(penaltyTypeId);
        penalty.setAmount(foregone);
        penalty.setTransactionDate(now);
        penalty.setBalanceAfter(null);
        penalty.setCurrencyId(account.getCurrencyId());
        penalty.setRemarks(PENALTY_REMARK);
        penalty.setUuid(UUID.randomUUID().toString());
        transactionRepository.save(penalty);

        FdTransaction settlementRow = new FdTransaction();
        settlementRow.setAccountId(account.getId());
        settlementRow.setTransactionTypeId(closureTypeId);
        settlementRow.setAmount(settlement);
        settlementRow.setTransactionDate(now);
        settlementRow.setBalanceAfter(settlement);
        settlementRow.setCurrencyId(account.getCurrencyId());
        settlementRow.setRemarks(SETTLEMENT_REMARK);
        settlementRow.setUuid(UUID.randomUUID().toString());
        transactionRepository.save(settlementRow);

        BigDecimal penalizedInterest = settlement.subtract(account.getPrincipalAmount())
                .setScale(4, RoundingMode.HALF_UP);
        OffsetDateTime closingAccrualDate =
                elapsedEndExclusive(account).atStartOfDay().atOffset(ZoneOffset.UTC);
        if (accrualRepository.findByAccountIdAndAccrualDate(accountId, closingAccrualDate).isEmpty()) {
            FdInterestAccrual closing = new FdInterestAccrual();
            closing.setAccountId(account.getId());
            closing.setAccrualDate(closingAccrualDate);
            closing.setAccruedAmount(penalizedInterest);
            closing.setCumulativeAccrued(penalizedInterest);
            closing.setCurrencyId(account.getCurrencyId());
            closing.setUuid(UUID.randomUUID().toString());
            accrualRepository.save(closing);
        }

        var holder = actorResolver.resolve(username);
        closure.setStatus(STATUS_APPROVED);
        closure.setApprovedBy(holder.getId());
        closureRepository.save(closure);

        var transition = stateMachine.transition(FdStatus.ACTIVE, FdLifecycleCommand.PREMATURE_CLOSE);
        account.setStatus(transition.to());
        accountRepository.save(account);

        BigDecimal penalizedRate = PrematurePenalty.penalizedRate(
                account.getInterestRate(), closure.getPenaltyPctApplied());
        return PrematureClosureResponse.approved(closure, penalizedRate, currencyCode);
    }

    private FdAccount loadOwnedAccount(Long accountId, String username) {
        FdAccount account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("FD account not found"));
        var holder = actorResolver.resolve(username);
        if (!holder.getId().equals(account.getPrimaryHolderId())) {
            throw new AccessDeniedException("Authenticated customer does not own this FD account");
        }
        return account;
    }

    /**
     * Resolves the stored {@code CLOSURE_PENALTY} outcome for THIS account via
     * its booked outcome reference (never live product tables, never a global
     * scan). A missing reference, or one that is not a penalty rule, fails
     * closed with the D-02 unsupported error, so a second product's penalty
     * row can never break this account's closure.
     */
    private FdRuleOutcomeReference resolvePenaltyOutcome(FdAccount account) {
        Long bookedOutcomeId = account.getAppliedRateOutcomeId();
        FdRuleOutcomeReference outcome = (bookedOutcomeId != null)
                ? ruleOutcomeRepository.findById(bookedOutcomeId).orElse(null)
                : null;
        if (outcome == null || !CLOSURE_PENALTY_RULE.equalsIgnoreCase(outcome.getOutcomeType())) {
            throw new PrematureClosureNotSupportedException(
                    "Premature closure is not supported for FD account " + account.getId()
                            + ": no usable CLOSURE_PENALTY rule is stored for this product");
        }
        return outcome;
    }

    /** Elapsed end clamped to min(businessDate, maturityDate), floored at the booking date. */
    private LocalDate elapsedEndExclusive(FdAccount account) {
        LocalDate businessDate = timePolicy.businessDate();
        LocalDate maturityDate = account.getMaturityDate();
        LocalDate endExclusive = (maturityDate != null && maturityDate.isBefore(businessDate))
                ? maturityDate : businessDate;
        LocalDate bookingDate = account.getBookingDate();
        if (bookingDate != null && endExclusive.isBefore(bookingDate)) {
            return bookingDate;
        }
        return endExclusive;
    }

    private String resolveCurrencyCode(FdAccount account) {
        return currencyRepository.findById(account.getCurrencyId())
                .orElseThrow(() -> new ResourceNotFoundException("FD account currency not found"))
                .getIsoCode();
    }

    private static String truncateReason(String reason) {
        if (reason == null) {
            return null;
        }
        String trimmed = reason.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > MAX_REASON_LENGTH ? trimmed.substring(0, MAX_REASON_LENGTH) : trimmed;
    }
}
