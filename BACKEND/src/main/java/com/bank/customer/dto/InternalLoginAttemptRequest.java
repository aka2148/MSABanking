package com.bank.customer.dto;

public record InternalLoginAttemptRequest(
        boolean successful,
        String ipAddress) {
}