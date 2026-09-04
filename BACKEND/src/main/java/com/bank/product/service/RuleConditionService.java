package com.bank.product.service;

import com.bank.product.dto.request.CreateRuleConditionRequest;
import com.bank.product.dto.request.UpdateRuleConditionRequest;
import com.bank.product.dto.response.RuleConditionResponse;
import java.util.List;

public interface RuleConditionService {
    RuleConditionResponse createRuleCondition(Integer productId, Integer ruleId, CreateRuleConditionRequest request);
    List<RuleConditionResponse> getConditionsByRuleId(Integer productId, Integer ruleId);
    RuleConditionResponse getRuleConditionById(Integer productId, Integer ruleId, Integer conditionId);
    RuleConditionResponse updateRuleCondition(Integer productId, Integer ruleId, Integer conditionId, UpdateRuleConditionRequest request);
    void deleteRuleCondition(Integer productId, Integer ruleId, Integer conditionId);
}