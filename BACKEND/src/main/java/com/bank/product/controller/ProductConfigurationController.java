package com.bank.product.controller;

import com.bank.product.dto.request.ProductConfigurationRequest;
import com.bank.product.dto.request.ProductPricingPreviewRequest;
import com.bank.product.dto.response.ProductBuilderMetadataResponse;
import com.bank.product.dto.response.ProductConfigurationResponse;
import com.bank.product.dto.response.ProductPricingPreviewResponse;
import com.bank.product.dto.response.ValidationResponse;
import com.bank.product.service.ProductConfigurationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/product-configurations")
@PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
public class ProductConfigurationController {
    private final ProductConfigurationService service;

    public ProductConfigurationController(ProductConfigurationService service) {
        this.service = service;
    }

    @GetMapping("/metadata")
    public ProductBuilderMetadataResponse metadata(@RequestParam(required = false) Integer categoryId) {
        return service.metadata(categoryId);
    }

    @GetMapping
    public List<ProductConfigurationResponse> list() {
        return service.list();
    }

    @PostMapping("/validate")
    public ValidationResponse validate(@Valid @RequestBody ProductConfigurationRequest request) {
        return service.validate(request);
    }

    @PostMapping("/preview")
    public ProductPricingPreviewResponse preview(@Valid @RequestBody ProductPricingPreviewRequest request) {
        return service.preview(request);
    }

    @PostMapping
    public ResponseEntity<ProductConfigurationResponse> create(@Valid @RequestBody ProductConfigurationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @PostMapping("/{productId}/activate")
    public ProductConfigurationResponse activate(@PathVariable Integer productId) {
        return service.activate(productId);
    }
}
