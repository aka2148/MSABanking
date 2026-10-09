package com.bank.product.controller;

import com.bank.product.dto.request.CreateGlCodeMappingRequest;
import com.bank.product.dto.request.UpdateGlCodeMappingRequest;
import com.bank.product.dto.response.GlCodeMappingResponse;
import com.bank.product.service.GlCodeMappingService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/gl-code-mappings")
public class GlCodeMappingController {

    private final GlCodeMappingService service;

    public GlCodeMappingController(
            GlCodeMappingService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<GlCodeMappingResponse> create(
            @Valid @RequestBody CreateGlCodeMappingRequest request) {

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(service.createGlCodeMapping(request));
    }

    @GetMapping
    public ResponseEntity<List<GlCodeMappingResponse>> getAll() {

        return ResponseEntity.ok(
                service.getAllGlCodeMappings());
    }

    @GetMapping("/{mappingId}")
    public ResponseEntity<GlCodeMappingResponse> getById(
            @PathVariable Integer mappingId) {

        return ResponseEntity.ok(
                service.getGlCodeMappingById(mappingId));
    }

    @GetMapping("/outcome/{outcomeId}")
    public ResponseEntity<List<GlCodeMappingResponse>>
    getByOutcomeId(@PathVariable Integer outcomeId) {

        return ResponseEntity.ok(
                service.getGlCodeMappingsByOutcomeId(outcomeId));
    }

    @PutMapping("/{mappingId}")
    public ResponseEntity<GlCodeMappingResponse> update(
            @PathVariable Integer mappingId,
            @Valid @RequestBody UpdateGlCodeMappingRequest request) {

        return ResponseEntity.ok(
                service.updateGlCodeMapping(mappingId, request));
    }

    @DeleteMapping("/{mappingId}")
    public ResponseEntity<Void> delete(
            @PathVariable Integer mappingId) {

        service.deleteGlCodeMapping(mappingId);

        return ResponseEntity.noContent().build();
    }
}