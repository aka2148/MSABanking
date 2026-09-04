package com.bank.fd.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Maps public.fd_transactions from the BankingTech PostgreSQL schema. */
@Entity
@Table(name = "fd_transactions", schema = "public")
public class FdTransaction {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "ft_transaction_id") private Long id;
    @Column(name = "ft_account_id", nullable = false) private Long accountId;
    @Column(name = "ft_txn_type_id", nullable = false) private Long transactionTypeId;
    @Column(name = "ft_amount", nullable = false, precision = 18, scale = 4) private BigDecimal amount;
    @Column(name = "ft_txn_date", nullable = false) private OffsetDateTime transactionDate;
    @Column(name = "ft_balance_after", precision = 18, scale = 4) private BigDecimal balanceAfter;
    @Column(name = "ft_currency_id", nullable = false) private Long currencyId;
    @Column(name = "ft_remarks", length = 500) private String remarks;
    @Column(name = "ft_uuid", nullable = false, length = 64) private String uuid;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public Long getTransactionTypeId() { return transactionTypeId; }
    public void setTransactionTypeId(Long transactionTypeId) { this.transactionTypeId = transactionTypeId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public OffsetDateTime getTransactionDate() { return transactionDate; }
    public void setTransactionDate(OffsetDateTime transactionDate) { this.transactionDate = transactionDate; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(BigDecimal balanceAfter) { this.balanceAfter = balanceAfter; }
    public Long getCurrencyId() { return currencyId; }
    public void setCurrencyId(Long currencyId) { this.currencyId = currencyId; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    @PrePersist
    void initializeUuid() { if (uuid == null) uuid = java.util.UUID.randomUUID().toString(); }
}
