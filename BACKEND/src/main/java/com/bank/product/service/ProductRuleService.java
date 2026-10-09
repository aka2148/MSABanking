package com.bank.product.service;

import com.bank.product.dto.request.CreateProductRuleRequest;
import com.bank.product.dto.request.UpdateProductRuleRequest;
import com.bank.product.dto.response.ProductRuleResponse;
import java.util.List;

public interface ProductRuleService {
    ProductRuleResponse createProductRule(Integer productId, CreateProductRuleRequest request);
    List<ProductRuleResponse> getRulesByProductId(Integer productId);
    ProductRuleResponse getProductRuleById(Integer productId, Integer ruleId);
    ProductRuleResponse updateProductRule(Integer productId, Integer ruleId, UpdateProductRuleRequest request);
    ProductRuleResponse updateProductRuleStatus(Integer productId, Integer ruleId, Boolean isActive);
    void deleteProductRule(Integer productId, Integer ruleId);
}