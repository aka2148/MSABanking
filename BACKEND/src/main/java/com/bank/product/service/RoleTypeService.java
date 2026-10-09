package com.bank.product.service;

import com.bank.product.dto.request.CreateRoleTypeRequest;
import com.bank.product.dto.request.UpdateRoleTypeRequest;
import com.bank.product.dto.response.RoleTypeResponse;

import java.util.List;

public interface RoleTypeService {
    RoleTypeResponse create(CreateRoleTypeRequest request);
    List<RoleTypeResponse> getAll();
    RoleTypeResponse getById(Integer id);
    RoleTypeResponse update(Integer id, UpdateRoleTypeRequest request);
    RoleTypeResponse updateStatus(Integer id, Boolean isActive);
}
