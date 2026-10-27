package com.bank.productservice;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FdComplianceControllerTest {

    @Mock
    private FdComplianceService compliance;

    @Mock
    private JdbcTemplate jdbc;

    private FdComplianceController complianceController;
    private ProductCatalogController catalogController;

    @BeforeEach
    void setUp() {
        complianceController = new FdComplianceController(compliance);
        catalogController = new ProductCatalogController(jdbc, compliance);
    }

    @Test
    @DisplayName("GET /products/{id}/schedule delegates to FdComplianceService")
    void getSchedule_delegatesToService() {
        FdComplianceService.ProductScheduleResponse mockResponse = new FdComplianceService.ProductScheduleResponse(
                1L, "FD_STANDARD", "Standard Fixed Deposit", "ACTIVE",
                LocalDate.of(2026, 1, 1), LocalDate.of(2036, 1, 1),
                List.of(new FdComplianceService.CurrencyLimitSummary("INR", new BigDecimal("1000.00"), new BigDecimal("10000000.00"), true)),
                List.of(new FdComplianceService.RateBandSummary(10L, "INR", 1, 12, new BigDecimal("0.0650"), "QUARTERLY", "AT_MATURITY", LocalDate.of(2026, 1, 1), LocalDate.of(2036, 1, 1), true, "ACTIVE"))
        );
        when(compliance.getProductSchedule(1L)).thenReturn(mockResponse);

        FdComplianceService.ProductScheduleResponse result = complianceController.getSchedule(1L);
        assertNotNull(result);
        assertEquals(1L, result.productId());
        assertEquals("FD_STANDARD", result.productCode());
        assertEquals(1, result.rateBands().size());
        verify(compliance, times(1)).getProductSchedule(1L);
    }

    @Test
    @DisplayName("PUT /products/{id}/schedule delegates to FdComplianceService")
    void updateSchedule_delegatesToService() {
        FdComplianceService.UpdateScheduleRequest request = new FdComplianceService.UpdateScheduleRequest(
                LocalDate.of(2026, 1, 1), LocalDate.of(2036, 1, 1),
                List.of(new FdComplianceService.RateBandInput("INR", 1, 12, new BigDecimal("0.0700"), "QUARTERLY", "AT_MATURITY", LocalDate.of(2026, 1, 1), LocalDate.of(2036, 1, 1)))
        );
        FdComplianceService.ProductScheduleResponse mockResponse = new FdComplianceService.ProductScheduleResponse(
                1L, "FD_STANDARD", "Standard Fixed Deposit", "ACTIVE",
                request.effectiveFrom(), request.effectiveTo(),
                List.of(), List.of()
        );
        when(compliance.updateProductSchedule(eq(1L), any())).thenReturn(mockResponse);

        FdComplianceService.ProductScheduleResponse result = complianceController.updateSchedule(1L, request);
        assertNotNull(result);
        assertEquals(1L, result.productId());
        verify(compliance, times(1)).updateProductSchedule(1L, request);
    }

    @Test
    @DisplayName("ProductCatalogController.status() validates compliance before activating FD product")
    @SuppressWarnings("unchecked")
    void statusPatch_validatesComplianceOnFdActivation() {
        // Mock product lookup
        when(jdbc.query(contains("SELECT p.pro_product_id"), any(RowMapper.class), any(), anyLong()))
                .thenReturn(List.of(new ProductCatalogController.ProductView(1L, "FD_STANDARD", "Standard FD", 100L, "INR", "DRAFT", "2026-01-01", "2036-01-01", "Desc")));
        when(jdbc.queryForList(contains("SELECT pcc_config_id FROM product_currency_config"), eq(Long.class), eq(1L)))
                .thenReturn(List.of(1L));
        // Mock FD category check -> returns 1 (it is an FD)
        when(jdbc.queryForObject(contains("SELECT COUNT(*) FROM product p JOIN product_category"), eq(Integer.class), eq(1L)))
                .thenReturn(1);
        when(jdbc.queryForMap(contains("SELECT pro_product_code"), eq(1L)))
                .thenReturn(Map.of("pro_product_code", "FD_STANDARD", "pro_product_name", "Standard FD", "pro_status", "ACTIVE", "pro_event_version", 1L));

        doNothing().when(compliance).validateProductScheduleForActivation(1L);

        ProductCatalogController.ProductView result = catalogController.status(1L, Map.of("status", "ACTIVE"));
        assertNotNull(result);
        verify(compliance, times(1)).validateProductScheduleForActivation(1L);
    }

    @Test
    @DisplayName("ProductCatalogController.status() rejects activation when compliance validation fails")
    @SuppressWarnings("unchecked")
    void statusPatch_failsWhenComplianceValidationFails() {
        when(jdbc.query(contains("SELECT p.pro_product_id"), any(RowMapper.class), any(), anyLong()))
                .thenReturn(List.of(new ProductCatalogController.ProductView(1L, "FD_STANDARD", "Standard FD", 100L, "INR", "DRAFT", "2026-01-01", "2036-01-01", "Desc")));
        when(jdbc.queryForList(contains("SELECT pcc_config_id FROM product_currency_config"), eq(Long.class), eq(1L)))
                .thenReturn(List.of(1L));
        when(jdbc.queryForObject(contains("SELECT COUNT(*) FROM product p JOIN product_category"), eq(Integer.class), eq(1L)))
                .thenReturn(1);

        doThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rate schedules for currency INR have expired."))
                .when(compliance).validateProductScheduleForActivation(1L);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> catalogController.status(1L, Map.of("status", "ACTIVE")));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertTrue(ex.getReason().contains("expired"));
        verify(jdbc, never()).update(contains("UPDATE product SET pro_status=?"), any(), any());
    }
}
