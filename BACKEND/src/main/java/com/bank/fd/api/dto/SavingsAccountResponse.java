package com.bank.fd.api.dto;
import com.bank.fd.entity.SavingsAccount;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record SavingsAccountResponse(
    @JsonProperty("account_id") Long accountId,
    @JsonProperty("account_number") String accountNumber,
    @JsonProperty("display_name") String displayName,
    @JsonProperty("currency_code") String currencyCode,
    BigDecimal balance, String status,
    @JsonProperty("created_date") OffsetDateTime createdDate) {
    public static SavingsAccountResponse from(SavingsAccount a, String currency) {
        return new SavingsAccountResponse(a.getId(), a.getAccountNumber(), a.getDisplayName(),
                currency, a.getBalance(), a.getStatus(), a.getCreatedDate());
    }
}
