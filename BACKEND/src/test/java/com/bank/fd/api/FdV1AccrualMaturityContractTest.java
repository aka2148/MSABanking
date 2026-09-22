package com.bank.fd.api;

import com.bank.common.exception.GlobalExceptionHandler;
import com.bank.fd.api.controller.FdV1ContractController;
import com.bank.fd.api.dto.AccrualResponse;
import com.bank.fd.application.AccrualService;
import com.bank.fd.application.AccountBookingService;
import com.bank.fd.application.ClosureService;
import com.bank.fd.application.FdActorResolver;
import com.bank.fd.application.FundingService;
import com.bank.fd.application.HistoryService;
import com.bank.fd.application.MaturityService;
import com.bank.fd.application.PrematureClosureService;
import com.bank.fd.application.ProductOfferQueryService;
import com.bank.fd.domain.FdLifecycleCommand;
import com.bank.fd.domain.FdTimePolicy;
import com.bank.fd.entity.FdStatus;
import com.bank.fd.exception.InvalidFdTransitionException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FdV1AccrualMaturityContractTest {
    private final AccrualService accrualService = mock(AccrualService.class);
    private final MaturityService maturityService = mock(MaturityService.class);
    private final Authentication authentication = mock(Authentication.class);
    private MockMvc mockMvc;

    private static AccrualResponse activeResponse() {
        return new AccrualResponse(11L, "FD000011", LocalDate.of(2026, 1, 1),
                new BigDecimal("6500.00"), new BigDecimal("6500.00"), "USD",
                new BigDecimal("106500.00"), "academic-v1", "ACTIVE");
    }

    @BeforeEach
    void setUp() {
        when(authentication.getName()).thenReturn("alice");
        FdV1ContractController controller = new FdV1ContractController(
                mock(ProductOfferQueryService.class), mock(AccountBookingService.class),
                mock(FundingService.class), accrualService, maturityService,
                mock(ClosureService.class), mock(PrematureClosureService.class),
                mock(HistoryService.class), mock(com.bank.fd.repository.FdAccountRepository.class),
                mock(com.bank.fd.repository.FdProductReferenceRepository.class),
                mock(com.bank.fd.repository.FdCurrencyReferenceRepository.class),
                mock(com.bank.fd.repository.FdPrematureClosureRepository.class),
                mock(com.bank.fd.repository.TransactionTypeRepository.class),
                mock(FdActorResolver.class), mock(FdTimePolicy.class));
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @Test
    void postAccrualRunReturnsTypedAccrual() throws Exception {
        when(accrualService.runAccrual(eq(11L), eq("alice"), any())).thenReturn(activeResponse());

        mockMvc.perform(post("/fd/v1/accounts/11/accrual/run").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"business_date\":\"2026-01-01\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account_id").value(11))
                .andExpect(jsonPath("$.accrued_amount").value(6500.00));
    }

    @Test
    void getAccrualReturnsHistoryList() throws Exception {
        when(accrualService.listAccruals(11L, "alice")).thenReturn(List.of(activeResponse()));

        mockMvc.perform(get("/fd/v1/accounts/11/accrual").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].accrued_amount").value(6500.00));
    }

    @Test
    void getCumulativeAccrualReturnsCumulativeTotal() throws Exception {
        when(accrualService.cumulative(11L, "alice")).thenReturn(activeResponse());

        mockMvc.perform(get("/fd/v1/accounts/11/accrual/cumulative").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cumulative_accrued").value(6500.00));
    }

    @Test
    void postMatureReturnsMaturedResponse() throws Exception {
        AccrualResponse matured = new AccrualResponse(11L, "FD000011", LocalDate.of(2026, 1, 1),
                new BigDecimal("6500.00"), new BigDecimal("6500.00"), "USD",
                new BigDecimal("106500.00"), "academic-v1", "MATURED");
        when(maturityService.processMaturity(11L, "alice")).thenReturn(matured);

        mockMvc.perform(post("/fd/v1/accounts/11/mature").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("MATURED"));
    }

    @Test
    void invalidTransitionMapsToStructuredErrorBody() throws Exception {
        when(accrualService.runAccrual(eq(11L), eq("alice"), any()))
                .thenThrow(new InvalidFdTransitionException(FdStatus.PENDING, FdLifecycleCommand.MATURE));

        mockMvc.perform(post("/fd/v1/accounts/11/accrual/run").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"business_date\":\"2026-01-01\"}"))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void endpointRolesAndUnrelatedPlaceholdersPreserved() throws Exception {
        var runAccrual = FdV1ContractController.class.getDeclaredMethod("runAccrual", Long.class,
                java.util.Map.class, Authentication.class);
        assertEquals("/accounts/{accountId}/accrual/run", runAccrual.getAnnotation(PostMapping.class).value()[0]);
        assertEquals("hasRole('CUSTOMER')", runAccrual.getAnnotation(PreAuthorize.class).value());

        var mature = FdV1ContractController.class.getDeclaredMethod("matureAccount", Long.class,
                Authentication.class);
        assertEquals("hasRole('CUSTOMER')", mature.getAnnotation(PreAuthorize.class).value());

        var list = FdV1ContractController.class.getDeclaredMethod("listAccrual", Long.class,
                Authentication.class);
        assertEquals("/accounts/{accountId}/accrual", list.getAnnotation(GetMapping.class).value()[0]);
        assertEquals("isAuthenticated()", list.getAnnotation(PreAuthorize.class).value());

        var cumulative = FdV1ContractController.class.getDeclaredMethod("getCumulativeAccrual", Long.class,
                Authentication.class);
        assertEquals("isAuthenticated()", cumulative.getAnnotation(PreAuthorize.class).value());

        var untouched = new FdV1ContractController().runPayout(1L, null);
        assertEquals(501, untouched.getStatusCode().value());
        assertEquals("runPayout", untouched.getHeaders().getFirst("X-Operation-Id"));
    }
}
