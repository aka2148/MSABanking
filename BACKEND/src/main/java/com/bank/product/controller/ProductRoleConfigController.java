package com.bank.product.controller;

import com.bank.product.dto.request.*;
import com.bank.product.dto.response.*;
import com.bank.product.service.ProductRoleConfigService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/products/{productId}/role-configs")
public class ProductRoleConfigController {

    private final ProductRoleConfigService service;

    public ProductRoleConfigController(ProductRoleConfigService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ProductRoleConfigResponse> create(
            @PathVariable @Positive Integer productId,
            @Valid @RequestBody ProductRoleConfigRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.create(productId, r));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public List<ProductRoleConfigResponse> list(
            @PathVariable @Positive Integer productId) {
        return service.list(productId);
    }

    @PutMapping("/{roleTypeId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProductRoleConfigResponse update(
            @PathVariable @Positive Integer productId,
            @PathVariable @Positive Integer roleTypeId,
            @Valid @RequestBody UpdateProductRoleConfigRequest r) {
        return service.update(productId, roleTypeId, r);
    }

    @DeleteMapping("/{roleTypeId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable @Positive Integer productId,
            @PathVariable @Positive Integer roleTypeId) {
        service.delete(productId, roleTypeId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/validate")
    @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public ValidationResponse validate(
            @PathVariable @Positive Integer productId,
            @Valid @RequestBody ValidateRoleConfigRequest r) {
        return service.validate(productId, r);
    }
}