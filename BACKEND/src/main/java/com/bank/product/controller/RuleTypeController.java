package com.bank.product.controller;

import com.bank.product.dto.request.CreateRuleTypeRequest;
import com.bank.product.dto.request.UpdateRuleTypeRequest;
import com.bank.product.dto.response.RuleTypeResponse;
import com.bank.product.service.RuleTypeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/rule-types")
public class RuleTypeController {

    private final RuleTypeService ruleTypeService;

    public RuleTypeController(RuleTypeService ruleTypeService) {
        this.ruleTypeService = ruleTypeService;
    }

    @PostMapping
    public ResponseEntity<RuleTypeResponse> createRuleType(@Valid @RequestBody CreateRuleTypeRequest request) {
        return new ResponseEntity<>(ruleTypeService.createRuleType(request), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<RuleTypeResponse>> getAllRuleTypes() {
        return ResponseEntity.ok(ruleTypeService.getAllRuleTypes());
    }

    @GetMapping("/{id}")
    public ResponseEntity<RuleTypeResponse> getRuleTypeById(@PathVariable Integer id) {
        return ResponseEntity.ok(ruleTypeService.getRuleTypeById(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RuleTypeResponse> updateRuleType(@PathVariable Integer id, @Valid @RequestBody UpdateRuleTypeRequest request) {
        return ResponseEntity.ok(ruleTypeService.updateRuleType(id, request));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<RuleTypeResponse> updateRuleTypeStatus(@PathVariable Integer id, @RequestBody Map<String, String> statusBody) {
        return ResponseEntity.ok(ruleTypeService.updateRuleTypeStatus(id, statusBody.get("status")));
    }
}