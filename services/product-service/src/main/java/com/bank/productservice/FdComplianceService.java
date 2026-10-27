package com.bank.productservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

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
    private final ObjectMapper json;

    @Autowired
    public FdComplianceService(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public FdComplianceService(JdbcTemplate jdbc) {
        this(jdbc, new ObjectMapper());
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
        List<Map<String, Object>> productRows = jdbc.queryForList(
                "SELECT pro_product_id, pro_product_code, pro_product_name, pro_status, pro_effective_from, pro_effective_to FROM product WHERE pro_product_id=?",
                productId);
        if (productRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: " + productId);
        }
        Map<String, Object> product = productRows.get(0);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate effectiveFrom = parseDate(product.get("pro_effective_from"));
        LocalDate effectiveTo = parseDate(product.get("pro_effective_to"));

        if (effectiveFrom != null && effectiveTo != null && effectiveFrom.isAfter(effectiveTo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Effective-to date must be on or after effective-from date.");
        }

        if (effectiveTo != null && effectiveTo.isBefore(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Product schedule has expired on " + effectiveTo + "; expired products cannot be activated.");
        }

        LocalDate evaluationDate = effectiveFrom != null && effectiveFrom.isAfter(today) ? effectiveFrom : today;

        List<Map<String, Object>> configs = jdbc.queryForList(
                """
                SELECT cfg.pcc_currency_id, cur.cur_iso_code, cfg.pcc_minimum_amount, cfg.pcc_maximum_amount
                  FROM product_currency_config cfg
                  JOIN currency cur ON cur.cur_currency_id=cfg.pcc_currency_id
                 WHERE cfg.pcc_product_id=? AND cur.cur_is_active=1
                 ORDER BY cur.cur_iso_code
                """,
                productId);

        if (configs.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "FD product requires at least one active currency configuration.");
        }

        for (Map<String, Object> cfg : configs) {
            String currencyCode = (String) cfg.get("cur_iso_code");
            BigDecimal minAmount = (BigDecimal) cfg.get("pcc_minimum_amount");
            BigDecimal maxAmount = (BigDecimal) cfg.get("pcc_maximum_amount");

            if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Minimum amount exceeds maximum amount for currency " + currencyCode);
            }

            List<Candidate> candidates = allCandidates(productId, currencyCode);
            if (candidates.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Currency " + currencyCode + " has no interest rate bands configured.");
            }

            for (Candidate c : candidates) {
                if (c.rate() == null || c.rate().signum() < 0 || c.rate().compareTo(BigDecimal.ONE) > 0) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Annual interest rate must be between 0.0000 and 1.0000.");
                }
                if (c.from() != null && c.to() != null && c.from().isAfter(c.to())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Rate band effective-from cannot be after effective-to.");
                }
                if (c.minMonths() == null || c.maxMonths() == null || c.minMonths() > c.maxMonths()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
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
                            throw new ResponseStatusException(HttpStatus.CONFLICT,
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
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "EXPIRED: Rate schedules for currency " + currencyCode + " have expired.");
                }
                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
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
        if (obj instanceof Date d)
            return d.toLocalDate();
        if (obj instanceof Timestamp ts)
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

    public ProductScheduleResponse getProductSchedule(long productId) {
        List<Map<String, Object>> productRows = jdbc.queryForList(
                "SELECT pro_product_id, pro_product_code, pro_product_name, pro_status, pro_effective_from, pro_effective_to FROM product WHERE pro_product_id=?",
                productId);
        if (productRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: " + productId);
        }
        Map<String, Object> product = productRows.get(0);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);

        List<CurrencyLimitSummary> currencies = jdbc.query(
                "SELECT c.cur_iso_code, pcc.pcc_minimum_amount, pcc.pcc_maximum_amount, pcc.pcc_is_base_currency FROM product_currency_config pcc JOIN currency c ON c.cur_currency_id=pcc.pcc_currency_id WHERE pcc.pcc_product_id=? AND c.cur_is_active=1 ORDER BY c.cur_iso_code",
                (rs, n) -> new CurrencyLimitSummary(rs.getString(1), rs.getBigDecimal(2), rs.getBigDecimal(3), rs.getBoolean(4)),
                productId);

        List<RateBandSummary> rateBands = jdbc.query(
                """
                SELECT r.pr_rule_id, c.cur_iso_code,
                       COALESCE(DATE(r.pr_effective_from), DATE(p.pro_effective_from)) effective_from,
                       COALESCE(DATE(r.pr_effective_to), DATE(p.pro_effective_to)) effective_to,
                       o.ro_outcome_value, o.ro_compounding_basis, o.ro_payout_frequency,
                       MAX(CASE WHEN UPPER(rc.rc_condition_attribute)='TENURE_MONTHS' THEN CAST(rc.rc_value_from AS UNSIGNED) END) min_months,
                       MIN(CASE WHEN UPPER(rc.rc_condition_attribute)='TENURE_MONTHS' THEN CAST(rc.rc_value_to AS UNSIGNED) END) max_months,
                       r.pr_is_active
                  FROM product_rule r
                  JOIN product p ON p.pro_product_id=r.pr_product_id
                  JOIN rule_outcome o ON o.ro_rule_id=r.pr_rule_id AND o.ro_outcome_type='INTEREST_RATE'
                  JOIN currency c ON c.cur_currency_id=o.ro_currency_id
                  LEFT JOIN rule_condition rc ON rc.rc_rule_id=r.pr_rule_id
                 WHERE r.pr_product_id=? AND r.pr_rule_name='TENURE_RATE'
                 GROUP BY r.pr_rule_id, c.cur_iso_code, r.pr_effective_from, r.pr_effective_to, p.pro_effective_from, p.pro_effective_to, o.ro_outcome_value, o.ro_compounding_basis, o.ro_payout_frequency, r.pr_is_active
                 ORDER BY c.cur_iso_code, min_months
                """,
                (rs, n) -> {
                    LocalDate from = rs.getObject("effective_from", LocalDate.class);
                    LocalDate to = rs.getObject("effective_to", LocalDate.class);
                    String pStatus = (String) product.get("pro_status");
                    String bandStatus = "DRAFT";
                    if ("ACTIVE".equalsIgnoreCase(pStatus)) {
                        bandStatus = to != null && today.isAfter(to) ? "EXPIRED"
                                : (from != null && today.isBefore(from) ? "FUTURE" : "ACTIVE");
                    } else if ("INACTIVE".equalsIgnoreCase(pStatus)) {
                        bandStatus = "INACTIVE";
                    }
                    Integer minM = rs.getObject("min_months", Integer.class);
                    Integer maxM = rs.getObject("max_months", Integer.class);
                    return new RateBandSummary(
                            rs.getLong("pr_rule_id"),
                            rs.getString("cur_iso_code"),
                            minM != null ? minM : 1,
                            maxM != null ? maxM : 12,
                            rs.getBigDecimal("ro_outcome_value"),
                            rs.getString("ro_compounding_basis"),
                            rs.getString("ro_payout_frequency"),
                            from,
                            to,
                            rs.getBoolean("pr_is_active"),
                            bandStatus);
                },
                productId);

        return new ProductScheduleResponse(
                productId,
                (String) product.get("pro_product_code"),
                (String) product.get("pro_product_name"),
                (String) product.get("pro_status"),
                parseDate(product.get("pro_effective_from")),
                parseDate(product.get("pro_effective_to")),
                currencies,
                rateBands);
    }

    @Transactional
    public ProductScheduleResponse updateProductSchedule(long productId, UpdateScheduleRequest request) {
        List<Map<String, Object>> productRows = jdbc.queryForList(
                "SELECT pro_product_id, pro_product_code, pro_product_name, pro_status, pro_event_version FROM product WHERE pro_product_id=?",
                productId);
        if (productRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: " + productId);
        }
        Map<String, Object> product = productRows.get(0);
        String status = (String) product.get("pro_status");
        LocalDate effFrom = request.effectiveFrom();
        LocalDate effTo = request.effectiveTo();

        if (effFrom != null && effTo != null && effFrom.isAfter(effTo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Effective-to date must be on or after effective-from date.");
        }
        if (request.rateBands() == null || request.rateBands().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rate schedule requires at least one rate band.");
        }
        for (RateBandInput band : request.rateBands()) {
            if (band.annualRate() == null || band.annualRate().signum() < 0 || band.annualRate().compareTo(BigDecimal.ONE) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Annual interest rate must be between 0.0000 and 1.0000.");
            }
            if (band.minimumTenureMonths() < 1 || band.maximumTenureMonths() < band.minimumTenureMonths()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tenure range is invalid: minimum tenure must be >= 1 and <= maximum tenure.");
            }
            if (band.effectiveFrom() != null && band.effectiveTo() != null && band.effectiveFrom().isAfter(band.effectiveTo())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rate band effective-from cannot be after effective-to.");
            }
        }

        jdbc.update(
                "UPDATE product SET pro_effective_from=?, pro_effective_to=?, pro_updated_by='STAFF_COMPLIANCE', pro_updated_date=UTC_TIMESTAMP(6) WHERE pro_product_id=?",
                effFrom == null ? null : effFrom.toString(),
                effTo == null ? null : effTo.toString(),
                productId);

        jdbc.update("DELETE FROM rule_condition WHERE rc_rule_id IN (SELECT pr_rule_id FROM product_rule WHERE pr_product_id=? AND pr_rule_name='TENURE_RATE')", productId);
        jdbc.update("DELETE FROM rule_outcome WHERE ro_rule_id IN (SELECT pr_rule_id FROM product_rule WHERE pr_product_id=? AND pr_rule_name='TENURE_RATE')", productId);
        jdbc.update("DELETE FROM product_rule WHERE pr_product_id=? AND pr_rule_name='TENURE_RATE'", productId);

        Long ruleTypeId = jdbc.query("SELECT rut_rule_type_id FROM rule_type WHERE rut_rule_type_code='FD_TENURE_RATE'", rs -> rs.next() ? rs.getLong(1) : 1L);
        int priority = 1;

        for (RateBandInput band : request.rateBands()) {
            String curr = norm(band.currencyCode());
            Long currencyId = jdbc.query(
                    "SELECT cur_currency_id FROM currency WHERE UPPER(cur_iso_code)=UPPER(?) AND cur_is_active=1",
                    rs -> rs.next() ? rs.getLong(1) : null,
                    curr);
            if (currencyId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Active currency not found: " + curr);
            }
            Map<String, BigDecimal> limits = jdbc.query(
                    "SELECT pcc_minimum_amount, pcc_maximum_amount FROM product_currency_config WHERE pcc_product_id=? AND pcc_currency_id=?",
                    rs -> rs.next() ? Map.of("min", rs.getBigDecimal(1), "max", rs.getBigDecimal(2)) : null,
                    productId, currencyId);
            BigDecimal minAmt = limits != null && limits.get("min") != null ? limits.get("min") : new BigDecimal("1.00");
            BigDecimal maxAmt = limits != null && limits.get("max") != null ? limits.get("max") : new BigDecimal("100000000.00");
            LocalDate bandFrom = band.effectiveFrom() != null ? band.effectiveFrom() : effFrom;
            LocalDate bandTo = band.effectiveTo() != null ? band.effectiveTo() : effTo;

            jdbc.update(
                    "INSERT INTO product_rule(pr_product_id,pr_rule_type_id,pr_rule_name,pr_priority,pr_combine_logic,pr_is_active,pr_effective_from,pr_effective_to,pr_created_by) VALUES(?,?,?,?,?,1,?,?,?)",
                    productId, ruleTypeId, "TENURE_RATE", priority++, "AND",
                    bandFrom != null ? Date.valueOf(bandFrom) : null,
                    bandTo != null ? Date.valueOf(bandTo) : null,
                    "STAFF_COMPLIANCE");
            Long ruleId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

            jdbc.update(
                    "INSERT INTO rule_condition(rc_rule_id,rc_condition_attribute,rc_operator,rc_value_from,rc_value_to,rc_created_by) VALUES(?, 'TENURE_MONTHS', 'BETWEEN_INCLUSIVE', ?, ?, 'STAFF_COMPLIANCE')",
                    ruleId, String.valueOf(band.minimumTenureMonths()), String.valueOf(band.maximumTenureMonths()));
            jdbc.update(
                    "INSERT INTO rule_condition(rc_rule_id,rc_condition_attribute,rc_operator,rc_value_from,rc_value_to,rc_created_by) VALUES(?, 'AMOUNT_RANGE', 'BETWEEN_INCLUSIVE', ?, ?, 'STAFF_COMPLIANCE')",
                    ruleId, minAmt.toPlainString(), maxAmt.toPlainString());

            String compounding = band.compoundingBasis() == null || band.compoundingBasis().isBlank() ? "QUARTERLY" : band.compoundingBasis().toUpperCase(Locale.ROOT);
            String payout = band.payoutFrequency() == null || band.payoutFrequency().isBlank() ? "AT_MATURITY" : band.payoutFrequency().toUpperCase(Locale.ROOT);
            jdbc.update(
                    "INSERT INTO rule_outcome(ro_rule_id,ro_outcome_type,ro_outcome_value,ro_unit,ro_compounding_basis,ro_payout_frequency,ro_currency_id,ro_created_by) VALUES(?, 'INTEREST_RATE', ?, 'DECIMAL_FRACTION', ?, ?, ?, 'STAFF_COMPLIANCE')",
                    ruleId, band.annualRate(), compounding, payout, currencyId);
        }

        validateProductScheduleForActivation(productId);

        if ("ACTIVE".equalsIgnoreCase(status)) {
            jdbc.update("UPDATE product SET pro_event_version=pro_event_version+1, pro_updated_by='STAFF_COMPLIANCE', pro_updated_date=UTC_TIMESTAMP(6) WHERE pro_product_id=?", productId);
            writeCatalogOutbox(productId);
        }

        return getProductSchedule(productId);
    }

    private void writeCatalogOutbox(long id) {
        Map<String, Object> product = jdbc.queryForMap(
                "SELECT pro_product_code,pro_product_name,pro_description,pro_base_currency_code,pro_status,pro_event_version FROM product WHERE pro_product_id=?",
                id);
        List<Map<String, Object>> currency = jdbc.queryForList(
                "SELECT c.cur_currency_id,c.cur_iso_code,c.cur_currency_name,c.cur_decimal_places,p.pcc_minimum_amount,p.pcc_maximum_amount,p.pcc_is_base_currency,(SELECT o.ro_outcome_value FROM product_rule pr JOIN rule_outcome o ON o.ro_rule_id=pr.pr_rule_id WHERE pr.pr_product_id=p.pcc_product_id AND o.ro_currency_id=p.pcc_currency_id AND o.ro_outcome_type='CLOSURE_PENALTY' AND pr.pr_is_active=1 ORDER BY pr.pr_priority LIMIT 1) premature_penalty_points FROM product_currency_config p JOIN currency c ON c.cur_currency_id=p.pcc_currency_id WHERE p.pcc_product_id=? ORDER BY c.cur_iso_code",
                id);
        List<Map<String, Object>> bands = jdbc.queryForList(
                "SELECT c.cur_iso_code,o.ro_outcome_value,o.ro_compounding_basis,o.ro_payout_frequency,MAX(CASE WHEN q.rc_condition_attribute='TENURE_MONTHS' THEN q.rc_value_from END) minimumMonth,MAX(CASE WHEN q.rc_condition_attribute='TENURE_MONTHS' THEN q.rc_value_to END) maximumMonth,DATE_FORMAT(COALESCE(DATE(r.pr_effective_from),DATE(p.pro_effective_from)),'%Y-%m-%d') effectiveFrom,DATE_FORMAT(COALESCE(DATE(r.pr_effective_to),DATE(p.pro_effective_to)),'%Y-%m-%d') effectiveTo FROM product_rule r JOIN product p ON p.pro_product_id=r.pr_product_id JOIN rule_outcome o ON o.ro_rule_id=r.pr_rule_id JOIN currency c ON c.cur_currency_id=o.ro_currency_id JOIN rule_condition q ON q.rc_rule_id=r.pr_rule_id WHERE r.pr_product_id=? AND r.pr_is_active=1 AND o.ro_outcome_type='INTEREST_RATE' GROUP BY r.pr_rule_id,o.ro_outcome_value,o.ro_compounding_basis,o.ro_payout_frequency,c.cur_iso_code,r.pr_effective_from,r.pr_effective_to,p.pro_effective_from,p.pro_effective_to ORDER BY c.cur_iso_code,minimumMonth",
                id);
        Map<String, Object> payload = Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", "PRODUCT_CATALOG",
                "schemaVersion", 1,
                "occurredAt", Instant.now().toString(),
                "aggregateId", Long.toString(id),
                "sourceVersion", product.get("pro_event_version"),
                "product", product,
                "currencies", currency,
                "rateBands", bands);
        try {
            jdbc.update(
                    "INSERT INTO outbox_event(event_key,event_type,topic,source_version,payload) VALUES(?,?,?,?,CAST(? AS JSON))",
                    Long.toString(id), "PRODUCT_CATALOG", "product.catalog", product.get("pro_event_version"),
                    json.writeValueAsString(payload));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to write catalog event", e);
        }
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

    public record CurrencyLimitSummary(String currencyCode, BigDecimal minimumAmount, BigDecimal maximumAmount,
            boolean isBaseCurrency) {
    }

    public record RateBandSummary(Long ruleId, String currencyCode, int minimumTenureMonths, int maximumTenureMonths,
            BigDecimal annualRate, String compoundingBasis, String payoutFrequency, LocalDate effectiveFrom,
            LocalDate effectiveTo, boolean isActive, String status) {
    }

    public record RateBandInput(String currencyCode, int minimumTenureMonths, int maximumTenureMonths,
            BigDecimal annualRate, String compoundingBasis, String payoutFrequency, LocalDate effectiveFrom,
            LocalDate effectiveTo) {
    }

    public record ProductScheduleResponse(long productId, String productCode, String productName, String status,
            LocalDate effectiveFrom, LocalDate effectiveTo, List<CurrencyLimitSummary> currencies,
            List<RateBandSummary> rateBands) {
    }

    public record UpdateScheduleRequest(LocalDate effectiveFrom, LocalDate effectiveTo,
            List<RateBandInput> rateBands) {
    }

    static record Candidate(LocalDate from, LocalDate to, BigDecimal rate, String compounding, String payout,
            BigDecimal minAmount, BigDecimal maxAmount, Integer minMonths, Integer maxMonths) {
        boolean effectiveOn(LocalDate date) {
            return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
        }
    }
}
