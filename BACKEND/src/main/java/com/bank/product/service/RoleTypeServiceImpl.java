package com.bank.product.service;

import com.bank.common.exception.DuplicateResourceException;
import com.bank.common.exception.ResourceNotFoundException;
import com.bank.product.dto.request.CreateRoleTypeRequest;
import com.bank.product.dto.request.UpdateRoleTypeRequest;
import com.bank.product.dto.response.RoleTypeResponse;
import com.bank.product.entity.RoleType;
import com.bank.product.repository.RoleTypeRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RoleTypeServiceImpl implements RoleTypeService {
    private final RoleTypeRepository repository;

    public RoleTypeServiceImpl(RoleTypeRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional
    public RoleTypeResponse create(CreateRoleTypeRequest request) {
        String code = request.getRoleTypeCode().trim();
        if (repository.findByRoleTypeCode(code).isPresent()) {
            throw new DuplicateResourceException("Role type code already exists: " + code);
        }

        RoleType entity = new RoleType();
        entity.setRoleTypeCode(code);
        entity.setDescription(request.getDescription());
        entity.setIsActive(true);
        entity.setCrudValue("C");
        entity.setUuid(UUID.randomUUID().toString());
        entity.setCreatedBy("SYSTEM");
        entity.setCreatedDate(LocalDateTime.now());
        return toResponse(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleTypeResponse> getAll() {
        return repository.findAll().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RoleTypeResponse getById(Integer id) {
        return toResponse(repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role type not found with ID: " + id)));
    }

    @Override
    @Transactional
    public RoleTypeResponse update(Integer id, UpdateRoleTypeRequest request) {
        RoleType entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role type not found with ID: " + id));

        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        entity.setCrudValue("U");
        entity.setUpdatedBy("SYSTEM");
        entity.setUpdatedDate(LocalDateTime.now());
        return toResponse(repository.save(entity));
    }

    @Override
    @Transactional
    public RoleTypeResponse updateStatus(Integer id, Boolean isActive) {
        RoleType entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role type not found with ID: " + id));

        entity.setIsActive(isActive);
        entity.setCrudValue("U");
        entity.setUpdatedBy("SYSTEM");
        entity.setUpdatedDate(LocalDateTime.now());
        return toResponse(repository.save(entity));
    }

    private RoleTypeResponse toResponse(RoleType entity) {
        RoleTypeResponse response = new RoleTypeResponse();
        response.setRoleTypeId(entity.getRoleTypeId());
        response.setRoleTypeCode(entity.getRoleTypeCode());
        response.setDescription(entity.getDescription());
        response.setIsActive(entity.getIsActive());
        response.setCreatedDate(entity.getCreatedDate());
        response.setUpdatedDate(entity.getUpdatedDate());
        return response;
    }
}
