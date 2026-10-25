package com.bank.fdservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Transaction boundary for all changes to one FD during a batch run. */
@Service
public class FdBatchAccountProcessor {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final PostingService postings;
    private final FdLifecycleRecordService lifecycleRecords;

    public FdBatchAccountProcessor(JdbcTemplate jdbc, ObjectMapper json, PostingService postings,
                                   FdLifecycleRecordService lifecycleRecords) {
        this.jdbc = jdbc;
        this.json = json;
        this.postings = postings;
        this.lifecycleRecords = lifecycleRecords;
    }

    @Transactional
    public DailyAccountResult processDaily(long id, LocalDate date) {
        Map<String, Object> fd = lockAccount(id);
        if (fd == null) return new DailyAccountResult(0, false);

        int accrualRows = 0;
        boolean matured = false;
        String status = (String) fd.get("status");
        if ("ACTIVE".equals(status)) {
            LocalDate maturity = (LocalDate) fd.get("maturity");
            if (!maturity.isAfter(date)) {
                accrualRows = matureLocked(id, date, fd);
                matured = true;
            } else {
                accrualRows = accrueOne(id, date, fd);
            }
        }

        boolean openedBeforePeriodEnd = ((LocalDateTime) fd.get("opened")).toLocalDate().isBefore(date);
        if (date.getDayOfMonth() == 1 && openedBeforePeriodEnd && isStatementEligible(status, matured)) {
            LocalDate periodStart = date.minusMonths(1).withDayOfMonth(1);
            lifecycleRecords.recordMonthlyStatementIfMissing(id, periodStart, date);
        }
        return new DailyAccountResult(accrualRows, matured);
    }

    @Transactional
    public int accrueAccount(long id, LocalDate date) {
        Map<String, Object> fd = lockAccount(id);
        if (fd == null || !"ACTIVE".equals(fd.get("status"))) return 0;
        return accrueOne(id, date, fd);
    }

    @Transactional
    public boolean matureAccount(long id, LocalDate date) {
        Map<String, Object> fd = lockAccount(id);
        if (fd == null || !"ACTIVE".equals(fd.get("status"))) return false;
        if (((LocalDate) fd.get("maturity")).isAfter(date)) return false;
        matureLocked(id, date, fd);
        return true;
    }

    private int matureLocked(long id, LocalDate batchDate, Map<String, Object> fd) {
        LocalDate maturity = (LocalDate) fd.get("maturity");
        int rows = accrueOne(id, maturity, fd);
        BigDecimal principal = (BigDecimal) fd.get("principal");
        long currency = ((Number) fd.get("currency")).longValue();
        long savings = ((Number) fd.get("savings")).longValue();
        Terms terms = terms(fd);
        BigDecimal balance = lastCompoundingBalance(id, principal).setScale(terms.currencyScale(), RoundingMode.HALF_UP);
        BigDecimal interest = balance.subtract(principal).max(BigDecimal.ZERO).setScale(terms.currencyScale(), RoundingMode.HALF_UP);

        int changed = jdbc.update("UPDATE fd_accounts SET fa_status='MATURED',fa_maturity_amount=?," +
                        "fa_updated_by='fd-batch',fa_updated_date=UTC_TIMESTAMP(6),fa_row_version=fa_row_version+1 " +
                        "WHERE fa_account_id=? AND fa_status='ACTIVE'",
                balance, id);
        if (changed != 1) return rows;

        if (interest.signum() > 0) {
            postings.postJournal("INTEREST:" + id + ":MATURITY", id, savings, currency, "INTEREST", "fd-batch", null,
                    List.of(new PostingService.LedgerLine("INTEREST_EXPENSE", null, interest,
                                    BigDecimal.ZERO.setScale(terms.currencyScale())),
                            new PostingService.LedgerLine("FIXED_DEPOSIT", null,
                                    BigDecimal.ZERO.setScale(terms.currencyScale()), interest)));
        }
        jdbc.update("INSERT INTO fd_transactions(ft_account_id,ft_txn_type_id,ft_amount,ft_balance_after,ft_currency_id,ft_remarks,ft_created_by) " +
                        "VALUES(?,?,?,?,?,?,?)",
                id, typeId("FD_MATURITY"), balance, balance, currency,
                "Maturity recorded; settlement requires the customer's explicit close action", "fd-batch");
        event(id, "MATURED", Map.of("status", "MATURED", "maturityAmount", balance, "maturityDate", maturity.toString()));
        lifecycleRecords.recordNoticeIfMissing(id, "FD_MATURED", batchDate, "fd-batch");
        return rows;
    }

    private int accrueOne(long id, LocalDate requestedEnd, Map<String, Object> fd) {
        if (!"ACTIVE".equals(fd.get("status"))) return 0;
        LocalDate start = ((LocalDateTime) fd.get("opened")).toLocalDate();
        LocalDate maturity = (LocalDate) fd.get("maturity");
        LocalDate end = requestedEnd.isAfter(maturity) ? maturity : requestedEnd;
        if (end.isBefore(start)) return 0;

        BigDecimal principal = (BigDecimal) fd.get("principal");
        Terms terms = terms(fd);
        long currency = ((Number) fd.get("currency")).longValue();
        List<Map<String, Object>> old = jdbc.queryForList(
                "SELECT fia_accrual_date,fia_accrued_amount,fia_cumulative_accrued,fia_compounding_balance,fia_capitalized_amount " +
                        "FROM fd_interest_accrual WHERE fia_account_id=? ORDER BY fia_accrual_date,fia_accrual_id", id);
        LocalDate last = start;
        BigDecimal balance = principal;
        BigDecimal cumulative = BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        BigDecimal pending = BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
        for (Map<String, Object> row : old) {
            LocalDate day = localDate(row.get("fia_accrual_date"));
            last = day;
            BigDecimal accrued = (BigDecimal) row.get("fia_accrued_amount");
            cumulative = row.get("fia_cumulative_accrued") == null ? cumulative.add(accrued) : (BigDecimal) row.get("fia_cumulative_accrued");
            if (row.get("fia_compounding_balance") != null) balance = (BigDecimal) row.get("fia_compounding_balance");
            BigDecimal capitalized = (BigDecimal) row.get("fia_capitalized_amount");
            pending = capitalized != null && capitalized.signum() > 0
                    ? BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP) : pending.add(accrued);
        }
        if (!last.isBefore(end)) return 0;

        int created = 0;
        for (LocalDate day = last.plusDays(1); !day.isAfter(end); day = day.plusDays(1)) {
            BigDecimal daily = balance.multiply(terms.annualRate())
                    .divide(BigDecimal.valueOf(365), 12, RoundingMode.HALF_UP)
                    .setScale(8, RoundingMode.HALF_UP);
            cumulative = cumulative.add(daily);
            pending = pending.add(daily);
            BigDecimal capitalized = BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
            if (FdCompounding.capitalizationDate(start, day, terms.compoundingBasis()) || day.equals(maturity)) {
                capitalized = pending.setScale(terms.currencyScale(), RoundingMode.HALF_UP).setScale(8, RoundingMode.HALF_UP);
                balance = balance.add(capitalized);
                pending = BigDecimal.ZERO.setScale(8, RoundingMode.HALF_UP);
            }
            jdbc.update("INSERT INTO fd_interest_accrual(fia_account_id,fia_accrual_date,fia_accrued_amount," +
                            "fia_cumulative_accrued,fia_currency_id,fia_compounding_balance,fia_capitalized_amount) " +
                            "VALUES(?,?,?,?,?,?,?)",
                    id, day.atStartOfDay(), daily, cumulative, currency, balance, capitalized);
            created++;
        }
        jdbc.update("UPDATE fd_accounts SET fa_maturity_amount=?,fa_updated_by='fd-batch',fa_updated_date=UTC_TIMESTAMP(6) WHERE fa_account_id=?",
                balance.add(pending).setScale(terms.currencyScale(), RoundingMode.HALF_UP), id);
        event(id, "INTEREST_ACCRUED", Map.of("throughDate", end.toString(),
                "cumulativeInterest", cumulative.setScale(terms.currencyScale(), RoundingMode.HALF_UP), "newAccrualDays", created));
        return created;
    }

    private Map<String, Object> lockAccount(long id) {
        return jdbc.query("SELECT fa_primary_holder_id,fa_principal_amount,fa_currency_id,fa_status,fa_funding_savings_account_id," +
                        "fa_open_date,fa_maturity_date,fa_interest_rate,fa_compounding_frequency,fa_terms_snapshot " +
                        "FROM fd_accounts WHERE fa_account_id=? FOR UPDATE",
                rs -> rs.next() ? accountRow(rs) : null, id);
    }

    private Map<String, Object> accountRow(ResultSet rs) throws SQLException {
        Map<String, Object> row = new HashMap<>();
        row.put("owner", rs.getLong(1));
        row.put("principal", rs.getBigDecimal(2));
        row.put("currency", rs.getLong(3));
        row.put("status", rs.getString(4));
        row.put("savings", rs.getLong(5));
        row.put("opened", rs.getObject(6, LocalDateTime.class));
        row.put("maturity", rs.getDate(7).toLocalDate());
        row.put("rate", rs.getBigDecimal(8));
        row.put("compounding", rs.getString(9));
        row.put("termsSnapshot", rs.getString(10));
        return row;
    }

    private Terms terms(Map<String, Object> fd) {
        BigDecimal rate = (BigDecimal) fd.get("rate");
        String basis = (String) fd.get("compounding");
        Integer scale = null;
        String snapshot = (String) fd.get("termsSnapshot");
        if (snapshot != null && !snapshot.isBlank()) {
            try {
                JsonNode root = json.readTree(snapshot);
                BigDecimal savedRate = decimal(root.get("annualRate"));
                if (savedRate != null) rate = savedRate;
                String savedBasis = root.path("compoundingBasis").asText(null);
                if (savedBasis != null && !savedBasis.isBlank()) basis = savedBasis;
                if (root.path("currencyScale").canConvertToInt()) scale = root.path("currencyScale").intValue();
            } catch (Exception ignored) {
                // Older rows remain usable through the copied account terms and currency reference.
            }
        }
        if (scale == null) {
            Long currency = ((Number) fd.get("currency")).longValue();
            scale = jdbc.query("SELECT fcr_decimal_places FROM fd_currency_ref WHERE fcr_currency_id=?",
                    rs -> rs.next() ? rs.getInt(1) : null, currency);
        }
        if (scale == null || scale < 0 || scale > 8 || rate == null || rate.signum() < 0)
            throw new IllegalStateException("Booked FD terms or currency scale are unavailable");
        return new Terms(rate, FdCompounding.basis(basis), scale);
    }

    private BigDecimal decimal(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        if (node.isNumber()) return node.decimalValue();
        if (node.isTextual()) {
            try { return new BigDecimal(node.textValue()); }
            catch (NumberFormatException ignored) { return null; }
        }
        return null;
    }

    private boolean isStatementEligible(String initialStatus, boolean maturedNow) {
        return maturedNow || "ACTIVE".equals(initialStatus) || "MATURED".equals(initialStatus)
                || "CLOSED".equals(initialStatus) || "PREMATURELY_CLOSED".equals(initialStatus);
    }

    private BigDecimal lastCompoundingBalance(long id, BigDecimal principal) {
        return jdbc.query("SELECT fia_compounding_balance FROM fd_interest_accrual WHERE fia_account_id=? " +
                        "ORDER BY fia_accrual_date DESC,fia_accrual_id DESC LIMIT 1",
                rs -> rs.next() && rs.getBigDecimal(1) != null ? rs.getBigDecimal(1) : principal, id);
    }

    private long typeId(String code) {
        return jdbc.queryForObject("SELECT tt_txn_type_id FROM transaction_types WHERE tt_txn_type_code=?", Long.class, code);
    }

    private LocalDate localDate(Object value) {
        if (value instanceof LocalDateTime dateTime) return dateTime.toLocalDate();
        if (value instanceof LocalDate date) return date;
        if (value instanceof java.sql.Timestamp timestamp) return timestamp.toLocalDateTime().toLocalDate();
        if (value instanceof java.sql.Date date) return date.toLocalDate();
        throw new IllegalStateException("Unexpected MySQL date value: " + (value == null ? "null" : value.getClass().getName()));
    }

    private void event(long id, String type, Object body) {
        try {
            jdbc.update("INSERT INTO fd_account_events(fae_account_id,fae_event_type,fae_payload,fae_aggregate_version,fae_created_by) " +
                            "VALUES(?,?,CAST(? AS JSON),COALESCE((SELECT fa_row_version FROM fd_accounts WHERE fa_account_id=?),0),'fd-batch')",
                    id, type, json.writeValueAsString(body), id);
        } catch (Exception e) {
            throw new IllegalStateException("Could not record FD event", e);
        }
    }

    private record Terms(BigDecimal annualRate, String compoundingBasis, int currencyScale) {}
    public record DailyAccountResult(int accrualRowsCreated, boolean matured) {}
}
