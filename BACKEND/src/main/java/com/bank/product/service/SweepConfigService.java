package com.bank.product.service;

import com.bank.product.dto.request.CreateSweepConfigRequest;
import com.bank.product.dto.request.UpdateSweepConfigRequest;
import com.bank.product.dto.response.SweepConfigResponse;

import java.util.List;

public interface SweepConfigService {

    SweepConfigResponse createSweepConfig(
            CreateSweepConfigRequest request);

    SweepConfigResponse getSweepConfigById(
            Integer sweepId);

    List<SweepConfigResponse> getAllSweepConfigs();

    List<SweepConfigResponse> getSweepConfigsBySourceProductId(
            Integer sourceProductId);

    List<SweepConfigResponse> getSweepConfigsByTargetProductId(
            Integer targetProductId);

    SweepConfigResponse updateSweepConfig(
            Integer sweepId,
            UpdateSweepConfigRequest request);

    void deleteSweepConfig(Integer sweepId);
}