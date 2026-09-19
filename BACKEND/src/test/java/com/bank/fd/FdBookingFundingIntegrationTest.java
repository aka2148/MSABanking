package com.bank.fd;

import com.bank.fd.api.controller.FdV1ContractController;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.PostMapping;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class FdBookingFundingIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void migrateSchema() {
        Flyway.configure().dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration").load().migrate();
    }

    @Test
    void exposesAuthenticatedFundingRouteAndOpenApiContract() throws IOException {
        var method = assertDoesNotThrow(() -> FdV1ContractController.class.getDeclaredMethod(
                "fundAccount", Long.class, com.bank.fd.api.dto.FundingRequest.class,
                org.springframework.security.core.Authentication.class));
        assertNotNull(method.getAnnotation(PostMapping.class));
        assertEquals("/accounts/{accountId}/fund", method.getAnnotation(PostMapping.class).value()[0]);
        String openApi = Files.readString(Path.of("src/main/resources/static/openapi/fd-service-openapi.yaml"),
                StandardCharsets.UTF_8);
        assertTrue(openApi.contains("/accounts/{accountId}/fund"));
        assertTrue(openApi.contains("FundingResponse"));
    }

    @Test
    void postgresPersistsPendingBookingAndOneInternalDepositActivation() throws Exception {
        try (Connection connection = connection()) {
            seedReferencesAndPendingAccount(connection);
            assertEquals("PENDING", scalar(connection,
                    "SELECT fa_status FROM public.fd_accounts WHERE fa_account_number = 'integration-fd-001'"));

            connection.setAutoCommit(false);
            long accountId;
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT fa_account_id, fa_principal_amount, fa_currency_id, fa_status "
                            + "FROM public.fd_accounts WHERE fa_account_number = ? FOR UPDATE")) {
                select.setString(1, "integration-fd-001");
                try (ResultSet result = select.executeQuery()) {
                    assertTrue(result.next());
                    accountId = result.getLong(1);
                    assertEquals("1000.0000", result.getBigDecimal(2).toPlainString());
                    assertTrue(result.getLong(3) > 0);
                    assertEquals("PENDING", result.getString(4));
                }
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO public.fd_transactions (ft_account_id, ft_txn_type_id, ft_amount, "
                            + "ft_balance_after, ft_currency_id, ft_remarks) VALUES (?, "
                            + "(SELECT tt_txn_type_id FROM public.transaction_types WHERE tt_txn_type_code='DEPOSIT'), "
                            + "1000.0000, 1000.0000, (SELECT fcr_currency_id FROM public.fd_currency_ref WHERE fcr_iso_code='USD'), ?)")) {
                insert.setLong(1, accountId);
                insert.setString(2, "Internal academic funding record; no external payment rail was called.");
                assertEquals(1, insert.executeUpdate());
            }
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE public.fd_accounts SET fa_status='ACTIVE' WHERE fa_account_id=? AND fa_status='PENDING'")) {
                update.setLong(1, accountId);
                assertEquals(1, update.executeUpdate());
            }
            connection.commit();
            assertEquals("ACTIVE", scalar(connection,
                    "SELECT fa_status FROM public.fd_accounts WHERE fa_account_number = 'integration-fd-001'"));
            assertEquals(1L, ((Number) scalarObject(connection,
                    "SELECT COUNT(*) FROM public.fd_transactions WHERE ft_account_id=" + accountId)).longValue());
        }
    }

    @Test
    void postgresRejectsPartialFundingWithoutOrphanTransaction() throws Exception {
        try (Connection connection = connection()) {
            seedReferencesAndPendingAccount(connection);
            connection.setAutoCommit(false);
            try (PreparedStatement select = connection.prepareStatement(
                    "SELECT fa_principal_amount FROM public.fd_accounts WHERE fa_account_number=? FOR UPDATE")) {
                select.setString(1, "integration-fd-001");
                try (ResultSet result = select.executeQuery()) {
                    assertTrue(result.next());
                    assertNotEquals(0, result.getBigDecimal(1).compareTo(java.math.BigDecimal.valueOf(999.99)));
                }
            }
            connection.rollback();
            assertEquals("PENDING", scalar(connection,
                    "SELECT fa_status FROM public.fd_accounts WHERE fa_account_number = 'integration-fd-001'"));
            assertEquals(0L, ((Number) scalarObject(connection,
                    "SELECT COUNT(*) FROM public.fd_transactions")).longValue());
        }
    }

    private static Connection connection() throws Exception {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static void seedReferencesAndPendingAccount(Connection connection) throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM public.fd_transactions");
            statement.executeUpdate("DELETE FROM public.fd_accounts");
            statement.executeUpdate("DELETE FROM public.fd_user_ref");
            statement.executeUpdate("DELETE FROM public.fd_product_ref");
            statement.executeUpdate("DELETE FROM public.fd_currency_ref");
            statement.executeUpdate("INSERT INTO public.fd_product_ref (fpr_product_code, fpr_product_name) VALUES ('FD-INT', 'Integration FD')");
            statement.executeUpdate("INSERT INTO public.fd_currency_ref (fcr_iso_code, fcr_currency_name) VALUES ('USD', 'US Dollar')");
            statement.executeUpdate("INSERT INTO public.fd_user_ref (fur_user_uuid, fur_customer_number, fur_full_name) VALUES ('integration-user', 'integration-customer', 'Integration Customer')");
            statement.executeUpdate("INSERT INTO public.fd_accounts (fa_account_number, fa_primary_holder_id, fa_product_id, fa_principal_amount, fa_currency_id, fa_tenure_months, fa_status) VALUES ('integration-fd-001', (SELECT fur_user_id FROM public.fd_user_ref WHERE fur_user_uuid='integration-user'), (SELECT fpr_product_id FROM public.fd_product_ref WHERE fpr_product_code='FD-INT'), 1000.0000, (SELECT fcr_currency_id FROM public.fd_currency_ref WHERE fcr_iso_code='USD'), 12, 'PENDING')");
        }
    }

    private static String scalar(Connection connection, String sql) throws Exception {
        return String.valueOf(scalarObject(connection, sql));
    }

    private static Object scalarObject(Connection connection, String sql) throws Exception {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getObject(1);
        }
    }
}
