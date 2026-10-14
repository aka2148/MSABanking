package com.bank.fd.api.dto;

import com.bank.fd.entity.FdAccount;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Active account response for the internal academic funding operation. */
public record FundingResponse(
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
        @JsonProperty("funding_recorded") boolean fundingRecorded,
        @JsonProperty("funding_description") String fundingDescription) {

    public static FundingResponse active(FdAccount account, String productCode, String currencyCode) {
        return new FundingResponse(account.getId(), account.getAccountNumber(), account.getProductId(), productCode,
                currencyCode, account.getPrincipalAmount(), account.getTenureMonths(), account.getInterestRate(),
                account.getCompoundingFrequency(), account.getPayoutFrequency(), account.getBookingDate(),
                account.getMaturityDate(), account.getMaturityAmount(), account.getPolicyVersion(),
                account.getStatus().name(), true,
                "Internal savings-to-FD funding; no external payment rail was called.");
    }
}
