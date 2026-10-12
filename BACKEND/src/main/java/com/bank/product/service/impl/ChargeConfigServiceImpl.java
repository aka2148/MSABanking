package com.bank.product.service.impl;

import com.bank.product.dto.request.CreateChargeConfigRequest;
import com.bank.product.dto.request.UpdateChargeConfigRequest;
import com.bank.product.dto.response.ChargeConfigResponse;
import com.bank.product.entity.ChargeConfig;
import com.bank.product.repository.ChargeConfigRepository;
import com.bank.product.service.ChargeConfigService;
import com.bank.product.service.ProductConfigurationIntegrityService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChargeConfigServiceImpl implements ChargeConfigService {

    @Autowired
    private ChargeConfigRepository chargeConfigRepository;

    @Autowired
    private ProductConfigurationIntegrityService integrity;

    @Override
    public ChargeConfigResponse addChargeConfig(Integer productId, CreateChargeConfigRequest request) {
        integrity.assertEditable(productId);
        ChargeConfig config = new ChargeConfig();
        config.setProductId(productId);
        config.setChargeCode(request.getChargeCode());
        config.setCalculationBasis(request.getCalculationBasis());
        config.setGlCode(request.getGlCode());
        config.setIsActive(request.getIsActive());

        return toResponse(chargeConfigRepository.save(config));
    }

    @Override
    public List<ChargeConfigResponse> getChargesForProduct(Integer productId) {
        return chargeConfigRepository.findByProductId(productId)
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public ChargeConfigResponse getChargeById(Integer chargeId) {
        ChargeConfig config = chargeConfigRepository.findById(chargeId)
            .orElseThrow(() -> new EntityNotFoundException("Charge config not found: " + chargeId));
        return toResponse(config);
    }

    @Override
    public ChargeConfigResponse updateChargeConfig(Integer chargeId, UpdateChargeConfigRequest request) {
        ChargeConfig config = chargeConfigRepository.findById(chargeId)
            .orElseThrow(() -> new EntityNotFoundException("Charge config not found: " + chargeId));
        integrity.assertEditable(config.getProductId());

        config.setChargeCode(request.getChargeCode());
        config.setCalculationBasis(request.getCalculationBasis());
        config.setGlCode(request.getGlCode());
        config.setIsActive(request.getIsActive());

        return toResponse(chargeConfigRepository.save(config));
    }

    @Override
    public void deleteChargeConfig(Integer chargeId) {
        ChargeConfig config = chargeConfigRepository.findById(chargeId)
                .orElseThrow(() -> new EntityNotFoundException("Charge config not found: " + chargeId));
        integrity.assertEditable(config.getProductId());
        chargeConfigRepository.delete(config);
    }

    private ChargeConfigResponse toResponse(ChargeConfig config) {
        ChargeConfigResponse response = new ChargeConfigResponse();
        response.setChargeId(config.getChargeId());
        response.setChargeCode(config.getChargeCode());
        response.setCalculationBasis(config.getCalculationBasis());
        response.setGlCode(config.getGlCode());
        response.setIsActive(config.getIsActive());
        return response;
    }
}
