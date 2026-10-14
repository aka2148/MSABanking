package com.bank.fd.application;

import com.bank.common.exception.InvalidInputException;
import com.bank.common.exception.ResourceNotFoundException;
import com.bank.fd.api.dto.FundingRequest;
import com.bank.fd.api.dto.FundingResponse;
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
import com.bank.fd.repository.FdProductReferenceRepository;
import com.bank.fd.repository.FdTransactionRepository;
import com.bank.fd.repository.TransactionTypeRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/** Executes one authenticated, exact-principal internal funding command. */
@Service
public class FundingService {
    private static final String DEPOSIT = "DEPOSIT";
    private static final String INTERNAL_REMARK = "Internal savings-to-FD funding; no external payment rail was called.";

    @Autowired
    private FdAccountingService accounting;

    private final FdAccountRepository accountRepository;
    private final FdTransactionRepository transactionRepository;
    private final TransactionTypeRepository transactionTypeRepository;
    private final FdProductReferenceRepository productRepository;
    private final FdCurrencyReferenceRepository currencyRepository;
    private final FdActorResolver actorResolver;
    private final FdLifecycleStateMachine stateMachine;
    private final FdTimePolicy timePolicy;
    private final FdIdempotencyGuard idempotencyGuard;
    private final FdAccessPolicy accessPolicy;

    @Autowired
    public FundingService(FdAccountRepository accountRepository, FdTransactionRepository transactionRepository,
                          TransactionTypeRepository transactionTypeRepository,
                          FdProductReferenceRepository productRepository,
                          FdCurrencyReferenceRepository currencyRepository,
                          FdActorResolver actorResolver, FdLifecycleStateMachine stateMachine, FdTimePolicy timePolicy,
                          FdIdempotencyGuard idempotencyGuard, FdAccessPolicy accessPolicy) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.transactionTypeRepository = transactionTypeRepository;
        this.productRepository = productRepository;
        this.currencyRepository = currencyRepository;
        this.actorResolver = actorResolver;
        this.stateMachine = stateMachine;
        this.timePolicy = timePolicy;
        this.idempotencyGuard = idempotencyGuard;
        this.accessPolicy = accessPolicy;
    }

    @Transactional
    public FundingResponse fund(Long accountId, String username, FundingRequest request) {
        return fund(accountId, username, request, null);
    }

    @Transactional
    public FundingResponse fund(Long accountId, String username, FundingRequest request, String idempotencyKey) {
        if (request == null || request.amount() == null || request.currencyCode() == null
                || request.currencyCode().isBlank()) {
            throw new InvalidInputException("Funding amount and currency are required");
        }

        FdAccount account = accountRepository.findByIdForUpdate(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("FD account not found"));
        var holder = actorResolver.resolve(username);
        accessPolicy.requireOwned(username, account,
                SecurityContextHolder.getContext().getAuthentication());

        String requestedCurrency = request.currencyCode().trim().toUpperCase();
        String accountCurrency = currencyRepository.findById(account.getCurrencyId())
                .orElseThrow(() -> new ResourceNotFoundException("FD account currency not found"))
                .getIsoCode();
        if (!requestedCurrency.equalsIgnoreCase(accountCurrency)) {
            throw new InvalidInputException("Funding currency must match the booked account currency");
        }
        if (request.amount().compareTo(account.getPrincipalAmount()) != 0) {
            throw new InvalidInputException("Funding amount must equal the booked principal exactly");
        }

        String canonical = accountId + "|" + request.amount().stripTrailingZeros().toPlainString()
                + "|" + requestedCurrency;
        FdOperationIdempotency claim = idempotencyGuard.claim("FUNDING", "fd-user-" + holder.getId(),
                idempotencyKey, FdIdempotencyGuard.sha256Hex(canonical));
        if (claim != null && claim.getAggregateId() != null) {
            FdAccount stored = accountRepository.findById(claim.getAggregateId())
                    .orElseThrow(() -> new ResourceNotFoundException("FD account not found"));
            String productCode = productRepository.findById(stored.getProductId())
                    .orElseThrow(() -> new ResourceNotFoundException("FD product reference not found"))
                    .getProductCode();
            String currencyCode = currencyRepository.findById(stored.getCurrencyId())
                    .orElseThrow(() -> new ResourceNotFoundException("FD account currency not found"))
                    .getIsoCode();
            return FundingResponse.active(stored, productCode, currencyCode);
        }
        if (account.getStatus() == FdStatus.ACTIVE) {
            throw new InvalidFdTransitionException("FD account is already ACTIVE; existing funding transaction recorded for account: "
                    + account.getAccountNumber());
        }

        var transition = stateMachine.transition(account.getStatus(), FdLifecycleCommand.FUND);
        Long transactionTypeId = transactionTypeRepository.findIdByCode(DEPOSIT)
                .orElseThrow(() -> new ResourceNotFoundException("DEPOSIT transaction type is not configured"));

        if (accounting != null) accounting.fund(account);

        FdTransaction transaction = new FdTransaction();
        transaction.setAccountId(account.getId());
        transaction.setTransactionTypeId(transactionTypeId);
        transaction.setAmount(account.getPrincipalAmount());
        transaction.setTransactionDate(OffsetDateTime.ofInstant(timePolicy.now(), ZoneOffset.UTC));
        transaction.setBalanceAfter(account.getPrincipalAmount());
        transaction.setCurrencyId(account.getCurrencyId());
        transaction.setRemarks(INTERNAL_REMARK);
        transaction.setUuid(UUID.randomUUID().toString());
        transactionRepository.save(transaction);

        account.setStatus(transition.to());
        accountRepository.save(account);
        idempotencyGuard.complete(claim, account.getId(), account.getAccountNumber());

        String productCode = productRepository.findById(account.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("FD product reference not found"))
                .getProductCode();
        return FundingResponse.active(account, productCode, accountCurrency);
    }
}
