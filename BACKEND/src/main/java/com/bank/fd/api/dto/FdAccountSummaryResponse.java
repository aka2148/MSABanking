package com.bank.fd.api.dto;

import com.bank.fd.entity.FdAccount;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Customer-safe FD list row; excludes internal holder, currency, and funding references. */
public record FdAccountSummaryResponse(
        @JsonProperty("account_id") Long accountId,
        @JsonProperty("account_number") String accountNumber,
        @JsonProperty("product_code") String productCode,
        @JsonProperty("product_name") String productName,
        @JsonProperty("currency_code") String currencyCode,
        @JsonProperty("principal_amount") BigDecimal principalAmount,
        @JsonProperty("tenure_months") Integer tenureMonths,
        @JsonProperty("annual_rate") BigDecimal annualRate,
        @JsonProperty("open_date") LocalDate openDate,
        @JsonProperty("maturity_date") LocalDate maturityDate,
        @JsonProperty("maturity_amount") BigDecimal maturityAmount,
        String status,
        @JsonProperty("funding_recorded") boolean fundingRecorded) {

    public static FdAccountSummaryResponse from(
            FdAccount account, String productCode, String productName, String currencyCode) {
        return new FdAccountSummaryResponse(account.getId(), account.getAccountNumber(), productCode,
                productName, currencyCode, account.getPrincipalAmount(), account.getTenureMonths(),
                account.getInterestRate(), account.getBookingDate(), account.getMaturityDate(),
                account.getMaturityAmount(), account.getStatus().name(),
                account.getStatus() != com.bank.fd.entity.FdStatus.PENDING);
    }
}
