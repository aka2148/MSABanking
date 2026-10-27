package com.bank.productservice;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FdOfferValidationTest {

        @Mock
        private JdbcTemplate jdbc;
        @Mock
        private FixedDepositCategories fdCategories;
        @Mock
        private FdComplianceService compliance;

        private ObjectMapper json;
        private FdOfferController controller;
        private FdProductTemplateBootstrap bootstrap;

        @BeforeEach
        void setUp() {
                json = new ObjectMapper();
                controller = new FdOfferController(jdbc, json, fdCategories, compliance);
                bootstrap = new FdProductTemplateBootstrap(jdbc, controller, fdCategories, true);
        }

        private FdOfferController.OfferRequest validOfferRequest(String code) {
                FdOfferController.RateBand band1 = new FdOfferController.RateBand(1, 12, new BigDecimal("0.0500"),
                                "QUARTERLY", "AT_MATURITY");
                FdOfferController.RateBand band2 = new FdOfferController.RateBand(13, 24, new BigDecimal("0.0600"),
                                "QUARTERLY", "AT_MATURITY");
                FdOfferController.CurrencySchedule schedule = new FdOfferController.CurrencySchedule(
                                "INR", new BigDecimal("1000.00"), new BigDecimal("100000.00"), List.of(band1, band2),
                                new BigDecimal("1.0000"));
                return new FdOfferController.OfferRequest(
                                code, "Valid Fixed Deposit", 100L, "Sample valid fixed deposit.", "INR",
                                List.of(schedule), LocalDate.now(), LocalDate.now().plusYears(5));
        }

        @Test
        @DisplayName("Positive: Valid multi-currency offer creation succeeds in DRAFT")
        void createValidMultiCurrencyFdOffer() {
                when(jdbc.queryForList(anyString(), eq(Long.class), eq("FD_VALID"))).thenReturn(List.of());
                when(jdbc.queryForList(anyString(), eq(Long.class), eq(100L))).thenReturn(List.of(100L));
                when(fdCategories.ids()).thenReturn(Set.of(100));
                when(jdbc.queryForList(anyString(), eq(Long.class), eq("INR"))).thenReturn(List.of(1L));
                when(jdbc.queryForObject(contains("LAST_INSERT_ID"), eq(Long.class))).thenReturn(10L, 20L, 30L, 40L);
                when(jdbc.queryForObject(contains("rut_rule_type_code='FD_TENURE_RATE'"), eq(Long.class)))
                                .thenReturn(5L);

                // response mock
                when(jdbc.queryForList(contains("SELECT p.pro_product_id"), eq(10L)))
                                .thenReturn(List.of(Map.of("pro_product_id", 10L, "pro_product_code", "FD_VALID",
                                                "pro_product_name", "Valid Fixed Deposit", "pro_status", "DRAFT",
                                                "pro_category_id", 100L)));
                when(jdbc.queryForList(contains("SELECT c.cur_iso_code FROM product_currency_config"), eq(String.class),
                                eq(10L)))
                                .thenReturn(List.of("INR"));

                FdOfferController.OfferResponse response = controller.create(validOfferRequest("FD_VALID"));
                assertNotNull(response);
                assertEquals("FD_VALID", response.productCode());
                assertEquals("DRAFT", response.status());
                assertEquals(List.of("INR"), response.currencyCodes());
        }

        @Test
        @DisplayName("Negative: Duplicate product code throws 409 CONFLICT")
        void rejectDuplicateProductCode() {
                when(jdbc.queryForList(anyString(), eq(Long.class), eq("FD_DUP"))).thenReturn(List.of(99L));
                ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                                () -> controller.create(validOfferRequest("FD_DUP")));
                assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
                assertTrue(ex.getReason().contains("already exists"));
        }

        @Test
        @DisplayName("Negative: Inverted effective dates throw 400 BAD_REQUEST")
        void rejectInvertedEffectiveDates() {
                FdOfferController.RateBand band = new FdOfferController.RateBand(1, 12, new BigDecimal("0.0500"));
                FdOfferController.CurrencySchedule schedule = new FdOfferController.CurrencySchedule(
                                "INR", new BigDecimal("1000.00"), new BigDecimal("10000.00"), List.of(band),
                                new BigDecimal("1.0000"));
                FdOfferController.OfferRequest request = new FdOfferController.OfferRequest(
                                "FD_DATE_ERR", "Inverted Date Deposit", 100L, "Invalid dates.", "INR",
                                List.of(schedule), LocalDate.now().plusDays(10), LocalDate.now());

                ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                                () -> controller.create(request));
                assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
                assertTrue(ex.getReason().contains("Effective-to date must be on or after"));
        }

        @Test
        @DisplayName("Negative: Inverted tenure range throws 400 BAD_REQUEST")
        void rejectInvertedTenureRange() {
                FdOfferController.RateBand band = new FdOfferController.RateBand(12, 6, new BigDecimal("0.0500"));
                FdOfferController.CurrencySchedule schedule = new FdOfferController.CurrencySchedule(
                                "INR", new BigDecimal("1000.00"), new BigDecimal("10000.00"), List.of(band),
                                new BigDecimal("1.0000"));
                FdOfferController.OfferRequest request = new FdOfferController.OfferRequest(
                                "FD_TENURE_ERR", "Inverted Tenure Deposit", 100L, "Invalid tenure.", "INR",
                                List.of(schedule), LocalDate.now(), LocalDate.now().plusYears(1));

                ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                                () -> controller.create(request));
                assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
                assertTrue(ex.getReason().contains("Tenure range is inverted"));
        }

        @Test
        @DisplayName("Negative: Non-contiguous tenure bands throw 400 BAD_REQUEST")
        void rejectNonContiguousTenureBands() {
                FdOfferController.RateBand band1 = new FdOfferController.RateBand(1, 6, new BigDecimal("0.0500"));
                FdOfferController.RateBand band2 = new FdOfferController.RateBand(8, 12, new BigDecimal("0.0600")); // gap:
                                                                                                                    // 7
                                                                                                                    // missing
                FdOfferController.CurrencySchedule schedule = new FdOfferController.CurrencySchedule(
                                "INR", new BigDecimal("1000.00"), new BigDecimal("10000.00"), List.of(band1, band2),
                                new BigDecimal("1.0000"));
                FdOfferController.OfferRequest request = new FdOfferController.OfferRequest(
                                "FD_GAP_ERR", "Gap Tenure Deposit", 100L, "Gap in tenure.", "INR",
                                List.of(schedule), LocalDate.now(), LocalDate.now().plusYears(1));

                ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                                () -> controller.create(request));
                assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
                assertTrue(ex.getReason().contains("Tenure bands must be contiguous"));
        }

        @Test
        @DisplayName("Negative: Premature penalty out of range throws 400 BAD_REQUEST")
        void rejectInvalidPrematurePenalty() {
                FdOfferController.RateBand band = new FdOfferController.RateBand(1, 12, new BigDecimal("0.0500"));
                FdOfferController.CurrencySchedule scheduleNegative = new FdOfferController.CurrencySchedule(
                                "INR", new BigDecimal("1000.00"), new BigDecimal("10000.00"), List.of(band),
                                new BigDecimal("-0.5000"));
                FdOfferController.OfferRequest requestNeg = new FdOfferController.OfferRequest(
                                "FD_PEN_NEG", "Negative Penalty", 100L, "Negative penalty.", "INR",
                                List.of(scheduleNegative), LocalDate.now(), LocalDate.now().plusYears(1));

                ResponseStatusException exNeg = assertThrows(ResponseStatusException.class,
                                () -> controller.create(requestNeg));
                assertEquals(HttpStatus.BAD_REQUEST, exNeg.getStatusCode());
                assertTrue(exNeg.getReason().contains("between 0 and 100"));

                FdOfferController.CurrencySchedule scheduleOver = new FdOfferController.CurrencySchedule(
                                "INR", new BigDecimal("1000.00"), new BigDecimal("10000.00"), List.of(band),
                                new BigDecimal("105.0000"));
                FdOfferController.OfferRequest requestOver = new FdOfferController.OfferRequest(
                                "FD_PEN_OVER", "Over 100 Penalty", 100L, "Over 100 penalty.", "INR",
                                List.of(scheduleOver), LocalDate.now(), LocalDate.now().plusYears(1));

                ResponseStatusException exOver = assertThrows(ResponseStatusException.class,
                                () -> controller.create(requestOver));
                assertEquals(HttpStatus.BAD_REQUEST, exOver.getStatusCode());
        }

        @Test
        @DisplayName("Negative: Invalid compounding basis throws 400 BAD_REQUEST")
        void rejectInvalidCompoundingBasis() {
                FdOfferController.RateBand band = new FdOfferController.RateBand(1, 12, new BigDecimal("0.0500"),
                                "DAILY", "AT_MATURITY");
                FdOfferController.CurrencySchedule schedule = new FdOfferController.CurrencySchedule(
                                "INR", new BigDecimal("1000.00"), new BigDecimal("10000.00"), List.of(band),
                                new BigDecimal("1.0000"));
                FdOfferController.OfferRequest request = new FdOfferController.OfferRequest(
                                "FD_COMP_ERR", "Invalid Compounding", 100L, "Daily compounding not supported.", "INR",
                                List.of(schedule), LocalDate.now(), LocalDate.now().plusYears(1));

                ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                                () -> controller.create(request));
                assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
                assertTrue(ex.getReason().contains("Compounding basis must be one of"));
        }

        @Test
        @DisplayName("Negative: Activating already ACTIVE product throws 409 CONFLICT")
        void rejectActivateAlreadyActiveProduct() {
                when(fdCategories.ids()).thenReturn(Set.of(100));
                when(jdbc.queryForList(contains("SELECT p.pro_product_id"), eq(55L)))
                                .thenReturn(List.of(Map.of("pro_product_id", 55L, "pro_product_code", "FD_ACTIVE",
                                                "pro_product_name", "Active Deposit", "pro_status", "ACTIVE",
                                                "pro_category_id", 100L)));
                when(jdbc.queryForList(contains("SELECT c.cur_iso_code FROM product_currency_config"), eq(String.class),
                                eq(55L)))
                                .thenReturn(List.of("INR"));

                ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                                () -> controller.activate(55L));
                assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
                assertTrue(ex.getReason().contains("already active"));
        }

        @Test
        @DisplayName("Negative: Deactivating non-active product throws 409 CONFLICT")
        void rejectDeactivateNonActiveProduct() {
                when(fdCategories.ids()).thenReturn(Set.of(100));
                when(jdbc.queryForList(contains("SELECT p.pro_product_id"), eq(55L)))
                                .thenReturn(List.of(Map.of("pro_product_id", 55L, "pro_product_code", "FD_DRAFT",
                                                "pro_product_name", "Draft Deposit", "pro_status", "DRAFT",
                                                "pro_category_id", 100L)));
                when(jdbc.queryForList(contains("SELECT c.cur_iso_code FROM product_currency_config"), eq(String.class),
                                eq(55L)))
                                .thenReturn(List.of("INR"));

                ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                                () -> controller.deactivate(55L));
                assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
                assertTrue(ex.getReason().contains("Only an active FD product can be deactivated"));
        }

        @Test
        @DisplayName("Positive & Idempotent: Bootstrap creates and activates FD_STANDARD once, skips on repeated run")
        void bootstrapIdempotency() {
                // Run 1: When FD_STANDARD does not exist
                when(jdbc.queryForList(contains("pc_category_code)='FIXED_DEPOSIT'"), eq(Long.class)))
                                .thenReturn(List.of(100L));
                when(jdbc.queryForList(contains(
                                "SELECT pro_product_id, pro_status FROM product WHERE UPPER(pro_product_code)=UPPER(?)"),
                                eq(FdProductTemplateBootstrap.STANDARD_PRODUCT_CODE)))
                                .thenReturn(List.of()) // first run: not found
                                .thenReturn(List.of(Map.of("pro_product_id", 1L, "pro_status", "ACTIVE"))); // second
                                                                                                            // run:
                                                                                                            // already
                                                                                                            // active

                when(jdbc.queryForObject(contains("SELECT COUNT(*) FROM currency WHERE UPPER(cur_iso_code)=UPPER(?)"),
                                eq(Integer.class), anyString()))
                                .thenReturn(1);
                when(jdbc.queryForList(anyString(), eq(Long.class), anyString())).thenReturn(List.of(1L));
                when(jdbc.queryForList(contains("SELECT pro_product_id FROM product WHERE UPPER(pro_product_code)"),
                                eq(Long.class), eq("FD_STANDARD"))).thenReturn(List.of());
                when(jdbc.queryForList(anyString(), eq(Long.class), eq(100L))).thenReturn(List.of(100L));
                when(fdCategories.ids()).thenReturn(Set.of(100));
                when(jdbc.queryForObject(contains("LAST_INSERT_ID"), eq(Long.class))).thenReturn(1L, 2L, 3L, 4L, 5L, 6L,
                                7L, 8L);
                when(jdbc.queryForObject(contains("rut_rule_type_code='FD_TENURE_RATE'"), eq(Long.class)))
                                .thenReturn(5L);

                // create and activate responses
                when(jdbc.queryForList(contains("SELECT p.pro_product_id,p.pro_product_code"), eq(1L)))
                                .thenReturn(List.of(Map.of("pro_product_id", 1L, "pro_product_code", "FD_STANDARD",
                                                "pro_product_name", "Standard Fixed Deposit", "pro_status", "DRAFT",
                                                "pro_category_id", 100L))) // return of create
                                .thenReturn(List.of(Map.of("pro_product_id", 1L, "pro_product_code", "FD_STANDARD",
                                                "pro_product_name", "Standard Fixed Deposit", "pro_status", "DRAFT",
                                                "pro_category_id", 100L))) // check in activate
                                .thenReturn(List.of(Map.of("pro_product_id", 1L, "pro_product_code", "FD_STANDARD",
                                                "pro_product_name", "Standard Fixed Deposit", "pro_status", "ACTIVE",
                                                "pro_category_id", 100L))); // return of activate
                when(jdbc.queryForList(contains("SELECT c.cur_iso_code FROM product_currency_config"), eq(String.class),
                                eq(1L)))
                                .thenReturn(List.of("INR", "USD", "KWD", "JPY"));

                // activate configs
                when(jdbc.queryForList(contains("SELECT p.pcc_currency_id,p.pcc_is_base_currency"), eq(1L)))
                                .thenReturn(List.of(
                                                Map.of("pcc_currency_id", 1L, "pcc_is_base_currency", true,
                                                                "cur_iso_code", "INR"),
                                                Map.of("pcc_currency_id", 2L, "pcc_is_base_currency", false,
                                                                "cur_iso_code", "USD"),
                                                Map.of("pcc_currency_id", 3L, "pcc_is_base_currency", false,
                                                                "cur_iso_code", "KWD"),
                                                Map.of("pcc_currency_id", 4L, "pcc_is_base_currency", false,
                                                                "cur_iso_code", "JPY")));
                when(jdbc.queryForObject(contains("SELECT COUNT(*) FROM product_rule r JOIN rule_outcome"),
                                eq(Integer.class), eq(1L), anyLong()))
                                .thenReturn(4);
                when(jdbc.queryForMap(contains("SELECT pro_product_code"), eq(1L)))
                                .thenReturn(Map.of("pro_product_code", "FD_STANDARD", "pro_product_name",
                                                "Standard Fixed Deposit", "pro_status", "ACTIVE", "pro_event_version",
                                                1L));

                // First run
                bootstrap.run(new DefaultApplicationArguments());
                verify(jdbc, atLeastOnce()).update(contains("UPDATE product SET pro_status='ACTIVE'"), eq(1L));

                // Second run (repeated startup)
                bootstrap.run(new DefaultApplicationArguments());
                // Verify duplicate product was not inserted
                verify(jdbc, times(1)).update(contains("INSERT INTO product(pro_product_code"), any(), any(), any(),
                                any(), any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("Activation: Calls FdComplianceService and activates when valid")
        void activate_callsComplianceValidation_andSucceedsWhenValid() {
                when(jdbc.queryForList(contains("SELECT p.pro_product_id,p.pro_product_code"), eq(10L)))
                                .thenReturn(List.of(Map.of("pro_product_id", 10L, "pro_product_code", "FD_ACTIVE",
                                                "pro_product_name", "Active FD", "pro_status", "DRAFT",
                                                "pro_category_id", 100L)))
                                .thenReturn(List.of(Map.of("pro_product_id", 10L, "pro_product_code", "FD_ACTIVE",
                                                "pro_product_name", "Active FD", "pro_status", "ACTIVE",
                                                "pro_category_id", 100L)));
                when(fdCategories.ids()).thenReturn(Set.of(100));
                when(jdbc.queryForList(contains("SELECT c.cur_iso_code FROM product_currency_config"), eq(String.class),
                                eq(10L)))
                                .thenReturn(List.of("INR"));
                when(jdbc.queryForList(contains("SELECT p.pcc_currency_id,p.pcc_is_base_currency"), eq(10L)))
                                .thenReturn(List.of(Map.of("pcc_currency_id", 1L, "pcc_is_base_currency", true,
                                                "cur_iso_code", "INR")));
                when(jdbc.queryForObject(contains("SELECT COUNT(*) FROM product_rule r JOIN rule_outcome"),
                                eq(Integer.class), eq(10L), anyLong()))
                                .thenReturn(1);
                when(jdbc.queryForMap(contains("SELECT pro_product_code"), eq(10L)))
                                .thenReturn(Map.of("pro_product_code", "FD_ACTIVE", "pro_product_name", "Active FD",
                                                "pro_status", "ACTIVE", "pro_event_version", 1L));

                doNothing().when(compliance).validateProductScheduleForActivation(10L);

                FdOfferController.OfferResponse res = controller.activate(10L);
                assertNotNull(res);
                assertEquals("ACTIVE", res.status());
                verify(compliance, times(1)).validateProductScheduleForActivation(10L);
                verify(jdbc).update(contains("UPDATE product SET pro_status='ACTIVE'"), eq(10L));
        }

        @Test
        @DisplayName("Activation: Rejects activation when FdComplianceService detects expired or ambiguous schedule")
        void activate_failsWhenComplianceValidationFails() {
                when(jdbc.queryForList(contains("SELECT p.pro_product_id,p.pro_product_code"), eq(20L)))
                                .thenReturn(List.of(Map.of("pro_product_id", 20L, "pro_product_code", "FD_FAIL",
                                                "pro_product_name", "Fail FD", "pro_status", "DRAFT", "pro_category_id",
                                                100L)));
                when(fdCategories.ids()).thenReturn(Set.of(100));

                doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                "Product schedule has expired on 2025-01-01; expired products cannot be activated."))
                                .when(compliance).validateProductScheduleForActivation(20L);

                ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                                () -> controller.activate(20L));
                assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
                assertTrue(ex.getReason().contains("expired"));
                verify(jdbc, never()).update(contains("UPDATE product SET pro_status='ACTIVE'"), eq(20L));
        }
}
