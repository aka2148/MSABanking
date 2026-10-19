package com.bank.fd.application;

import com.bank.common.exception.InvalidInputException;
import com.bank.common.exception.ResourceNotFoundException;
import com.bank.fd.api.dto.SavingsTransferRequest;
import com.bank.fd.api.dto.SavingsTransferResponse;
import com.bank.fd.domain.FdTimePolicy;
import com.bank.fd.entity.FdAccount;
import com.bank.fd.entity.SavingsAccount;
import com.bank.fd.entity.SavingsTransaction;
import com.bank.fd.repository.FdAccountRepository;
import com.bank.fd.repository.FdCurrencyReferenceRepository;
import com.bank.fd.repository.SavingsAccountRepository;
import com.bank.fd.repository.SavingsTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
public class SavingsTransferService {
    private final SavingsAccountRepository accounts;
    private final SavingsTransactionRepository transactions;
    private final FdAccountRepository fdAccounts;
    private final FdCurrencyReferenceRepository currencies;
    private final FdActorResolver actors;
    private final FdIdempotencyGuard idempotency;
    private final FdTimePolicy time;

    public SavingsTransferService(SavingsAccountRepository accounts, SavingsTransactionRepository transactions,
                                  FdAccountRepository fdAccounts, FdCurrencyReferenceRepository currencies,
                                  FdActorResolver actors, FdIdempotencyGuard idempotency, FdTimePolicy time) {
        this.accounts = accounts;
        this.transactions = transactions;
        this.fdAccounts = fdAccounts;
        this.currencies = currencies;
        this.actors = actors;
        this.idempotency = idempotency;
        this.time = time;
    }

    @Transactional
    public SavingsTransferResponse transfer(String username, SavingsTransferRequest request, String idempotencyKey) {
        if (request == null) throw new InvalidInputException("Transfer request is required");
        if (request.sourceAccountId() == null || request.sourceAccountId() <= 0) {
            throw new InvalidInputException("Source savings account is required");
        }
        if (request.destinationAccountNumber() == null || request.destinationAccountNumber().isBlank()) {
            throw new InvalidInputException("Destination savings account number is required");
        }
        if (request.amount() == null || request.amount().signum() <= 0) {
            throw new InvalidInputException("Transfer amount must be positive");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new InvalidInputException("Idempotency-Key is required for savings transfers");
        }
        String key = idempotencyKey.trim();
        if (key.length() > 255) throw new InvalidInputException("Idempotency-Key must not exceed 255 characters");
        String destinationNumber = request.destinationAccountNumber().trim();
        String remark = normalizeRemark(request.remark());
        var holder = actors.resolve(username);
        SavingsAccount sourceSnapshot = accounts.findById(request.sourceAccountId())
                .filter(source -> holder.getId().equals(source.getHolderId()))
                .orElseThrow(() -> new ResourceNotFoundException("Source savings account not found"));
        String canonical = request.sourceAccountId() + "|" + destinationNumber + "|"
                + request.amount().stripTrailingZeros().toPlainString() + "|" + (remark == null ? "" : remark);

        SavingsAccount destinationSnapshot = accounts.findByAccountNumber(destinationNumber)
                .orElseGet(() -> {
                    FdAccount fd = fdAccounts.findByAccountNumber(destinationNumber).orElse(null);
                    if (fd != null) throw new InvalidInputException("Destination must be a savings account; FD accounts cannot receive transfers");
                    throw new InvalidInputException("Destination account number must belong to a savings account");
                });

        if (sourceSnapshot.getId().equals(destinationSnapshot.getId())) {
            throw new InvalidInputException("Source and destination savings accounts must be different");
        }

        // Always acquire row locks in ID order so opposing transfers cannot deadlock.
        SavingsAccount first = sourceSnapshot.getId() < destinationSnapshot.getId() ? sourceSnapshot : destinationSnapshot;
        SavingsAccount second = first == sourceSnapshot ? destinationSnapshot : sourceSnapshot;
        SavingsAccount lockedFirst = accounts.findByIdForUpdate(first.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Savings account not found"));
        SavingsAccount lockedSecond = accounts.findByIdForUpdate(second.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Savings account not found"));
        SavingsAccount source = lockedFirst.getId().equals(sourceSnapshot.getId()) ? lockedFirst : lockedSecond;
        SavingsAccount destination = lockedFirst.getId().equals(destinationSnapshot.getId()) ? lockedFirst : lockedSecond;

        if (!source.getAccountNumber().equals(sourceSnapshot.getAccountNumber())
                || !destination.getAccountNumber().equals(destinationNumber)) {
            throw new InvalidInputException("Savings account details changed while processing the transfer");
        }
        if (!holder.getId().equals(source.getHolderId())) {
            throw new ResourceNotFoundException("Source savings account not found");
        }
        var claim = idempotency.claim("SAVINGS_TRANSFER", "fd-user-" + holder.getId(), key,
                FdIdempotencyGuard.sha256Hex(canonical));
        if (claim != null && claim.getAggregateId() != null && claim.getResponseReference() != null) {
            return replay(claim.getResponseReference(), source.getAccountNumber(), destination.getAccountNumber());
        }
        if (!"ACTIVE".equals(source.getStatus()) || !"ACTIVE".equals(destination.getStatus())) {
            throw new InvalidInputException("Source and destination savings accounts must both be active");
        }
        if (!source.getCurrencyId().equals(destination.getCurrencyId())) {
            throw new InvalidInputException("Savings transfer requires source and destination to use the same currency");
        }
        var currency = currencies.findById(source.getCurrencyId())
                .orElseThrow(() -> new ResourceNotFoundException("Savings currency not found"));
        int scale = currency.getDecimalPlaces() == null ? 2 : currency.getDecimalPlaces();
        BigDecimal amount;
        try {
            amount = request.amount().setScale(scale, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new InvalidInputException("Transfer amount exceeds the supported minor units for " + currency.getIsoCode());
        }
        if (amount.signum() <= 0) throw new InvalidInputException("Transfer amount must be positive");
        if (source.getBalance().compareTo(amount) < 0) {
            throw new InvalidInputException("Source savings account has insufficient balance");
        }

        BigDecimal sourceBalance = source.getBalance().subtract(amount);
        BigDecimal destinationBalance = destination.getBalance().add(amount);
        source.setBalance(sourceBalance);
        destination.setBalance(destinationBalance);
        accounts.save(source);
        accounts.save(destination);

        String reference = UUID.randomUUID().toString();
        OffsetDateTime postedAt = OffsetDateTime.ofInstant(time.now(), ZoneOffset.UTC);
        SavingsTransaction debit = posting(reference, source, "DEBIT", amount, sourceBalance, remark, postedAt);
        SavingsTransaction credit = posting(reference, destination, "CREDIT", amount, destinationBalance, remark, postedAt);
        transactions.saveAndFlush(debit);
        transactions.saveAndFlush(credit);
        idempotency.complete(claim, debit.getId(), reference);
        return response(reference, amount, currency.getIsoCode(), remark, postedAt,
                List.of(debit, credit), source.getAccountNumber(), destination.getAccountNumber());
    }

    private SavingsTransferResponse replay(String reference, String sourceAccountNumber,
                                           String destinationAccountNumber) {
        List<SavingsTransaction> entries = transactions.findByReferenceIdOrderByIdAsc(reference);
        if (entries.size() != 2) throw new ResourceNotFoundException("Recorded savings transfer was not found");
        var currency = currencies.findById(entries.get(0).getCurrencyId())
                .orElseThrow(() -> new ResourceNotFoundException("Savings currency not found"));
        return response(reference, entries.get(0).getAmount(), currency.getIsoCode(), entries.get(0).getRemarks(),
                entries.get(0).getPostedAt(), entries, sourceAccountNumber, destinationAccountNumber);
    }

    private SavingsTransferResponse response(String reference, BigDecimal amount, String currency, String remark,
                                             OffsetDateTime postedAt, List<SavingsTransaction> entries,
                                             String debitAccountNumber, String creditAccountNumber) {
        return SavingsTransferResponse.of(reference, amount, currency, remark, postedAt, entries,
                debitAccountNumber, creditAccountNumber);
    }

    private static SavingsTransaction posting(String reference, SavingsAccount account, String type,
                                              BigDecimal amount, BigDecimal balance, String remark,
                                              OffsetDateTime postedAt) {
        SavingsTransaction transaction = new SavingsTransaction();
        transaction.setReferenceId(reference);
        transaction.setAccountId(account.getId());
        transaction.setEntryType(type);
        transaction.setAmount(amount);
        transaction.setCurrencyId(account.getCurrencyId());
        transaction.setBalanceAfter(balance);
        transaction.setRemarks(remark);
        transaction.setPostedAt(postedAt);
        return transaction;
    }

    private static String normalizeRemark(String value) {
        if (value == null || value.isBlank()) return null;
        String remark = value.trim();
        if (remark.length() > 500) throw new InvalidInputException("Transfer remark must not exceed 500 characters");
        return remark;
    }
}
