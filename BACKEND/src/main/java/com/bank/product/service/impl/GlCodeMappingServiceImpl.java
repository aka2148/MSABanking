package com.bank.product.service.impl;

import com.bank.common.exception.ResourceNotFoundException;
import com.bank.product.dto.request.CreateGlCodeMappingRequest;
import com.bank.product.dto.request.UpdateGlCodeMappingRequest;
import com.bank.product.dto.response.GlCodeMappingResponse;
import com.bank.product.entity.GlCodeMapping;
import com.bank.product.repository.GlCodeMappingRepository;
import com.bank.product.service.GlCodeMappingService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class GlCodeMappingServiceImpl implements GlCodeMappingService {

    private final GlCodeMappingRepository repository;

    public GlCodeMappingServiceImpl(
            GlCodeMappingRepository repository) {
        this.repository = repository;
    }

    @Override
    public GlCodeMappingResponse createGlCodeMapping(
            CreateGlCodeMappingRequest request) {

        GlCodeMapping mapping = new GlCodeMapping();

        mapping.setOutcomeId(request.getOutcomeId());
        mapping.setGlCode(request.getGlCode());

        GlCodeMapping savedMapping = repository.save(mapping);

        return toResponse(savedMapping);
    }

    @Override
    @Transactional(readOnly = true)
    public GlCodeMappingResponse getGlCodeMappingById(
            Integer mappingId) {

        GlCodeMapping mapping = repository.findById(mappingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "GL code mapping not found with id: " + mappingId));

        return toResponse(mapping);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GlCodeMappingResponse> getAllGlCodeMappings() {

        return repository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<GlCodeMappingResponse> getGlCodeMappingsByOutcomeId(
            Integer outcomeId) {

        return repository.findByOutcomeId(outcomeId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public GlCodeMappingResponse updateGlCodeMapping(
            Integer mappingId,
            UpdateGlCodeMappingRequest request) {

        GlCodeMapping mapping = repository.findById(mappingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "GL code mapping not found with id: " + mappingId));

        if (request.getOutcomeId() != null) {
            mapping.setOutcomeId(request.getOutcomeId());
        }

        if (request.getGlCode() != null) {
            mapping.setGlCode(request.getGlCode());
        }

        GlCodeMapping updatedMapping = repository.save(mapping);

        return toResponse(updatedMapping);
    }

    @Override
    public void deleteGlCodeMapping(Integer mappingId) {

        GlCodeMapping mapping = repository.findById(mappingId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "GL code mapping not found with id: " + mappingId));

        repository.delete(mapping);
    }

    private GlCodeMappingResponse toResponse(
            GlCodeMapping mapping) {

        return new GlCodeMappingResponse(
                mapping.getMappingId(),
                mapping.getOutcomeId(),
                mapping.getGlCode(),
                mapping.getCreatedDate(),
                mapping.getUpdatedDate());
    }
}