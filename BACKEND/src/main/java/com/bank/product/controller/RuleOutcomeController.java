package com.bank.product.controller;

import com.bank.product.dto.request.CreateRuleOutcomeRequest;
import com.bank.product.dto.request.UpdateRuleOutcomeRequest;
import com.bank.product.dto.response.RuleOutcomeResponse;
import com.bank.product.service.RuleOutcomeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class RuleOutcomeController {

    @Autowired
    private RuleOutcomeService outcomeService;

    @PostMapping("/api/v1/rules/{ruleId}/outcomes")
    public ResponseEntity<RuleOutcomeResponse> addOutcome(
            @PathVariable Integer ruleId,
            @RequestBody CreateRuleOutcomeRequest request) {
        return ResponseEntity.ok(outcomeService.addOutcome(ruleId, request));
    }

    @GetMapping("/api/v1/rules/{ruleId}/outcomes")
    public ResponseEntity<List<RuleOutcomeResponse>> getOutcomes(@PathVariable Integer ruleId) {
        return ResponseEntity.ok(outcomeService.getOutcomesForRule(ruleId));
    }

    @GetMapping("/api/v1/outcomes/{outcomeId}")
    public ResponseEntity<RuleOutcomeResponse> getOutcomeById(@PathVariable Integer outcomeId) {
        return ResponseEntity.ok(outcomeService.getOutcomeById(outcomeId));
    }

    @PutMapping("/api/v1/outcomes/{outcomeId}")
    public ResponseEntity<RuleOutcomeResponse> updateOutcome(
            @PathVariable Integer outcomeId,
            @RequestBody UpdateRuleOutcomeRequest request) {
        return ResponseEntity.ok(outcomeService.updateOutcome(outcomeId, request));
    }

    @DeleteMapping("/api/v1/outcomes/{outcomeId}")
    public ResponseEntity<Void> deleteOutcome(@PathVariable Integer outcomeId) {
        outcomeService.deleteOutcome(outcomeId);
        return ResponseEntity.noContent().build();
    }
}
