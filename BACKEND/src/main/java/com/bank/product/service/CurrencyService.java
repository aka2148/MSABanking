package com.bank.product.service;

import com.bank.common.exception.ResourceNotFoundException;
import com.bank.product.entity.Currency;
import com.bank.product.repository.CurrencyRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class CurrencyService {

    @Autowired
    private CurrencyRepository currencyRepository;

    @Transactional
    public Currency createCurrency(Currency currency) {
        if (currencyRepository.existsByIsoCode(currency.getIsoCode())) {
            throw new RuntimeException("Currency already exists for ISO code: " + currency.getIsoCode());
        }
        currency.setUuid(UUID.randomUUID().toString());
        currency.setCreatedDate(LocalDateTime.now());
        currency.setCreatedBy("SYSTEM");
        currency.setCrudValue("C");
        return currencyRepository.save(currency);
    }

    public List<Currency> getAllCurrencies() {
        return currencyRepository.findAll();
    }

    public List<Currency> getActiveCurrencies() {
        return currencyRepository.findByIsActive(true);
    }

    public Currency getCurrencyById(Integer id) {
        return currencyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Currency not found with ID: " + id));
    }

    public Currency getCurrencyByIsoCode(String isoCode) {
        return currencyRepository.findByIsoCode(isoCode)
                .orElseThrow(() -> new ResourceNotFoundException("Currency not found for ISO code: " + isoCode));
    }

    @Transactional
    public Currency updateCurrency(Integer id, Currency updates) {
        Currency existing = getCurrencyById(id);
        if (updates.getCurrencyName() != null) existing.setCurrencyName(updates.getCurrencyName());
        if (updates.getCurrencySymbol() != null) existing.setCurrencySymbol(updates.getCurrencySymbol());
        if (updates.getDecimalPlaces() != null) existing.setDecimalPlaces(updates.getDecimalPlaces());
        if (updates.getIsActive() != null) existing.setIsActive(updates.getIsActive());
        existing.setUpdatedBy("SYSTEM");
        existing.setUpdatedDate(LocalDateTime.now());
        existing.setCrudValue("U");
        return currencyRepository.save(existing);
    }

    @Transactional
    public void deleteCurrency(Integer id) {
        getCurrencyById(id);
        currencyRepository.deleteById(id);
    }
}