package com.bank.fd.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record SavingsTransferRequest(
        @NotNull @Positive @JsonProperty("source_account_id") Long sourceAccountId,
        @NotBlank @Size(max = 50) @JsonProperty("destination_account_number") String destinationAccountNumber,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 14, fraction = 4) BigDecimal amount,
        @Size(max = 500) String remark) {
}
