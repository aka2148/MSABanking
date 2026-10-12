package com.bank.product.service.impl;

import com.bank.product.dto.request.CreateRuleConditionRequest;
import com.bank.product.dto.request.UpdateRuleConditionRequest;
import com.bank.product.dto.response.RuleConditionResponse;
import com.bank.product.entity.RuleCondition;
import com.bank.product.repository.ProductRuleRepository;
import com.bank.product.repository.RuleConditionRepository;
import com.bank.product.service.ProductConfigurationIntegrityService;
import com.bank.product.service.RuleConditionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RuleConditionServiceImpl implements RuleConditionService {

    private final RuleConditionRepository ruleConditionRepository;
    private final ProductRuleRepository productRuleRepository;
    private final ProductConfigurationIntegrityService integrity;

    public RuleConditionServiceImpl(RuleConditionRepository ruleConditionRepository,
                                  ProductRuleRepository productRuleRepository,
                                  ProductConfigurationIntegrityService integrity) {
        this.ruleConditionRepository = ruleConditionRepository;
        this.productRuleRepository = productRuleRepository;
        this.integrity = integrity;
    }

    private void validateParentRule(Integer productId, Integer ruleId) {
        productRuleRepository.findByRuleIdAndProductId(ruleId, productId)
                .orElseThrow(() -> new RuntimeException("Rule ID " + ruleId + " does not belong to product ID " + productId));
    }

    @Override
    @Transactional
    public RuleConditionResponse createRuleCondition(Integer productId, Integer ruleId, CreateRuleConditionRequest request) {
        validateParentRule(productId, ruleId);
        integrity.assertEditable(productId);

        RuleCondition entity = new RuleCondition();
        entity.setRuleId(ruleId);
        entity.setConditionAttribute(request.getConditionAttribute());
        entity.setOperator(request.getOperator());
        entity.setValueFrom(request.getValueFrom());
        entity.setValueTo(request.getValueTo());
        entity.setValueSet(request.getValueSet());
        entity.setUuid(UUID.randomUUID().toString());
        entity.setCrudValue("C");
        entity.setCreatedDate(LocalDateTime.now());
        entity.setCreatedBy("SYSTEM");

        RuleCondition saved = ruleConditionRepository.save(entity);
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RuleConditionResponse> getConditionsByRuleId(Integer productId, Integer ruleId) {
        validateParentRule(productId, ruleId);
        List<RuleCondition> entities = ruleConditionRepository.findByRuleId(ruleId);
        List<RuleConditionResponse> responses = new ArrayList<>();
        for (RuleCondition entity : entities) {
            responses.add(mapToResponse(entity));
        }
        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public RuleConditionResponse getRuleConditionById(Integer productId, Integer ruleId, Integer conditionId) {
        validateParentRule(productId, ruleId);
        RuleCondition entity = ruleConditionRepository.findByConditionIdAndRuleId(conditionId, ruleId)
                .orElseThrow(() -> new RuntimeException("Condition ID " + conditionId + " not found under rule " + ruleId));
        return mapToResponse(entity);
    }

    @Override
    @Transactional
    public RuleConditionResponse updateRuleCondition(Integer productId, Integer ruleId, Integer conditionId, UpdateRuleConditionRequest request) {
        validateParentRule(productId, ruleId);
        integrity.assertEditable(productId);
        RuleCondition entity = ruleConditionRepository.findByConditionIdAndRuleId(conditionId, ruleId)
                .orElseThrow(() -> new RuntimeException("Condition ID " + conditionId + " not found under rule " + ruleId));

        if (request.getConditionAttribute() != null) entity.setConditionAttribute(request.getConditionAttribute());
        if (request.getOperator() != null) entity.setOperator(request.getOperator());
        if (request.getValueFrom() != null) entity.setValueFrom(request.getValueFrom());
        if (request.getValueTo() != null) entity.setValueTo(request.getValueTo());
        if (request.getValueSet() != null) entity.setValueSet(request.getValueSet());

        entity.setUpdatedDate(LocalDateTime.now());
        entity.setUpdatedBy("SYSTEM");
        entity.setCrudValue("U");

        RuleCondition updated = ruleConditionRepository.save(entity);
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public void deleteRuleCondition(Integer productId, Integer ruleId, Integer conditionId) {
        validateParentRule(productId, ruleId);
        integrity.assertEditable(productId);
        RuleCondition entity = ruleConditionRepository.findByConditionIdAndRuleId(conditionId, ruleId)
                .orElseThrow(() -> new RuntimeException("Condition ID " + conditionId + " not found under rule " + ruleId));
        ruleConditionRepository.delete(entity);
    }

    private RuleConditionResponse mapToResponse(RuleCondition entity) {
        RuleConditionResponse response = new RuleConditionResponse();
        response.setConditionId(entity.getConditionId());
        response.setRuleId(entity.getRuleId());
        response.setConditionAttribute(entity.getConditionAttribute());
        response.setOperator(entity.getOperator());
        response.setValueFrom(entity.getValueFrom());
        response.setValueTo(entity.getValueTo());
        response.setValueSet(entity.getValueSet());
        response.setCreatedDate(entity.getCreatedDate());
        response.setUpdatedDate(entity.getUpdatedDate());
        return response;
    }
}