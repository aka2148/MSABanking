package com.bank.product.service;

import com.bank.common.exception.ResourceNotFoundException;
import com.bank.product.entity.ProductCategory;
import com.bank.product.repository.ProductCategoryRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class ProductCategoryService {

    @Autowired
    private ProductCategoryRepository productCategoryRepository;

    @Transactional
    public ProductCategory createCategory(ProductCategory category) {
        if (productCategoryRepository.existsByCategoryCode(category.getCategoryCode())) {
            throw new RuntimeException("Category code already exists: " + category.getCategoryCode());
        }
        category.setUuid(UUID.randomUUID().toString());
        category.setCreatedDate(LocalDateTime.now());
        category.setCreatedBy("SYSTEM");
        category.setCrudValue("C");
        return productCategoryRepository.save(category);
    }

    public List<ProductCategory> getAllCategories() {
        return productCategoryRepository.findAll();
    }

    public List<ProductCategory> getTopLevelCategories() {
        return productCategoryRepository.findByParentCategoryIdIsNull();
    }

    public List<ProductCategory> getChildCategories(Integer parentCategoryId) {
        return productCategoryRepository.findByParentCategoryId(parentCategoryId);
    }

    public ProductCategory getCategoryById(Integer id) {
        return productCategoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product category not found with ID: " + id));
    }

    @Transactional
    public ProductCategory updateCategory(Integer id, ProductCategory updates) {
        ProductCategory existing = getCategoryById(id);
        if (updates.getCategoryName() != null) existing.setCategoryName(updates.getCategoryName());
        if (updates.getCategoryCode() != null) existing.setCategoryCode(updates.getCategoryCode());
        if (updates.getParentCategoryId() != null) existing.setParentCategoryId(updates.getParentCategoryId());
        existing.setUpdatedBy("SYSTEM");
        existing.setUpdatedDate(LocalDateTime.now());
        existing.setCrudValue("U");
        return productCategoryRepository.save(existing);
    }

    @Transactional
    public void deleteCategory(Integer id) {
        getCategoryById(id);
        productCategoryRepository.deleteById(id);
    }
}