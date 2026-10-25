package com.bank.fdservice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/fd/v1/staff")
@PreAuthorize("hasAnyRole('BANK_OFFICER','ADMIN')")
public class StaffAccountController {
    private final JdbcTemplate jdbc;
    private final PostingService postings;

    public StaffAccountController(JdbcTemplate jdbc, PostingService postings) { this.jdbc = jdbc; this.postings = postings; }

    @GetMapping("/accounts")
    public Page<FdRow> fixedDeposits(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int pageSize,
                                    @RequestParam(required = false) String status) {
        int safePage = Math.max(0, page), size = Math.min(100, Math.max(1, pageSize));
        String filter = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        if (filter != null && !List.of("PENDING", "ACTIVE", "MATURED", "CLOSED", "PREMATURELY_CLOSED").contains(filter))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown fixed-deposit status");
        String where = filter == null ? "" : " WHERE a.fa_status=?";
        Object[] args = filter == null ? new Object[]{} : new Object[]{filter};
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM fd_accounts a" + where, Long.class, args);
        List<FdRow> rows = jdbc.query("SELECT a.fa_account_id,a.fa_account_number,a.fa_status,a.fa_principal_amount,c.fcr_iso_code,a.fa_interest_rate,a.fa_tenure_months,a.fa_open_date,a.fa_maturity_date,u.fur_full_name,p.fpr_product_name FROM fd_accounts a JOIN fd_currency_ref c ON c.fcr_currency_id=a.fa_currency_id LEFT JOIN fd_user_ref u ON u.fur_user_id=a.fa_primary_holder_id LEFT JOIN fd_product_ref p ON p.fpr_product_id=a.fa_product_id" + where + " ORDER BY a.fa_account_id DESC LIMIT ? OFFSET ?",
                (rs, n) -> new FdRow(rs.getLong(1), mask(rs.getString(2)), rs.getString(3), rs.getBigDecimal(4), rs.getString(5), rs.getBigDecimal(6), rs.getInt(7), str(rs.getTimestamp(8)), str(rs.getTimestamp(9)), rs.getString(10), rs.getString(11)), append(args, size, (long) safePage * size));
        return new Page<>(rows, safePage, size, total);
    }

    @GetMapping("/savings-accounts")
    public Page<SavingsRow> savings(@RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int pageSize,
                                    @RequestParam(required = false) String status) {
        int safePage = Math.max(0, page), size = Math.min(100, Math.max(1, pageSize));
        String filter = status == null || status.isBlank() ? null : status.trim().toUpperCase();
        if (filter != null && !List.of("ACTIVE", "INACTIVE").contains(filter)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown savings status");
        String where = filter == null ? "" : " WHERE s.sa_status=?";
        Object[] args = filter == null ? new Object[]{} : new Object[]{filter};
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM savings_accounts s" + where, Long.class, args);
        List<SavingsRow> rows = jdbc.query("SELECT s.sa_account_id,s.sa_account_number,s.sa_display_name,s.sa_balance,c.fcr_iso_code,c.fcr_decimal_places,s.sa_status,s.sa_created_date,u.fur_full_name FROM savings_accounts s JOIN fd_currency_ref c ON c.fcr_currency_id=s.sa_currency_id LEFT JOIN fd_user_ref u ON u.fur_user_id=s.sa_holder_id" + where + " ORDER BY s.sa_account_id DESC LIMIT ? OFFSET ?",
                (rs, n) -> new SavingsRow(rs.getLong(1), mask(rs.getString(2)), rs.getString(3), rs.getBigDecimal(4), rs.getString(5), rs.getInt(6), rs.getString(7), str(rs.getTimestamp(8)), rs.getString(9)), append(args, size, (long) safePage * size));
        return new Page<>(rows, safePage, size, total);
    }

    @GetMapping("/savings-accounts/{id}/transactions")
    public List<Map<String, Object>> transactions(@PathVariable long id) {
        if (requireSavings(id, false) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Savings account not found");
        return jdbc.queryForList("SELECT st_id,st_reference_id,st_entry_type,st_amount,st_balance_after,st_remarks,st_posted_at FROM savings_transactions WHERE st_account_id=? ORDER BY st_posted_at DESC,st_id DESC LIMIT 100", id);
    }

    @PostMapping("/savings-accounts/{id}/deposit")
    @Transactional
    public SavingsRow deposit(Authentication actor, @PathVariable long id, @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) String key, @Valid @RequestBody MoneyRequest request) {
        return transact(actor, id, key, request, true);
    }

    @PostMapping("/savings-accounts/{id}/withdraw")
    @Transactional
    public SavingsRow withdraw(Authentication actor, @PathVariable long id, @RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) String key, @Valid @RequestBody MoneyRequest request) {
        return transact(actor, id, key, request, false);
    }

    private SavingsRow transact(Authentication actor, long id, String key, MoneyRequest request, boolean credit) {
        String scope = credit ? "STAFF_SAVINGS_DEPOSIT" : "STAFF_SAVINGS_WITHDRAWAL";
        String actorKey = actor.getName();
        String hash = hash(id + "|" + request.amount.toPlainString() + "|" + (request.remarks == null ? "" : request.remarks));
        try { jdbc.update("INSERT INTO fd_operation_idempotency(foi_operation_scope,foi_actor_key,foi_idempotency_key,foi_request_hash,foi_status) VALUES(?,?,?,?,'PENDING')", scope, actorKey, key, hash); }
        catch (DuplicateKeyException e) {
            Map<String, Object> existing = jdbc.queryForMap("SELECT foi_request_hash,foi_status,foi_aggregate_id FROM fd_operation_idempotency WHERE foi_operation_scope=? AND foi_actor_key=? AND foi_idempotency_key=?", scope, actorKey, key);
            if (!hash.equals(existing.get("foi_request_hash"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key was already used for a different transaction");
            if ("COMPLETED".equals(existing.get("foi_status"))) return savingsRow(id);
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Transaction is already in progress");
        }
        Map<String, Object> account = jdbc.query("SELECT sa_currency_id,sa_balance,sa_status FROM savings_accounts WHERE sa_account_id=? FOR UPDATE", rs -> rs.next() ? Map.of("currency", rs.getLong(1), "balance", rs.getBigDecimal(2), "status", rs.getString(3)) : null, id);
        if (account == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Savings account not found");
        if (!"ACTIVE".equals(account.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "Savings account is not active");
        long currencyId = ((Number) account.get("currency")).longValue();
        Integer scale = jdbc.queryForObject("SELECT fcr_decimal_places FROM fd_currency_ref WHERE fcr_currency_id=?", Integer.class, currencyId);
        int decimals = scale == null ? 2 : scale;
        BigDecimal amount;
        try { amount = request.amount.setScale(decimals, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Amount exceeds the currency decimal scale"); }
        String reference = "STAFF-" + UUID.randomUUID();
        PostingService.MovementResult movement = postings.postSavingsMovement(id,currencyId,amount,credit?"CREDIT":"DEBIT",reference,
                request.remarks == null ? "Staff teller transaction" : request.remarks.trim(),actorKey,
                (credit?"TELLER_DEPOSIT:":"TELLER_WITHDRAWAL:")+reference,credit?"TELLER_DEPOSIT":"TELLER_WITHDRAWAL",
                credit?"CASH_TELLER":"SAVINGS",credit?"SAVINGS":"CASH_TELLER");
        jdbc.update("UPDATE fd_operation_idempotency SET foi_status='COMPLETED',foi_aggregate_id=?,foi_response_reference=?,foi_completed_at=UTC_TIMESTAMP(6) WHERE foi_operation_scope=? AND foi_actor_key=? AND foi_idempotency_key=?", id, reference, scope, actorKey, key);
        return savingsRow(id);
    }

    private SavingsRow savingsRow(long id) {
        return jdbc.query("SELECT s.sa_account_id,s.sa_account_number,s.sa_display_name,s.sa_balance,c.fcr_iso_code,c.fcr_decimal_places,s.sa_status,s.sa_created_date,u.fur_full_name FROM savings_accounts s JOIN fd_currency_ref c ON c.fcr_currency_id=s.sa_currency_id LEFT JOIN fd_user_ref u ON u.fur_user_id=s.sa_holder_id WHERE s.sa_account_id=?", rs -> { if (!rs.next()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Savings account not found"); return new SavingsRow(rs.getLong(1), mask(rs.getString(2)), rs.getString(3), rs.getBigDecimal(4), rs.getString(5), rs.getInt(6), rs.getString(7), str(rs.getTimestamp(8)), rs.getString(9)); }, id);
    }

    private Map<String, Object> requireSavings(long id, boolean lock) {
        return jdbc.query("SELECT sa_account_id FROM savings_accounts WHERE sa_account_id=?" + (lock ? " FOR UPDATE" : ""), rs -> rs.next() ? Map.of("id", rs.getLong(1)) : null, id);
    }
    private static String mask(String value) { if (value == null) return "—"; return value.length() <= 4 ? "••••" : "••••" + value.substring(value.length() - 4); }
    private static String str(java.sql.Timestamp value) { return value == null ? null : value.toInstant().toString(); }
    private static Object[] append(Object[] values, Object... tail) { Object[] all = java.util.Arrays.copyOf(values, values.length + tail.length); System.arraycopy(tail, 0, all, values.length, tail.length); return all; }
    private static String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException(e); } }

    public record Page<T>(List<T> content, int number, int size, long totalElements) {}
    public record FdRow(long id, String accountNumber, String status, BigDecimal principal, String currency, BigDecimal rate, int tenureMonths, String openedAt, String maturityAt, String customerName, String productName) {}
    public record SavingsRow(long id, String accountNumber, String displayName, BigDecimal balance, String currency, int decimalPlaces, String status, String createdAt, String customerName) {}
    public record MoneyRequest(@NotNull @DecimalMin("0.01") BigDecimal amount, @Size(max = 500) String remarks) {}
}
