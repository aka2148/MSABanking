package com.bank.product.controller;

import com.bank.product.dto.request.CreateChargeSlabRequest;
import com.bank.product.dto.request.UpdateChargeSlabRequest;
import com.bank.product.dto.response.ChargeSlabResponse;
import com.bank.product.service.ChargeSlabService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@RestController
public class ChargeSlabController {

    @Autowired
    private ChargeSlabService slabService;

    @PostMapping("/api/v1/charges/{chargeId}/slabs")
    public ResponseEntity<ChargeSlabResponse> addSlab(
            @PathVariable Integer chargeId,
            @RequestBody CreateChargeSlabRequest request) {
        return ResponseEntity.ok(slabService.addSlab(chargeId, request));
    }

    @GetMapping("/api/v1/charges/{chargeId}/slabs")
    public ResponseEntity<List<ChargeSlabResponse>> getSlabs(@PathVariable Integer chargeId) {
        return ResponseEntity.ok(slabService.getSlabsForCharge(chargeId));
    }

    @GetMapping("/api/v1/charges/{chargeId}/slabs/applicable")
    public ResponseEntity<ChargeSlabResponse> getApplicableSlab(
            @PathVariable Integer chargeId,
            @RequestParam BigDecimal value) {
        return ResponseEntity.ok(slabService.getApplicableSlab(chargeId, value));
    }

    @PutMapping("/api/v1/slabs/{slabId}")
    public ResponseEntity<ChargeSlabResponse> updateSlab(
            @PathVariable Integer slabId,
            @RequestBody UpdateChargeSlabRequest request) {
        return ResponseEntity.ok(slabService.updateSlab(slabId, request));
    }

    @DeleteMapping("/api/v1/slabs/{slabId}")
    public ResponseEntity<Void> deleteSlab(@PathVariable Integer slabId) {
        slabService.deleteSlab(slabId);
        return ResponseEntity.noContent().build();
    }
}
