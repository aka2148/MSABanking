package com.bank.product.service;

import com.bank.product.dto.request.CreateGlCodeMappingRequest;
import com.bank.product.dto.request.UpdateGlCodeMappingRequest;
import com.bank.product.dto.response.GlCodeMappingResponse;

import java.util.List;

public interface GlCodeMappingService {

    GlCodeMappingResponse createGlCodeMapping(
            CreateGlCodeMappingRequest request);

    GlCodeMappingResponse getGlCodeMappingById(
            Integer mappingId);

    List<GlCodeMappingResponse> getAllGlCodeMappings();

    List<GlCodeMappingResponse> getGlCodeMappingsByOutcomeId(
            Integer outcomeId);

    GlCodeMappingResponse updateGlCodeMapping(
            Integer mappingId,
            UpdateGlCodeMappingRequest request);

    void deleteGlCodeMapping(Integer mappingId);
}