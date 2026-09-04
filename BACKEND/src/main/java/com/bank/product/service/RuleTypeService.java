package com.bank.product.service;

import com.bank.product.dto.request.CreateRuleTypeRequest;
import com.bank.product.dto.request.UpdateRuleTypeRequest;
import com.bank.product.dto.response.RuleTypeResponse;
import java.util.List;

public interface RuleTypeService {
    RuleTypeResponse createRuleType(CreateRuleTypeRequest request);
    List<RuleTypeResponse> getAllRuleTypes();
    RuleTypeResponse getRuleTypeById(Integer id);
    RuleTypeResponse updateRuleType(Integer id, UpdateRuleTypeRequest request);
    RuleTypeResponse updateRuleTypeStatus(Integer id, String status);
}