package com.bank.product.controller;

import com.bank.product.dto.request.*;
import com.bank.product.dto.response.*;
import com.bank.product.service.AttributeDefinitionService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;

import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/attribute-definitions")
public class AttributeDefinitionController {

    private final AttributeDefinitionService service;

    public AttributeDefinitionController(AttributeDefinitionService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AttributeDefinitionResponse> create(
            @Valid @RequestBody CreateAttributeDefinitionRequest r) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(r));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public List<AttributeDefinitionResponse> list(
            @RequestParam @Positive Integer categoryId) {
        return service.list(categoryId);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public AttributeDefinitionResponse get(
            @PathVariable @Positive Integer id) {
        return service.get(id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public AttributeDefinitionResponse update(
            @PathVariable @Positive Integer id,
            @Valid @RequestBody UpdateAttributeDefinitionRequest r) {
        return service.update(id, r);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(
            @PathVariable @Positive Integer id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
