package com.bank.productservice;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Read-only diagnostic checks against the product service's authoritative MySQL
 * rate tables.
 */
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

    public FdComplianceService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public RateDisclosureResponse verifyRate(RateDisclosureRequest request, LocalDate defaultDate) {
        LocalDate date = request.evaluationDate() == null ? defaultDate : request.evaluationDate();
        String currency = norm(request.currencyCode());
        List<Candidate> matching = matching(request.productId(), currency, request.principalAmount(),
                request.tenureMonths());
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
                : "Displayed rate differs from the authoritative rate on " + date
                        + ". Use the authoritative rate for FD calculations.";
        return new RateDisclosureResponse(request.productId(), currency, request.principalAmount(),
                request.tenureMonths(), request.displayedRate(), candidate.rate(), status, match, difference,
                explanation, candidate.compounding(), candidate.payout(), date);
    }

    public EffectiveDateResponse evaluateDate(EffectiveDateRequest request) {
        String currency = norm(request.currencyCode());
        LocalDate date = request.evaluationDate();
        List<Candidate> candidates = matching(request.productId(), currency, request.principalAmount(),
                request.tenureMonths());
        if (candidates.isEmpty())
            return dateResult(request, currency, "NO_APPLICABLE_RATE", false,
                    null, null, null, "No rate schedule matches this product, currency, amount, and tenure.");

        List<Candidate> effective = candidates.stream().filter(candidate -> candidate.effectiveOn(date)).toList();
        if (effective.size() == 1) {
            Candidate candidate = effective.get(0);
            return dateResult(request, currency, "VALID", true, candidate.rate(), candidate.from(), candidate.to(),
                    "The rate is valid on " + date + "; both effective-date boundaries are inclusive.");
        }
        if (effective.size() > 1)
            return dateResult(request, currency, "AMBIGUOUS_CONFIGURATION", false,
                    null, null, null, "Multiple rate schedules overlap on the selected date.");

        if (candidates.stream().allMatch(candidate -> candidate.from() != null && date.isBefore(candidate.from()))) {
            Candidate earliest = candidates.stream().min(Comparator.comparing(Candidate::from)).orElseThrow();
            return dateResult(request, currency, "NOT_YET_EFFECTIVE", false, earliest.rate(), earliest.from(),
                    earliest.to(),
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

    public void validateProductScheduleForActivation(long productId) {
        List<java.util.Map<String, Object>> productRows = jdbc.queryForList(
                "SELECT pro_product_id, pro_product_code, pro_product_name, pro_status, pro_effective_from, pro_effective_to FROM product WHERE pro_product_id=?",
                productId);
        if (productRows.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Product not found: " + productId);
        }
        java.util.Map<String, Object> product = productRows.get(0);

        LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        LocalDate effectiveFrom = parseDate(product.get("pro_effective_from"));
        LocalDate effectiveTo = parseDate(product.get("pro_effective_to"));

        if (effectiveFrom != null && effectiveTo != null && effectiveFrom.isAfter(effectiveTo)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Effective-to date must be on or after effective-from date.");
        }
        if (effectiveTo != null && effectiveTo.isBefore(today)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "Product schedule has expired on " + effectiveTo + "; expired products cannot be activated.");
        }

        LocalDate evaluationDate = (effectiveFrom != null && effectiveFrom.isAfter(today)) ? effectiveFrom : today;

        List<java.util.Map<String, Object>> configs = jdbc.queryForList(
                "SELECT cfg.pcc_currency_id, cur.cur_iso_code, cfg.pcc_minimum_amount, cfg.pcc_maximum_amount " +
                        "  FROM product_currency_config cfg " +
                        "  JOIN currency cur ON cur.cur_currency_id=cfg.pcc_currency_id " +
                        " WHERE cfg.pcc_product_id=? AND cur.cur_is_active=1 ORDER BY cur.cur_iso_code",
                productId);
        if (configs.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "FD product requires at least one active currency configuration.");
        }

        for (java.util.Map<String, Object> cfg : configs) {
            String currencyCode = (String) cfg.get("cur_iso_code");
            BigDecimal minAmount = (BigDecimal) cfg.get("pcc_minimum_amount");
            BigDecimal maxAmount = (BigDecimal) cfg.get("pcc_maximum_amount");
            if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Minimum amount exceeds maximum amount for currency " + currencyCode);
            }

            List<Candidate> candidates = allCandidates(productId, currencyCode);
            if (candidates.isEmpty()) {
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "Currency " + currencyCode + " has no interest rate bands configured.");
            }

            for (Candidate c : candidates) {
                if (c.rate() == null || c.rate().signum() < 0 || c.rate().compareTo(BigDecimal.ONE) > 0) {
                    throw new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.BAD_REQUEST,
                            "Annual interest rate must be between 0.0000 and 1.0000.");
                }
                if (c.from() != null && c.to() != null && c.from().isAfter(c.to())) {
                    throw new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.BAD_REQUEST,
                            "Rate band effective-from cannot be after effective-to.");
                }
                if (c.minMonths() == null || c.maxMonths() == null || c.minMonths() > c.maxMonths()) {
                    throw new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.BAD_REQUEST,
                            "Tenure range is inverted or missing in currency " + currencyCode);
                }
            }

            for (int i = 0; i < candidates.size(); i++) {
                Candidate c1 = candidates.get(i);
                for (int j = i + 1; j < candidates.size(); j++) {
                    Candidate c2 = candidates.get(j);
                    boolean tenureOverlap = Math.max(c1.minMonths(), c2.minMonths()) <= Math.min(c1.maxMonths(),
                            c2.maxMonths());
                    boolean amountOverlap = c1.minAmount().compareTo(c2.maxAmount()) <= 0
                            && c2.minAmount().compareTo(c1.maxAmount()) <= 0;
                    if (tenureOverlap && amountOverlap && c1.effectiveOn(evaluationDate)
                            && c2.effectiveOn(evaluationDate)) {
                        if (c1.rate().compareTo(c2.rate()) != 0) {
                            throw new org.springframework.web.server.ResponseStatusException(
                                    org.springframework.http.HttpStatus.CONFLICT,
                                    "AMBIGUOUS_CONFIGURATION: Multiple rate schedules overlap for currency "
                                            + currencyCode + " on " + evaluationDate + " with differing rates.");
                        }
                    }
                }
            }

            boolean anyEffective = candidates.stream().anyMatch(c -> c.effectiveOn(evaluationDate));
            if (!anyEffective) {
                boolean allExpired = candidates.stream()
                        .allMatch(c -> c.to() != null && evaluationDate.isAfter(c.to()));
                if (allExpired) {
                    throw new org.springframework.web.server.ResponseStatusException(
                            org.springframework.http.HttpStatus.BAD_REQUEST,
                            "EXPIRED: Rate schedules for currency " + currencyCode + " have expired.");
                }
                throw new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "NO_APPLICABLE_RATE: No rate schedule for currency " + currencyCode + " is effective on "
                                + evaluationDate + ".");
            }
        }
    }

    public List<Candidate> allCandidates(long productId, String currency) {
        return jdbc.query(CANDIDATES, (rs, row) -> new Candidate(
                rs.getObject("effective_from", LocalDate.class), rs.getObject("effective_to", LocalDate.class),
                rs.getBigDecimal("annual_rate"), rs.getString("ro_compounding_basis"),
                rs.getString("ro_payout_frequency"), rs.getBigDecimal("min_amount"),
                rs.getBigDecimal("max_amount"), rs.getObject("min_months", Integer.class),
                rs.getObject("max_months", Integer.class)), productId, norm(currency));
    }

    private List<Candidate> matching(long productId, String currency, BigDecimal principal, int tenure) {
        return allCandidates(productId, currency).stream()
                .filter(candidate -> candidate.minAmount() != null && candidate.maxAmount() != null
                        && principal.compareTo(candidate.minAmount()) >= 0
                        && principal.compareTo(candidate.maxAmount()) <= 0
                        && candidate.minMonths() != null && candidate.maxMonths() != null
                        && tenure >= candidate.minMonths() && tenure <= candidate.maxMonths())
                .toList();
    }

    private LocalDate parseDate(Object obj) {
        if (obj == null)
            return null;
        if (obj instanceof LocalDate d)
            return d;
        if (obj instanceof java.sql.Date d)
            return d.toLocalDate();
        if (obj instanceof java.sql.Timestamp ts)
            return ts.toLocalDateTime().toLocalDate();
        String str = obj.toString().trim();
        if (str.isEmpty())
            return null;
        if (str.length() >= 10)
            return LocalDate.parse(str.substring(0, 10));
        return LocalDate.parse(str);
    }

    private EffectiveDateResponse dateResult(EffectiveDateRequest request, String currency, String status,
            boolean valid, BigDecimal rate, LocalDate from, LocalDate to,
            String explanation) {
        return new EffectiveDateResponse(request.productId(), currency, request.principalAmount(),
                request.tenureMonths(), request.evaluationDate(), status, valid, rate, from, to, explanation);
    }

    private static String norm(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    public record RateDisclosureRequest(long productId, String currencyCode, BigDecimal principalAmount,
            int tenureMonths, BigDecimal displayedRate, LocalDate evaluationDate) {
    }

    public record EffectiveDateRequest(long productId, String currencyCode, BigDecimal principalAmount,
            int tenureMonths, LocalDate evaluationDate) {
    }

    public record RateDisclosureResponse(long productId, String currencyCode, BigDecimal principalAmount,
            int tenureMonths, BigDecimal displayedRate, BigDecimal authoritativeRate, String matchStatus,
            boolean isMatch, BigDecimal difference, String explanation, String compoundingFrequency,
            String payoutFrequency, LocalDate evaluationDate) {
    }

    public record EffectiveDateResponse(long productId, String currencyCode, BigDecimal principalAmount,
            int tenureMonths, LocalDate evaluationDate, String validityStatus, boolean isValid,
            BigDecimal applicableRate, LocalDate effectiveFrom, LocalDate effectiveTo, String explanation) {
    }

    static record Candidate(LocalDate from, LocalDate to, BigDecimal rate, String compounding, String payout,
            BigDecimal minAmount, BigDecimal maxAmount, Integer minMonths, Integer maxMonths) {
        boolean effectiveOn(LocalDate date) {
            return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
        }
    }
}
