package com.bank.product.service;

import com.bank.product.dto.request.CreateChargeSlabRequest;
import com.bank.product.dto.request.UpdateChargeSlabRequest;
import com.bank.product.dto.response.ChargeSlabResponse;

import java.math.BigDecimal;
import java.util.List;

public interface ChargeSlabService {

    ChargeSlabResponse addSlab(Integer chargeId, CreateChargeSlabRequest request);

    List<ChargeSlabResponse> getSlabsForCharge(Integer chargeId);

    ChargeSlabResponse getApplicableSlab(Integer chargeId, BigDecimal value);

    ChargeSlabResponse updateSlab(Integer slabId, UpdateChargeSlabRequest request);

    void deleteSlab(Integer slabId);
}
