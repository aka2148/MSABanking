package com.bank.fd.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "fd_accounts")
public class FdAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String accountNumber;

    @Column(nullable = false)
    private Long customerId; // Reference to CustomerProfile.id

    @Column(nullable = false)
    private Long productId; // Reference to FdProduct.id

    @Column(nullable = false)
    private BigDecimal depositAmount;

    @Column(nullable = false)
    private BigDecimal interestRate; // Locked-in rate at creation (inclusive of bonus)

    @Column(nullable = false)
    private LocalDate bookingDate;

    @Column(nullable = false)
    private LocalDate maturityDate;

    @Column(nullable = false)
    private Integer termDays;

    @Column(nullable = false)
    private String compoundingFrequency; // SIMPLE, MONTHLY, QUARTERLY, YEARLY

    @Column(nullable = false)
    private BigDecimal accruedInterest = BigDecimal.ZERO;

    @Column(nullable = false)
    private BigDecimal maturityAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FdStatus status = FdStatus.ACTIVE;

    // Constructors
    public FdAccount() {
    }

    public FdAccount(Long id, String accountNumber, Long customerId, Long productId, BigDecimal depositAmount, BigDecimal interestRate, LocalDate bookingDate, LocalDate maturityDate, Integer termDays, String compoundingFrequency, BigDecimal accruedInterest, BigDecimal maturityAmount, FdStatus status) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.customerId = customerId;
        this.productId = productId;
        this.depositAmount = depositAmount;
        this.interestRate = interestRate;
        this.bookingDate = bookingDate;
        this.maturityDate = maturityDate;
        this.termDays = termDays;
        this.compoundingFrequency = compoundingFrequency;
        this.accruedInterest = accruedInterest != null ? accruedInterest : BigDecimal.ZERO;
        this.maturityAmount = maturityAmount;
        this.status = status != null ? status : FdStatus.ACTIVE;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public void setAccountNumber(String accountNumber) {
        this.accountNumber = accountNumber;
    }

    public Long getCustomerId() {
        return customerId;
    }

    public void setCustomerId(Long customerId) {
        this.customerId = customerId;
    }

    public Long getProductId() {
        return productId;
    }

    public void setProductId(Long productId) {
        this.productId = productId;
    }

    public BigDecimal getDepositAmount() {
        return depositAmount;
    }

    public void setDepositAmount(BigDecimal depositAmount) {
        this.depositAmount = depositAmount;
    }

    public BigDecimal getInterestRate() {
        return interestRate;
    }

    public void setInterestRate(BigDecimal interestRate) {
        this.interestRate = interestRate;
    }

    public LocalDate getBookingDate() {
        return bookingDate;
    }

    public void setBookingDate(LocalDate bookingDate) {
        this.bookingDate = bookingDate;
    }

    public LocalDate getMaturityDate() {
        return maturityDate;
    }

    public void setMaturityDate(LocalDate maturityDate) {
        this.maturityDate = maturityDate;
    }

    public Integer getTermDays() {
        return termDays;
    }

    public void setTermDays(Integer termDays) {
        this.termDays = termDays;
    }

    public String getCompoundingFrequency() {
        return compoundingFrequency;
    }

    public void setCompoundingFrequency(String compoundingFrequency) {
        this.compoundingFrequency = compoundingFrequency;
    }

    public BigDecimal getAccruedInterest() {
        return accruedInterest;
    }

    public void setAccruedInterest(BigDecimal accruedInterest) {
        this.accruedInterest = accruedInterest;
    }

    public BigDecimal getMaturityAmount() {
        return maturityAmount;
    }

    public void setMaturityAmount(BigDecimal maturityAmount) {
        this.maturityAmount = maturityAmount;
    }

    public FdStatus getStatus() {
        return status;
    }

    public void setStatus(FdStatus status) {
        this.status = status;
    }

    // Custom Builder
    public static FdAccountBuilder builder() {
        return new FdAccountBuilder();
    }

    public static class FdAccountBuilder {
        private Long id;
        private String accountNumber;
        private Long customerId;
        private Long productId;
        private BigDecimal depositAmount;
        private BigDecimal interestRate;
        private LocalDate bookingDate;
        private LocalDate maturityDate;
        private Integer termDays;
        private String compoundingFrequency;
        private BigDecimal accruedInterest = BigDecimal.ZERO;
        private BigDecimal maturityAmount;
        private FdStatus status = FdStatus.ACTIVE;

        public FdAccountBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public FdAccountBuilder accountNumber(String accountNumber) {
            this.accountNumber = accountNumber;
            return this;
        }

        public FdAccountBuilder customerId(Long customerId) {
            this.customerId = customerId;
            return this;
        }

        public FdAccountBuilder productId(Long productId) {
            this.productId = productId;
            return this;
        }

        public FdAccountBuilder depositAmount(BigDecimal depositAmount) {
            this.depositAmount = depositAmount;
            return this;
        }

        public FdAccountBuilder interestRate(BigDecimal interestRate) {
            this.interestRate = interestRate;
            return this;
        }

        public FdAccountBuilder bookingDate(LocalDate bookingDate) {
            this.bookingDate = bookingDate;
            return this;
        }

        public FdAccountBuilder maturityDate(LocalDate maturityDate) {
            this.maturityDate = maturityDate;
            return this;
        }

        public FdAccountBuilder termDays(Integer termDays) {
            this.termDays = termDays;
            return this;
        }

        public FdAccountBuilder compoundingFrequency(String compoundingFrequency) {
            this.compoundingFrequency = compoundingFrequency;
            return this;
        }

        public FdAccountBuilder accruedInterest(BigDecimal accruedInterest) {
            this.accruedInterest = accruedInterest;
            return this;
        }

        public FdAccountBuilder maturityAmount(BigDecimal maturityAmount) {
            this.maturityAmount = maturityAmount;
            return this;
        }

        public FdAccountBuilder status(FdStatus status) {
            this.status = status;
            return this;
        }

        public FdAccount build() {
            return new FdAccount(id, accountNumber, customerId, productId, depositAmount, interestRate, bookingDate, maturityDate, termDays, compoundingFrequency, accruedInterest, maturityAmount, status);
        }
    }
}
