package com.bank.product.service.impl;

import com.bank.product.dto.request.CreateChargeSlabRequest;
import com.bank.product.dto.request.UpdateChargeSlabRequest;
import com.bank.product.dto.response.ChargeSlabResponse;
import com.bank.product.entity.ChargeSlab;
import com.bank.product.repository.ChargeConfigRepository;
import com.bank.product.repository.ChargeSlabRepository;
import com.bank.product.service.ChargeSlabService;
import com.bank.product.service.ProductConfigurationIntegrityService;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChargeSlabServiceImpl implements ChargeSlabService {

    @Autowired
    private ChargeSlabRepository slabRepository;

    @Autowired
    private ChargeConfigRepository chargeRepository;

    @Autowired
    private ProductConfigurationIntegrityService integrity;

    @Override
    public ChargeSlabResponse addSlab(Integer chargeId, CreateChargeSlabRequest request) {
        guardCharge(chargeId);
        ChargeSlab slab = new ChargeSlab();
        slab.setChargeId(chargeId);
        slab.setSlabAttribute(request.getSlabAttribute());
        slab.setValueFrom(request.getValueFrom());
        slab.setValueTo(request.getValueTo());
        slab.setChargeAmount(request.getChargeAmount());
        slab.setCurrencyId(request.getCurrencyId());

        return toResponse(slabRepository.save(slab));
    }

    @Override
    public List<ChargeSlabResponse> getSlabsForCharge(Integer chargeId) {
        return slabRepository.findByChargeId(chargeId)
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Override
    public ChargeSlabResponse getApplicableSlab(Integer chargeId, BigDecimal value) {
        ChargeSlab slab = slabRepository.findApplicableSlab(chargeId, value)
            .orElseThrow(() -> new EntityNotFoundException(
                "No slab found for charge " + chargeId + " at value " + value));
        return toResponse(slab);
    }

    @Override
    public ChargeSlabResponse updateSlab(Integer slabId, UpdateChargeSlabRequest request) {
        ChargeSlab slab = slabRepository.findById(slabId)
            .orElseThrow(() -> new EntityNotFoundException("Slab not found: " + slabId));
        guardCharge(slab.getChargeId());

        slab.setSlabAttribute(request.getSlabAttribute());
        slab.setValueFrom(request.getValueFrom());
        slab.setValueTo(request.getValueTo());
        slab.setChargeAmount(request.getChargeAmount());
        slab.setCurrencyId(request.getCurrencyId());

        return toResponse(slabRepository.save(slab));
    }

    @Override
    public void deleteSlab(Integer slabId) {
        ChargeSlab slab = slabRepository.findById(slabId)
                .orElseThrow(() -> new EntityNotFoundException("Slab not found: " + slabId));
        guardCharge(slab.getChargeId());
        slabRepository.delete(slab);
    }

    private void guardCharge(Integer chargeId) {
        Integer productId = chargeRepository.findById(chargeId)
                .orElseThrow(() -> new EntityNotFoundException("Charge config not found: " + chargeId)).getProductId();
        integrity.assertEditable(productId);
    }

    private ChargeSlabResponse toResponse(ChargeSlab slab) {
        ChargeSlabResponse response = new ChargeSlabResponse();
        response.setSlabId(slab.getSlabId());
        response.setSlabAttribute(slab.getSlabAttribute());
        response.setValueFrom(slab.getValueFrom());
        response.setValueTo(slab.getValueTo());
        response.setChargeAmount(slab.getChargeAmount());
        response.setCurrencyId(slab.getCurrencyId());
        return response;
    }
}
