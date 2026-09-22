package com.bank.fd.api;

import com.bank.common.exception.GlobalExceptionHandler;
import com.bank.fd.api.controller.FdV1ContractController;
import com.bank.fd.api.dto.ClosureResponse;
import com.bank.fd.api.dto.HistoryEntry;
import com.bank.fd.api.dto.PrematureClosureResponse;
import com.bank.fd.application.AccrualService;
import com.bank.fd.application.AccountBookingService;
import com.bank.fd.application.ClosureService;
import com.bank.fd.application.FdAccessPolicy;
import com.bank.fd.application.FdActorResolver;
import com.bank.fd.application.FundingService;
import com.bank.fd.application.HistoryService;
import com.bank.fd.application.MaturityService;
import com.bank.fd.application.PrematureClosureService;
import com.bank.fd.application.ProductOfferQueryService;
import com.bank.fd.domain.FdLifecycleCommand;
import com.bank.fd.domain.FdTimePolicy;
import com.bank.fd.entity.FdAccount;
import com.bank.fd.entity.FdCurrencyReference;
import com.bank.fd.entity.FdPrematureClosure;
import com.bank.fd.entity.FdProductReference;
import com.bank.fd.entity.FdStatus;
import com.bank.fd.entity.FdUserReference;
import com.bank.fd.exception.InvalidFdTransitionException;
import com.bank.fd.exception.PrematureClosureNotSupportedException;
import com.bank.fd.repository.FdAccountRepository;
import com.bank.fd.repository.FdCurrencyReferenceRepository;
import com.bank.fd.repository.FdPrematureClosureRepository;
import com.bank.fd.repository.FdProductReferenceRepository;
import com.bank.fd.repository.TransactionTypeRepository;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class FdV1ClosureHistoryContractTest {
    private final ClosureService closureService = mock(ClosureService.class);
    private final PrematureClosureService prematureClosureService = mock(PrematureClosureService.class);
    private final HistoryService historyService = mock(HistoryService.class);
    private final AccrualService accrualService = mock(AccrualService.class);
    private final FdAccountRepository accountRepository = mock(FdAccountRepository.class);
    private final FdProductReferenceRepository productRepository = mock(FdProductReferenceRepository.class);
    private final FdCurrencyReferenceRepository currencyRepository = mock(FdCurrencyReferenceRepository.class);
    private final FdPrematureClosureRepository prematureClosureRepository =
            mock(FdPrematureClosureRepository.class);
    private final TransactionTypeRepository transactionTypeRepository = mock(TransactionTypeRepository.class);
    private final FdActorResolver actorResolver = mock(FdActorResolver.class);
    private final FdTimePolicy timePolicy = mock(FdTimePolicy.class);
    private final Authentication authentication = mock(Authentication.class);
    private MockMvc mockMvc;

    private static FdAccount account(FdStatus status) {
        FdAccount account = new FdAccount();
        account.setId(11L);
        account.setAccountNumber("FD000011");
        account.setPrimaryHolderId(42L);
        account.setProductId(5L);
        account.setCurrencyId(1L);
        account.setPrincipalAmount(new BigDecimal("100000.0000"));
        account.setTenureMonths(12);
        account.setInterestRate(new BigDecimal("0.0650"));
        account.setCompoundingFrequency("AT_MATURITY");
        account.setPayoutFrequency("NONE");
        account.setBookingDate(LocalDate.of(2025, 1, 1));
        account.setMaturityDate(LocalDate.of(2026, 1, 1));
        account.setMaturityAmount(new BigDecimal("106500.0000"));
        account.setPolicyVersion("academic-v1");
        account.setStatus(status);
        return account;
    }

    private static FdPrematureClosure pendingClosure() {
        FdPrematureClosure closure = new FdPrematureClosure();
        closure.setId(7L);
        closure.setAccountId(11L);
        closure.setClosureDate(OffsetDateTime.of(2025, 7, 2, 0, 0, 0, 0, ZoneOffset.UTC));
        closure.setPenaltyPctApplied(new BigDecimal("1.00"));
        closure.setPenaltyAmount(new BigDecimal("498.63"));
        closure.setNetPayableAmount(new BigDecimal("102742.47"));
        closure.setCurrencyId(1L);
        closure.setStatus("PENDING");
        return closure;
    }

    private void stubOwnership(FdAccount account) {
        FdUserReference holder = new FdUserReference();
        holder.setId(42L);
        when(actorResolver.resolve("alice")).thenReturn(holder);
        FdProductReference product = mock(FdProductReference.class);
        when(product.getProductCode()).thenReturn("FD-12M");
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));
        FdCurrencyReference currency = mock(FdCurrencyReference.class);
        when(currency.getIsoCode()).thenReturn("USD");
        when(currencyRepository.findById(1L)).thenReturn(Optional.of(currency));
    }

    @BeforeEach
    void setUp() {
        when(authentication.getName()).thenReturn("alice");
        when(timePolicy.businessDate()).thenReturn(LocalDate.of(2026, 1, 1));
        FdV1ContractController controller = new FdV1ContractController(
                mock(ProductOfferQueryService.class), mock(AccountBookingService.class),
                mock(FundingService.class), accrualService, mock(MaturityService.class),
                closureService, prematureClosureService, historyService, accountRepository,
                productRepository, currencyRepository, prematureClosureRepository,
                transactionTypeRepository, actorResolver, mock(FdAccessPolicy.class), timePolicy);
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(mapper))
                .build();
    }

    @Test
    void closeReturnsClosedSettlement() throws Exception {
        FdAccount closed = account(FdStatus.CLOSED);
        when(closureService.close(eq(11L), eq("alice"), isNull())).thenReturn(
                ClosureResponse.closed(closed, new BigDecimal("106500.0000"), "USD",
                        LocalDate.of(2026, 1, 1)));

        mockMvc.perform(post("/fd/v1/accounts/11/close").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account_id").value(11))
                .andExpect(jsonPath("$.status").value("CLOSED"))
                .andExpect(jsonPath("$.settlement_amount").value(106500.00));
    }

    @Test
    void closeInvalidTransitionMaps409() throws Exception {
        when(closureService.close(eq(11L), eq("alice"), isNull()))
                .thenThrow(new InvalidFdTransitionException(FdStatus.ACTIVE, FdLifecycleCommand.CLOSE));

        mockMvc.perform(post("/fd/v1/accounts/11/close").principal(authentication))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void prematureRequestReturns201Pending() throws Exception {
        when(prematureClosureService.requestPrematureClosure(eq(11L), eq("alice"), any(), isNull()))
                .thenReturn(PrematureClosureResponse.requested(
                        pendingClosure(), new BigDecimal("0.0550"), "USD"));

        mockMvc.perform(post("/fd/v1/accounts/11/premature-closure").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"need funds\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.closure_id").value(7))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.penalized_rate").value(0.055));
    }

    @Test
    void prematureUnsupportedRuleMaps422() throws Exception {
        when(prematureClosureService.requestPrematureClosure(eq(11L), eq("alice"), any(), isNull()))
                .thenThrow(new PrematureClosureNotSupportedException(
                        "Premature closure is not supported for FD account 11: "
                                + "no usable CLOSURE_PENALTY rule is stored for this product"));

        mockMvc.perform(post("/fd/v1/accounts/11/premature-closure").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"need funds\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("CLOSURE_PENALTY")));
    }

    @Test
    void approvePrematureClosureReturnsApproved() throws Exception {
        FdPrematureClosure approved = pendingClosure();
        approved.setStatus("APPROVED");
        approved.setApprovedBy(42L);
        when(prematureClosureService.approvePrematureClosure(eq(11L), eq(7L), eq("alice"), isNull()))
                .thenReturn(PrematureClosureResponse.approved(
                        approved, new BigDecimal("0.0550"), "USD"));

        mockMvc.perform(post("/fd/v1/accounts/11/premature-closure/7/approve")
                        .principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.net_payable_amount").value(102742.47));
    }

    @Test
    void getPrematureClosureReturnsRequestList() throws Exception {
        FdAccount active = account(FdStatus.ACTIVE);
        stubOwnership(active);
        when(accountRepository.findById(11L)).thenReturn(Optional.of(active));
        when(prematureClosureRepository.findByAccountIdOrderByClosureDateAscIdAsc(11L))
                .thenReturn(List.of(pendingClosure()));

        mockMvc.perform(get("/fd/v1/accounts/11/premature-closure").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].closure_id").value(7))
                .andExpect(jsonPath("$[0].status").value("PENDING"))
                .andExpect(jsonPath("$[0].penalized_rate").value(0.055));
    }

    @Test
    void getAccountReturnsStatusSnapshot() throws Exception {
        FdAccount active = account(FdStatus.ACTIVE);
        stubOwnership(active);
        when(accountRepository.findById(11L)).thenReturn(Optional.of(active));

        mockMvc.perform(get("/fd/v1/accounts/11").principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.account_id").value(11))
                .andExpect(jsonPath("$.product_code").value("FD-12M"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.funding_recorded").value(true));
    }

    @Test
    void listTransactionsReturnsHistoryTimeline() throws Exception {
        when(historyService.timeline(11L, "alice")).thenReturn(List.of(
                new HistoryEntry("FUNDING",
                        OffsetDateTime.of(2025, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
                        new BigDecimal("100000.00"), "USD", new BigDecimal("100000.00"),
                        "TRANSACTION", "Internal academic funding record"),
                new HistoryEntry("ACCRUAL",
                        OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC),
                        new BigDecimal("6500.00"), "USD", new BigDecimal("106500.00"),
                        "ACCRUAL", "Internal academic interest accrual record")));

        mockMvc.perform(get("/fd/v1/accounts/11/transactions")
                        .param("from_date", "2025-01-01")
                        .param("to_date", "2026-01-01")
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].entry_type").value("FUNDING"))
                .andExpect(jsonPath("$[1].source").value("ACCRUAL"));
    }

    @Test
    void malformedBusinessDateMaps400WithoutServiceCall() throws Exception {
        mockMvc.perform(post("/fd/v1/accounts/11/accrual/run").principal(authentication)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"business_date\":\"01-2026\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());

        verifyNoInteractions(accrualService);
        verifyNoInteractions(closureService);
        verifyNoInteractions(prematureClosureService);
        verifyNoInteractions(historyService);
    }

    @Test
    void endpointRolesAndUnrelatedPlaceholdersPreserved() throws Exception {
        var close = FdV1ContractController.class.getDeclaredMethod("closeAccount", Long.class,
                String.class, Authentication.class);
        assertEquals("/accounts/{accountId}/close", close.getAnnotation(PostMapping.class).value()[0]);
        assertEquals("hasAnyRole('CUSTOMER','BANK_OFFICER','ADMIN')", close.getAnnotation(PreAuthorize.class).value());

        var request = FdV1ContractController.class.getDeclaredMethod("requestPrematureClosure",
                Long.class, Map.class, String.class, Authentication.class);
        assertEquals("hasAnyRole('CUSTOMER','BANK_OFFICER','ADMIN')", request.getAnnotation(PreAuthorize.class).value());

        var approve = FdV1ContractController.class.getDeclaredMethod("approvePrematureClosure",
                Long.class, Long.class, Map.class, String.class, Authentication.class);
        assertEquals("hasAnyRole('CUSTOMER','BANK_OFFICER','ADMIN')", approve.getAnnotation(PreAuthorize.class).value());

        var get = FdV1ContractController.class.getDeclaredMethod("getAccount", Long.class,
                Authentication.class);
        assertEquals("/accounts/{accountId}", get.getAnnotation(GetMapping.class).value()[0]);
        assertEquals("isAuthenticated()", get.getAnnotation(PreAuthorize.class).value());

        var transactions = FdV1ContractController.class.getDeclaredMethod("listTransactions",
                Long.class, String.class, String.class, Long.class, Authentication.class);
        assertEquals("isAuthenticated()", transactions.getAnnotation(PreAuthorize.class).value());

        var premature = FdV1ContractController.class.getDeclaredMethod("getPrematureClosure",
                Long.class, Authentication.class);
        assertEquals("isAuthenticated()", premature.getAnnotation(PreAuthorize.class).value());

        var untouched = new FdV1ContractController().runPayout(1L, null);
        assertEquals(501, untouched.getStatusCode().value());
        assertEquals("runPayout", untouched.getHeaders().getFirst("X-Operation-Id"));
    }
}
