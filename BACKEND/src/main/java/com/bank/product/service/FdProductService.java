package com.bank.product.service;

import com.bank.common.exception.ResourceNotFoundException;
import com.bank.product.entity.FdProduct;
import com.bank.product.repository.FdProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FdProductService {

    @Autowired
    private FdProductRepository fdProductRepository;

    public FdProduct createProduct(FdProduct product) {
        return fdProductRepository.save(product);
    }

    public List<FdProduct> getAllProducts() {
        return fdProductRepository.findAll();
    }

    public List<FdProduct> getActiveProducts() {
        return fdProductRepository.findByIsActiveTrue();
    }

    public FdProduct getProductById(Long id) {
        return fdProductRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Fixed Deposit Product not found with ID: " + id));
    }

    public FdProduct updateProduct(Long id, FdProduct productDetails) {
        FdProduct product = getProductById(id);
        product.setProductName(productDetails.getProductName());
        product.setMinAmount(productDetails.getMinAmount());
        product.setMaxAmount(productDetails.getMaxAmount());
        product.setMinTermDays(productDetails.getMinTermDays());
        product.setMaxTermDays(productDetails.getMaxTermDays());
        product.setBaseInterestRate(productDetails.getBaseInterestRate());
        product.setActive(productDetails.isActive());
        return fdProductRepository.save(product);
    }
}
