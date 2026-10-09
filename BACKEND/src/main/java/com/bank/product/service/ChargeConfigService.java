package com.bank.product.service;

import com.bank.product.dto.request.CreateChargeConfigRequest;
import com.bank.product.dto.request.UpdateChargeConfigRequest;
import com.bank.product.dto.response.ChargeConfigResponse;

import java.util.List;

public interface ChargeConfigService {

    ChargeConfigResponse addChargeConfig(Integer productId, CreateChargeConfigRequest request);

    List<ChargeConfigResponse> getChargesForProduct(Integer productId);

    ChargeConfigResponse getChargeById(Integer chargeId);

    ChargeConfigResponse updateChargeConfig(Integer chargeId, UpdateChargeConfigRequest request);

    void deleteChargeConfig(Integer chargeId);
}
