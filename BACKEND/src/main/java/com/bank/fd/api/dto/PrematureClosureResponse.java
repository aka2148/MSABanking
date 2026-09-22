package com.bank.fd.api.dto;

import com.bank.fd.entity.FdPrematureClosure;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Typed two-step premature-closure response for the /fd/v1 academic demonstration. Never serializes entities. */
public record PrematureClosureResponse(
        @JsonProperty("closure_id") Long closureId,
        @JsonProperty("account_id") Long accountId,
        @JsonProperty("status") String status,
        @JsonProperty("requested_date") OffsetDateTime requestedDate,
        @JsonProperty("penalty_points") BigDecimal penaltyPoints,
        @JsonProperty("penalized_rate") BigDecimal penalizedRate,
        @JsonProperty("penalty_amount") BigDecimal penaltyAmount,
        @JsonProperty("net_payable_amount") BigDecimal netPayableAmount,
        @JsonProperty("currency_code") String currencyCode,
        @JsonProperty("approved_by") Long approvedBy) {

    public static PrematureClosureResponse requested(FdPrematureClosure closure,
                                                     BigDecimal penalizedRate,
                                                     String currencyCode) {
        return new PrematureClosureResponse(
                closure.getId(),
                closure.getAccountId(),
                closure.getStatus(),
                closure.getClosureDate(),
                closure.getPenaltyPctApplied(),
                penalizedRate,
                closure.getPenaltyAmount(),
                closure.getNetPayableAmount(),
                currencyCode,
                null);
    }

    public static PrematureClosureResponse approved(FdPrematureClosure closure,
                                                    BigDecimal penalizedRate,
                                                    String currencyCode) {
        return new PrematureClosureResponse(
                closure.getId(),
                closure.getAccountId(),
                closure.getStatus(),
                closure.getClosureDate(),
                closure.getPenaltyPctApplied(),
                penalizedRate,
                closure.getPenaltyAmount(),
                closure.getNetPayableAmount(),
                currencyCode,
                closure.getApprovedBy());
    }
}
