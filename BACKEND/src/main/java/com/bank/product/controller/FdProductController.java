package com.bank.product.controller;

import com.bank.product.entity.FdProduct;
import com.bank.product.service.FdProductService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/product")
public class FdProductController {

    @Autowired
    private FdProductService fdProductService;

    @GetMapping
    public ResponseEntity<List<FdProduct>> getActiveProducts() {
        return ResponseEntity.ok(fdProductService.getActiveProducts());
    }

    @GetMapping("/all")
    @PreAuthorize("hasAnyRole('BANK_OFFICER', 'ADMIN')")
    public ResponseEntity<List<FdProduct>> getAllProducts() {
        return ResponseEntity.ok(fdProductService.getAllProducts());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FdProduct> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(fdProductService.getProductById(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('BANK_OFFICER', 'ADMIN')")
    public ResponseEntity<FdProduct> createProduct(@Valid @RequestBody FdProduct product) {
        return ResponseEntity.ok(fdProductService.createProduct(product));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('BANK_OFFICER', 'ADMIN')")
    public ResponseEntity<FdProduct> updateProduct(@PathVariable Long id, @Valid @RequestBody FdProduct product) {
        return ResponseEntity.ok(fdProductService.updateProduct(id, product));
    }
}
