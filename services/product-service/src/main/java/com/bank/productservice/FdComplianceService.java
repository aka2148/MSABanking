package com.bank.productservice;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Read-only diagnostic checks against the product service's authoritative MySQL rate tables. */
@Service
public class FdComplianceService {
    private static final String CANDIDATES = """
            SELECT r.pr_rule_id,
                   COALESCE(DATE(r.pr_effective_from), DATE(p.pro_effective_from)) effective_from,
                   COALESCE(DATE(r.pr_effective_to), DATE(p.pro_effective_to)) effective_to,
                   o.ro_outcome_value annual_rate, o.ro_compounding_basis, o.ro_payout_frequency,
                   COALESCE(MAX(CASE WHEN UPPER(c.rc_condition_attribute)='AMOUNT_RANGE'
                                     THEN CAST(c.rc_value_from AS DECIMAL(22,8)) END), cfg.pcc_minimum_amount) min_amount,
                   COALESCE(MIN(CASE WHEN UPPER(c.rc_condition_attribute)='AMOUNT_RANGE'
                                     THEN CAST(c.rc_value_to AS DECIMAL(22,8)) END), cfg.pcc_maximum_amount) max_amount,
                   MAX(CASE WHEN UPPER(c.rc_condition_attribute)='TENURE_MONTHS'
                            THEN CAST(c.rc_value_from AS UNSIGNED) END) min_months,
                   MIN(CASE WHEN UPPER(c.rc_condition_attribute)='TENURE_MONTHS'
                            THEN CAST(c.rc_value_to AS UNSIGNED) END) max_months
              FROM product p
              JOIN product_currency_config cfg ON cfg.pcc_product_id=p.pro_product_id
              JOIN currency cur ON cur.cur_currency_id=cfg.pcc_currency_id
              JOIN product_rule r ON r.pr_product_id=p.pro_product_id AND r.pr_is_active=1
              JOIN rule_outcome o ON o.ro_rule_id=r.pr_rule_id
                   AND o.ro_outcome_type='INTEREST_RATE' AND o.ro_currency_id=cur.cur_currency_id
              LEFT JOIN rule_condition c ON c.rc_rule_id=r.pr_rule_id
             WHERE p.pro_product_id=? AND UPPER(cur.cur_iso_code)=?
             GROUP BY r.pr_rule_id,r.pr_effective_from,r.pr_effective_to,p.pro_effective_from,
                      p.pro_effective_to,o.ro_outcome_value,o.ro_compounding_basis,
                      o.ro_payout_frequency,cfg.pcc_minimum_amount,cfg.pcc_maximum_amount
            """;

    private final JdbcTemplate jdbc;

    public FdComplianceService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public RateDisclosureResponse verifyRate(RateDisclosureRequest request, LocalDate defaultDate) {
        LocalDate date = request.evaluationDate() == null ? defaultDate : request.evaluationDate();
        String currency = norm(request.currencyCode());
        List<Candidate> matching = matching(request.productId(), currency, request.principalAmount(), request.tenureMonths());
        List<Candidate> effective = matching.stream().filter(candidate -> candidate.effectiveOn(date)).toList();
        if (effective.size() != 1) {
            String status = effective.size() > 1 ? "AMBIGUOUS_RATE" : "NO_APPLICABLE_RATE";
            String explanation = effective.size() > 1
                    ? "Multiple active rate rules match this product, currency, amount, tenure, and evaluation date."
                    : "No active rate rule matches this product, currency, amount, tenure, and evaluation date.";
            return new RateDisclosureResponse(request.productId(), currency, request.principalAmount(),
                    request.tenureMonths(), request.displayedRate(), null, status, false, null, explanation,
                    null, null, date);
        }
        Candidate candidate = effective.get(0);
        BigDecimal difference = request.displayedRate().subtract(candidate.rate());
        boolean match = difference.compareTo(BigDecimal.ZERO) == 0;
        String status = match ? "MATCH" : "MISMATCH";
        String explanation = match
                ? "Displayed rate matches the authoritative rate on " + date + "."
                : "Displayed rate differs from the authoritative rate on " + date + ". Use the authoritative rate for FD calculations.";
        return new RateDisclosureResponse(request.productId(), currency, request.principalAmount(),
                request.tenureMonths(), request.displayedRate(), candidate.rate(), status, match, difference,
                explanation, candidate.compounding(), candidate.payout(), date);
    }

    public EffectiveDateResponse evaluateDate(EffectiveDateRequest request) {
        String currency = norm(request.currencyCode());
        LocalDate date = request.evaluationDate();
        List<Candidate> candidates = matching(request.productId(), currency, request.principalAmount(), request.tenureMonths());
        if (candidates.isEmpty()) return dateResult(request, currency, "NO_APPLICABLE_RATE", false,
                null, null, null, "No rate schedule matches this product, currency, amount, and tenure.");

        List<Candidate> effective = candidates.stream().filter(candidate -> candidate.effectiveOn(date)).toList();
        if (effective.size() == 1) {
            Candidate candidate = effective.get(0);
            return dateResult(request, currency, "VALID", true, candidate.rate(), candidate.from(), candidate.to(),
                    "The rate is valid on " + date + "; both effective-date boundaries are inclusive.");
        }
        if (effective.size() > 1) return dateResult(request, currency, "AMBIGUOUS_CONFIGURATION", false,
                null, null, null, "Multiple rate schedules overlap on the selected date.");

        if (candidates.stream().allMatch(candidate -> candidate.from() != null && date.isBefore(candidate.from()))) {
            Candidate earliest = candidates.stream().min(Comparator.comparing(Candidate::from)).orElseThrow();
            return dateResult(request, currency, "NOT_YET_EFFECTIVE", false, earliest.rate(), earliest.from(), earliest.to(),
                    "The matching rate schedule starts on " + earliest.from() + ".");
        }
        if (candidates.stream().allMatch(candidate -> candidate.to() != null && date.isAfter(candidate.to()))) {
            Candidate latest = candidates.stream().max(Comparator.comparing(Candidate::to)).orElseThrow();
            return dateResult(request, currency, "EXPIRED", false, latest.rate(), latest.from(), latest.to(),
                    "The matching rate schedule ended on " + latest.to() + ".");
        }
        return dateResult(request, currency, "NO_APPLICABLE_RATE", false, null, null, null,
                "The selected date falls in a gap between matching rate schedules.");
    }

    private List<Candidate> matching(long productId, String currency, BigDecimal principal, int tenure) {
        return jdbc.query(CANDIDATES, (rs, row) -> new Candidate(
                        rs.getObject("effective_from", LocalDate.class), rs.getObject("effective_to", LocalDate.class),
                        rs.getBigDecimal("annual_rate"), rs.getString("ro_compounding_basis"),
                        rs.getString("ro_payout_frequency"), rs.getBigDecimal("min_amount"),
                        rs.getBigDecimal("max_amount"), rs.getObject("min_months", Integer.class),
                        rs.getObject("max_months", Integer.class)), productId, currency)
                .stream().filter(candidate -> candidate.minAmount() != null && candidate.maxAmount() != null
                        && principal.compareTo(candidate.minAmount()) >= 0
                        && principal.compareTo(candidate.maxAmount()) <= 0
                        && candidate.minMonths() != null && candidate.maxMonths() != null
                        && tenure >= candidate.minMonths() && tenure <= candidate.maxMonths())
                .toList();
    }

    private EffectiveDateResponse dateResult(EffectiveDateRequest request, String currency, String status,
                                             boolean valid, BigDecimal rate, LocalDate from, LocalDate to,
                                             String explanation) {
        return new EffectiveDateResponse(request.productId(), currency, request.principalAmount(),
                request.tenureMonths(), request.evaluationDate(), status, valid, rate, from, to, explanation);
    }

    private static String norm(String value) { return value.trim().toUpperCase(Locale.ROOT); }

    public record RateDisclosureRequest(long productId, String currencyCode, BigDecimal principalAmount,
                                        int tenureMonths, BigDecimal displayedRate, LocalDate evaluationDate) {}
    public record EffectiveDateRequest(long productId, String currencyCode, BigDecimal principalAmount,
                                       int tenureMonths, LocalDate evaluationDate) {}
    public record RateDisclosureResponse(long productId, String currencyCode, BigDecimal principalAmount,
            int tenureMonths, BigDecimal displayedRate, BigDecimal authoritativeRate, String matchStatus,
            boolean isMatch, BigDecimal difference, String explanation, String compoundingFrequency,
            String payoutFrequency, LocalDate evaluationDate) {}
    public record EffectiveDateResponse(long productId, String currencyCode, BigDecimal principalAmount,
            int tenureMonths, LocalDate evaluationDate, String validityStatus, boolean isValid,
            BigDecimal applicableRate, LocalDate effectiveFrom, LocalDate effectiveTo, String explanation) {}

    private record Candidate(LocalDate from, LocalDate to, BigDecimal rate, String compounding, String payout,
                             BigDecimal minAmount, BigDecimal maxAmount, Integer minMonths, Integer maxMonths) {
        boolean effectiveOn(LocalDate date) {
            return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
        }
    }
}
