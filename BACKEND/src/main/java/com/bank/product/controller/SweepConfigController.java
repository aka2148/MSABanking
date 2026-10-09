package com.bank.product.controller;

import com.bank.product.dto.request.CreateSweepConfigRequest;
import com.bank.product.dto.request.UpdateSweepConfigRequest;
import com.bank.product.dto.response.SweepConfigResponse;
import com.bank.product.service.SweepConfigService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sweep-configs")
public class SweepConfigController {

    private final SweepConfigService service;

    public SweepConfigController(
            SweepConfigService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<SweepConfigResponse> create(
            @Valid @RequestBody CreateSweepConfigRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createSweepConfig(request));
    }

    @GetMapping
    public ResponseEntity<List<SweepConfigResponse>> getAll() {

        return ResponseEntity.ok(
                service.getAllSweepConfigs());
    }

    @GetMapping("/{sweepId}")
    public ResponseEntity<SweepConfigResponse> getById(
            @PathVariable Integer sweepId) {

        return ResponseEntity.ok(
                service.getSweepConfigById(sweepId));
    }

    @GetMapping("/source-product/{sourceProductId}")
    public ResponseEntity<List<SweepConfigResponse>>
    getBySourceProductId(
            @PathVariable Integer sourceProductId) {

        return ResponseEntity.ok(
                service.getSweepConfigsBySourceProductId(
                        sourceProductId));
    }

    @GetMapping("/target-product/{targetProductId}")
    public ResponseEntity<List<SweepConfigResponse>>
    getByTargetProductId(
            @PathVariable Integer targetProductId) {

        return ResponseEntity.ok(
                service.getSweepConfigsByTargetProductId(
                        targetProductId));
    }

    @PutMapping("/{sweepId}")
    public ResponseEntity<SweepConfigResponse> update(
            @PathVariable Integer sweepId,
            @Valid @RequestBody UpdateSweepConfigRequest request) {

        return ResponseEntity.ok(
                service.updateSweepConfig(sweepId, request));
    }

    @DeleteMapping("/{sweepId}")
    public ResponseEntity<Void> delete(
            @PathVariable Integer sweepId) {

        service.deleteSweepConfig(sweepId);

        return ResponseEntity.noContent().build();
    }
}