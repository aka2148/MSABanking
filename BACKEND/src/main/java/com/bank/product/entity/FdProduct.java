package com.bank.product.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "fd_products")
public class FdProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String productName;

    @Column(nullable = false)
    private BigDecimal minAmount;

    @Column(nullable = false)
    private BigDecimal maxAmount;

    @Column(nullable = false)
    private Integer minTermDays;

    @Column(nullable = false)
    private Integer maxTermDays;

    @Column(nullable = false)
    private BigDecimal baseInterestRate; // e.g. 6.50 for 6.5%

    @Column(nullable = false)
    private boolean isActive = true;

    // Constructors
    public FdProduct() {
    }

    public FdProduct(Long id, String productName, BigDecimal minAmount, BigDecimal maxAmount, Integer minTermDays, Integer maxTermDays, BigDecimal baseInterestRate, boolean isActive) {
        this.id = id;
        this.productName = productName;
        this.minAmount = minAmount;
        this.maxAmount = maxAmount;
        this.minTermDays = minTermDays;
        this.maxTermDays = maxTermDays;
        this.baseInterestRate = baseInterestRate;
        this.isActive = isActive;
    }

    // Getters and Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public BigDecimal getMinAmount() {
        return minAmount;
    }

    public void setMinAmount(BigDecimal minAmount) {
        this.minAmount = minAmount;
    }

    public BigDecimal getMaxAmount() {
        return maxAmount;
    }

    public void setMaxAmount(BigDecimal maxAmount) {
        this.maxAmount = maxAmount;
    }

    public Integer getMinTermDays() {
        return minTermDays;
    }

    public void setMinTermDays(Integer minTermDays) {
        this.minTermDays = minTermDays;
    }

    public Integer getMaxTermDays() {
        return maxTermDays;
    }

    public void setMaxTermDays(Integer maxTermDays) {
        this.maxTermDays = maxTermDays;
    }

    public BigDecimal getBaseInterestRate() {
        return baseInterestRate;
    }

    public void setBaseInterestRate(BigDecimal baseInterestRate) {
        this.baseInterestRate = baseInterestRate;
    }

    public boolean isActive() {
        return isActive;
    }

    public void setActive(boolean active) {
        isActive = active;
    }

    // Custom Builder
    public static FdProductBuilder builder() {
        return new FdProductBuilder();
    }

    public static class FdProductBuilder {
        private Long id;
        private String productName;
        private BigDecimal minAmount;
        private BigDecimal maxAmount;
        private Integer minTermDays;
        private Integer maxTermDays;
        private BigDecimal baseInterestRate;
        private boolean isActive = true;

        public FdProductBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public FdProductBuilder productName(String productName) {
            this.productName = productName;
            return this;
        }

        public FdProductBuilder minAmount(BigDecimal minAmount) {
            this.minAmount = minAmount;
            return this;
        }

        public FdProductBuilder maxAmount(BigDecimal maxAmount) {
            this.maxAmount = maxAmount;
            return this;
        }

        public FdProductBuilder minTermDays(Integer minTermDays) {
            this.minTermDays = minTermDays;
            return this;
        }

        public FdProductBuilder maxTermDays(Integer maxTermDays) {
            this.maxTermDays = maxTermDays;
            return this;
        }

        public FdProductBuilder baseInterestRate(BigDecimal baseInterestRate) {
            this.baseInterestRate = baseInterestRate;
            return this;
        }

        public FdProductBuilder isActive(boolean isActive) {
            this.isActive = isActive;
            return this;
        }

        public FdProduct build() {
            return new FdProduct(id, productName, minAmount, maxAmount, minTermDays, maxTermDays, baseInterestRate, isActive);
        }
    }
}
