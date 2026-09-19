package com.bank.fd.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/** Maps public.fd_accounts from the BankingTech PostgreSQL schema. */
@Entity
@Table(name = "fd_accounts", schema = "public")
public class FdAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fa_account_id") private Long id;
    @Column(name = "fa_account_number", nullable = false, unique = true, length = 50) private String accountNumber;
    @Column(name = "fa_primary_holder_id", nullable = false) private Long primaryHolderId;
    @Column(name = "fa_product_id", nullable = false) private Long productId;
    @Column(name = "fa_applied_rate_outcome_id") private Long appliedRateOutcomeId;
    @Column(name = "fa_principal_amount", nullable = false, precision = 18, scale = 4) private BigDecimal principalAmount;
    @Column(name = "fa_currency_id", nullable = false) private Long currencyId;
    @Column(name = "fa_tenure_months", nullable = false) private Integer tenureMonths;
    @Column(name = "fa_interest_rate", precision = 9, scale = 4) private BigDecimal interestRate;
    @Column(name = "fa_compounding_frequency", length = 30) private String compoundingFrequency;
    @Column(name = "fa_payout_frequency", length = 30) private String payoutFrequency;
    @Column(name = "fa_auto_renewal", nullable = false) private boolean autoRenewal;
    @Column(name = "fa_open_date", nullable = false) private OffsetDateTime openDate;
    @Column(name = "fa_maturity_date") private OffsetDateTime maturityDate;
    @Column(name = "fa_maturity_amount", precision = 18, scale = 4) private BigDecimal maturityAmount;
    @Column(name = "fa_policy_version", nullable = false, length = 50) private String policyVersion = "academic-v1";
    @Enumerated(EnumType.STRING) @Column(name = "fa_status", nullable = false, length = 30) private FdStatus status = FdStatus.PENDING;
    @Column(name = "fa_created_by", length = 255) private String createdBy;
    @Column(name = "fa_created_date", nullable = false) private OffsetDateTime createdDate;
    @Column(name = "fa_updated_by", length = 255) private String updatedBy;
    @Column(name = "fa_updated_date") private OffsetDateTime updatedDate;
    @Column(name = "fa_version", length = 50) private String version;
    @Column(name = "fa_uuid", nullable = false, length = 64) private String uuid;
    @Version @Column(name = "fa_row_version", nullable = false) private Long rowVersion;

    /* Accrual entries now live in fd_interest_accrual, not on fd_accounts. */
    @Transient private BigDecimal accruedInterest = BigDecimal.ZERO;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }
    public Long getPrimaryHolderId() { return primaryHolderId; }
    public void setPrimaryHolderId(Long primaryHolderId) { this.primaryHolderId = primaryHolderId; }
    public Long getCustomerId() { return primaryHolderId; }
    public void setCustomerId(Long customerId) { this.primaryHolderId = customerId; }
    public Long getProductId() { return productId; }
    public void setProductId(Long productId) { this.productId = productId; }
    public Long getAppliedRateOutcomeId() { return appliedRateOutcomeId; }
    public void setAppliedRateOutcomeId(Long appliedRateOutcomeId) { this.appliedRateOutcomeId = appliedRateOutcomeId; }
    public BigDecimal getPrincipalAmount() { return principalAmount; }
    public void setPrincipalAmount(BigDecimal principalAmount) { this.principalAmount = principalAmount; }
    public BigDecimal getDepositAmount() { return principalAmount; }
    public void setDepositAmount(BigDecimal depositAmount) { this.principalAmount = depositAmount; }
    public Long getCurrencyId() { return currencyId; }
    public void setCurrencyId(Long currencyId) { this.currencyId = currencyId; }
    public Integer getTenureMonths() { return tenureMonths; }
    public void setTenureMonths(Integer tenureMonths) { this.tenureMonths = tenureMonths; }
    public Integer getTermDays() { return tenureMonths == null ? null : tenureMonths * 30; }
    public void setTermDays(Integer termDays) {
        if (termDays == null) {
            this.tenureMonths = null;
            return;
        }
        if (termDays <= 0 || termDays % 30 != 0) {
            throw new IllegalArgumentException("Legacy term days must be a positive whole number of 30-day months");
        }
        this.tenureMonths = termDays / 30;
    }
    public BigDecimal getInterestRate() { return interestRate; }
    public void setInterestRate(BigDecimal interestRate) { this.interestRate = interestRate; }
    public String getCompoundingFrequency() { return compoundingFrequency; }
    public void setCompoundingFrequency(String compoundingFrequency) { this.compoundingFrequency = compoundingFrequency; }
    public String getPayoutFrequency() { return payoutFrequency; }
    public void setPayoutFrequency(String payoutFrequency) { this.payoutFrequency = payoutFrequency; }
    public boolean isAutoRenewal() { return autoRenewal; }
    public void setAutoRenewal(boolean autoRenewal) { this.autoRenewal = autoRenewal; }
    public OffsetDateTime getOpenDate() { return openDate; }
    public void setOpenDate(OffsetDateTime openDate) { this.openDate = openDate; }
    public LocalDate getBookingDate() { return openDate == null ? null : openDate.toLocalDate(); }
    public void setBookingDate(LocalDate bookingDate) { this.openDate = bookingDate == null ? null : bookingDate.atStartOfDay().atOffset(ZoneOffset.UTC); }
    public LocalDate getMaturityDate() { return maturityDate == null ? null : maturityDate.toLocalDate(); }
    public void setMaturityDate(LocalDate maturityDate) { this.maturityDate = maturityDate == null ? null : maturityDate.atStartOfDay().atOffset(ZoneOffset.UTC); }
    public OffsetDateTime getMaturityDateTime() { return maturityDate; }
    public void setMaturityDateTime(OffsetDateTime maturityDate) { this.maturityDate = maturityDate; }
    public BigDecimal getMaturityAmount() { return maturityAmount; }
    public void setMaturityAmount(BigDecimal maturityAmount) { this.maturityAmount = maturityAmount; }
    public String getPolicyVersion() { return policyVersion; }
    public void setPolicyVersion(String policyVersion) { this.policyVersion = policyVersion; }
    public FdStatus getStatus() { return status; }
    public Long getRowVersion() { return rowVersion; }
    public BigDecimal getAccruedInterest() { return accruedInterest; }
    public void setAccruedInterest(BigDecimal accruedInterest) { this.accruedInterest = accruedInterest; }
    @PrePersist
    void initializeMetadata() {
        if (uuid == null) uuid = java.util.UUID.randomUUID().toString();
        if (createdDate == null) createdDate = OffsetDateTime.now(ZoneOffset.UTC);
        if (openDate == null) openDate = OffsetDateTime.now(ZoneOffset.UTC);
    }
}
