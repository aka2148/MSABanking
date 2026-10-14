package com.bank.fd.application;

import com.bank.common.exception.InvalidInputException;
import com.bank.common.exception.ResourceNotFoundException;
import com.bank.fd.api.dto.*;
import com.bank.fd.domain.FdTimePolicy;
import com.bank.fd.entity.SavingsAccount;
import com.bank.fd.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class SavingsAccountService {
    private final SavingsAccountRepository accounts;
    private final FdCurrencyReferenceRepository currencies;
    private final FdActorResolver actors;
    private final FdTimePolicy time;
    private final FdIdempotencyGuard idempotency;
    public SavingsAccountService(SavingsAccountRepository accounts, FdCurrencyReferenceRepository currencies,
                                FdActorResolver actors, FdTimePolicy time, FdIdempotencyGuard idempotency) {
        this.accounts = accounts; this.currencies = currencies; this.actors = actors;
        this.time = time; this.idempotency = idempotency;
    }
    @Transactional
    public SavingsAccountResponse create(String username, CreateSavingsRequest request, String key) {
        if (request == null || request.displayName() == null || request.displayName().isBlank()
                || request.displayName().trim().length() > 80 || request.currencyCode() == null
                || request.currencyCode().isBlank()) throw new InvalidInputException("Savings name and currency are required");
        var holder = actors.resolve(username);
        String code = request.currencyCode().trim().toUpperCase(Locale.ROOT);
        var currency = currencies.findByIsoCode(code)
                .orElseThrow(() -> new InvalidInputException("Savings currency is not supported"));
        var claim = idempotency.claim("SAVINGS_CREATE", "fd-user-" + holder.getId(), key,
                FdIdempotencyGuard.sha256Hex(request.displayName().trim() + "|" + code));
        if (claim != null && claim.getAggregateId() != null) return get(username, claim.getAggregateId());
        SavingsAccount account = new SavingsAccount();
        account.setAccountNumber("SA-" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT));
        account.setHolderId(holder.getId());
        account.setCurrencyId(currency.getId());
        account.setDisplayName(request.displayName().trim());
        account.setBalance(BigDecimal.ZERO.setScale(currency.getDecimalPlaces() == null ? 2 : currency.getDecimalPlaces()));
        account.setCreatedDate(OffsetDateTime.ofInstant(time.now(), ZoneOffset.UTC));
        SavingsAccount saved = accounts.save(account);
        idempotency.complete(claim, saved.getId(), saved.getAccountNumber());
        return SavingsAccountResponse.from(saved, code);
    }
    @Transactional
    public List<SavingsAccountResponse> list(String username) {
        return accounts.findByHolderIdOrderByIdAsc(actors.resolve(username).getId()).stream().map(this::response).toList();
    }
    @Transactional
    public SavingsAccountResponse get(String username, Long id) {
        SavingsAccount account = accounts.findById(id)
                .filter(a -> a.getHolderId().equals(actors.resolve(username).getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Savings account not found"));
        return response(account);
    }
    /** Validate against the FD holder, never a client-supplied customer identity. */
    @Transactional(readOnly = true)
    public SavingsAccount requireFundingAccount(Long id, Long holderId, Long currencyId) {
        SavingsAccount account = accounts.findById(id).filter(a -> a.getHolderId().equals(holderId))
                .orElseThrow(() -> new ResourceNotFoundException("Savings account not found"));
        if (!"ACTIVE".equals(account.getStatus())) throw new InvalidInputException("Savings account must be active");
        if (!account.getCurrencyId().equals(currencyId)) throw new InvalidInputException("Savings currency must match the FD currency");
        return account;
    }
    private SavingsAccountResponse response(SavingsAccount account) {
        String code = currencies.findById(account.getCurrencyId())
                .orElseThrow(() -> new ResourceNotFoundException("Savings currency not found")).getIsoCode();
        return SavingsAccountResponse.from(account, code);
    }
}
