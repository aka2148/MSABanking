package com.bank.fd.controller;

import com.bank.fd.application.FdBatchProcessingService;
import com.bank.fd.application.FdBatchRunResponse;
import com.bank.fd.domain.FdTimePolicy;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FdClockControllerTest {
    private static final Instant SYSTEM_TIME = Instant.parse("2026-01-01T23:30:00Z");
    private final FdTimePolicy timePolicy = new FdTimePolicy(
            Clock.fixed(SYSTEM_TIME, ZoneOffset.UTC), ZoneId.of("UTC"));
    private final FdBatchProcessingService batchService = mock(FdBatchProcessingService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(new FdClockController(timePolicy, batchService))
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @Test
    void statusAdvanceAndResetExposeEffectiveTime() throws Exception {
        when(batchService.runDaily(null)).thenReturn(new FdBatchRunResponse(
                LocalDate.of(2026, 5, 1), 3, 3, 0, 0, List.of()));

        mockMvc.perform(get("/api/admin/clock"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.system_time").value("2026-01-01T23:30:00Z"))
                .andExpect(jsonPath("$.effective_time").value("2026-01-01T23:30:00Z"))
                .andExpect(jsonPath("$.simulation_active").value(false));

        mockMvc.perform(post("/api/admin/clock/advance").contentType("application/json")
                        .content("{\"days\":120}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.system_time").value("2026-01-01T23:30:00Z"))
                .andExpect(jsonPath("$.business_date").value("2026-05-01"))
                .andExpect(jsonPath("$.business_zone").value("UTC"))
                .andExpect(jsonPath("$.simulation_active").value(true))
                .andExpect(jsonPath("$.batch_run.business_date").value("2026-05-01"))
                .andExpect(jsonPath("$.batch_run.accounts_examined").value(3))
                .andExpect(jsonPath("$.batch_run.accruals_processed").value(3));

        verify(batchService).runDaily(null);

        mockMvc.perform(post("/api/admin/clock/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.effective_time").value("2026-01-01T23:30:00Z"))
                .andExpect(jsonPath("$.simulation_active").value(false));
    }

    @Test
    void clockControlsRemainAdminOnly() throws Exception {
        assertEquals("/api/admin/clock", FdClockController.class.getAnnotation(RequestMapping.class).value()[0]);
        assertAdminOnly("status", GetMapping.class, "");
        assertAdminOnly("advance", PostMapping.class, "/advance");
        assertAdminOnly("reset", PostMapping.class, "/reset");
    }

    private static void assertAdminOnly(String methodName,
                                        Class<? extends java.lang.annotation.Annotation> mappingType,
                                        String expectedPath) throws Exception {
        var method = methodName.equals("advance")
                ? FdClockController.class.getDeclaredMethod(methodName, FdClockController.AdvanceClockRequest.class)
                : FdClockController.class.getDeclaredMethod(methodName);
        assertEquals("hasRole('ADMIN')", method.getAnnotation(PreAuthorize.class).value());
        if (mappingType == GetMapping.class) {
            assertEquals(expectedPath, method.getAnnotation(GetMapping.class).value().length == 0
                    ? "" : method.getAnnotation(GetMapping.class).value()[0]);
        } else {
            assertEquals(expectedPath, method.getAnnotation(PostMapping.class).value()[0]);
        }
    }
}
