package com.bank.fd.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Local currency reference identity used by FD foreign keys. */
@Entity
@Table(name = "fd_currency_ref", schema = "public")
public class FdCurrencyReference {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fcr_currency_id")
    private Long id;

    @Column(name = "fcr_iso_code", nullable = false, length = 10)
    private String isoCode;

    @Column(name = "fcr_currency_name", length = 255)
    private String currencyName;

    @Column(name = "fcr_decimal_places")
    private Integer decimalPlaces;

    public Long getId() { return id; }
    public String getIsoCode() { return isoCode; }
    public String getCurrencyName() { return currencyName; }
    public Integer getDecimalPlaces() { return decimalPlaces; }
}
