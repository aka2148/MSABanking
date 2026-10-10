package com.bank.fd.application;

import com.bank.common.exception.BankingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Resolves active FD tiers from normalized product/rule/currency rows and local FD reference IDs. */
@Component
public class NormalizedProductTermsAdapter implements ProductTermsPort {
    private final NormalizedTermsDataSource dataSource;

    @Autowired
    public NormalizedProductTermsAdapter(JdbcTemplate jdbcTemplate) {
        this(new JdbcTemplateTermsDataSource(jdbcTemplate));
    }

    public NormalizedProductTermsAdapter(NormalizedTermsDataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public ProductTerms resolveActiveTerms(ProductTermsQuery query, LocalDate effectiveDate) {
        if (effectiveDate == null) throw new ProductTermsResolutionException("Effective date is required");
        List<NormalizedOffer> eligible = dataSource.find(query.productId(),
                        query.currencyCode().trim().toUpperCase(Locale.ROOT), effectiveDate).stream()
                .filter(NormalizedOffer::active)
                .filter(offer -> offer.productId() != null && offer.productId().equals(query.productId()))
                .filter(offer -> offer.currencyCode() != null
                        && offer.currencyCode().equalsIgnoreCase(query.currencyCode().trim()))
                .filter(offer -> query.principal().compareTo(offer.minimumAmount()) >= 0
                        && query.principal().compareTo(offer.maximumAmount()) <= 0)
                .filter(offer -> query.tenureMonths() >= offer.minimumTenureMonths()
                        && query.tenureMonths() <= offer.maximumTenureMonths())
                .toList();
        if (eligible.size() != 1) {
            throw new ProductTermsResolutionException(eligible.isEmpty()
                    ? "FD product, currency, amount, or tenure is not eligible"
                    : "FD terms are ambiguous for the requested product, currency, and tenure");
        }
        return toTerms(eligible.get(0));
    }

    @Override
    public List<ProductTerms> listEligibleTerms(LocalDate effectiveDate) {
        if (effectiveDate == null) throw new ProductTermsResolutionException("Effective date is required");
        return dataSource.findEligible(effectiveDate).stream().filter(NormalizedOffer::active)
                .map(this::toTerms).toList();
    }

    private ProductTerms toTerms(NormalizedOffer offer) {
        try {
            return new ProductTerms(offer.productId(), offer.productCode(), offer.productName(),
                    offer.currencyCode().toUpperCase(Locale.ROOT), offer.minimumAmount(), offer.maximumAmount(),
                    offer.minimumTenureMonths(), offer.maximumTenureMonths(), offer.annualRate(),
                    offer.compoundingFrequency(), offer.payoutFrequency(), offer.appliedRateOutcomeId(),
                    offer.closurePenaltyRule());
        } catch (RuntimeException exception) {
            throw new ProductTermsResolutionException("FD normalized terms are malformed", exception);
        }
    }

    @FunctionalInterface
    public interface NormalizedTermsDataSource {
        List<NormalizedOffer> find(Long productId, String currencyCode, LocalDate effectiveDate);
        default List<NormalizedOffer> findEligible(LocalDate effectiveDate) { return List.of(); }
    }

    public record NormalizedOffer(
            Long productId,
            String productCode,
            String productName,
            String currencyCode,
            boolean active,
            BigDecimal minimumAmount,
            BigDecimal maximumAmount,
            int minimumTenureMonths,
            int maximumTenureMonths,
            BigDecimal annualRate,
            String compoundingFrequency,
            String payoutFrequency,
            Long appliedRateOutcomeId,
            String closurePenaltyRule) {
        public NormalizedOffer withActive(boolean value) {
            return new NormalizedOffer(productId, productCode, productName, currencyCode, value,
                    minimumAmount, maximumAmount, minimumTenureMonths, maximumTenureMonths, annualRate,
                    compoundingFrequency, payoutFrequency, appliedRateOutcomeId, closurePenaltyRule);
        }
    }

    public static class ProductTermsResolutionException extends BankingException {
        public ProductTermsResolutionException(String message) { super(message); }
        public ProductTermsResolutionException(String message, Throwable cause) { super(message, cause); }
    }

    private static final class JdbcTemplateTermsDataSource implements NormalizedTermsDataSource {
        private static final String TIERED_SQL = """
                SELECT fpr.fpr_product_id, p.pro_product_code, p.pro_product_name,
                       fpr.fpr_status, c.cur_iso_code,
                       MIN(CASE WHEN upper(rc.rc_condition_attribute) = 'AMOUNT_RANGE'
                                THEN rc.rc_value_from::numeric END) AS min_amount,
                       MAX(CASE WHEN upper(rc.rc_condition_attribute) = 'AMOUNT_RANGE'
                                THEN rc.rc_value_to::numeric END) AS max_amount,
                       MIN(CASE WHEN upper(rc.rc_condition_attribute) = 'TENURE_MONTHS'
                                THEN rc.rc_value_from::integer END) AS min_months,
                       MAX(CASE WHEN upper(rc.rc_condition_attribute) = 'TENURE_MONTHS'
                                THEN rc.rc_value_to::integer END) AS max_months,
                       ro.ro_outcome_value AS annual_rate,
                       ro.ro_compounding_basis AS compounding,
                       ro.ro_payout_frequency AS payout,
                       frr.fror_outcome_id AS applied_rate_id
                  FROM public.product p
                  JOIN public.fd_product_ref fpr
                    ON upper(fpr.fpr_product_code) = upper(p.pro_product_code)
                  JOIN public.product_currency_config pcc ON pcc.pcc_product_id = p.pro_product_id
                  JOIN public.currency c ON c.cur_currency_id = pcc.pcc_currency_id
                  JOIN public.fd_currency_ref fcr ON upper(fcr.fcr_iso_code) = upper(c.cur_iso_code)
                  JOIN public.product_rule pr ON pr.pr_product_id = p.pro_product_id
                       AND upper(pr.pr_rule_name) = 'TENURE_RATE' AND pr.pr_is_active
                       AND (pr.pr_effective_from IS NULL OR pr.pr_effective_from::date <= ?)
                       AND (pr.pr_effective_to IS NULL OR pr.pr_effective_to::date >= ?)
                  JOIN public.rule_condition rc ON rc.rc_rule_id = pr.pr_rule_id
                  JOIN public.rule_outcome ro ON ro.ro_rule_id = pr.pr_rule_id
                       AND upper(ro.ro_outcome_type) = 'INTEREST_RATE'
                       AND ro.ro_currency_id = c.cur_currency_id
                  JOIN public.fd_rule_outcome_ref frr
                    ON frr.fror_source_outcome_id = ro.ro_outcome_id
                 WHERE upper(p.pro_status) = 'ACTIVE'
                   AND upper(coalesce(fpr.fpr_status, 'ACTIVE')) = 'ACTIVE'
                   AND c.cur_is_active
                   AND fpr.fpr_product_id = coalesce(?, fpr.fpr_product_id)
                   AND upper(c.cur_iso_code) = upper(coalesce(?, c.cur_iso_code))
                 GROUP BY fpr.fpr_product_id, p.pro_product_code, p.pro_product_name,
                          fpr.fpr_status, c.cur_iso_code, pr.pr_rule_id,
                          ro.ro_outcome_value, ro.ro_compounding_basis,
                          ro.ro_payout_frequency, frr.fror_outcome_id
                """;

        /** Compatibility read for already-provisioned flat-rate products. */
        private static final String LEGACY_SQL = """
                SELECT fpr.fpr_product_id, p.pro_product_code, p.pro_product_name,
                       fpr.fpr_status, c.cur_iso_code,
                       MIN(CASE WHEN upper(pr.pr_rule_name) = 'AMOUNT_RANGE'
                                THEN rc.rc_value_from::numeric END) AS min_amount,
                       MAX(CASE WHEN upper(pr.pr_rule_name) = 'AMOUNT_RANGE'
                                THEN rc.rc_value_to::numeric END) AS max_amount,
                       MIN(CASE WHEN upper(pr.pr_rule_name) = 'TENURE_RANGE'
                                THEN rc.rc_value_from::integer END) AS min_months,
                       MAX(CASE WHEN upper(pr.pr_rule_name) = 'TENURE_RANGE'
                                THEN rc.rc_value_to::integer END) AS max_months,
                       MAX(CASE WHEN upper(ro.ro_outcome_type) = 'INTEREST_RATE'
                                THEN ro.ro_outcome_value END) AS annual_rate,
                       MAX(CASE WHEN upper(ro.ro_outcome_type) = 'INTEREST_RATE'
                                THEN ro.ro_compounding_basis END) AS compounding,
                       MAX(CASE WHEN upper(ro.ro_outcome_type) = 'INTEREST_RATE'
                                THEN ro.ro_payout_frequency END) AS payout,
                       MAX(CASE WHEN upper(ro.ro_outcome_type) = 'CLOSURE_PENALTY'
                                THEN ro.ro_outcome_value::text END) AS closure_rule,
                       MAX(CASE WHEN upper(ro.ro_outcome_type) = 'INTEREST_RATE'
                                THEN frr.fror_outcome_id END) AS applied_rate_id
                  FROM public.product p
                  JOIN public.fd_product_ref fpr
                    ON upper(fpr.fpr_product_code) = upper(p.pro_product_code)
                  JOIN public.product_currency_config pcc ON pcc.pcc_product_id = p.pro_product_id
                  JOIN public.currency c ON c.cur_currency_id = pcc.pcc_currency_id
                  JOIN public.fd_currency_ref fcr ON upper(fcr.fcr_iso_code) = upper(c.cur_iso_code)
                  LEFT JOIN public.product_rule pr ON pr.pr_product_id = p.pro_product_id
                       AND pr.pr_is_active
                       AND upper(pr.pr_rule_name) IN ('AMOUNT_RANGE', 'TENURE_RANGE', 'CLOSURE_PENALTY')
                       AND (pr.pr_effective_from IS NULL OR pr.pr_effective_from::date <= ?)
                       AND (pr.pr_effective_to IS NULL OR pr.pr_effective_to::date >= ?)
                  LEFT JOIN public.rule_outcome ro ON ro.ro_rule_id = pr.pr_rule_id
                       AND (ro.ro_currency_id IS NULL OR ro.ro_currency_id = c.cur_currency_id)
                  LEFT JOIN public.rule_condition rc ON rc.rc_rule_id = pr.pr_rule_id
                  LEFT JOIN public.fd_rule_outcome_ref frr
                    ON frr.fror_source_outcome_id = ro.ro_outcome_id
                    OR (frr.fror_source_outcome_id IS NULL
                        AND upper(frr.fror_outcome_type) = upper(ro.ro_outcome_type)
                        AND CASE WHEN frr.fror_outcome_value ~ '^[+-]?([0-9]+(\\.[0-9]+)?|\\.[0-9]+)$'
                                 THEN frr.fror_outcome_value::numeric = ro.ro_outcome_value
                                 ELSE false END)
                 WHERE upper(p.pro_status) = 'ACTIVE'
                   AND upper(coalesce(fpr.fpr_status, 'ACTIVE')) = 'ACTIVE'
                   AND c.cur_is_active
                   AND fpr.fpr_product_id = coalesce(?, fpr.fpr_product_id)
                   AND upper(c.cur_iso_code) = upper(coalesce(?, c.cur_iso_code))
                   AND NOT EXISTS (
                       SELECT 1 FROM public.product_rule tier
                        WHERE tier.pr_product_id = p.pro_product_id
                          AND upper(tier.pr_rule_name) = 'TENURE_RATE' AND tier.pr_is_active)
                 GROUP BY fpr.fpr_product_id, p.pro_product_code, p.pro_product_name,
                          fpr.fpr_status, c.cur_iso_code, fcr.fcr_currency_id
                HAVING COUNT(*) FILTER (WHERE upper(pr.pr_rule_name) = 'TENURE_RANGE') > 0
                   AND COUNT(*) FILTER (WHERE upper(ro.ro_outcome_type) = 'INTEREST_RATE') > 0
                """;

        private final JdbcTemplate jdbcTemplate;

        private JdbcTemplateTermsDataSource(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @Override
        public List<NormalizedOffer> find(Long productId, String currencyCode, LocalDate effectiveDate) {
            return query(productId, currencyCode, effectiveDate);
        }

        @Override
        public List<NormalizedOffer> findEligible(LocalDate effectiveDate) {
            return query(null, null, effectiveDate);
        }

        private List<NormalizedOffer> query(Long productId, String currencyCode, LocalDate effectiveDate) {
            LocalDateTime date = effectiveDate.atStartOfDay();
            Object[] args = {date, date, productId, productId, currencyCode, currencyCode};
            List<NormalizedOffer> rows = new ArrayList<>(jdbcTemplate.query(TIERED_SQL, this::mapTier, args));
            rows.addAll(jdbcTemplate.query(LEGACY_SQL, this::mapLegacy, args));
            return rows;
        }

        private NormalizedOffer mapTier(ResultSet result, int row) throws SQLException {
            return new NormalizedOffer(result.getLong("fpr_product_id"), result.getString("pro_product_code"),
                    result.getString("pro_product_name"), result.getString("cur_iso_code"),
                    "ACTIVE".equalsIgnoreCase(result.getString("fpr_status")), decimal(result, "min_amount"),
                    decimal(result, "max_amount"), integer(result, "min_months"), integer(result, "max_months"),
                    decimal(result, "annual_rate"), result.getString("compounding"), result.getString("payout"),
                    result.getObject("applied_rate_id", Long.class), "NONE");
        }

        private NormalizedOffer mapLegacy(ResultSet result, int row) throws SQLException {
            String compounding = result.getString("compounding");
            if (compounding == null || compounding.isBlank()) compounding = "SIMPLE";
            String payout = result.getString("payout");
            if (payout == null || payout.isBlank()) payout = "AT_MATURITY";
            String closureRule = result.getString("closure_rule");
            if (closureRule == null || closureRule.isBlank()) closureRule = "UNSUPPORTED";
            return new NormalizedOffer(result.getLong("fpr_product_id"), result.getString("pro_product_code"),
                    result.getString("pro_product_name"), result.getString("cur_iso_code"),
                    "ACTIVE".equalsIgnoreCase(result.getString("fpr_status")), decimal(result, "min_amount"),
                    decimal(result, "max_amount"), integer(result, "min_months"), integer(result, "max_months"),
                    decimal(result, "annual_rate"), compounding, payout,
                    result.getObject("applied_rate_id", Long.class), closureRule);
        }

        private static BigDecimal decimal(ResultSet result, String name) throws SQLException {
            Object value = result.getObject(name);
            return value == null ? null : new BigDecimal(value.toString());
        }

        private static int integer(ResultSet result, String name) throws SQLException {
            Object value = result.getObject(name);
            return value == null ? 0 : new BigDecimal(value.toString()).intValueExact();
        }
    }
}
