package com.bank.product.controller;

import com.bank.product.dto.request.CreateFdOfferRequest;
import com.bank.product.dto.response.FdOfferAdminResponse;
import com.bank.product.service.FdOfferAdminService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Staff-only authoring surface for FD catalog products and their rate schedules. */
@RestController
@RequestMapping("/api/v1/fd-products")
@PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
public class FdOfferAdminController {
    private final FdOfferAdminService service;

    public FdOfferAdminController(FdOfferAdminService service) {
        this.service = service;
    }

    @PostMapping
    @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.CREATED)
    public FdOfferAdminResponse create(@Valid @RequestBody CreateFdOfferRequest request) {
        return service.create(request);
    }

    @GetMapping
    public List<FdOfferAdminResponse> list() {
        return service.list();
    }

    @PostMapping("/{fdProductId}/activate")
    public FdOfferAdminResponse activate(@PathVariable Long fdProductId) {
        return service.activate(fdProductId);
    }
}
