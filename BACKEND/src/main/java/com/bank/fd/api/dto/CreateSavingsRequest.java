package com.bank.fd.api.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** No client-controlled owner, balance, status, or interest terms. */
public record CreateSavingsRequest(
    @NotBlank @Size(max = 80) @JsonProperty("display_name") String displayName,
    @NotBlank @Size(max = 10) @JsonProperty("currency_code") String currencyCode) { }
