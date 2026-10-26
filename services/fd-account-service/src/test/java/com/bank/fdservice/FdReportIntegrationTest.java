package com.bank.fdservice;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class FdReportIntegrationTest {
    private static final String JWT_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWYwMTIzNDU2Nzg5YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZg==";
    private static final long CUSTOMER_ID = 961;
    private static final long OTHER_CUSTOMER_ID = 962;
    private static final long ADMIN_ID = 963;
    private static final String CUSTOMER_NAME = "report-customer-one";
    private static final String OTHER_CUSTOMER_NAME = "report-customer-two";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("moneypal_fd")
            .withUsername("report_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16))
            .withPassword(UUID.randomUUID().toString());

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", MYSQL::getJdbcUrl);
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
        properties.add("app.jwt.secret", () -> JWT_SECRET);
        properties.add("app.kafka.enabled", () -> false);
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockBean IdentitySessionClient identitySessionClient;

    @BeforeEach
    void stubIdentity() {
        IdentitySessionTestSupport.stub(identitySessionClient, JWT_SECRET);
    }

    @Test
    void reportsUseStatementAndNoticeRowsKeepCurrenciesSeparateAndEnforceOwnership() throws Exception {
        seedReportRows();
        String customerToken = token(CUSTOMER_NAME, CUSTOMER_ID, "ROLE_CUSTOMER");
        String adminToken = token("report-admin", ADMIN_ID, "ROLE_ADMIN");

        mvc.perform(get("/api/reports/portfolio").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerUsername").value(CUSTOMER_NAME))
                .andExpect(jsonPath("$.accounts.length()").value(2));

        mvc.perform(get("/api/reports/portfolio/{username}", OTHER_CUSTOMER_NAME).header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/reports/interest-history").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].accountNumber").value("REPORT-USD"));

        mvc.perform(get("/api/reports/statements").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].interestAccruedInPeriod").value(1.25));

        mvc.perform(get("/api/reports/notices").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].deliveryStatus").value("RECORDED"));

        mvc.perform(get("/api/reports/fd-summary").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/reports/fd-summary").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].currencyCode").value("USD"))
                .andExpect(jsonPath("$[0].totalAccounts").value(2))
                .andExpect(jsonPath("$[1].currencyCode").value("JPY"))
                .andExpect(jsonPath("$[1].decimalPlaces").value(0));

        mvc.perform(get("/api/reports/maturity-summary").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].currencyCode").value("USD"));

        mvc.perform(get("/api/reports/fd-summary.csv").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("fd-summary.csv")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("JPY")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("USD")));

        mvc.perform(get("/api/reports/interest-history.csv").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("REPORT-USD")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("REPORT-OTHER"))));
    }

    private void seedReportRows() {
        jdbc.update("INSERT INTO fd_user_ref(fur_user_id,fur_user_uuid,fur_full_name,fur_username,fur_user_status,fur_kyc_status,fur_risk_category) VALUES(?,?,?,?, 'ACTIVE','VERIFIED','LOW'),(?,?,?,?, 'ACTIVE','VERIFIED','LOW')",
                CUSTOMER_ID, UUID.randomUUID().toString(), "Report Customer One", CUSTOMER_NAME,
                OTHER_CUSTOMER_ID, UUID.randomUUID().toString(), "Report Customer Two", OTHER_CUSTOMER_NAME);
        jdbc.update("INSERT INTO fd_currency_ref(fcr_currency_id,fcr_iso_code,fcr_currency_name,fcr_decimal_places) VALUES(961,'USD','US Dollar',2),(962,'JPY','Japanese Yen',0)");
        jdbc.update("INSERT INTO fd_product_ref(fpr_product_id,fpr_product_code,fpr_product_name,fpr_status) VALUES(961,'REPORT-FD-A','Report Product A','ACTIVE'),(962,'REPORT-FD-B','Report Product B','ACTIVE')");

        long ownUsd = account("REPORT-USD", CUSTOMER_ID, 961, 961, "1000.00", "ACTIVE", LocalDate.now().plusDays(5), "1100.00");
        long ownJpy = account("REPORT-JPY", CUSTOMER_ID, 962, 962, "5000", "MATURED", LocalDate.now().minusDays(2), "5200");
        long otherUsd = account("REPORT-OTHER", OTHER_CUSTOMER_ID, 961, 961, "300.00", "ACTIVE", LocalDate.now().plusDays(5), "330.00");
        accrual(ownUsd, 961, new BigDecimal("1.25"), new BigDecimal("1.25"));
        accrual(ownJpy, 962, new BigDecimal("2"), new BigDecimal("2"));
        accrual(otherUsd, 961, new BigDecimal("9.99"), new BigDecimal("9.99"));
        jdbc.update("INSERT INTO fd_statements(fs_account_id,fs_period_start,fs_period_end,fs_created_by) VALUES(?,?,?,'fd-batch')",
                ownUsd, Timestamp.valueOf(LocalDate.now().minusDays(2).atStartOfDay()), Timestamp.valueOf(LocalDate.now().plusDays(1).atStartOfDay()));
        jdbc.update("INSERT INTO fd_notices(fn_account_id,fn_notice_type,fn_notice_date,fn_channel,fn_delivery_status,fn_created_by) VALUES(?,'FD_MATURED',UTC_TIMESTAMP(6),'IN_APP','RECORDED','fd-batch')", ownJpy);
    }

    private long account(String number, long holder, long product, long currency, String principal, String status, LocalDate maturity, String maturityAmount) {
        jdbc.update("INSERT INTO fd_accounts(fa_account_number,fa_primary_holder_id,fa_product_id,fa_principal_amount,fa_currency_id,fa_tenure_months,fa_interest_rate,fa_compounding_frequency,fa_payout_frequency,fa_open_date,fa_maturity_date,fa_maturity_amount,fa_status) VALUES(?,?,?,?,?,12,0.05,'QUARTERLY','AT_MATURITY',?,?,?,?)",
                number, holder, product, new BigDecimal(principal), currency, Timestamp.valueOf(LocalDate.now().minusMonths(1).atStartOfDay()),
                Timestamp.valueOf(maturity.atStartOfDay()), new BigDecimal(maturityAmount), status);
        return jdbc.queryForObject("SELECT fa_account_id FROM fd_accounts WHERE fa_account_number=?", Long.class, number);
    }

    private void accrual(long accountId, long currencyId, BigDecimal amount, BigDecimal cumulative) {
        jdbc.update("INSERT INTO fd_interest_accrual(fia_account_id,fia_accrual_date,fia_accrued_amount,fia_cumulative_accrued,fia_currency_id,fia_capitalized_amount) VALUES(?,UTC_TIMESTAMP(6),?,?,?,0)",
                accountId, amount, cumulative, currencyId);
    }

    private String token(String username, long userId, String role) {
        return Jwts.builder().setSubject(username).claim("userId", userId).claim("roles", role).claim("tokenVersion", 0)
                .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(JWT_SECRET)), SignatureAlgorithm.HS512).compact();
    }
}
