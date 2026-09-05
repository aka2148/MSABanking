package com.bank.product.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "currency")
public class Currency {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cur_currency_id")
    private Integer currencyId;

    @Column(name = "cur_iso_code", nullable = false, unique = true, length = 3)
    private String isoCode;

    @Column(name = "cur_iso_numeric_code", length = 3)
    private String isoNumericCode;

    @Column(name = "cur_currency_name", nullable = false, length = 64)
    private String currencyName;

    @Column(name = "cur_currency_symbol", length = 8)
    private String currencySymbol;

    @Column(name = "cur_decimal_places", nullable = false)
    private Integer decimalPlaces = 2;

    @Column(name = "cur_is_active", nullable = false)
    private Boolean isActive = true;

    @Column(name = "cur_created_by", length = 100)
    private String createdBy;

    @Column(name = "cur_created_date")
    private LocalDateTime createdDate;

    @Column(name = "cur_updated_by", length = 100)
    private String updatedBy;

    @Column(name = "cur_updated_date")
    private LocalDateTime updatedDate;

    @Column(name = "cur_version", length = 20)
    private String version;

    @Column(name = "cur_uuid", length = 64)
    private String uuid;

    @Column(name = "cur_crud_value", length = 10)
    private String crudValue;

    public Currency() {}

    public Integer getCurrencyId() { return currencyId; }
    public void setCurrencyId(Integer currencyId) { this.currencyId = currencyId; }

    public String getIsoCode() { return isoCode; }
    public void setIsoCode(String isoCode) { this.isoCode = isoCode; }

    public String getIsoNumericCode() { return isoNumericCode; }
    public void setIsoNumericCode(String isoNumericCode) { this.isoNumericCode = isoNumericCode; }

    public String getCurrencyName() { return currencyName; }
    public void setCurrencyName(String currencyName) { this.currencyName = currencyName; }

    public String getCurrencySymbol() { return currencySymbol; }
    public void setCurrencySymbol(String currencySymbol) { this.currencySymbol = currencySymbol; }

    public Integer getDecimalPlaces() { return decimalPlaces; }
    public void setDecimalPlaces(Integer decimalPlaces) { this.decimalPlaces = decimalPlaces; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public LocalDateTime getCreatedDate() { return createdDate; }
    public void setCreatedDate(LocalDateTime createdDate) { this.createdDate = createdDate; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public LocalDateTime getUpdatedDate() { return updatedDate; }
    public void setUpdatedDate(LocalDateTime updatedDate) { this.updatedDate = updatedDate; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getUuid() { return uuid; }
    public void setUuid(String uuid) { this.uuid = uuid; }

    public String getCrudValue() { return crudValue; }
    public void setCrudValue(String crudValue) { this.crudValue = crudValue; }
}