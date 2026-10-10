package com.bank.fd.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Maps public.fd_interest_accrual from the BankingTech PostgreSQL schema. */
@Entity
@Table(name = "fd_interest_accrual", schema = "public")
public class FdInterestAccrual {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) @Column(name = "fia_accrual_id") private Long id;
    @Column(name = "fia_account_id", nullable = false) private Long accountId;
    @Column(name = "fia_accrual_date", nullable = false) private OffsetDateTime accrualDate;
    @Column(name = "fia_accrued_amount", nullable = false, precision = 22, scale = 8) private BigDecimal accruedAmount;
    @Column(name = "fia_cumulative_accrued", precision = 22, scale = 8) private BigDecimal cumulativeAccrued;
    @Column(name = "fia_compounding_balance", precision = 22, scale = 8) private BigDecimal compoundingBalance;
    @Column(name = "fia_capitalized_amount", nullable = false, precision = 22, scale = 8) private BigDecimal capitalizedAmount = BigDecimal.ZERO;
    @Column(name = "fia_currency_id", nullable = false) private Long currencyId;
    @Column(name = "fia_uuid", nullable = false, length = 64) private String uuid;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public OffsetDateTime getAccrualDate() { return accrualDate; }
    public void setAccrualDate(OffsetDateTime accrualDate) { this.accrualDate = accrualDate; }
    public BigDecimal getAccruedAmount() { return accruedAmount; }
    public void setAccruedAmount(BigDecimal accruedAmount) { this.accruedAmount = accruedAmount; }
    public BigDecimal getCumulativeAccrued() { return cumulativeAccrued; }
    public void setCumulativeAccrued(BigDecimal cumulativeAccrued) { this.cumulativeAccrued = cumulativeAccrued; }
    public BigDecimal getCompoundingBalance() { return compoundingBalance; }
    public void setCompoundingBalance(BigDecimal compoundingBalance) { this.compoundingBalance = compoundingBalance; }
    public BigDecimal getCapitalizedAmount() { return capitalizedAmount; }
    public void setCapitalizedAmount(BigDecimal capitalizedAmount) { this.capitalizedAmount = capitalizedAmount; }
    public Long getCurrencyId() { return currencyId; }
    public void setCurrencyId(Long currencyId) { this.currencyId = currencyId; }
    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }
    @PrePersist
    void initializeUuid() { if (uuid == null) uuid = java.util.UUID.randomUUID().toString(); }
}
