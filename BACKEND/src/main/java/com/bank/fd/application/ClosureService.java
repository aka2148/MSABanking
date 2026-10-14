package com.bank.fd.application;

import com.bank.common.exception.InvalidInputException;
import com.bank.common.exception.ResourceNotFoundException;
import com.bank.fd.api.dto.ClosureResponse;
import com.bank.fd.domain.FdLifecycleCommand;
import com.bank.fd.domain.FdLifecycleStateMachine;
import com.bank.fd.domain.FdTimePolicy;
import com.bank.fd.entity.FdAccount;
import com.bank.fd.entity.FdOperationIdempotency;
import com.bank.fd.entity.FdStatus;
import com.bank.fd.entity.FdTransaction;
import com.bank.fd.exception.InvalidFdTransitionException;
import com.bank.fd.repository.FdAccountRepository;
import com.bank.fd.repository.FdCurrencyReferenceRepository;
import com.bank.fd.repository.FdTransactionRepository;
import com.bank.fd.repository.TransactionTypeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Closes one matured FD with a CLOSURE settlement transaction. */
@Service
public class ClosureService {

    private static final String CLOSURE = "CLOSURE";
    private static final String INTERNAL_REMARK =
            "Internal FD settlement to original savings account; no external payment rail was called.";

    @Autowired
    private FdAccountingService accounting;

    private final FdAccountRepository accountRepository;
    private final FdTransactionRepository transactionRepository;
    private final TransactionTypeRepository transactionTypeRepository;
    private final FdCurrencyReferenceRepository currencyRepository;
    private final FdActorResolver actorResolver;
    private final FdLifecycleStateMachine stateMachine;
    private final FdTimePolicy timePolicy;
    private final FdIdempotencyGuard idempotencyGuard;
    private final FdAccessPolicy accessPolicy;

    @Autowired
    public ClosureService(FdAccountRepository accountRepository,
                          FdTransactionRepository transactionRepository,
                          TransactionTypeRepository transactionTypeRepository,
                          FdCurrencyReferenceRepository currencyRepository,
                          FdActorResolver actorResolver,
                          FdLifecycleStateMachine stateMachine,
                          FdTimePolicy timePolicy,
                          FdIdempotencyGuard idempotencyGuard,
                          FdAccessPolicy accessPolicy) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.transactionTypeRepository = transactionTypeRepository;
        this.currencyRepository = currencyRepository;
        this.actorResolver = actorResolver;
        this.stateMachine = stateMachine;
        this.timePolicy = timePolicy;
        this.idempotencyGuard = idempotencyGuard;
        this.accessPolicy = accessPolicy;
    }

    @Transactional
    public ClosureResponse close(Long accountId, String username) {
        return close(accountId, username, null);
    }

    @Transactional
    public ClosureResponse close(Long accountId, String username, String idempotencyKey) {
        FdAccount account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("FD account not found"));
        var holder = actorResolver.resolve(username);
        accessPolicy.requireOwned(username, account,
                SecurityContextHolder.getContext().getAuthentication());

        FdOperationIdempotency claim = idempotencyGuard.claim("CLOSURE", "fd-user-" + holder.getId(),
                idempotencyKey, FdIdempotencyGuard.sha256Hex(String.valueOf(accountId)));
        if (claim != null && claim.getAggregateId() != null) {
            FdAccount stored = accountRepository.findById(claim.getAggregateId())
                    .orElseThrow(() -> new ResourceNotFoundException("FD account not found"));
            BigDecimal settlement = stored.getMaturityAmount();
            if (settlement == null) {
                throw new InvalidInputException("Matured FD has no computed maturity amount to settle");
            }
            String currencyCode = currencyRepository.findById(stored.getCurrencyId())
                    .orElseThrow(() -> new ResourceNotFoundException("FD account currency not found"))
                    .getIsoCode();
            LocalDate originalDate = accounting == null ? timePolicy.businessDate() : accounting.settlement(stored.getId())
                    .orElseThrow(() -> new InvalidInputException("Closure has no savings settlement receipt"))
                    .getPostedAt().atZoneSameInstant(timePolicy.businessZone()).toLocalDate();
            return ClosureResponse.closed(stored, settlement, currencyCode, originalDate);
        }
        if (account.getStatus() == FdStatus.CLOSED && (idempotencyKey == null || idempotencyKey.isBlank())) {
            throw new InvalidFdTransitionException("FD account is already CLOSED; existing settlement recorded for account: "
                    + account.getAccountNumber());
        }

        // Pure transition check first: only MATURED accepts CLOSE, and this
        // throws before any write for every other status.
        var transition = stateMachine.transition(account.getStatus(), FdLifecycleCommand.CLOSE);

        BigDecimal settlement = account.getMaturityAmount();
        if (settlement == null) {
            throw new InvalidInputException("Matured FD has no computed maturity amount to settle");
        }

        Long closureTypeId = transactionTypeRepository.findIdByCode(CLOSURE)
                .orElseThrow(() -> new ResourceNotFoundException("CLOSURE transaction type is not configured"));
        String currencyCode = currencyRepository.findById(account.getCurrencyId())
                .orElseThrow(() -> new ResourceNotFoundException("FD account currency not found"))
                .getIsoCode();

        var receipt = accounting == null ? null : accounting.settle(account, settlement);
        LocalDate closedDate = receipt == null ? timePolicy.businessDate()
                : receipt.getPostedAt().atZoneSameInstant(timePolicy.businessZone()).toLocalDate();
        FdTransaction transaction = new FdTransaction();
        transaction.setAccountId(account.getId());
        transaction.setTransactionTypeId(closureTypeId);
        transaction.setAmount(settlement);
        transaction.setTransactionDate(OffsetDateTime.ofInstant(timePolicy.now(), ZoneOffset.UTC));
        transaction.setBalanceAfter(BigDecimal.ZERO.setScale(4));
        transaction.setCurrencyId(account.getCurrencyId());
        transaction.setRemarks(INTERNAL_REMARK);
        transaction.setUuid(UUID.randomUUID().toString());
        transactionRepository.save(transaction);

        account.setStatus(transition.to());
        accountRepository.save(account);
        idempotencyGuard.complete(claim, account.getId(), account.getAccountNumber());

        return ClosureResponse.closed(account, settlement, currencyCode, closedDate);
    }
}
