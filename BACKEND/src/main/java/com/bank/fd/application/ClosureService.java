package com.bank.fd.application;

import com.bank.common.exception.InvalidInputException;
import com.bank.common.exception.ResourceNotFoundException;
import com.bank.fd.api.dto.ClosureResponse;
import com.bank.fd.domain.FdLifecycleCommand;
import com.bank.fd.domain.FdLifecycleStateMachine;
import com.bank.fd.domain.FdTimePolicy;
import com.bank.fd.entity.FdAccount;
import com.bank.fd.entity.FdTransaction;
import com.bank.fd.repository.FdAccountRepository;
import com.bank.fd.repository.FdCurrencyReferenceRepository;
import com.bank.fd.repository.FdTransactionRepository;
import com.bank.fd.repository.TransactionTypeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
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
            "Internal academic closure settlement; no external payment rail was called.";

    private final FdAccountRepository accountRepository;
    private final FdTransactionRepository transactionRepository;
    private final TransactionTypeRepository transactionTypeRepository;
    private final FdCurrencyReferenceRepository currencyRepository;
    private final FdActorResolver actorResolver;
    private final FdLifecycleStateMachine stateMachine;
    private final FdTimePolicy timePolicy;

    @Autowired
    public ClosureService(FdAccountRepository accountRepository,
                          FdTransactionRepository transactionRepository,
                          TransactionTypeRepository transactionTypeRepository,
                          FdCurrencyReferenceRepository currencyRepository,
                          FdActorResolver actorResolver,
                          FdLifecycleStateMachine stateMachine,
                          FdTimePolicy timePolicy) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.transactionTypeRepository = transactionTypeRepository;
        this.currencyRepository = currencyRepository;
        this.actorResolver = actorResolver;
        this.stateMachine = stateMachine;
        this.timePolicy = timePolicy;
    }

    @Transactional
    public ClosureResponse close(Long accountId, String username) {
        FdAccount account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("FD account not found"));
        var holder = actorResolver.resolve(username);
        if (!holder.getId().equals(account.getPrimaryHolderId())) {
            throw new AccessDeniedException("Authenticated customer does not own this FD account");
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

        LocalDate closedDate = timePolicy.businessDate();
        FdTransaction transaction = new FdTransaction();
        transaction.setAccountId(account.getId());
        transaction.setTransactionTypeId(closureTypeId);
        transaction.setAmount(settlement);
        transaction.setTransactionDate(OffsetDateTime.ofInstant(timePolicy.now(), ZoneOffset.UTC));
        transaction.setBalanceAfter(settlement);
        transaction.setCurrencyId(account.getCurrencyId());
        transaction.setRemarks(INTERNAL_REMARK);
        transaction.setUuid(UUID.randomUUID().toString());
        transactionRepository.save(transaction);

        account.setStatus(transition.to());
        accountRepository.save(account);

        return ClosureResponse.closed(account, settlement, currencyCode, closedDate);
    }
}
