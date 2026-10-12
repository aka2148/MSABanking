package com.bank.product.service.impl;

import com.bank.product.dto.request.CreateRuleOutcomeRequest;
import com.bank.product.dto.request.UpdateRuleOutcomeRequest;
import com.bank.product.dto.response.RuleOutcomeResponse;
import com.bank.product.entity.RuleOutcome;
import com.bank.product.repository.ProductRuleRepository;
import com.bank.product.repository.RuleOutcomeRepository;
import com.bank.product.service.ProductConfigurationIntegrityService;
import com.bank.product.service.RuleOutcomeService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RuleOutcomeServiceImpl implements RuleOutcomeService {

    @Autowired
    private RuleOutcomeRepository outcomeRepository;

    @Autowired
    private ProductRuleRepository ruleRepository;

    @Autowired
    private ProductConfigurationIntegrityService integrity;

    @Override
    public RuleOutcomeResponse addOutcome(Integer ruleId, CreateRuleOutcomeRequest request) {
        guardRule(ruleId);
        RuleOutcome outcome = new RuleOutcome();
        outcome.setRuleId(ruleId);
        outcome.setOutcomeType(request.getOutcomeType());
        outcome.setOutcomeValue(request.getOutcomeValue());
        outcome.setUnit(request.getUnit());
        outcome.setCompoundingBasis(request.getCompoundingBasis());
        outcome.setPayoutFrequency(request.getPayoutFrequency());
        outcome.setCurrencyId(request.getCurrencyId());

        return toResponse(outcomeRepository.save(outcome));
    }

    @Override
    public List<RuleOutcomeResponse> getOutcomesForRule(Integer ruleId) {
        return outcomeRepository.findByRuleId(ruleId)
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public RuleOutcomeResponse getOutcomeById(Integer outcomeId) {
        RuleOutcome outcome = outcomeRepository.findById(outcomeId)
            .orElseThrow(() -> new EntityNotFoundException("Outcome not found: " + outcomeId));
        return toResponse(outcome);
    }

    @Override
    public RuleOutcomeResponse updateOutcome(Integer outcomeId, UpdateRuleOutcomeRequest request) {
        RuleOutcome outcome = outcomeRepository.findById(outcomeId)
            .orElseThrow(() -> new EntityNotFoundException("Outcome not found: " + outcomeId));
        guardRule(outcome.getRuleId());

        outcome.setOutcomeType(request.getOutcomeType());
        outcome.setOutcomeValue(request.getOutcomeValue());
        outcome.setUnit(request.getUnit());
        outcome.setCompoundingBasis(request.getCompoundingBasis());
        outcome.setPayoutFrequency(request.getPayoutFrequency());
        outcome.setCurrencyId(request.getCurrencyId());

        return toResponse(outcomeRepository.save(outcome));
    }

    @Override
    public void deleteOutcome(Integer outcomeId) {
        RuleOutcome outcome = outcomeRepository.findById(outcomeId)
                .orElseThrow(() -> new EntityNotFoundException("Outcome not found: " + outcomeId));
        guardRule(outcome.getRuleId());
        outcomeRepository.delete(outcome);
    }

    private void guardRule(Integer ruleId) {
        Integer productId = ruleRepository.findById(ruleId)
                .orElseThrow(() -> new EntityNotFoundException("Rule not found: " + ruleId)).getProductId();
        integrity.assertEditable(productId);
    }

    private RuleOutcomeResponse toResponse(RuleOutcome outcome) {
        RuleOutcomeResponse response = new RuleOutcomeResponse();
        response.setRoOutcomeId(outcome.getRoOutcomeId());
        response.setRuleId(outcome.getRuleId());
        response.setOutcomeType(outcome.getOutcomeType());
        response.setOutcomeValue(outcome.getOutcomeValue());
        response.setUnit(outcome.getUnit());
        response.setCompoundingBasis(outcome.getCompoundingBasis());
        response.setPayoutFrequency(outcome.getPayoutFrequency());
        response.setCurrencyId(outcome.getCurrencyId());
        return response;
    }
}
