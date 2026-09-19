package com.bank.fd.application;

import com.bank.common.exception.BankingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * Resolves FD terms from normalized product/rule/currency rows and local FD
 * reference identities. It deliberately fails closed when the projection is
 * absent, inactive, ambiguous, or incomplete.
 */
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
        if (effectiveDate == null) {
            throw new ProductTermsResolutionException("Effective date is required");
        }

        List<NormalizedOffer> matches = dataSource.find(query.productId(),
                query.currencyCode().trim().toUpperCase(Locale.ROOT), effectiveDate);
        if (matches == null || matches.size() != 1) {
            throw new ProductTermsResolutionException("FD terms are missing or ambiguous for the requested product");
        }

        NormalizedOffer offer = matches.get(0);
        if (!offer.active() || offer.productId() == null || !offer.productId().equals(query.productId())
                || offer.currencyCode() == null
                || !offer.currencyCode().equalsIgnoreCase(query.currencyCode().trim())) {
            throw new ProductTermsResolutionException("FD product or currency is not active and eligible");
        }
        if (offer.minimumAmount() == null || offer.maximumAmount() == null
                || query.principal().compareTo(offer.minimumAmount()) < 0
                || query.principal().compareTo(offer.maximumAmount()) > 0
                || query.tenureMonths() < offer.minimumTenureMonths()
                || query.tenureMonths() > offer.maximumTenureMonths()) {
            throw new ProductTermsResolutionException("FD principal or tenure is outside the supported range");
        }
        try {
            return new ProductTerms(
                    offer.productId(), offer.productCode(), offer.productName(),
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
        private static final String SQL = """
                SELECT p.pro_product_id, p.pro_product_code, p.pro_product_name,
                       fpr.fpr_status, c.cur_iso_code, fcr.fcr_currency_id,
                       fpr.fpr_product_id, frr.fror_outcome_id,
                       MIN(CASE WHEN upper(pr.pr_rule_name) = 'AMOUNT_RANGE' THEN rc.rc_value_from END) AS min_amount,
                       MAX(CASE WHEN upper(pr.pr_rule_name) = 'AMOUNT_RANGE' THEN rc.rc_value_to END) AS max_amount,
                       MIN(CASE WHEN upper(pr.pr_rule_name) = 'TENURE_RANGE' THEN rc.rc_value_from END) AS min_months,
                       MAX(CASE WHEN upper(pr.pr_rule_name) = 'TENURE_RANGE' THEN rc.rc_value_to END) AS max_months,
                       MAX(CASE WHEN upper(ro.ro_outcome_type) = 'INTEREST_RATE' THEN ro.ro_outcome_value END) AS annual_rate,
                       MAX(CASE WHEN upper(ro.ro_outcome_type) = 'INTEREST_RATE' THEN ro.ro_compounding_basis END) AS compounding,
                       MAX(CASE WHEN upper(ro.ro_outcome_type) = 'INTEREST_RATE' THEN ro.ro_payout_frequency END) AS payout,
                       MAX(CASE WHEN upper(pr.pr_rule_name) = 'CLOSURE_PENALTY' THEN ro.ro_outcome_value END) AS closure_rule
                  FROM public.product p
                  JOIN public.fd_product_ref fpr ON upper(fpr.fpr_product_code) = upper(p.pro_product_code)
                  JOIN public.product_currency_config pcc ON pcc.pcc_product_id = p.pro_product_id
                  JOIN public.currency c ON c.cur_currency_id = pcc.pcc_currency_id
                  JOIN public.fd_currency_ref fcr ON upper(fcr.fcr_iso_code) = upper(c.cur_iso_code)
                  LEFT JOIN public.product_rule pr ON pr.pr_product_id = p.pro_product_id AND pr.pr_is_active
                  LEFT JOIN public.rule_outcome ro ON ro.ro_rule_id = pr.pr_rule_id
                  LEFT JOIN public.rule_condition rc ON rc.rc_rule_id = pr.pr_rule_id
                  LEFT JOIN public.fd_rule_outcome_ref frr
                         ON upper(frr.fror_outcome_type) = upper(ro.ro_outcome_type)
                        AND frr.fror_outcome_value = ro.ro_outcome_value
                 WHERE fpr.fpr_product_id = ? AND upper(c.cur_iso_code) = upper(?)
                   AND upper(p.pro_status) = 'ACTIVE' AND coalesce(fpr.fpr_status, 'ACTIVE') = 'ACTIVE'
                   AND c.cur_is_active
                 GROUP BY p.pro_product_id, p.pro_product_code, p.pro_product_name,
                          fpr.fpr_status, c.cur_iso_code, fcr.fcr_currency_id,
                          fpr.fpr_product_id, frr.fror_outcome_id
                """;

        private final JdbcTemplate jdbcTemplate;

        private JdbcTemplateTermsDataSource(JdbcTemplate jdbcTemplate) {
            this.jdbcTemplate = jdbcTemplate;
        }

        @Override
        public List<NormalizedOffer> find(Long productId, String currencyCode, LocalDate effectiveDate) {
            return jdbcTemplate.query(SQL, this::map, productId, currencyCode);
        }

        private NormalizedOffer map(ResultSet result, int row) throws SQLException {
            return new NormalizedOffer(
                    result.getLong("fpr_product_id"), result.getString("pro_product_code"),
                    result.getString("pro_product_name"), result.getString("cur_iso_code"),
                    "ACTIVE".equalsIgnoreCase(result.getString("fpr_status")),
                    decimal(result, "min_amount"), decimal(result, "max_amount"),
                    integer(result, "min_months"), integer(result, "max_months"),
                    decimal(result, "annual_rate"), result.getString("compounding"),
                    result.getString("payout"), result.getObject("fror_outcome_id", Long.class),
                    result.getString("closure_rule"));
        }

        private static BigDecimal decimal(ResultSet result, String name) throws SQLException {
            String value = result.getString(name);
            return value == null ? null : new BigDecimal(value);
        }

        private static int integer(ResultSet result, String name) throws SQLException {
            String value = result.getString(name);
            return value == null ? 0 : new BigDecimal(value).intValueExact();
        }
    }
}
