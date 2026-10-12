package com.bank.product.service;

import com.bank.common.exception.ResourceNotFoundException;
import com.bank.product.entity.ProductCurrencyConfig;
import com.bank.product.repository.ProductCurrencyConfigRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ProductCurrencyConfigService {

    @Autowired
    private ProductCurrencyConfigRepository productCurrencyConfigRepository;

    @Autowired
    private ProductConfigurationIntegrityService integrity;

    @Transactional
    public ProductCurrencyConfig createConfig(ProductCurrencyConfig config) {
        integrity.assertEditable(config.getProductId());
        if (productCurrencyConfigRepository.existsByProductIdAndCurrencyId(
                config.getProductId(), config.getCurrencyId())) {
            throw new RuntimeException("Currency config already exists for this product and currency");
        }
        config.setUuid(UUID.randomUUID().toString());
        config.setCreatedDate(LocalDateTime.now());
        config.setCreatedBy("SYSTEM");
        config.setCrudValue("C");
        return productCurrencyConfigRepository.save(config);
    }

    public List<ProductCurrencyConfig> getConfigsByProductId(Integer productId) {
        return productCurrencyConfigRepository.findByProductId(productId);
    }

    public ProductCurrencyConfig getConfigById(Integer id) {
        return productCurrencyConfigRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product currency config not found with ID: " + id));
    }

    @Transactional
    public ProductCurrencyConfig updateConfig(Integer id, ProductCurrencyConfig updates) {
        ProductCurrencyConfig existing = getConfigById(id);
        integrity.assertEditable(existing.getProductId());
        if (updates.getIsBaseCurrency() != null) existing.setIsBaseCurrency(updates.getIsBaseCurrency());
        if (updates.getWalletEnabled() != null) existing.setWalletEnabled(updates.getWalletEnabled());
        if (updates.getMinimumAmount() != null) existing.setMinimumAmount(updates.getMinimumAmount());
        if (updates.getMaximumAmount() != null) existing.setMaximumAmount(updates.getMaximumAmount());
        existing.setUpdatedBy("SYSTEM");
        existing.setUpdatedDate(LocalDateTime.now());
        existing.setCrudValue("U");
        return productCurrencyConfigRepository.save(existing);
    }

    @Transactional
    public void deleteConfig(Integer id) {
        ProductCurrencyConfig existing = getConfigById(id);
        integrity.assertEditable(existing.getProductId());
        productCurrencyConfigRepository.deleteById(id);
    }
}