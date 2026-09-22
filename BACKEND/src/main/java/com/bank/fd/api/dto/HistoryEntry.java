package com.bank.fd.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * One full-detail timeline entry per D-06, merging {@code fd_transactions} rows
 * (funding, maturity, closure, settlement, penalty) with
 * {@code fd_interest_accrual} rows. {@code runningBalance} is null where no
 * balance is known (for example penalty records).
 */
public record HistoryEntry(
        @JsonProperty("entry_type") String entryType,
        @JsonProperty("entry_date") OffsetDateTime entryDate,
        @JsonProperty("amount") BigDecimal amount,
        @JsonProperty("currency_code") String currencyCode,
        @JsonProperty("running_balance") BigDecimal runningBalance,
        @JsonProperty("source") String source,
        @JsonProperty("description") String description) {
}
