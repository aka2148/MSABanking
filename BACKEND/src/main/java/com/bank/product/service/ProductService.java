package com.bank.product.service;

import com.bank.common.exception.InvalidInputException;
import com.bank.common.exception.ResourceNotFoundException;
import com.bank.product.entity.Product;
import com.bank.product.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ProductService {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductConfigurationService productConfigurationService;

    @Transactional
    public Product createProduct(Product product) {
        if (productRepository.existsByProductCode(product.getProductCode())) {
            throw new RuntimeException("Product code already exists: " + product.getProductCode());
        }
        product.setUuid(UUID.randomUUID().toString());
        product.setCreatedDate(LocalDateTime.now());
        product.setCreatedBy("SYSTEM");
        product.setCrudValue("C");
        product.setStatus("DRAFT");
        return productRepository.save(product);
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public List<Product> getProductsByStatus(String status) {
        return productRepository.findByStatus(status);
    }

    public List<Product> getProductsByCategory(Integer categoryId) {
        return productRepository.findByCategoryId(categoryId);
    }

    public Product getProductById(Integer id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + id));
    }

    public Product getActiveProductByCode(String productCode) {
        return productRepository.findByProductCodeAndStatus(productCode, "ACTIVE")
                .orElseThrow(() -> new ResourceNotFoundException("Active product not found for code: " + productCode));
    }

    @Transactional
    public Product updateProduct(Integer id, Product updates) {
        Product existing = getProductById(id);
        productConfigurationService.assertEditable(id);
        if (updates.getProductName() != null) existing.setProductName(updates.getProductName());
        if (updates.getProductCode() != null) existing.setProductCode(updates.getProductCode());
        if (updates.getCategoryId() != null) existing.setCategoryId(updates.getCategoryId());
        if (updates.getBaseCurrencyCode() != null) existing.setBaseCurrencyCode(updates.getBaseCurrencyCode());
        if (updates.getEffectiveFrom() != null) existing.setEffectiveFrom(updates.getEffectiveFrom());
        if (updates.getEffectiveTo() != null) existing.setEffectiveTo(updates.getEffectiveTo());
        if (updates.getDescription() != null) existing.setDescription(updates.getDescription());
        existing.setUpdatedBy("SYSTEM");
        existing.setUpdatedDate(LocalDateTime.now());
        existing.setCrudValue("U");
        return productRepository.save(existing);
    }

    @Transactional
    public Product updateStatus(Integer id, String status) {
        if (status == null || status.isBlank()) {
            throw new InvalidInputException("Product status is required");
        }
        String requested = status.trim().toUpperCase(java.util.Locale.ROOT);
        Product existing = getProductById(id);
        if ("ACTIVE".equals(requested)) {
            productConfigurationService.activate(id);
            return getProductById(id);
        }
        if (requested.equalsIgnoreCase(existing.getStatus())) return existing;
        if (!"INACTIVE".equals(requested)) {
            throw new InvalidInputException("Unsupported product status transition; use ACTIVE or INACTIVE");
        }
        if (!"ACTIVE".equalsIgnoreCase(existing.getStatus())) {
            throw new InvalidInputException("Only an ACTIVE product can transition to INACTIVE");
        }
        existing.setStatus("INACTIVE");
        existing.setUpdatedBy("SYSTEM");
        existing.setUpdatedDate(LocalDateTime.now());
        existing.setCrudValue("U");
        return productRepository.save(existing);
    }

    @Transactional
    public void deleteProduct(Integer id) {
        getProductById(id);
        productConfigurationService.assertEditable(id);
        productRepository.deleteById(id);
    }
}