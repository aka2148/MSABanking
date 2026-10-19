package com.bank.fd.api.dto;

import com.bank.fd.entity.SavingsTransaction;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record SavingsTransferResponse(
        @JsonProperty("transaction_reference") String transactionReference,
        BigDecimal amount,
        @JsonProperty("currency_code") String currencyCode,
        String remark,
        @JsonProperty("posted_at") OffsetDateTime postedAt,
        List<Posting> postings) {

    public record Posting(
            @JsonProperty("account_number") String accountNumber,
            @JsonProperty("entry_type") String entryType,
            BigDecimal amount,
            @JsonProperty("balance_after") BigDecimal balanceAfter) { }

    public static SavingsTransferResponse of(String reference, BigDecimal amount, String currencyCode,
                                             String remark, OffsetDateTime postedAt,
                                             List<SavingsTransaction> entries, String sourceAccountNumber,
                                             String destinationAccountNumber) {
        List<Posting> postings = entries.stream().map(entry -> {
            String accountNumber = entry.getEntryType().equals("DEBIT")
                    ? sourceAccountNumber : destinationAccountNumber;
            return new Posting(accountNumber, entry.getEntryType(), entry.getAmount(),
                    entry.getBalanceAfter());
        }).toList();
        return new SavingsTransferResponse(reference, amount, currencyCode, remark, postedAt, postings);
    }
}
