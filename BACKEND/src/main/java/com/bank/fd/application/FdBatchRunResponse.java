package com.bank.fd.application;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

public record FdBatchRunResponse(
        @JsonProperty("business_date") LocalDate businessDate,
        @JsonProperty("accounts_examined") int accountsExamined,
        @JsonProperty("accruals_processed") int accrualsProcessed,
        @JsonProperty("maturities_processed") int maturitiesProcessed,
        @JsonProperty("accounts_failed") int accountsFailed,
        @JsonProperty("failed_account_ids") List<Long> failedAccountIds) {
}
