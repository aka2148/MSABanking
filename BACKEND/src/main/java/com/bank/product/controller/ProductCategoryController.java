package com.bank.product.controller;

import com.bank.product.entity.ProductCategory;
import com.bank.product.service.ProductCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/product-categories")
public class ProductCategoryController {

    @Autowired
    private ProductCategoryService productCategoryService;

    @PostMapping
    @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public ResponseEntity<ProductCategory> createCategory(@RequestBody ProductCategory category) {
        return new ResponseEntity<>(productCategoryService.createCategory(category), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<ProductCategory>> getAllCategories() {
        return ResponseEntity.ok(productCategoryService.getAllCategories());
    }

    @GetMapping("/top-level")
    public ResponseEntity<List<ProductCategory>> getTopLevelCategories() {
        return ResponseEntity.ok(productCategoryService.getTopLevelCategories());
    }

    @GetMapping("/{id}/children")
    public ResponseEntity<List<ProductCategory>> getChildCategories(@PathVariable Integer id) {
        return ResponseEntity.ok(productCategoryService.getChildCategories(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ProductCategory> getCategoryById(@PathVariable Integer id) {
        return ResponseEntity.ok(productCategoryService.getCategoryById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductCategory> updateCategory(@PathVariable Integer id, @RequestBody ProductCategory updates) {
        return ResponseEntity.ok(productCategoryService.updateCategory(id, updates));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteCategory(@PathVariable Integer id) {
        productCategoryService.deleteCategory(id);
        return ResponseEntity.noContent().build();
    }
}