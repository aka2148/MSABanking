package com.bank.fd.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "fd_transactions")
public class FdTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType transactionType;

    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDateTime transactionDate;

    private String description;

    // Constructors
    public FdTransaction() {
    }

    public FdTransaction(Long id, String accountNumber, TransactionType transactionType, BigDecimal amount, LocalDateTime transactionDate, String description) {
        this.id = id;
        this.accountNumber = accountNumber;
        this.transactionType = transactionType;
        this.amount = amount;
        this.transactionDate = transactionDate;
        this.description = description;
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

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public void setTransactionType(TransactionType transactionType) {
        this.transactionType = transactionType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDateTime getTransactionDate() {
        return transactionDate;
    }

    public void setTransactionDate(LocalDateTime transactionDate) {
        this.transactionDate = transactionDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    // Custom Builder
    public static FdTransactionBuilder builder() {
        return new FdTransactionBuilder();
    }

    public static class FdTransactionBuilder {
        private Long id;
        private String accountNumber;
        private TransactionType transactionType;
        private BigDecimal amount;
        private LocalDateTime transactionDate;
        private String description;

        public FdTransactionBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public FdTransactionBuilder accountNumber(String accountNumber) {
            this.accountNumber = accountNumber;
            return this;
        }

        public FdTransactionBuilder transactionType(TransactionType transactionType) {
            this.transactionType = transactionType;
            return this;
        }

        public FdTransactionBuilder amount(BigDecimal amount) {
            this.amount = amount;
            return this;
        }

        public FdTransactionBuilder transactionDate(LocalDateTime transactionDate) {
            this.transactionDate = transactionDate;
            return this;
        }

        public FdTransactionBuilder description(String description) {
            this.description = description;
            return this;
        }

        public FdTransaction build() {
            return new FdTransaction(id, accountNumber, transactionType, amount, transactionDate, description);
        }
    }
}
