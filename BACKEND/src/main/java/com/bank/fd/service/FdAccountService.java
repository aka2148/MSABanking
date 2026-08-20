package com.bank.fd.service;

import com.bank.calculator.dto.EstimationResponse;
import com.bank.calculator.service.FdCalculatorService;
import com.bank.common.exception.BankingException;
import com.bank.common.exception.ResourceNotFoundException;
import com.bank.customer.entity.CustomerProfile;
import com.bank.customer.service.CustomerService;
import com.bank.fd.dto.CreateFdRequest;
import com.bank.fd.entity.FdAccount;
import com.bank.fd.entity.FdStatus;
import com.bank.fd.entity.FdTransaction;
import com.bank.fd.entity.TransactionType;
import com.bank.fd.repository.FdAccountRepository;
import com.bank.fd.repository.FdTransactionRepository;
import com.bank.product.entity.FdProduct;
import com.bank.product.service.FdProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class FdAccountService {

    @Autowired
    private FdAccountRepository fdAccountRepository;

    @Autowired
    private FdTransactionRepository fdTransactionRepository;

    @Autowired
    private FdProductService fdProductService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private FdCalculatorService fdCalculatorService;

    @Autowired
    private AccountNumberGenerator accountNumberGenerator;

    @Transactional
    public FdAccount createFdAccount(String username, CreateFdRequest request) {
        CustomerProfile customer = customerService.getProfileEntityByUsername(username);
        FdProduct product = fdProductService.getProductById(request.getProductId());

        if (!product.isActive()) {
            throw new BankingException("Selected Fixed Deposit Product is inactive.");
        }

        // Validate limits
        if (request.getDepositAmount().compareTo(product.getMinAmount()) < 0 ||
                request.getDepositAmount().compareTo(product.getMaxAmount()) > 0) {
            throw new BankingException("Deposit amount must be between " + product.getMinAmount() + " and " + product.getMaxAmount());
        }

        if (request.getTermDays() < product.getMinTermDays() || request.getTermDays() > product.getMaxTermDays()) {
            throw new BankingException("Term days must be between " + product.getMinTermDays() + " and " + product.getMaxTermDays());
        }

        // Calculate rates and maturity
        BigDecimal effectiveRate = fdCalculatorService.calculateEffectiveRate(product.getBaseInterestRate(), customer.getCategory());
        EstimationResponse calculation = fdCalculatorService.calculate(
                request.getDepositAmount(),
                effectiveRate,
                request.getTermDays(),
                request.getCompoundingFrequency()
        );

        String accountNumber = accountNumberGenerator.generateNextAccountNumber(request.getBranchCode());

        FdAccount account = FdAccount.builder()
                .accountNumber(accountNumber)
                .customerId(customer.getId())
                .productId(product.getId())
                .depositAmount(request.getDepositAmount())
                .interestRate(effectiveRate)
                .bookingDate(LocalDate.now())
                .maturityDate(LocalDate.now().plusDays(request.getTermDays()))
                .termDays(request.getTermDays())
                .compoundingFrequency(request.getCompoundingFrequency())
                .accruedInterest(BigDecimal.ZERO)
                .maturityAmount(calculation.getMaturityAmount())
                .status(FdStatus.ACTIVE)
                .build();

        FdAccount savedAccount = fdAccountRepository.save(account);

        // Log transaction
        FdTransaction transaction = FdTransaction.builder()
                .accountNumber(accountNumber)
                .transactionType(TransactionType.DEPOSIT)
                .amount(request.getDepositAmount())
                .transactionDate(LocalDateTime.now())
                .description("Initial deposit to book Fixed Deposit")
                .build();

        fdTransactionRepository.save(transaction);

        return savedAccount;
    }

    @Transactional(readOnly = true)
    public List<FdAccount> getAccountsByCustomerUsername(String username) {
        CustomerProfile customer = customerService.getProfileEntityByUsername(username);
        return fdAccountRepository.findByCustomerId(customer.getId());
    }

    @Transactional(readOnly = true)
    public FdAccount getAccountByNumber(String accountNumber) {
        return fdAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Fixed Deposit Account not found: " + accountNumber));
    }

    @Transactional
    public FdAccount prematureWithdrawal(String accountNumber) {
        FdAccount account = getAccountByNumber(accountNumber);

        if (account.getStatus() != FdStatus.ACTIVE) {
            throw new BankingException("Only ACTIVE accounts can be prematurely closed. Current status: " + account.getStatus());
        }

        LocalDate today = LocalDate.now();
        long elapsedDays = ChronoUnit.DAYS.between(account.getBookingDate(), today);
        if (elapsedDays < 0) {
            elapsedDays = 0;
        }

        CustomerProfile customer = customerService.getProfileEntityById(account.getCustomerId());
        FdProduct product = fdProductService.getProductById(account.getProductId());

        BigDecimal payoutAmount = fdCalculatorService.calculatePrematurePayout(
                account.getDepositAmount(),
                product.getBaseInterestRate(),
                (int) elapsedDays,
                customer.getCategory(),
                account.getCompoundingFrequency()
        );

        BigDecimal actualInterestEarned = payoutAmount.subtract(account.getDepositAmount());
        BigDecimal originalExpectedInterest = account.getMaturityAmount().subtract(account.getDepositAmount());
        BigDecimal penaltyDifference = originalExpectedInterest.subtract(actualInterestEarned);

        if (penaltyDifference.compareTo(BigDecimal.ZERO) < 0) {
            penaltyDifference = BigDecimal.ZERO;
        }

        // Log Penalty Transaction if applicable
        if (penaltyDifference.compareTo(BigDecimal.ZERO) > 0) {
            FdTransaction penaltyTx = FdTransaction.builder()
                    .accountNumber(accountNumber)
                    .transactionType(TransactionType.PENALTY)
                    .amount(penaltyDifference)
                    .transactionDate(LocalDateTime.now())
                    .description("Premature withdrawal penalty. Deducted from expected interest.")
                    .build();
            fdTransactionRepository.save(penaltyTx);
        }

        // Log Payout Transaction
        FdTransaction payoutTx = FdTransaction.builder()
                .accountNumber(accountNumber)
                .transactionType(TransactionType.PAYOUT)
                .amount(payoutAmount)
                .transactionDate(LocalDateTime.now())
                .description("Premature closure payout. Holding period: " + elapsedDays + " days.")
                .build();
        fdTransactionRepository.save(payoutTx);

        // Update Account
        account.setAccruedInterest(actualInterestEarned);
        account.setMaturityAmount(payoutAmount);
        account.setStatus(FdStatus.CLOSED_PREMATURE);

        return fdAccountRepository.save(account);
    }

    @Transactional
    public void accrueDailyInterest() {
        List<FdAccount> activeAccounts = fdAccountRepository.findByStatus(FdStatus.ACTIVE);

        for (FdAccount account : activeAccounts) {
            // Straight-line accrual: Daily Increment = (Maturity Amount - Principal) / Term Days
            BigDecimal totalExpectedInterest = account.getMaturityAmount().subtract(account.getDepositAmount());
            BigDecimal dailyInterest = totalExpectedInterest.divide(
                    BigDecimal.valueOf(account.getTermDays()),
                    2,
                    RoundingMode.HALF_UP
            );

            account.setAccruedInterest(account.getAccruedInterest().add(dailyInterest));
            fdAccountRepository.save(account);

            // Log accrual transaction
            FdTransaction accrualTx = FdTransaction.builder()
                    .accountNumber(account.getAccountNumber())
                    .transactionType(TransactionType.ACCRUAL)
                    .amount(dailyInterest)
                    .transactionDate(LocalDateTime.now())
                    .description("Daily interest accrual")
                    .build();
            fdTransactionRepository.save(accrualTx);
        }
    }

    @Transactional
    public void processMaturedAccounts() {
        List<FdAccount> activeAccounts = fdAccountRepository.findByStatus(FdStatus.ACTIVE);
        LocalDate today = LocalDate.now();

        for (FdAccount account : activeAccounts) {
            if (account.getMaturityDate().isBefore(today) || account.getMaturityDate().isEqual(today)) {
                // Change status to MATURED temporarily
                account.setStatus(FdStatus.MATURED);
                fdAccountRepository.save(account);

                // Log payout transaction
                FdTransaction payoutTx = FdTransaction.builder()
                        .accountNumber(account.getAccountNumber())
                        .transactionType(TransactionType.PAYOUT)
                        .amount(account.getMaturityAmount())
                        .transactionDate(LocalDateTime.now())
                        .description("Maturity settlement payout. Account closed automatically on maturity.")
                        .build();
                fdTransactionRepository.save(payoutTx);

                account.setStatus(FdStatus.CLOSED_MATURED);
                fdAccountRepository.save(account);
            }
        }
    }
}
