package com.bank.fd.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Minimal internal savings account; balances change only through financial services. */
@Entity
@Table(name = "savings_accounts", schema = "public")
public class SavingsAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "sa_account_id") private Long id;
    @Column(name = "sa_account_number", nullable = false, unique = true, length = 50) private String accountNumber;
    @Column(name = "sa_holder_id", nullable = false) private Long holderId;
    @Column(name = "sa_currency_id", nullable = false) private Long currencyId;
    @Column(name = "sa_display_name", nullable = false, length = 80) private String displayName;
    @Column(name = "sa_balance", nullable = false, precision = 18, scale = 4) private BigDecimal balance = BigDecimal.ZERO;
    @Column(name = "sa_status", nullable = false, length = 20) private String status = "ACTIVE";
    @Column(name = "sa_created_date", nullable = false) private OffsetDateTime createdDate;
    @Version @Column(name = "sa_row_version", nullable = false) private Long rowVersion;
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String value) { accountNumber = value; }
    public Long getHolderId() { return holderId; }
    public void setHolderId(Long value) { holderId = value; }
    public Long getCurrencyId() { return currencyId; }
    public void setCurrencyId(Long value) { currencyId = value; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String value) { displayName = value; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal value) { balance = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public OffsetDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(OffsetDateTime value) { createdDate = value; }
}
