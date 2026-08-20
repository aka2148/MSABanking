package com.bank.reports.dto;

import com.bank.fd.entity.FdAccount;

import java.math.BigDecimal;
import java.util.List;

public class CustomerPortfolioReport {
    private String customerUsername;
    private String fullName;
    private int activeCount;
    private BigDecimal totalDepositAmount;
    private BigDecimal totalAccruedInterest;
    private BigDecimal totalProjectedMaturityAmount;
    private List<FdAccount> accounts;

    // Constructors
    public CustomerPortfolioReport() {
    }

    public CustomerPortfolioReport(String customerUsername, String fullName, int activeCount, BigDecimal totalDepositAmount, BigDecimal totalAccruedInterest, BigDecimal totalProjectedMaturityAmount, List<FdAccount> accounts) {
        this.customerUsername = customerUsername;
        this.fullName = fullName;
        this.activeCount = activeCount;
        this.totalDepositAmount = totalDepositAmount;
        this.totalAccruedInterest = totalAccruedInterest;
        this.totalProjectedMaturityAmount = totalProjectedMaturityAmount;
        this.accounts = accounts;
    }

    // Getters and Setters
    public String getCustomerUsername() {
        return customerUsername;
    }

    public void setCustomerUsername(String customerUsername) {
        this.customerUsername = customerUsername;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public int getActiveCount() {
        return activeCount;
    }

    public void setActiveCount(int activeCount) {
        this.activeCount = activeCount;
    }

    public BigDecimal getTotalDepositAmount() {
        return totalDepositAmount;
    }

    public void setTotalDepositAmount(BigDecimal totalDepositAmount) {
        this.totalDepositAmount = totalDepositAmount;
    }

    public BigDecimal getTotalAccruedInterest() {
        return totalAccruedInterest;
    }

    public void setTotalAccruedInterest(BigDecimal totalAccruedInterest) {
        this.totalAccruedInterest = totalAccruedInterest;
    }

    public BigDecimal getTotalProjectedMaturityAmount() {
        return totalProjectedMaturityAmount;
    }

    public void setTotalProjectedMaturityAmount(BigDecimal totalProjectedMaturityAmount) {
        this.totalProjectedMaturityAmount = totalProjectedMaturityAmount;
    }

    public List<FdAccount> getAccounts() {
        return accounts;
    }

    public void setAccounts(List<FdAccount> accounts) {
        this.accounts = accounts;
    }

    // Custom Builder
    public static CustomerPortfolioReportBuilder builder() {
        return new CustomerPortfolioReportBuilder();
    }

    public static class CustomerPortfolioReportBuilder {
        private String customerUsername;
        private String fullName;
        private int activeCount;
        private BigDecimal totalDepositAmount;
        private BigDecimal totalAccruedInterest;
        private BigDecimal totalProjectedMaturityAmount;
        private List<FdAccount> accounts;

        public CustomerPortfolioReportBuilder customerUsername(String customerUsername) {
            this.customerUsername = customerUsername;
            return this;
        }

        public CustomerPortfolioReportBuilder fullName(String fullName) {
            this.fullName = fullName;
            return this;
        }

        public CustomerPortfolioReportBuilder activeCount(int activeCount) {
            this.activeCount = activeCount;
            return this;
        }

        public CustomerPortfolioReportBuilder totalDepositAmount(BigDecimal totalDepositAmount) {
            this.totalDepositAmount = totalDepositAmount;
            return this;
        }

        public CustomerPortfolioReportBuilder totalAccruedInterest(BigDecimal totalAccruedInterest) {
            this.totalAccruedInterest = totalAccruedInterest;
            return this;
        }

        public CustomerPortfolioReportBuilder totalProjectedMaturityAmount(BigDecimal totalProjectedMaturityAmount) {
            this.totalProjectedMaturityAmount = totalProjectedMaturityAmount;
            return this;
        }

        public CustomerPortfolioReportBuilder accounts(List<FdAccount> accounts) {
            this.accounts = accounts;
            return this;
        }

        public CustomerPortfolioReport build() {
            return new CustomerPortfolioReport(customerUsername, fullName, activeCount, totalDepositAmount, totalAccruedInterest, totalProjectedMaturityAmount, accounts);
        }
    }
}
