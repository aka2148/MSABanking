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
        return postSavingsMovementInternal(null, accountId, currencyId, amount, entryType, reference, remarks,
                actorUsername, journalKey, operation, debitCode, creditCode, false);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public MovementResult postSystemCredit(long fdAccountId, long accountId, long currencyId, BigDecimal amount,
            String reference, String remarks, String actorUsername, String journalKey, String operation,
            String debitCode, String creditCode) {
        return postSavingsMovementInternal(fdAccountId, accountId, currencyId, amount, "CREDIT", reference, remarks,
                actorUsername, journalKey, operation, debitCode, creditCode, true);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public MovementResult postSystemCredit(long accountId,long currencyId,BigDecimal amount,String reference,String remarks,
            String actorUsername,String journalKey,String operation,String debitCode,String creditCode) {
        return postSavingsMovementInternal(null,accountId,currencyId,amount,"CREDIT",reference,remarks,actorUsername,
                journalKey,operation,debitCode,creditCode,true);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public MovementResult postSavingsMovement(long fdAccountId, long accountId, long currencyId, BigDecimal amount,
            String entryType, String reference, String remarks, String actorUsername,
            String journalKey, String operation, String debitCode, String creditCode) {
        return postSavingsMovementInternal(fdAccountId, accountId, currencyId, amount, entryType, reference, remarks,
                actorUsername, journalKey, operation, debitCode, creditCode, false);
    }

    private MovementResult postSavingsMovementInternal(Long fdAccountId, long accountId, long currencyId, BigDecimal amount,
            String entryType, String reference, String remarks, String actorUsername,
            String journalKey, String operation, String debitCode, String creditCode, boolean allowFrozenCredit) {
        Map<String, Object> account = lockAccount(accountId);
        if (((Number) account.get("currency")).longValue() != currencyId)
            throw bad("Savings account currency changed");
        int scale = currencyScale(currencyId);
        BigDecimal money = scaled(amount, scale);
        String status = (String) account.get("status");
        if ("CLOSED".equals(status) || (!"ACTIVE".equals(status) && !(allowFrozenCredit && "CREDIT".equals(entryType) && "INACTIVE".equals(status))))
            throw conflict("Deposit account is not active");
        BigDecimal prior = (BigDecimal) account.get("balance");
        BigDecimal after = "CREDIT".equals(entryType) ? prior.add(money) : prior.subtract(money);
        if (after.signum() < 0) throw conflict("Insufficient funds");
        if ("DEBIT".equals(entryType)) enforceDebitFloor(account, after, currencyId);
        after = after.setScale(scale, RoundingMode.UNNECESSARY);
        jdbc.update("UPDATE savings_accounts SET sa_balance=?,sa_row_version=sa_row_version+1 WHERE sa_account_id=?", after, accountId);
        jdbc.update("INSERT INTO savings_transactions(st_reference_id,st_account_id,st_entry_type,st_amount,st_currency_id,st_balance_after,st_remarks,st_posted_at) VALUES(?,?,?,?,?,?,?,UTC_TIMESTAMP(6))",
                reference, accountId, entryType, money, currencyId, after, remarks);
        List<LedgerLine> lines = List.of(
                new LedgerLine(isDepositCode(debitCode) ? debitCode(accountId) : debitCode, isDepositCode(debitCode) ? accountId : null, money, BigDecimal.ZERO.setScale(scale)),
                new LedgerLine(isDepositCode(creditCode) ? debitCode(accountId) : creditCode, isDepositCode(creditCode) ? accountId : null, BigDecimal.ZERO.setScale(scale), money));
        postJournal(journalKey, fdAccountId, accountId, currencyId, operation, actorUsername, after, lines);
        return new MovementResult(reference, after, money);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public TransferResult postSavingsTransfer(long fromId, long toId, long currencyId, BigDecimal amount,
            String reference, String remarks, String actorUsername) {
        if (fromId == toId) throw bad("Source and destination savings accounts must differ");
        List<Map<String, Object>> accounts = jdbc.query(
                "SELECT sa_account_id,sa_currency_id,sa_balance,sa_status,sa_minimum_balance FROM savings_accounts WHERE sa_account_id IN (?,?) ORDER BY sa_account_id FOR UPDATE",
                (rs, n) -> Map.of("id", rs.getLong(1), "currency", rs.getLong(2), "balance", rs.getBigDecimal(3), "status", rs.getString(4), "minimum",rs.getBigDecimal(5)), fromId, toId);
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
        enforceDebitFloor(source, fromAfter, currencyId);
        jdbc.update("UPDATE savings_accounts SET sa_balance=?,sa_row_version=sa_row_version+1 WHERE sa_account_id=?", fromAfter, fromId);
        jdbc.update("UPDATE savings_accounts SET sa_balance=?,sa_row_version=sa_row_version+1 WHERE sa_account_id=?", toAfter, toId);
        jdbc.update("INSERT INTO savings_transactions(st_reference_id,st_account_id,st_entry_type,st_amount,st_currency_id,st_balance_after,st_remarks,st_posted_at) VALUES(?,?,'DEBIT',?,?,?,?,UTC_TIMESTAMP(6)),(?,?,'CREDIT',?,?,?,?,UTC_TIMESTAMP(6))",
                reference, fromId, money, currencyId, fromAfter, remarks, reference, toId, money, currencyId, toAfter, remarks);
        List<LedgerLine> lines = List.of(
                new LedgerLine(depositLedgerCode(fromId), fromId, money, BigDecimal.ZERO.setScale(scale)),
                new LedgerLine(depositLedgerCode(toId), toId, BigDecimal.ZERO.setScale(scale), money));
        postJournal("TRANSFER:" + reference, null, fromId, currencyId, "TRANSFER", actorUsername, fromAfter, lines);
        return new TransferResult(fromAfter, toAfter, money);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ForexPostingResult postForexConversion(long customerId, long fromId, long toId,
            long fundingCurrencyId, long destinationCurrencyId, BigDecimal principal, BigDecimal fee,
            BigDecimal destinationAmount, String reference, String actorUsername) {
        if (fromId == toId) throw bad("Source and destination savings accounts must differ");
        List<ForexAccount> accounts = jdbc.query(
                "SELECT sa_account_id,sa_currency_id,sa_balance,sa_status,sa_minimum_balance FROM savings_accounts " +
                        "WHERE sa_account_id IN (?,?) AND sa_holder_id=? ORDER BY sa_account_id FOR UPDATE",
                (rs, n) -> new ForexAccount(rs.getLong(1), rs.getLong(2), rs.getBigDecimal(3), rs.getString(4),rs.getBigDecimal(5)),
                fromId, toId, customerId);
        if (accounts.size() != 2) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Savings account not found");
        Map<Long, ForexAccount> byId = new HashMap<>();
        for (ForexAccount account : accounts) byId.put(account.id(), account);
        ForexAccount source = byId.get(fromId), destination = byId.get(toId);
        if (source == null || destination == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Savings account not found");
        if (source.currencyId() != fundingCurrencyId || destination.currencyId() != destinationCurrencyId)
            throw bad("Selected savings account currency does not match the forex conversion");
        if (!"ACTIVE".equals(source.status()) || !"ACTIVE".equals(destination.status()))
            throw conflict("Both savings accounts must be active");

        int fundingScale = currencyScale(fundingCurrencyId);
        int destinationScale = currencyScale(destinationCurrencyId);
        BigDecimal fundingPrincipal = scaled(principal, fundingScale);
        BigDecimal fundingFee = exactScale(fee, fundingScale);
        BigDecimal destinationCredit = scaled(destinationAmount, destinationScale);
        BigDecimal totalDebit = fundingPrincipal.add(fundingFee).setScale(fundingScale, RoundingMode.UNNECESSARY);
        BigDecimal sourceAfter = source.balance().subtract(totalDebit).setScale(fundingScale, RoundingMode.UNNECESSARY);
        BigDecimal destinationAfter = destination.balance().add(destinationCredit).setScale(destinationScale, RoundingMode.UNNECESSARY);
        if (sourceAfter.signum() < 0) throw conflict("Insufficient funds for the conversion and fee");
        enforceDebitFloor(source.minimumBalance(), sourceAfter, fundingCurrencyId);

        jdbc.update("UPDATE savings_accounts SET sa_balance=?,sa_row_version=sa_row_version+1 WHERE sa_account_id=?", sourceAfter, fromId);
        jdbc.update("UPDATE savings_accounts SET sa_balance=?,sa_row_version=sa_row_version+1 WHERE sa_account_id=?", destinationAfter, toId);
        String sourceRemarks = "Forex conversion #" + reference + " (principal and fee)";
        String destinationRemarks = "Forex conversion #" + reference;
        jdbc.update("INSERT INTO savings_transactions(st_reference_id,st_account_id,st_entry_type,st_amount,st_currency_id,st_balance_after,st_remarks,st_posted_at) " +
                        "VALUES(?,?,'DEBIT',?,?,?,?,UTC_TIMESTAMP(6)),(?,?,'CREDIT',?,?,?,?,UTC_TIMESTAMP(6))",
                reference, fromId, totalDebit, fundingCurrencyId, sourceAfter, sourceRemarks,
                reference, toId, destinationCredit, destinationCurrencyId, destinationAfter, destinationRemarks);

        BigDecimal zeroFunding = BigDecimal.ZERO.setScale(fundingScale);
        List<LedgerLine> sourceLines = new ArrayList<>(List.of(
                new LedgerLine(depositLedgerCode(fromId), fromId, totalDebit, zeroFunding),
                new LedgerLine("FOREX_CLEARING", null, zeroFunding, fundingPrincipal)));
        if (fundingFee.signum() > 0)
            sourceLines.add(new LedgerLine("FOREX_FEE_INCOME", null, zeroFunding, fundingFee));
        postJournal(reference + ":SOURCE", null, fromId, fundingCurrencyId, "FOREX", actorUsername, sourceAfter, sourceLines);
        BigDecimal zeroDestination = BigDecimal.ZERO.setScale(destinationScale);
        postJournal(reference + ":DESTINATION", null, toId, destinationCurrencyId, "FOREX", actorUsername, destinationAfter,
                List.of(new LedgerLine("FOREX_CLEARING", null, destinationCredit, zeroDestination),
                        new LedgerLine(depositLedgerCode(toId), toId, zeroDestination, destinationCredit)));
        return new ForexPostingResult(totalDebit, sourceAfter, destinationAfter, destinationCredit);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public long postJournal(String key, Long fdAccountId, long savingsAccountId, long currencyId,
            String operation, String actorUsername, BigDecimal savingsBalanceAfter, List<LedgerLine> lines) {
        int scale = currencyScale(currencyId);
        validateLines(lines, scale);
        List<LedgerLine> normalizedLines = lines.stream().map(line -> new LedgerLine(line.accountCode(),line.savingsAccountId(),exactScale(line.debit(),scale),exactScale(line.credit(),scale))).toList();
        for (LedgerLine line : normalizedLines) {
            if (line.savingsAccountId() != null) {
                if (!isDepositCode(line.accountCode())) throw bad("Only deposit-account journal lines may reference a deposit account");
                String actualCode = depositLedgerCode(line.savingsAccountId());
                if (!actualCode.equals(line.accountCode())) throw bad("Deposit account ledger code does not match its account type");
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

    public String depositLedgerCode(long accountId) {
        String type=jdbc.query("SELECT sa_account_type FROM savings_accounts WHERE sa_account_id=?",rs->rs.next()?rs.getString(1):null,accountId);
        if(type==null)throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Deposit account not found");
        return "CURRENT".equals(type)?"CURRENT_ACCOUNT":"SAVINGS";
    }

    private String debitCode(long accountId) { return depositLedgerCode(accountId); }
    private boolean isDepositCode(String code) { return "SAVINGS".equals(code)||"CURRENT_ACCOUNT".equals(code); }
    private void enforceDebitFloor(Map<String,Object> account, BigDecimal after,long currencyId) {
        enforceDebitFloor((BigDecimal)account.get("minimum"),after,currencyId);
    }
    private void enforceDebitFloor(BigDecimal minimum,BigDecimal after,long currencyId) {
        if(after.compareTo(minimum)<0){Integer scale=jdbc.query("SELECT fcr_decimal_places FROM fd_currency_ref WHERE fcr_currency_id=?",rs->rs.next()?rs.getInt(1):null,currencyId);String currency=jdbc.query("SELECT fcr_iso_code FROM fd_currency_ref WHERE fcr_currency_id=?",rs->rs.next()?rs.getString(1):"",currencyId);throw new BelowMinimumBalanceException(minimum.setScale(scale==null?2:scale,RoundingMode.HALF_UP).toPlainString()+" "+currency);}
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
        return jdbc.query("SELECT sa_currency_id,sa_balance,sa_status,sa_minimum_balance FROM savings_accounts WHERE sa_account_id=? FOR UPDATE",
                rs -> rs.next() ? Map.of("currency", rs.getLong(1), "balance", rs.getBigDecimal(2), "status", rs.getString(3),"minimum",rs.getBigDecimal(4)) : null, id);
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
    public record ForexPostingResult(BigDecimal totalDebit, BigDecimal sourceBalanceAfter,
                                     BigDecimal destinationBalanceAfter, BigDecimal destinationAmount) {}
    private record ForexAccount(long id, long currencyId, BigDecimal balance, String status,BigDecimal minimumBalance) {}
}
