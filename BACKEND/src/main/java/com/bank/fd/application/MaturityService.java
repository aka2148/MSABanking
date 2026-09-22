package com.bank.fd.application;

import com.bank.common.exception.InvalidInputException;
import com.bank.common.exception.ResourceNotFoundException;
import com.bank.fd.api.dto.AccrualResponse;
import com.bank.fd.domain.FdInterestCalculator;
import com.bank.fd.domain.FdLifecycleCommand;
import com.bank.fd.domain.FdLifecycleStateMachine;
import com.bank.fd.domain.FdTimePolicy;
import com.bank.fd.entity.FdAccount;
import com.bank.fd.entity.FdInterestAccrual;
import com.bank.fd.entity.FdOperationIdempotency;
import com.bank.fd.entity.FdStatus;
import com.bank.fd.entity.FdTransaction;
import com.bank.fd.exception.InvalidFdTransitionException;
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
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Transitions a maturity-date-reached ACTIVE FD to MATURED with a final through-end-date calculation. */
@Service
public class MaturityService {

    private static final String MATURITY = "MATURITY";
    private static final String INTERNAL_REMARK =
            "Internal academic maturity record; no external payment rail was called.";

    private final FdAccountRepository accountRepository;
    private final FdInterestAccrualRepository accrualRepository;
    private final FdCurrencyReferenceRepository currencyRepository;
    private final TransactionTypeRepository transactionTypeRepository;
    private final FdTransactionRepository transactionRepository;
    private final FdActorResolver actorResolver;
    private final FdLifecycleStateMachine stateMachine;
    private final FdTimePolicy timePolicy;
    private final FdIdempotencyGuard idempotencyGuard;
    private final FdAccessPolicy accessPolicy;

    @Autowired
    public MaturityService(FdAccountRepository accountRepository,
                           FdInterestAccrualRepository accrualRepository,
                           FdCurrencyReferenceRepository currencyRepository,
                           TransactionTypeRepository transactionTypeRepository,
                           FdTransactionRepository transactionRepository,
                           FdActorResolver actorResolver,
                           FdLifecycleStateMachine stateMachine,
                           FdTimePolicy timePolicy,
                           FdIdempotencyGuard idempotencyGuard,
                           FdAccessPolicy accessPolicy) {
        this.accountRepository = accountRepository;
        this.accrualRepository = accrualRepository;
        this.currencyRepository = currencyRepository;
        this.transactionTypeRepository = transactionTypeRepository;
        this.transactionRepository = transactionRepository;
        this.actorResolver = actorResolver;
        this.stateMachine = stateMachine;
        this.timePolicy = timePolicy;
        this.idempotencyGuard = idempotencyGuard;
        this.accessPolicy = accessPolicy;
    }

    @Transactional
    public AccrualResponse processMaturity(Long accountId, String username) {
        return processMaturity(accountId, username, null);
    }

    @Transactional
    public AccrualResponse processMaturity(Long accountId, String username, String idempotencyKey) {
        FdAccount account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("FD account not found"));
        var holder = actorResolver.resolve(username);
        accessPolicy.requireOwned(username, account,
                SecurityContextHolder.getContext().getAuthentication());

        FdOperationIdempotency claim = idempotencyGuard.claim("MATURITY", "fd-user-" + holder.getId(), idempotencyKey,
                FdIdempotencyGuard.sha256Hex(accountId + "|MATURITY"));
        if (claim != null && claim.getAggregateId() != null) {
            FdAccount stored = accountRepository.findById(claim.getAggregateId())
                    .orElseThrow(() -> new ResourceNotFoundException("FD account not found"));
            BigDecimal replayedInterest = FdInterestCalculator.interestForPeriod(
                    stored.getPrincipalAmount(), stored.getInterestRate(),
                    stored.getBookingDate(), stored.getMaturityDate());
            String replayedCurrency = currencyRepository.findById(stored.getCurrencyId())
                    .orElseThrow(() -> new ResourceNotFoundException("FD account currency not found"))
                    .getIsoCode();
            return AccrualResponse.matured(stored, replayedInterest, replayedInterest,
                    stored.getMaturityAmount(), replayedCurrency, stored.getMaturityDate());
        }
        if (account.getStatus() != FdStatus.ACTIVE) {
            if (account.getStatus() == FdStatus.MATURED && (idempotencyKey == null || idempotencyKey.isBlank())) {
                throw new InvalidFdTransitionException("FD account is already MATURED; existing maturity transaction recorded for account: "
                        + account.getAccountNumber());
            }
            throw new InvalidFdTransitionException(account.getStatus(), FdLifecycleCommand.MATURE);
        }

        LocalDate businessDate = timePolicy.businessDate();
        if (account.getMaturityDate() == null || businessDate.isBefore(account.getMaturityDate())) {
            throw new InvalidInputException("FD has not reached its maturity date");
        }

        BigDecimal interest = FdInterestCalculator.interestForPeriod(
                account.getPrincipalAmount(), account.getInterestRate(),
                account.getBookingDate(), account.getMaturityDate());
        BigDecimal finalMaturity = account.getPrincipalAmount().add(interest)
                .setScale(2, RoundingMode.HALF_UP);

        OffsetDateTime closingAccrualDate = account.getMaturityDate().atStartOfDay().atOffset(ZoneOffset.UTC);
        if (accrualRepository.findByAccountIdAndAccrualDate(accountId, closingAccrualDate).isEmpty()) {
            FdInterestAccrual closing = new FdInterestAccrual();
            closing.setAccountId(account.getId());
            closing.setAccrualDate(closingAccrualDate);
            closing.setAccruedAmount(interest.setScale(4, RoundingMode.HALF_UP));
            closing.setCumulativeAccrued(interest.setScale(4, RoundingMode.HALF_UP));
            closing.setCurrencyId(account.getCurrencyId());
            closing.setUuid(UUID.randomUUID().toString());
            accrualRepository.save(closing);
        }

        Long maturityTypeId = transactionTypeRepository.findIdByCode(MATURITY)
                .orElseThrow(() -> new ResourceNotFoundException("MATURITY transaction type is not configured"));
        FdTransaction transaction = new FdTransaction();
        transaction.setAccountId(account.getId());
        transaction.setTransactionTypeId(maturityTypeId);
        transaction.setAmount(finalMaturity);
        transaction.setTransactionDate(OffsetDateTime.ofInstant(timePolicy.now(), ZoneOffset.UTC));
        transaction.setBalanceAfter(finalMaturity);
        transaction.setCurrencyId(account.getCurrencyId());
        transaction.setRemarks(INTERNAL_REMARK);
        transaction.setUuid(UUID.randomUUID().toString());
        transactionRepository.save(transaction);

        var transition = stateMachine.transition(FdStatus.ACTIVE, FdLifecycleCommand.MATURE);
        account.setStatus(transition.to());
        account.setMaturityAmount(finalMaturity);
        accountRepository.save(account);
        idempotencyGuard.complete(claim, account.getId(), account.getAccountNumber());

        String currencyCode = currencyRepository.findById(account.getCurrencyId())
                .orElseThrow(() -> new ResourceNotFoundException("FD account currency not found"))
                .getIsoCode();
        return AccrualResponse.matured(account, interest, interest, finalMaturity, currencyCode,
                account.getMaturityDate());
    }
}
