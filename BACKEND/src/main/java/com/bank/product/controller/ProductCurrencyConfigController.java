package com.bank.product.controller;

import com.bank.product.entity.ProductCurrencyConfig;
import com.bank.product.service.ProductCurrencyConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products/{productId}/currency-configs")
public class ProductCurrencyConfigController {

    @Autowired
    private ProductCurrencyConfigService productCurrencyConfigService;

    @PostMapping
    public ResponseEntity<ProductCurrencyConfig> createConfig(
            @PathVariable Integer productId,
            @RequestBody ProductCurrencyConfig config) {
        config.setProductId(productId);
        return new ResponseEntity<>(productCurrencyConfigService.createConfig(config), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<ProductCurrencyConfig>> getConfigsByProduct(@PathVariable Integer productId) {
        return ResponseEntity.ok(productCurrencyConfigService.getConfigsByProductId(productId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductCurrencyConfig> getConfigById(
            @PathVariable Integer productId,
            @PathVariable Integer id) {
        return ResponseEntity.ok(productCurrencyConfigService.getConfigById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ProductCurrencyConfig> updateConfig(
            @PathVariable Integer productId,
            @PathVariable Integer id,
            @RequestBody ProductCurrencyConfig updates) {
        return ResponseEntity.ok(productCurrencyConfigService.updateConfig(id, updates));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteConfig(
            @PathVariable Integer productId,
            @PathVariable Integer id) {
        productCurrencyConfigService.deleteConfig(id);
        return ResponseEntity.noContent().build();
    }
}