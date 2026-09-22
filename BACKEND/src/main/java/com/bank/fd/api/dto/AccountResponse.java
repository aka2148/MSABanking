package com.bank.fd.api.dto;

import com.bank.fd.entity.FdAccount;
import com.bank.fd.entity.FdStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Explicit truthful response for a booked FD snapshot. */
public record AccountResponse(
        @JsonProperty("account_id") Long accountId,
        @JsonProperty("account_number") String accountNumber,
        @JsonProperty("product_id") Long productId,
        @JsonProperty("product_code") String productCode,
        @JsonProperty("currency_code") String currencyCode,
        @JsonProperty("principal_amount") BigDecimal principalAmount,
        @JsonProperty("tenure_months") Integer tenureMonths,
        @JsonProperty("annual_rate") BigDecimal annualRate,
        @JsonProperty("compounding_frequency") String compoundingFrequency,
        @JsonProperty("payout_frequency") String payoutFrequency,
        @JsonProperty("open_date") LocalDate openDate,
        @JsonProperty("maturity_date") LocalDate maturityDate,
        @JsonProperty("maturity_amount") BigDecimal maturityAmount,
        @JsonProperty("policy_version") String policyVersion,
        String status,
        @JsonProperty("funding_recorded") boolean fundingRecorded) {

    public static AccountResponse pending(FdAccount account, String productCode, String currencyCode) {
        return new AccountResponse(account.getId(), account.getAccountNumber(), account.getProductId(), productCode,
                currencyCode, account.getPrincipalAmount(), account.getTenureMonths(), account.getInterestRate(),
                account.getCompoundingFrequency(), account.getPayoutFrequency(), account.getBookingDate(),
                account.getMaturityDate(), account.getMaturityAmount(), account.getPolicyVersion(),
                account.getStatus().name(), false);
    }

    /** Status snapshot for the GET /accounts/{id} read; funding is recorded for every non-pending status. */
    public static AccountResponse status(FdAccount account, String productCode, String currencyCode) {
        return new AccountResponse(account.getId(), account.getAccountNumber(), account.getProductId(), productCode,
                currencyCode, account.getPrincipalAmount(), account.getTenureMonths(), account.getInterestRate(),
                account.getCompoundingFrequency(), account.getPayoutFrequency(), account.getBookingDate(),
                account.getMaturityDate(), account.getMaturityAmount(), account.getPolicyVersion(),
                account.getStatus().name(), account.getStatus() != FdStatus.PENDING);
    }
}
