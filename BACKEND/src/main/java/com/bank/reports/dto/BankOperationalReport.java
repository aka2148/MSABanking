package com.bank.reports.dto;

import com.bank.fd.entity.FdAccount;

import java.math.BigDecimal;
import java.util.List;

public class BankOperationalReport {
    private int totalActiveFds;
    private BigDecimal totalPrincipalDeposits;
    private BigDecimal totalAccruedInterestLiability;
    private BigDecimal averageInterestRate;
    private List<FdAccount> maturingSoonAccounts;

    // Constructors
    public BankOperationalReport() {
    }

    public BankOperationalReport(int totalActiveFds, BigDecimal totalPrincipalDeposits, BigDecimal totalAccruedInterestLiability, BigDecimal averageInterestRate, List<FdAccount> maturingSoonAccounts) {
        this.totalActiveFds = totalActiveFds;
        this.totalPrincipalDeposits = totalPrincipalDeposits;
        this.totalAccruedInterestLiability = totalAccruedInterestLiability;
        this.averageInterestRate = averageInterestRate;
        this.maturingSoonAccounts = maturingSoonAccounts;
    }

    // Getters and Setters
    public int getTotalActiveFds() {
        return totalActiveFds;
    }

    public void setTotalActiveFds(int totalActiveFds) {
        this.totalActiveFds = totalActiveFds;
    }

    public BigDecimal getTotalPrincipalDeposits() {
        return totalPrincipalDeposits;
    }

    public void setTotalPrincipalDeposits(BigDecimal totalPrincipalDeposits) {
        this.totalPrincipalDeposits = totalPrincipalDeposits;
    }

    public BigDecimal getTotalAccruedInterestLiability() {
        return totalAccruedInterestLiability;
    }

    public void setTotalAccruedInterestLiability(BigDecimal totalAccruedInterestLiability) {
        this.totalAccruedInterestLiability = totalAccruedInterestLiability;
    }

    public BigDecimal getAverageInterestRate() {
        return averageInterestRate;
    }

    public void setAverageInterestRate(BigDecimal averageInterestRate) {
        this.averageInterestRate = averageInterestRate;
    }

    public List<FdAccount> getMaturingSoonAccounts() {
        return maturingSoonAccounts;
    }

    public void setMaturingSoonAccounts(List<FdAccount> maturingSoonAccounts) {
        this.maturingSoonAccounts = maturingSoonAccounts;
    }

    // Custom Builder
    public static BankOperationalReportBuilder builder() {
        return new BankOperationalReportBuilder();
    }

    public static class BankOperationalReportBuilder {
        private int totalActiveFds;
        private BigDecimal totalPrincipalDeposits;
        private BigDecimal totalAccruedInterestLiability;
        private BigDecimal averageInterestRate;
        private List<FdAccount> maturingSoonAccounts;

        public BankOperationalReportBuilder totalActiveFds(int totalActiveFds) {
            this.totalActiveFds = totalActiveFds;
            return this;
        }

        public BankOperationalReportBuilder totalPrincipalDeposits(BigDecimal totalPrincipalDeposits) {
            this.totalPrincipalDeposits = totalPrincipalDeposits;
            return this;
        }

        public BankOperationalReportBuilder totalAccruedInterestLiability(BigDecimal totalAccruedInterestLiability) {
            this.totalAccruedInterestLiability = totalAccruedInterestLiability;
            return this;
        }

        public BankOperationalReportBuilder averageInterestRate(BigDecimal averageInterestRate) {
            this.averageInterestRate = averageInterestRate;
            return this;
        }

        public BankOperationalReportBuilder maturingSoonAccounts(List<FdAccount> maturingSoonAccounts) {
            this.maturingSoonAccounts = maturingSoonAccounts;
            return this;
        }

        public BankOperationalReport build() {
            return new BankOperationalReport(totalActiveFds, totalPrincipalDeposits, totalAccruedInterestLiability, averageInterestRate, maturingSoonAccounts);
        }
    }
}
