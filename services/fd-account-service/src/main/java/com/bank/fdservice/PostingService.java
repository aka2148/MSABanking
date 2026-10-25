package com.bank.fdservice;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PostingService {
    private final JdbcTemplate jdbc;

    public PostingService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.MANDATORY)
    public MovementResult postSavingsMovement(long accountId, long currencyId, BigDecimal amount,
            String entryType, String reference, String remarks, String actorUsername,
            String journalKey, String operation, String debitCode, String creditCode) {
        Map<String, Object> account = lockAccount(accountId);
        if (((Number) account.get("currency")).longValue() != currencyId)
            throw bad("Savings account currency changed");
        int scale = currencyScale(currencyId);
        BigDecimal money = scaled(amount, scale);
        if (!"ACTIVE".equals(account.get("status"))) throw conflict("Savings account is not active");
        BigDecimal prior = (BigDecimal) account.get("balance");
        BigDecimal after = "CREDIT".equals(entryType) ? prior.add(money) : prior.subtract(money);
        if (after.signum() < 0) throw conflict("Insufficient funds");
        after = after.setScale(scale, RoundingMode.UNNECESSARY);
        jdbc.update("UPDATE savings_accounts SET sa_balance=?,sa_row_version=sa_row_version+1 WHERE sa_account_id=?", after, accountId);
        jdbc.update("INSERT INTO savings_transactions(st_reference_id,st_account_id,st_entry_type,st_amount,st_currency_id,st_balance_after,st_remarks,st_posted_at) VALUES(?,?,?,?,?,?,?,UTC_TIMESTAMP(6))",
                reference, accountId, entryType, money, currencyId, after, remarks);
        List<LedgerLine> lines = List.of(
                new LedgerLine(debitCode, "SAVINGS".equals(debitCode) ? accountId : null, money, BigDecimal.ZERO.setScale(scale)),
                new LedgerLine(creditCode, "SAVINGS".equals(creditCode) ? accountId : null, BigDecimal.ZERO.setScale(scale), money));
        postJournal(journalKey, null, accountId, currencyId, operation, actorUsername, after, lines);
        return new MovementResult(reference, after, money);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public TransferResult postSavingsTransfer(long fromId, long toId, long currencyId, BigDecimal amount,
            String reference, String remarks, String actorUsername) {
        if (fromId == toId) throw bad("Source and destination savings accounts must differ");
        List<Map<String, Object>> accounts = jdbc.query(
                "SELECT sa_account_id,sa_currency_id,sa_balance,sa_status FROM savings_accounts WHERE sa_account_id IN (?,?) ORDER BY sa_account_id FOR UPDATE",
                (rs, n) -> Map.of("id", rs.getLong(1), "currency", rs.getLong(2), "balance", rs.getBigDecimal(3), "status", rs.getString(4)), fromId, toId);
        if (accounts.size() != 2) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Savings account not found");
        Map<Long, Map<String, Object>> byId = new HashMap<>();
        for (Map<String, Object> row : accounts) byId.put(((Number) row.get("id")).longValue(), row);
        Map<String, Object> source = byId.get(fromId), destination = byId.get(toId);
        int scale = currencyScale(currencyId);
        if (((Number) source.get("currency")).longValue() != currencyId || ((Number) destination.get("currency")).longValue() != currencyId)
            throw bad("Transfer accounts must use the same currency");
        if (!"ACTIVE".equals(source.get("status")) || !"ACTIVE".equals(destination.get("status")))
            throw conflict("Both savings accounts must be active");
        BigDecimal money = scaled(amount, scale);
        BigDecimal fromAfter = ((BigDecimal) source.get("balance")).subtract(money).setScale(scale, RoundingMode.UNNECESSARY);
        BigDecimal toAfter = ((BigDecimal) destination.get("balance")).add(money).setScale(scale, RoundingMode.UNNECESSARY);
        if (fromAfter.signum() < 0) throw conflict("Insufficient funds");
        jdbc.update("UPDATE savings_accounts SET sa_balance=?,sa_row_version=sa_row_version+1 WHERE sa_account_id=?", fromAfter, fromId);
        jdbc.update("UPDATE savings_accounts SET sa_balance=?,sa_row_version=sa_row_version+1 WHERE sa_account_id=?", toAfter, toId);
        jdbc.update("INSERT INTO savings_transactions(st_reference_id,st_account_id,st_entry_type,st_amount,st_currency_id,st_balance_after,st_remarks,st_posted_at) VALUES(?,?,'DEBIT',?,?,?,?,UTC_TIMESTAMP(6)),(?,?,'CREDIT',?,?,?,?,UTC_TIMESTAMP(6))",
                reference, fromId, money, currencyId, fromAfter, remarks, reference, toId, money, currencyId, toAfter, remarks);
        List<LedgerLine> lines = List.of(
                new LedgerLine("SAVINGS", fromId, money, BigDecimal.ZERO.setScale(scale)),
                new LedgerLine("SAVINGS", toId, BigDecimal.ZERO.setScale(scale), money));
        postJournal("TRANSFER:" + reference, null, fromId, currencyId, "TRANSFER", actorUsername, fromAfter, lines);
        return new TransferResult(fromAfter, toAfter, money);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long postJournal(String key, Long fdAccountId, long savingsAccountId, long currencyId,
            String operation, String actorUsername, BigDecimal savingsBalanceAfter, List<LedgerLine> lines) {
        int scale = currencyScale(currencyId);
        validateLines(lines, scale);
        List<LedgerLine> normalizedLines = lines.stream().map(line -> new LedgerLine(line.accountCode(),line.savingsAccountId(),exactScale(line.debit(),scale),exactScale(line.credit(),scale))).toList();
        for (LedgerLine line : normalizedLines) {
            if (line.savingsAccountId() != null) {
                if (!"SAVINGS".equals(line.accountCode())) throw bad("Only SAVINGS journal lines may reference a savings account");
                Long lineCurrency = jdbc.query("SELECT sa_currency_id FROM savings_accounts WHERE sa_account_id=?",rs->rs.next()?rs.getLong(1):null,line.savingsAccountId());
                if (lineCurrency == null || lineCurrency.longValue() != currencyId) throw bad("Journal savings line currency does not match the journal currency");
            }
        }
        BigDecimal debitTotal = normalizedLines.stream().map(LedgerLine::debit).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(scale);
        BigDecimal creditTotal = normalizedLines.stream().map(LedgerLine::credit).reduce(BigDecimal.ZERO, BigDecimal::add).setScale(scale);
        if (debitTotal.compareTo(creditTotal) != 0) throw bad("Journal debits and credits must balance");
        for (LedgerLine line : normalizedLines) {
            Integer active = jdbc.query("SELECT coa_active FROM chart_of_accounts WHERE coa_code=?", rs -> rs.next() ? rs.getInt(1) : null, line.accountCode());
            if (active == null || active != 1) throw bad("Journal account code is missing or inactive: " + line.accountCode());
        }
        try {
            jdbc.update("INSERT INTO fd_journals(fj_key,fj_fd_account_id,fj_savings_account_id,fj_currency_id,fj_operation,fj_posted_at,fj_total_debit,fj_total_credit,fj_savings_balance_after,fj_created_by) VALUES(?,?,?,?,?,UTC_TIMESTAMP(6),?,?,?,?)",
                    key, fdAccountId, savingsAccountId, currencyId, operation, debitTotal, creditTotal, savingsBalanceAfter, actorUsername);
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A journal already exists for business reference " + key);
        }
        Long journalId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        int sequence = 1;
        for (LedgerLine line : normalizedLines) {
            jdbc.update("INSERT INTO fd_journal_lines(jl_journal_id,jl_sequence,jl_account_code,jl_debit,jl_credit,jl_savings_account_id) VALUES(?,?,?,?,?,?)",
                    journalId, sequence++, line.accountCode(), line.debit(), line.credit(), line.savingsAccountId());
        }
        return journalId;
    }

    public static void validateLines(List<LedgerLine> lines, int scale) {
        if (lines == null || lines.size() < 2) throw bad("A journal must have at least two lines");
        BigDecimal debits = BigDecimal.ZERO.setScale(scale), credits = BigDecimal.ZERO.setScale(scale);
        for (LedgerLine line : lines) {
            if (line == null || line.accountCode() == null || line.accountCode().isBlank()) throw bad("Journal account code is required");
            BigDecimal debit = exactScale(line.debit(), scale), credit = exactScale(line.credit(), scale);
            boolean debitSide = debit.signum() > 0 && credit.signum() == 0;
            boolean creditSide = credit.signum() > 0 && debit.signum() == 0;
            if (!debitSide && !creditSide) throw bad("Each journal line must have exactly one positive side");
            debits = debits.add(debit); credits = credits.add(credit);
        }
        if (debits.compareTo(credits) != 0) throw bad("Journal debits and credits must balance");
    }

    private Map<String, Object> lockAccount(long id) {
        return jdbc.query("SELECT sa_currency_id,sa_balance,sa_status FROM savings_accounts WHERE sa_account_id=? FOR UPDATE",
                rs -> rs.next() ? Map.of("currency", rs.getLong(1), "balance", rs.getBigDecimal(2), "status", rs.getString(3)) : null, id);
    }
    private int currencyScale(long id) {
        Integer scale = jdbc.query("SELECT fcr_decimal_places FROM fd_currency_ref WHERE fcr_currency_id=?", rs -> rs.next() ? rs.getInt(1) : null, id);
        if (scale == null || scale < 0 || scale > 8) throw bad("Currency scale is unavailable or invalid");
        return scale;
    }
    private static BigDecimal scaled(BigDecimal amount, int scale) {
        if (amount == null || amount.signum() <= 0) throw bad("Amount must be positive");
        return exactScale(amount, scale);
    }
    private static BigDecimal exactScale(BigDecimal amount, int scale) {
        if (amount == null || amount.signum() < 0) throw bad("Journal amounts must not be negative");
        try { return amount.setScale(scale, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException e) { throw bad("Amount exceeds the currency decimal scale"); }
    }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }

    public record LedgerLine(String accountCode, Long savingsAccountId, BigDecimal debit, BigDecimal credit) {}
    public record MovementResult(String reference, BigDecimal balanceAfter, BigDecimal amount) {}
    public record TransferResult(BigDecimal fromBalanceAfter, BigDecimal toBalanceAfter, BigDecimal amount) {}
}
