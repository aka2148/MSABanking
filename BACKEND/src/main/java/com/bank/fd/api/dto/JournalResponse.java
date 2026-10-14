package com.bank.fd.api.dto;
import com.bank.fd.entity.FdJournal;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
public record JournalResponse(
    @JsonProperty("journal_id") Long journalId, String operation,
    @JsonProperty("posted_at") OffsetDateTime postedAt,
    @JsonProperty("savings_account_id") Long savingsAccountId,
    @JsonProperty("currency_code") String currencyCode,
    @JsonProperty("total_debit") BigDecimal totalDebit,
    @JsonProperty("total_credit") BigDecimal totalCredit,
    @JsonProperty("savings_balance_after") BigDecimal savingsBalanceAfter,
    List<Line> lines) {
    public record Line(@JsonProperty("account_code") String accountCode, BigDecimal debit, BigDecimal credit) { }
    public static JournalResponse from(FdJournal j, String currency) {
        return new JournalResponse(j.getId(), j.getOperation(), j.getPostedAt(), j.getSavingsAccountId(),currency,
            j.getTotalDebit(),j.getTotalCredit(),j.getSavingsBalanceAfter(),
            j.getLines().stream().map(l -> new Line(l.getAccountCode(),l.getDebit(),l.getCredit())).toList());
    }
}
