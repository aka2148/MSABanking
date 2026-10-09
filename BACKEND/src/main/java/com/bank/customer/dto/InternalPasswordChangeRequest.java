package com.bank.customer.dto;

import jakarta.validation.constraints.NotBlank;

public record InternalPasswordChangeRequest(
        String currentPassword,
        @NotBlank String newPassword,
        boolean reset) {
}