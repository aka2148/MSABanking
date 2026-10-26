package com.bank.fdservice;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/calculator")
public class FdCalculatorController {
    private static final BigDecimal SENIOR_BOOST = new BigDecimal("0.50");
    private static final BigDecimal EMPLOYEE_BOOST = new BigDecimal("0.75");
    private static final BigDecimal MAX_CATEGORY_BOOST = new BigDecimal("2.00");
    private static final BigDecimal MAX_EFFECTIVE_RATE = new BigDecimal("8.50");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final JdbcTemplate jdbc;

    public FdCalculatorController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @PostMapping("/estimate")
    public EstimationResponse estimate(@Valid @RequestBody EstimationRequest request) {
        // 1. Resolve Categories & Boost
        Set<CustomerCategory> selectedCategories = new HashSet<>();
        if (request.categories() != null) {
            selectedCategories.addAll(request.categories());
        }
        if (request.category() != null) {
            selectedCategories.add(request.category());
        }

        BigDecimal totalBoost = BigDecimal.ZERO;
        for (CustomerCategory cat : selectedCategories) {
            if (cat == CustomerCategory.SENIOR_CITIZEN) {
                totalBoost = totalBoost.add(SENIOR_BOOST);
            } else if (cat == CustomerCategory.EMPLOYEE) {
                totalBoost = totalBoost.add(EMPLOYEE_BOOST);
            }
        }
        // Cap category boost at MAX_CATEGORY_BOOST (2.00%)
        BigDecimal cappedBoost = totalBoost.min(MAX_CATEGORY_BOOST);

        // 2. Resolve Base Rate
        BigDecimal baseRate = request.baseInterestRate();
        if (baseRate == null && request.productId() != null) {
            int tenureMonths = Math.max(1, (request.termDays() + 15) / 30);
            String curr = request.currencyCode() == null ? "INR" : request.currencyCode().toUpperCase(Locale.ROOT);
            List<Map<String, Object>> candidateBands = jdbc.queryForList(
                    "SELECT rr.annual_rate, rr.effective_from, rr.effective_to FROM fd_product_rate_ref rr JOIN fd_currency_ref c ON c.fcr_currency_id=rr.currency_id " +
                    "WHERE rr.product_id=? AND UPPER(c.fcr_iso_code)=? AND rr.minimum_tenure_months<=? AND rr.maximum_tenure_months>=?",
                    request.productId(), curr, tenureMonths, tenureMonths);
            if (candidateBands.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Requested tenure of " + tenureMonths + " months does not match any configured tenure rate band");
            }
            LocalDate evalDate = LocalDate.now();
            List<Map<String, Object>> effectiveBands = candidateBands.stream().filter(b -> {
                LocalDate from = b.get("effective_from") == null ? null : (b.get("effective_from") instanceof java.sql.Date d ? d.toLocalDate() : LocalDate.parse(b.get("effective_from").toString().substring(0, 10)));
                LocalDate to = b.get("effective_to") == null ? null : (b.get("effective_to") instanceof java.sql.Date d ? d.toLocalDate() : LocalDate.parse(b.get("effective_to").toString().substring(0, 10)));
                return (from == null || !evalDate.isBefore(from)) && (to == null || !evalDate.isAfter(to));
            }).toList();
            if (effectiveBands.isEmpty()) {
                boolean allFuture = candidateBands.stream().allMatch(b -> {
                    LocalDate from = b.get("effective_from") == null ? null : (b.get("effective_from") instanceof java.sql.Date d ? d.toLocalDate() : LocalDate.parse(b.get("effective_from").toString().substring(0, 10)));
                    return from != null && evalDate.isBefore(from);
                });
                if (allFuture) {
                    LocalDate earliest = candidateBands.stream().map(b -> b.get("effective_from") instanceof java.sql.Date d ? d.toLocalDate() : LocalDate.parse(b.get("effective_from").toString().substring(0, 10))).min(LocalDate::compareTo).orElse(null);
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "NOT_YET_EFFECTIVE: The rate schedule is not effective until " + earliest);
                }
                boolean allPast = candidateBands.stream().allMatch(b -> {
                    LocalDate to = b.get("effective_to") == null ? null : (b.get("effective_to") instanceof java.sql.Date d ? d.toLocalDate() : LocalDate.parse(b.get("effective_to").toString().substring(0, 10)));
                    return to != null && evalDate.isAfter(to);
                });
                if (allPast) {
                    LocalDate latest = candidateBands.stream().map(b -> b.get("effective_to") instanceof java.sql.Date d ? d.toLocalDate() : LocalDate.parse(b.get("effective_to").toString().substring(0, 10))).max(LocalDate::compareTo).orElse(null);
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "EXPIRED: The rate schedule expired on " + latest);
                }
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "NO_APPLICABLE_RATE: No active rate schedule is effective on " + evalDate);
            }
            if (effectiveBands.size() > 1) {
                BigDecimal firstRate = (BigDecimal) effectiveBands.get(0).get("annual_rate");
                boolean allSame = effectiveBands.stream().allMatch(b -> ((BigDecimal) b.get("annual_rate")).compareTo(firstRate) == 0);
                if (!allSame) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "AMBIGUOUS_CONFIGURATION: Multiple rate schedules overlap on " + evalDate + " with differing rates");
                }
                baseRate = firstRate.multiply(HUNDRED);
            } else {
                baseRate = ((BigDecimal) effectiveBands.get(0).get("annual_rate")).multiply(HUNDRED);
            }
        }
        if (baseRate == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Base interest rate or valid product reference is required");
        }

        // Effective Rate capped at MAX_EFFECTIVE_RATE (8.50%)
        BigDecimal rawEffective = baseRate.add(cappedBoost);
        BigDecimal effectiveRate = rawEffective.min(MAX_EFFECTIVE_RATE).setScale(2, RoundingMode.HALF_UP);

        // 3. Resolve Currency Scale
        int scale = 2;
        if (request.currencyCode() != null && !request.currencyCode().isBlank()) {
            Integer dbScale = jdbc.query("SELECT fcr_decimal_places FROM fd_currency_ref WHERE UPPER(fcr_iso_code)=UPPER(?)",
                    rs -> rs.next() ? rs.getInt(1) : null, request.currencyCode().trim());
            if (dbScale != null && dbScale >= 0 && dbScale <= 8) {
                scale = dbScale;
            }
        }

        // 4. Calculate Compounding using FdCompounding
        LocalDate start = LocalDate.now();
        LocalDate end = start.plusDays(request.termDays());
        BigDecimal annualRateFraction = effectiveRate.divide(HUNDRED, 12, RoundingMode.HALF_UP);
        BigDecimal maturity;
        BigDecimal interest;
        BigDecimal apyPercent;

        if ("SIMPLE".equalsIgnoreCase(request.compoundingFrequency())) {
            interest = request.principal()
                    .multiply(annualRateFraction)
                    .multiply(BigDecimal.valueOf(request.termDays()))
                    .divide(BigDecimal.valueOf(365), 12, RoundingMode.HALF_UP)
                    .setScale(scale, RoundingMode.HALF_UP);
            maturity = request.principal().add(interest).setScale(scale, RoundingMode.HALF_UP);
            apyPercent = effectiveRate.setScale(4, RoundingMode.HALF_UP);
        } else {
            String basis = FdCompounding.basis(request.compoundingFrequency());
            maturity = FdCompounding.maturity(request.principal(), annualRateFraction, start, end, scale, basis);
            interest = maturity.subtract(request.principal()).setScale(scale, RoundingMode.HALF_UP);
            int periods = 12 / FdCompounding.months(basis);
            BigDecimal periodicRate = annualRateFraction.divide(BigDecimal.valueOf(periods), 16, RoundingMode.HALF_UP);
            BigDecimal onePlusR = BigDecimal.ONE.add(periodicRate);
            BigDecimal apyFactor = onePlusR.pow(periods);
            apyPercent = apyFactor.subtract(BigDecimal.ONE).multiply(HUNDRED).setScale(4, RoundingMode.HALF_UP);
        }

        return new EstimationResponse(request.principal(), effectiveRate, interest, maturity, apyPercent);
    }

    public enum CustomerCategory { STANDARD, SENIOR_CITIZEN, EMPLOYEE }
    public record EstimationRequest(@NotNull @DecimalMin("1.00") BigDecimal principal,
                                    @DecimalMin("0.01") BigDecimal baseInterestRate,
                                    @NotNull @Min(1) Integer termDays,
                                    CustomerCategory category,
                                    List<CustomerCategory> categories,
                                    String currencyCode,
                                    Long productId,
                                    @NotBlank String compoundingFrequency) {
        public EstimationRequest(BigDecimal principal, BigDecimal baseInterestRate, Integer termDays, CustomerCategory category, String compoundingFrequency) {
            this(principal, baseInterestRate, termDays, category, null, "INR", null, compoundingFrequency);
        }
    }
    public record EstimationResponse(BigDecimal principal, BigDecimal effectiveInterestRate, BigDecimal totalInterest,
                                     BigDecimal maturityAmount, BigDecimal apy) {}
}
