package com.bank.fd.service;

import com.bank.common.exception.BankingException;
import com.bank.common.exception.ResourceNotFoundException;
import com.bank.fd.dto.CreateFdRequest;
import com.bank.fd.entity.FdAccount;
import com.bank.fd.entity.FdStatus;
import com.bank.fd.repository.FdAccountRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Legacy /api/fd bridge after the BankingTech schema migration.
 * Account creation and lifecycle operations must be implemented through the
 * normalized FD v1 reference-data model; they are intentionally not guessed here.
 */
@Service
public class FdAccountService {
    private final FdAccountRepository fdAccountRepository;

    public FdAccountService(FdAccountRepository fdAccountRepository) {
        this.fdAccountRepository = fdAccountRepository;
    }

    public FdAccount createFdAccount(String username, CreateFdRequest request) {
        throw new BankingException("Legacy FD booking is unavailable after the BankingTech schema migration. Use the /fd/v1 contract.");
    }

    @Transactional(readOnly = true)
    public List<FdAccount> getAccountsByCustomerUsername(String username) {
        throw new BankingException("Account lookup now requires an FD user reference ID, not a legacy username.");
    }

    @Transactional(readOnly = true)
    public FdAccount getAccountByNumber(String accountNumber) {
        return fdAccountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new ResourceNotFoundException("Fixed Deposit Account not found: " + accountNumber));
    }

    public FdAccount prematureWithdrawal(String accountNumber) {
        throw new BankingException("Premature closure must be processed through /fd/v1/accounts/{accountId}/premature-closure.");
    }

    public void accrueDailyInterest() {
        throw new BankingException("Interest accrual is represented by fd_interest_accrual and must be invoked through the FD v1 API.");
    }

    public void processMaturedAccounts() {
        throw new BankingException("Maturity processing must be invoked through the FD v1 API.");
    }

    @Transactional(readOnly = true)
    public List<FdAccount> getActiveAccounts() {
        return fdAccountRepository.findByStatus(FdStatus.ACTIVE);
    }
}
