package com.bank.product.service;

import com.bank.product.dto.request.CreateRuleOutcomeRequest;
import com.bank.product.dto.request.UpdateRuleOutcomeRequest;
import com.bank.product.dto.response.RuleOutcomeResponse;

import java.util.List;

public interface RuleOutcomeService {

    RuleOutcomeResponse addOutcome(Integer ruleId, CreateRuleOutcomeRequest request);

    List<RuleOutcomeResponse> getOutcomesForRule(Integer ruleId);

    RuleOutcomeResponse getOutcomeById(Integer outcomeId);

    RuleOutcomeResponse updateOutcome(Integer outcomeId, UpdateRuleOutcomeRequest request);

    void deleteOutcome(Integer outcomeId);
}
