package com.bank.product.service;

import com.bank.common.exception.*;
import com.bank.product.dto.request.*;
import com.bank.product.dto.response.*;
import com.bank.product.entity.ProductRoleConfig;
import com.bank.product.repository.*;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class ProductRoleConfigService {

    private final ProductRoleConfigRepository repo;
    private final ProductRepository products;
    private final RoleTypeRepository roles;

    public ProductRoleConfigService(
            ProductRoleConfigRepository repo,
            ProductRepository products,
            RoleTypeRepository roles) {
        this.repo = repo;
        this.products = products;
        this.roles = roles;
    }

    private void product(Integer id) {
        if (id == null || id < 1) {
            throw new InvalidInputException("productId must be positive");
        }

        if (!products.existsById(id)) {
            throw new ResourceNotFoundException(
                    "Product not found with ID: " + id);
        }
    }

    private void role(Integer id) {
        if (id == null || id < 1) {
            throw new InvalidInputException("roleTypeId must be positive");
        }

        if (!roles.existsById(id)) {
            throw new ResourceNotFoundException(
                    "Role type not found with ID: " + id);
        }
    }

    private void counts(Integer min, Integer max) {
        if (min != null && max != null && min > max) {
            throw new InvalidInputException(
                    "minCount cannot be greater than maxCount");
        }
    }

    @Transactional
    public ProductRoleConfigResponse create(
            Integer pid,
            ProductRoleConfigRequest r) {

        product(pid);
        role(r.getRoleTypeId());
        counts(r.getMinCount(), r.getMaxCount());

        if (repo.existsByProductIdAndRoleTypeId(pid, r.getRoleTypeId())) {
            throw new DuplicateResourceException(
                    "Role type is already configured for this product");
        }

        ProductRoleConfig e = new ProductRoleConfig();
        e.setProductId(pid);

        apply(e, r);

        e.setCreatedBy("SYSTEM");
        e.setCreatedDate(LocalDateTime.now());
        e.setCrudValue("C");
        e.setUuid(UUID.randomUUID().toString());

        return map(repo.save(e));
    }

    @Transactional(readOnly = true)
    public List<ProductRoleConfigResponse> list(Integer pid) {
        product(pid);

        return repo.findByProductId(pid)
                .stream()
                .map(this::map)
                .toList();
    }

    @Transactional
    public ProductRoleConfigResponse update(
            Integer pid,
            Integer roleId,
            UpdateProductRoleConfigRequest r) {

        product(pid);
        role(roleId);
        counts(r.getMinCount(), r.getMaxCount());

        ProductRoleConfig e = repo.findByProductIdAndRoleTypeId(pid, roleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Role configuration not found for productId=" + pid
                        + " and roleTypeId=" + roleId));

        applyUpdate(e, r);

        e.setUpdatedBy("SYSTEM");
        e.setUpdatedDate(LocalDateTime.now());
        e.setCrudValue("U");

        return map(repo.save(e));
    }

    @Transactional
    public void delete(Integer pid, Integer roleId) {
        product(pid);
        role(roleId);

        ProductRoleConfig e = repo.findByProductIdAndRoleTypeId(pid, roleId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Role configuration not found for productId=" + pid
                        + " and roleTypeId=" + roleId));

        repo.delete(e);
    }

    @Transactional(readOnly = true)
    public ValidationResponse validate(
            Integer pid,
            ValidateRoleConfigRequest req) {

        product(pid);

        List<String> errors = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();

        for (ProductRoleConfigRequest r : req.getConfigs()) {
            Integer id = r.getRoleTypeId();

            if (id == null) {
                errors.add("roleTypeId is required");
                continue;
            }

            if (!seen.add(id)) {
                errors.add("Duplicate roleTypeId: " + id);
            }

            if (!roles.existsById(id)) {
                errors.add("Role type not found: " + id);
            }

            if (r.getMinCount() != null
                    && r.getMaxCount() != null
                    && r.getMinCount() > r.getMaxCount()) {
                errors.add(
                        "minCount cannot be greater than maxCount for roleTypeId " + id);
            }

            if (Boolean.TRUE.equals(r.getMandatory())
                    && (r.getMinCount() == null || r.getMinCount() < 1)) {
                errors.add(
                        "Mandatory roleTypeId " + id + " must have minCount >= 1");
            }
        }

        return new ValidationResponse(errors.isEmpty(), errors);
    }

    private void apply(ProductRoleConfig e, ProductRoleConfigRequest r) {
        e.setRoleTypeId(r.getRoleTypeId());
        e.setMandatory(Boolean.TRUE.equals(r.getMandatory()));
        e.setMinCount(r.getMinCount());
        e.setMaxCount(r.getMaxCount());
    }

    private void applyUpdate(
            ProductRoleConfig e,
            UpdateProductRoleConfigRequest r) {

        if (r.getMandatory() != null) {
            e.setMandatory(r.getMandatory());
        }

        if (r.getMinCount() != null) {
            e.setMinCount(r.getMinCount());
        }

        if (r.getMaxCount() != null) {
            e.setMaxCount(r.getMaxCount());
        }

        if (e.getMinCount() != null
                && e.getMaxCount() != null
                && e.getMinCount() > e.getMaxCount()) {
            throw new InvalidInputException(
                    "minCount cannot be greater than maxCount");
        }
    }

    private ProductRoleConfigResponse map(ProductRoleConfig e) {
        ProductRoleConfigResponse r = new ProductRoleConfigResponse();

        r.setId(e.getId());
        r.setProductId(e.getProductId());
        r.setRoleTypeId(e.getRoleTypeId());
        r.setMandatory(e.getMandatory());
        r.setMinCount(e.getMinCount());
        r.setMaxCount(e.getMaxCount());
        r.setCreatedDate(e.getCreatedDate());
        r.setUpdatedDate(e.getUpdatedDate());

        return r;
    }
}