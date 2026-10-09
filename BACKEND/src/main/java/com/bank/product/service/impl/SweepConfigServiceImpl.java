package com.bank.product.service.impl;

import com.bank.common.exception.ResourceNotFoundException;
import com.bank.product.dto.request.CreateSweepConfigRequest;
import com.bank.product.dto.request.UpdateSweepConfigRequest;
import com.bank.product.dto.response.SweepConfigResponse;
import com.bank.product.entity.SweepConfig;
import com.bank.product.repository.SweepConfigRepository;
import com.bank.product.service.SweepConfigService;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@Transactional
public class SweepConfigServiceImpl implements SweepConfigService {

    private final SweepConfigRepository repository;

    public SweepConfigServiceImpl(
            SweepConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    public SweepConfigResponse createSweepConfig(
            CreateSweepConfigRequest request) {

        SweepConfig config = new SweepConfig();

        config.setSourceProductId(request.getSourceProductId());
        config.setTargetProductId(request.getTargetProductId());
        config.setSweepType(request.getSweepType());
        config.setThresholdAmount(request.getThresholdAmount());
        config.setSweepMultiple(request.getSweepMultiple());
        config.setCurrencyId(request.getCurrencyId());

        SweepConfig savedConfig = repository.save(config);

        return toResponse(savedConfig);
    }

    @Override
    @Transactional(readOnly = true)
    public SweepConfigResponse getSweepConfigById(
            Integer sweepId) {

        SweepConfig config = repository.findById(sweepId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Sweep configuration not found with id: " + sweepId));

        return toResponse(config);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SweepConfigResponse> getAllSweepConfigs() {

        return repository.findAll()
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SweepConfigResponse> getSweepConfigsBySourceProductId(
            Integer sourceProductId) {

        return repository.findBySourceProductId(sourceProductId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<SweepConfigResponse> getSweepConfigsByTargetProductId(
            Integer targetProductId) {

        return repository.findByTargetProductId(targetProductId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public SweepConfigResponse updateSweepConfig(
            Integer sweepId,
            UpdateSweepConfigRequest request) {

        SweepConfig config = repository.findById(sweepId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Sweep configuration not found with id: " + sweepId));

        if (request.getSourceProductId() != null) {
            config.setSourceProductId(request.getSourceProductId());
        }

        if (request.getTargetProductId() != null) {
            config.setTargetProductId(request.getTargetProductId());
        }

        if (request.getSweepType() != null) {
            config.setSweepType(request.getSweepType());
        }

        if (request.getThresholdAmount() != null) {
            config.setThresholdAmount(request.getThresholdAmount());
        }

        if (request.getSweepMultiple() != null) {
            config.setSweepMultiple(request.getSweepMultiple());
        }

        if (request.getCurrencyId() != null) {
            config.setCurrencyId(request.getCurrencyId());
        }

        SweepConfig updatedConfig = repository.save(config);

        return toResponse(updatedConfig);
    }

    @Override
    public void deleteSweepConfig(Integer sweepId) {

        SweepConfig config = repository.findById(sweepId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Sweep configuration not found with id: " + sweepId));

        repository.delete(config);
    }

    private SweepConfigResponse toResponse(
            SweepConfig config) {

        return new SweepConfigResponse(
                config.getSweepId(),
                config.getSourceProductId(),
                config.getTargetProductId(),
                config.getSweepType(),
                config.getThresholdAmount(),
                config.getSweepMultiple(),
                config.getCurrencyId(),
                config.getCreatedDate(),
                config.getUpdatedDate());
    }
}