package com.bank.customer.dto;

import jakarta.validation.constraints.NotBlank;

public record InternalCredentialValidationRequest(
        @NotBlank String username,
        @NotBlank String password) {
}