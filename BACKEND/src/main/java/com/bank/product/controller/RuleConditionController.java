package com.bank.product.controller;

import com.bank.product.dto.request.CreateRuleConditionRequest;
import com.bank.product.dto.request.UpdateRuleConditionRequest;
import com.bank.product.dto.response.RuleConditionResponse;
import com.bank.product.service.RuleConditionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/products/{productId}/rules/{ruleId}/conditions")
public class RuleConditionController {

    private final RuleConditionService ruleConditionService;

    public RuleConditionController(RuleConditionService ruleConditionService) {
        this.ruleConditionService = ruleConditionService;
    }

    @PostMapping
    public ResponseEntity<RuleConditionResponse> createRuleCondition(
            @PathVariable Integer productId,
            @PathVariable Integer ruleId,
            @Valid @RequestBody CreateRuleConditionRequest request) {
        return new ResponseEntity<>(ruleConditionService.createRuleCondition(productId, ruleId, request), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<RuleConditionResponse>> getConditionsByRuleId(
            @PathVariable Integer productId,
            @PathVariable Integer ruleId) {
        return ResponseEntity.ok(ruleConditionService.getConditionsByRuleId(productId, ruleId));
    }

    @GetMapping("/{conditionId}")
    public ResponseEntity<RuleConditionResponse> getRuleConditionById(
            @PathVariable Integer productId,
            @PathVariable Integer ruleId,
            @PathVariable Integer conditionId) {
        return ResponseEntity.ok(ruleConditionService.getRuleConditionById(productId, ruleId, conditionId));
    }

    @PutMapping("/{conditionId}")
    public ResponseEntity<RuleConditionResponse> updateRuleCondition(
            @PathVariable Integer productId,
            @PathVariable Integer ruleId,
            @PathVariable Integer conditionId,
            @Valid @RequestBody UpdateRuleConditionRequest request) {
        return ResponseEntity.ok(ruleConditionService.updateRuleCondition(productId, ruleId, conditionId, request));
    }

    @DeleteMapping("/{conditionId}")
    public ResponseEntity<Void> deleteRuleCondition(
            @PathVariable Integer productId,
            @PathVariable Integer ruleId,
            @PathVariable Integer conditionId) {
        ruleConditionService.deleteRuleCondition(productId, ruleId, conditionId);
        return ResponseEntity.noContent().build();
    }
}