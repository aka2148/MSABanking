package com.bank.fd.controller;

import com.bank.fd.application.FdBatchProcessingService;
import com.bank.fd.application.FdBatchRunResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BatchTriggerControllerTest {
    private final FdBatchProcessingService batchService = mock(FdBatchProcessingService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(new BatchTriggerController(batchService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @Test
    void allManualBatchRoutesDelegateAndReturnTheBatchSummary() throws Exception {
        FdBatchRunResponse summary = new FdBatchRunResponse(
                LocalDate.of(2026, 4, 1), 2, 2, 1, 0, List.of());
        when(batchService.runAccrual(null)).thenReturn(summary);
        when(batchService.runMaturity(null)).thenReturn(summary);
        when(batchService.runDaily(null)).thenReturn(summary);

        mockMvc.perform(post("/api/admin/batch/accrue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.business_date").value("2026-04-01"))
                .andExpect(jsonPath("$.accruals_processed").value(2));
        mockMvc.perform(post("/api/admin/batch/maturity"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.maturities_processed").value(1));
        mockMvc.perform(post("/api/admin/batch/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accounts_examined").value(2));

        verify(batchService).runAccrual(null);
        verify(batchService).runMaturity(null);
        verify(batchService).runDaily(null);
    }

    @Test
    void manualBatchRoutesRemainStaffOnly() throws Exception {
        assertEquals("/api/admin/batch", BatchTriggerController.class.getAnnotation(RequestMapping.class).value()[0]);
        assertStaffOnly("triggerAccrue", "/accrue");
        assertStaffOnly("triggerMaturity", "/maturity");
        assertStaffOnly("triggerDailyBatch", "/run");
    }

    private static void assertStaffOnly(String methodName, String route) throws Exception {
        var method = BatchTriggerController.class.getDeclaredMethod(methodName);
        assertEquals("hasAnyRole('BANK_OFFICER', 'ADMIN')", method.getAnnotation(PreAuthorize.class).value());
        assertEquals(route, method.getAnnotation(PostMapping.class).value()[0]);
    }
}
