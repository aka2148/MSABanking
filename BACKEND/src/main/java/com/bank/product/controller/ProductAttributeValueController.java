package com.bank.product.controller;

import com.bank.product.dto.request.ProductAttributeValueRequest;
import com.bank.product.dto.response.ProductAttributeValueResponse;
import com.bank.product.service.ProductAttributeValueService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products/{productId}/attribute-values")
public class ProductAttributeValueController {

    private final ProductAttributeValueService service;

    public ProductAttributeValueController(ProductAttributeValueService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductAttributeValueResponse> create(
            @PathVariable @Positive Integer productId,
            @Valid @RequestBody ProductAttributeValueRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(productId, r, r.getAttributeDefId()));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public List<ProductAttributeValueResponse> list(
            @PathVariable @Positive Integer productId) {
        return service.list(productId);
    }

    @GetMapping("/{attributeDefId}")
    @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public ProductAttributeValueResponse get(
            @PathVariable @Positive Integer productId,
            @PathVariable @Positive Integer attributeDefId) {
        return service.get(productId, attributeDefId);
    }

    @PutMapping("/{attributeDefId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductAttributeValueResponse update(
            @PathVariable @Positive Integer productId,
            @PathVariable @Positive Integer attributeDefId,
            @Valid @RequestBody ProductAttributeValueRequest r) {
        return service.update(productId, attributeDefId, r);
    }

    @DeleteMapping("/{attributeDefId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable @Positive Integer productId,
            @PathVariable @Positive Integer attributeDefId) {
        service.delete(productId, attributeDefId);
        return ResponseEntity.noContent().build();
    }
}