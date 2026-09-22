package com.bank.fd.api.dto;

import com.bank.fd.entity.FdAccount;
import com.bank.fd.entity.FdInterestAccrual;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Typed accrual/maturity response for the /fd/v1 academic demonstration. Never serializes entities. */
public record AccrualResponse(
        @JsonProperty("account_id") Long accountId,
        @JsonProperty("account_number") String accountNumber,
        @JsonProperty("accrual_date") LocalDate accrualDate,
        @JsonProperty("accrued_amount") BigDecimal accruedAmount,
        @JsonProperty("cumulative_accrued") BigDecimal cumulativeAccrued,
        @JsonProperty("currency_code") String currencyCode,
        @JsonProperty("maturity_amount") BigDecimal maturityAmount,
        @JsonProperty("policy_version") String policyVersion,
        @JsonProperty("status") String status) {

    public static AccrualResponse accrued(FdAccount account, FdInterestAccrual row,
                                          BigDecimal cumulative, String currencyCode) {
        return new AccrualResponse(
                account.getId(),
                account.getAccountNumber(),
                row.getAccrualDate() == null ? null : row.getAccrualDate().toLocalDate(),
                row.getAccruedAmount(),
                cumulative,
                currencyCode,
                account.getMaturityAmount(),
                account.getPolicyVersion(),
                account.getStatus().name());
    }

    public static AccrualResponse matured(FdAccount account, BigDecimal accrued, BigDecimal cumulative,
                                          BigDecimal maturity, String currencyCode, LocalDate accrualDate) {
        return new AccrualResponse(
                account.getId(),
                account.getAccountNumber(),
                accrualDate,
                accrued,
                cumulative,
                currencyCode,
                maturity,
                account.getPolicyVersion(),
                account.getStatus().name());
    }
}
