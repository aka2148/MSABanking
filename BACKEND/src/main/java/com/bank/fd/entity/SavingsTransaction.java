package com.bank.fd.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/** One account-side posting belonging to a savings transfer reference. */
@Entity
@Table(name = "savings_transactions", schema = "public")
public class SavingsTransaction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "st_id")
    private Long id;

    @Column(name = "st_reference_id", nullable = false, length = 64)
    private String referenceId;

    @Column(name = "st_account_id", nullable = false)
    private Long accountId;

    @Column(name = "st_entry_type", nullable = false, length = 10)
    private String entryType;

    @Column(name = "st_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "st_currency_id", nullable = false)
    private Long currencyId;

    @Column(name = "st_balance_after", nullable = false, precision = 18, scale = 4)
    private BigDecimal balanceAfter;

    @Column(name = "st_remarks", length = 500)
    private String remarks;

    @Column(name = "st_posted_at", nullable = false)
    private OffsetDateTime postedAt;

    @Column(name = "st_uuid", nullable = false, length = 64)
    private String uuid;

    public Long getId() { return id; }
    public String getReferenceId() { return referenceId; }
    public void setReferenceId(String referenceId) { this.referenceId = referenceId; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getEntryType() { return entryType; }
    public void setEntryType(String entryType) { this.entryType = entryType; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public Long getCurrencyId() { return currencyId; }
    public void setCurrencyId(Long currencyId) { this.currencyId = currencyId; }
    public BigDecimal getBalanceAfter() { return balanceAfter; }
    public void setBalanceAfter(BigDecimal balanceAfter) { this.balanceAfter = balanceAfter; }
    public String getRemarks() { return remarks; }
    public void setRemarks(String remarks) { this.remarks = remarks; }
    public OffsetDateTime getPostedAt() { return postedAt; }
    public void setPostedAt(OffsetDateTime postedAt) { this.postedAt = postedAt; }
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    @PrePersist
    void initializeUuid() {
        if (uuid == null) uuid = UUID.randomUUID().toString();
    }
}
