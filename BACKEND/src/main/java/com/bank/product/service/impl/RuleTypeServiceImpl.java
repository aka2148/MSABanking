package com.bank.product.service.impl;

import com.bank.product.dto.request.CreateRuleTypeRequest;
import com.bank.product.dto.request.UpdateRuleTypeRequest;
import com.bank.product.dto.response.RuleTypeResponse;
import com.bank.product.entity.RuleType;
import com.bank.product.repository.RuleTypeRepository;
import com.bank.product.service.RuleTypeService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class RuleTypeServiceImpl implements RuleTypeService {

    private final RuleTypeRepository ruleTypeRepository;

    public RuleTypeServiceImpl(RuleTypeRepository ruleTypeRepository) {
        this.ruleTypeRepository = ruleTypeRepository;
    }

    @Override
    @Transactional
    public RuleTypeResponse createRuleType(CreateRuleTypeRequest request) {
        RuleType entity = new RuleType();
        entity.setRuleTypeCode(request.getRuleTypeCode());
        entity.setDescription(request.getDescription());
        entity.setCrudValue("C");
        entity.setUuid(UUID.randomUUID().toString());
        entity.setCreatedDate(LocalDateTime.now());
        entity.setCreatedBy("SYSTEM");

        RuleType saved = ruleTypeRepository.save(entity);
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RuleTypeResponse> getAllRuleTypes() {
        List<RuleType> entities = ruleTypeRepository.findAll();
        List<RuleTypeResponse> responses = new ArrayList<>();
        for (RuleType entity : entities) {
            responses.add(mapToResponse(entity));
        }
        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public RuleTypeResponse getRuleTypeById(Integer id) {
        RuleType entity = ruleTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rule type not found with ID: " + id));
        return mapToResponse(entity);
    }

    @Override
    @Transactional
    public RuleTypeResponse updateRuleType(Integer id, UpdateRuleTypeRequest request) {
        RuleType entity = ruleTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rule type not found with ID: " + id));

        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getCrudValue() != null) {
            entity.setCrudValue(request.getCrudValue());
        }
        entity.setUpdatedDate(LocalDateTime.now());
        entity.setUpdatedBy("SYSTEM");

        RuleType updated = ruleTypeRepository.save(entity);
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public RuleTypeResponse updateRuleTypeStatus(Integer id, String status) {
        RuleType entity = ruleTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Rule type not found with ID: " + id));

        entity.setCrudValue(status);
        entity.setUpdatedDate(LocalDateTime.now());
        entity.setUpdatedBy("SYSTEM");

        RuleType updated = ruleTypeRepository.save(entity);
        return mapToResponse(updated);
    }

    private RuleTypeResponse mapToResponse(RuleType entity) {
        RuleTypeResponse response = new RuleTypeResponse();
        response.setRuleTypeId(entity.getRuleTypeId());
        response.setRuleTypeCode(entity.getRuleTypeCode());
        response.setDescription(entity.getDescription());
        response.setCrudValue(entity.getCrudValue());
        response.setCreatedDate(entity.getCreatedDate());
        response.setUpdatedDate(entity.getUpdatedDate());
        return response;
    }
}