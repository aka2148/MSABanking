package com.bank.reports.service;

import com.bank.customer.dto.CustomerProfileDto;
import com.bank.customer.service.CustomerService;
import com.bank.fd.entity.FdAccount;
import com.bank.fd.entity.FdStatus;
import com.bank.fd.repository.FdAccountRepository;
import com.bank.reports.dto.BankOperationalReport;
import com.bank.reports.dto.CustomerPortfolioReport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReportService {

    @Autowired
    private FdAccountRepository fdAccountRepository;

    @Autowired
    private CustomerService customerService;

    public CustomerPortfolioReport getCustomerPortfolioReport(String username) {
        throw new UnsupportedOperationException("Portfolio reports require an FD user reference mapping after the BankingTech schema migration.");
    }

    public BankOperationalReport getBankOperationalReport() {
        List<FdAccount> activeAccounts = fdAccountRepository.findByStatus(FdStatus.ACTIVE);

        int totalActive = activeAccounts.size();
        BigDecimal totalPrincipal = BigDecimal.ZERO;
        BigDecimal totalAccrued = BigDecimal.ZERO;
        BigDecimal sumRate = BigDecimal.ZERO;

        for (FdAccount account : activeAccounts) {
            totalPrincipal = totalPrincipal.add(account.getDepositAmount());
            totalAccrued = totalAccrued.add(account.getAccruedInterest());
            sumRate = sumRate.add(account.getInterestRate());
        }

        BigDecimal avgRate = BigDecimal.ZERO;
        if (totalActive > 0) {
            avgRate = sumRate.divide(BigDecimal.valueOf(totalActive), 2, RoundingMode.HALF_UP);
        }

        LocalDate today = LocalDate.now();
        LocalDate target = today.plusDays(30);

        List<FdAccount> maturingSoon = activeAccounts.stream()
                .filter(a -> !a.getMaturityDate().isBefore(today) && !a.getMaturityDate().isAfter(target))
                .collect(Collectors.toList());

        return BankOperationalReport.builder()
                .totalActiveFds(totalActive)
                .totalPrincipalDeposits(totalPrincipal.setScale(2, RoundingMode.HALF_UP))
                .totalAccruedInterestLiability(totalAccrued.setScale(2, RoundingMode.HALF_UP))
                .averageInterestRate(avgRate)
                .maturingSoonAccounts(maturingSoon)
                .build();
    }
}
