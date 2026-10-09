package com.bank.product.controller;

import com.bank.product.dto.request.CreateProductRuleRequest;
import com.bank.product.dto.request.UpdateProductRuleRequest;
import com.bank.product.dto.response.ProductRuleResponse;
import com.bank.product.service.ProductRuleService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/products/{productId}/rules")
public class ProductRuleController {

    private final ProductRuleService productRuleService;

    public ProductRuleController(ProductRuleService productRuleService) {
        this.productRuleService = productRuleService;
    }

    @PostMapping
    public ResponseEntity<ProductRuleResponse> createProductRule(
            @PathVariable Integer productId,
            @Valid @RequestBody CreateProductRuleRequest request) {
        return new ResponseEntity<>(productRuleService.createProductRule(productId, request), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<ProductRuleResponse>> getRulesByProductId(@PathVariable Integer productId) {
        return ResponseEntity.ok(productRuleService.getRulesByProductId(productId));
    }

    @GetMapping("/{ruleId}")
    public ResponseEntity<ProductRuleResponse> getProductRuleById(
            @PathVariable Integer productId,
            @PathVariable Integer ruleId) {
        return ResponseEntity.ok(productRuleService.getProductRuleById(productId, ruleId));
    }

    @PutMapping("/{ruleId}")
    public ResponseEntity<ProductRuleResponse> updateProductRule(
            @PathVariable Integer productId,
            @PathVariable Integer ruleId,
            @Valid @RequestBody UpdateProductRuleRequest request) {
        return ResponseEntity.ok(productRuleService.updateProductRule(productId, ruleId, request));
    }

    @PatchMapping("/{ruleId}/status")
    public ResponseEntity<ProductRuleResponse> updateProductRuleStatus(
            @PathVariable Integer productId,
            @PathVariable Integer ruleId,
            @RequestBody Map<String, Boolean> statusBody) {
        return ResponseEntity.ok(productRuleService.updateProductRuleStatus(productId, ruleId, statusBody.get("isActive")));
    }

    @DeleteMapping("/{ruleId}")
    public ResponseEntity<Void> deleteProductRule(
            @PathVariable Integer productId,
            @PathVariable Integer ruleId) {
        productRuleService.deleteProductRule(productId, ruleId);
        return ResponseEntity.noContent().build();
    }
}