package com.bank.product.controller;

import com.bank.product.dto.request.CreateChargeConfigRequest;
import com.bank.product.dto.request.UpdateChargeConfigRequest;
import com.bank.product.dto.response.ChargeConfigResponse;
import com.bank.product.service.ChargeConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class ChargeConfigController {

    @Autowired
    private ChargeConfigService chargeConfigService;

    @PostMapping("/api/v1/products/{productId}/charges")
    public ResponseEntity<ChargeConfigResponse> addCharge(
            @PathVariable Integer productId,
            @RequestBody CreateChargeConfigRequest request) {
        return ResponseEntity.ok(chargeConfigService.addChargeConfig(productId, request));
    }

    @GetMapping("/api/v1/products/{productId}/charges")
    public ResponseEntity<List<ChargeConfigResponse>> getCharges(@PathVariable Integer productId) {
        return ResponseEntity.ok(chargeConfigService.getChargesForProduct(productId));
    }

    @GetMapping("/api/v1/charges/{chargeId}")
    public ResponseEntity<ChargeConfigResponse> getChargeById(@PathVariable Integer chargeId) {
        return ResponseEntity.ok(chargeConfigService.getChargeById(chargeId));
    }

    @PutMapping("/api/v1/charges/{chargeId}")
    public ResponseEntity<ChargeConfigResponse> updateCharge(
            @PathVariable Integer chargeId,
            @RequestBody UpdateChargeConfigRequest request) {
        return ResponseEntity.ok(chargeConfigService.updateChargeConfig(chargeId, request));
    }

    @DeleteMapping("/api/v1/charges/{chargeId}")
    public ResponseEntity<Void> deleteCharge(@PathVariable Integer chargeId) {
        chargeConfigService.deleteChargeConfig(chargeId);
        return ResponseEntity.noContent().build();
    }
}
