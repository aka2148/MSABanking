package com.bank.product.controller;

import com.bank.product.entity.Currency;
import com.bank.product.service.CurrencyService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/currencies")
public class CurrencyController {

    @Autowired
    private CurrencyService currencyService;

    @PostMapping
    public ResponseEntity<Currency> createCurrency(@RequestBody Currency currency) {
        return new ResponseEntity<>(currencyService.createCurrency(currency), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<Currency>> getAllCurrencies(@RequestParam(required = false) Boolean active) {
        if (Boolean.TRUE.equals(active)) {
            return ResponseEntity.ok(currencyService.getActiveCurrencies());
        }
        return ResponseEntity.ok(currencyService.getAllCurrencies());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Currency> getCurrencyById(@PathVariable Integer id) {
        return ResponseEntity.ok(currencyService.getCurrencyById(id));
    }

    @GetMapping("/by-iso-code/{isoCode}")
    public ResponseEntity<Currency> getCurrencyByIsoCode(@PathVariable String isoCode) {
        return ResponseEntity.ok(currencyService.getCurrencyByIsoCode(isoCode));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Currency> updateCurrency(@PathVariable Integer id, @RequestBody Currency updates) {
        return ResponseEntity.ok(currencyService.updateCurrency(id, updates));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCurrency(@PathVariable Integer id) {
        currencyService.deleteCurrency(id);
        return ResponseEntity.noContent().build();
    }
}