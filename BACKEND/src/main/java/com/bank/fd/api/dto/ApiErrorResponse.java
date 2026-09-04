package com.bank.fd.api.dto;

/** Matches the OpenAPI Error schema for placeholder endpoint responses. */
public record ApiErrorResponse(String code, String message) {
}
