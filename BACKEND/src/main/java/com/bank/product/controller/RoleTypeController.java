package com.bank.product.controller;

import com.bank.product.dto.request.CreateRoleTypeRequest;
import com.bank.product.dto.request.UpdateRoleTypeRequest;
import com.bank.product.dto.request.UpdateRoleTypeStatusRequest;
import com.bank.product.dto.response.RoleTypeResponse;
import com.bank.product.service.RoleTypeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/role-types")
public class RoleTypeController {
    private final RoleTypeService service;

    public RoleTypeController(RoleTypeService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoleTypeResponse> create(@Valid @RequestBody CreateRoleTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(request));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public ResponseEntity<List<RoleTypeResponse>> getAll() {
        return ResponseEntity.ok(service.getAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
    public ResponseEntity<RoleTypeResponse> getById(@PathVariable @Positive Integer id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoleTypeResponse> update(@PathVariable @Positive Integer id,
                                                    @Valid @RequestBody UpdateRoleTypeRequest request) {
        return ResponseEntity.ok(service.update(id, request));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<RoleTypeResponse> updateStatus(@PathVariable @Positive Integer id,
                                                          @Valid @RequestBody UpdateRoleTypeStatusRequest request) {
        return ResponseEntity.ok(service.updateStatus(id, request.getIsActive()));
    }
}
