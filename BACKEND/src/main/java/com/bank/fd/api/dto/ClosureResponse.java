package com.bank.fd.api.dto;

import com.bank.fd.entity.FdAccount;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Typed normal-closure response for the /fd/v1 academic demonstration. Never serializes entities. */
public record ClosureResponse(
        @JsonProperty("account_id") Long accountId,
        @JsonProperty("account_number") String accountNumber,
        @JsonProperty("status") String status,
        @JsonProperty("closed_date") LocalDate closedDate,
        @JsonProperty("settlement_amount") BigDecimal settlementAmount,
        @JsonProperty("currency_code") String currencyCode,
        @JsonProperty("policy_version") String policyVersion,
        @JsonProperty("remark") String remark) {

    public static ClosureResponse closed(FdAccount account, BigDecimal settlementAmount,
                                         String currencyCode, LocalDate closedDate) {
        return new ClosureResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getStatus().name(),
                closedDate,
                settlementAmount,
                currencyCode,
                account.getPolicyVersion(),
                "Internal FD settlement to original savings account; no external payment rail was called.");
    }
}
