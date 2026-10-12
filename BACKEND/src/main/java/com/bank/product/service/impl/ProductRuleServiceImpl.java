package com.bank.product.service.impl;

import com.bank.product.dto.request.CreateProductRuleRequest;
import com.bank.product.dto.request.UpdateProductRuleRequest;
import com.bank.product.dto.response.ProductRuleResponse;
import com.bank.product.entity.ProductRule;
import com.bank.product.repository.ProductRuleRepository;
import com.bank.product.service.ProductConfigurationIntegrityService;
import com.bank.product.service.ProductRuleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ProductRuleServiceImpl implements ProductRuleService {

    private final ProductRuleRepository productRuleRepository;
    private final ProductConfigurationIntegrityService integrity;

    public ProductRuleServiceImpl(ProductRuleRepository productRuleRepository,
                                  ProductConfigurationIntegrityService integrity) {
        this.productRuleRepository = productRuleRepository;
        this.integrity = integrity;
    }

    @Override
    @Transactional
    public ProductRuleResponse createProductRule(Integer productId, CreateProductRuleRequest request) {
        integrity.assertEditable(productId);
        ProductRule entity = new ProductRule();
        entity.setProductId(productId);
        entity.setRuleTypeId(request.getRuleTypeId());
        entity.setRuleName(request.getRuleName());
        entity.setPriority(request.getPriority() != null ? request.getPriority() : 1);
        entity.setCombineLogic(request.getCombineLogic() != null ? request.getCombineLogic() : "AND");
        entity.setIsActive(request.getIsActive() != null ? request.getIsActive() : true);
        entity.setEffectiveFrom(request.getEffectiveFrom());
        entity.setEffectiveTo(request.getEffectiveTo());
        entity.setUuid(UUID.randomUUID().toString());
        entity.setCrudValue("C");
        entity.setCreatedDate(LocalDateTime.now());
        entity.setCreatedBy("SYSTEM");

        ProductRule saved = productRuleRepository.save(entity);
        return mapToResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProductRuleResponse> getRulesByProductId(Integer productId) {
        List<ProductRule> entities = productRuleRepository.findByProductId(productId);
        List<ProductRuleResponse> responses = new ArrayList<>();
        for (ProductRule entity : entities) {
            responses.add(mapToResponse(entity));
        }
        return responses;
    }

    @Override
    @Transactional(readOnly = true)
    public ProductRuleResponse getProductRuleById(Integer productId, Integer ruleId) {
        ProductRule entity = productRuleRepository.findByRuleIdAndProductId(ruleId, productId)
                .orElseThrow(() -> new RuntimeException("Rule not found with ID: " + ruleId + " for product: " + productId));
        return mapToResponse(entity);
    }

    @Override
    @Transactional
    public ProductRuleResponse updateProductRule(Integer productId, Integer ruleId, UpdateProductRuleRequest request) {
        integrity.assertEditable(productId);
        ProductRule entity = productRuleRepository.findByRuleIdAndProductId(ruleId, productId)
                .orElseThrow(() -> new RuntimeException("Rule not found with ID: " + ruleId + " for product: " + productId));

        if (request.getRuleName() != null) entity.setRuleName(request.getRuleName());
        if (request.getPriority() != null) entity.setPriority(request.getPriority());
        if (request.getCombineLogic() != null) entity.setCombineLogic(request.getCombineLogic());
        if (request.getIsActive() != null) entity.setIsActive(request.getIsActive());
        if (request.getEffectiveFrom() != null) entity.setEffectiveFrom(request.getEffectiveFrom());
        if (request.getEffectiveTo() != null) entity.setEffectiveTo(request.getEffectiveTo());

        entity.setUpdatedDate(LocalDateTime.now());
        entity.setUpdatedBy("SYSTEM");
        entity.setCrudValue("U");

        ProductRule updated = productRuleRepository.save(entity);
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public ProductRuleResponse updateProductRuleStatus(Integer productId, Integer ruleId, Boolean isActive) {
        integrity.assertEditable(productId);
        ProductRule entity = productRuleRepository.findByRuleIdAndProductId(ruleId, productId)
                .orElseThrow(() -> new RuntimeException("Rule not found with ID: " + ruleId + " for product: " + productId));

        entity.setIsActive(isActive);
        entity.setUpdatedDate(LocalDateTime.now());
        entity.setUpdatedBy("SYSTEM");
        entity.setCrudValue("U");

        ProductRule updated = productRuleRepository.save(entity);
        return mapToResponse(updated);
    }

    @Override
    @Transactional
    public void deleteProductRule(Integer productId, Integer ruleId) {
        integrity.assertEditable(productId);
        ProductRule entity = productRuleRepository.findByRuleIdAndProductId(ruleId, productId)
                .orElseThrow(() -> new RuntimeException("Rule not found with ID: " + ruleId + " for product: " + productId));
        productRuleRepository.delete(entity);
    }

    private ProductRuleResponse mapToResponse(ProductRule entity) {
        ProductRuleResponse response = new ProductRuleResponse();
        response.setRuleId(entity.getRuleId());
        response.setProductId(entity.getProductId());
        response.setRuleTypeId(entity.getRuleTypeId());
        response.setRuleName(entity.getRuleName());
        response.setPriority(entity.getPriority());
        response.setCombineLogic(entity.getCombineLogic());
        response.setIsActive(entity.getIsActive());
        response.setEffectiveFrom(entity.getEffectiveFrom());
        response.setEffectiveTo(entity.getEffectiveTo());
        response.setCreatedDate(entity.getCreatedDate());
        response.setUpdatedDate(entity.getUpdatedDate());
        return response;
    }
}