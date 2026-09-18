package com.bank.fd;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class FdPostgresSchemaTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @BeforeAll
    static void migrateSchema() {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @Test
    void appliesAllMigrationsAndExposesTheLifecycleBaseline() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            assertEquals(Set.of("1", "2", "3", "4", "5", "6"), appliedVersions(connection));
            assertTrue(scalar(connection,
                    "SELECT column_default FROM information_schema.columns "
                            + "WHERE table_schema = 'public' AND table_name = 'fd_accounts' "
                            + "AND column_name = 'fa_status'").contains("PENDING"));
            assertEquals("bigint", scalar(connection,
                    "SELECT data_type FROM information_schema.columns "
                            + "WHERE table_schema = 'public' AND table_name = 'fd_accounts' "
                            + "AND column_name = 'fa_row_version'"));
            assertEquals("NO", scalar(connection,
                    "SELECT is_nullable FROM information_schema.columns "
                            + "WHERE table_schema = 'public' AND table_name = 'fd_accounts' "
                            + "AND column_name = 'fa_row_version'"));

            assertTrue(constraintExists(connection, "fd_accounts", "ck_fd_accounts_status"));
            assertTrue(constraintExists(connection, "fd_accounts", "ck_fd_accounts_principal_positive"));
            assertTrue(constraintExists(connection, "fd_accounts", "ck_fd_accounts_tenure_positive"));
            assertTrue(tableExists(connection, "fd_operation_idempotency"));
            assertTrue(constraintExists(connection, "fd_operation_idempotency",
                    "uq_fd_operation_idempotency_scope_key"));

            for (String index : Set.of(
                    "uq_fd_accounts_fa_uuid",
                    "uq_fd_transactions_ft_uuid",
                    "uq_fd_account_events_fae_uuid",
                    "uq_fd_interest_payout_fip_uuid",
                    "uq_fd_premature_closure_fpc_uuid")) {
                assertTrue(indexExists(connection, index), "Missing index " + index);
            }
        }
    }

    @Test
    void suppliesPendingAndRejectsInvalidLifecycleAndDuplicateOperationRows() throws SQLException {
        try (Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            connection.setAutoCommit(true);
            insertReferenceRows(connection);

            try (PreparedStatement account = connection.prepareStatement(
                    "INSERT INTO public.fd_accounts "
                            + "(fa_account_number, fa_primary_holder_id, fa_product_id, "
                            + "fa_principal_amount, fa_currency_id, fa_tenure_months) "
                            + "VALUES (?, 1, 1, 100.0000, 1, 12)")) {
                account.setString(1, "fixture-fd-001");
                assertEquals(1, account.executeUpdate());
            }

            assertEquals("PENDING", scalar(connection,
                    "SELECT fa_status FROM public.fd_accounts WHERE fa_account_number = 'fixture-fd-001'"));

            assertRejected(connection,
                    "INSERT INTO public.fd_accounts "
                            + "(fa_account_number, fa_primary_holder_id, fa_product_id, fa_principal_amount, "
                            + "fa_currency_id, fa_tenure_months, fa_status) "
                            + "VALUES ('fixture-fd-invalid-status', 1, 1, 100.0000, 1, 12, 'INVALID')");
            assertRejected(connection,
                    "INSERT INTO public.fd_accounts "
                            + "(fa_account_number, fa_primary_holder_id, fa_product_id, fa_principal_amount, "
                            + "fa_currency_id, fa_tenure_months) "
                            + "VALUES ('fixture-fd-invalid-principal', 1, 1, 0, 1, 12)");
            assertRejected(connection,
                    "INSERT INTO public.fd_accounts "
                            + "(fa_account_number, fa_primary_holder_id, fa_product_id, fa_principal_amount, "
                            + "fa_currency_id, fa_tenure_months) "
                            + "VALUES ('fixture-fd-invalid-tenure', 1, 1, 100.0000, 1, 0)");

            insertOperation(connection, "BOOK", "actor-1", "operation-1");
            assertRejected(connection,
                    "INSERT INTO public.fd_operation_idempotency "
                            + "(foi_operation_scope, foi_actor_key, foi_idempotency_key, foi_request_hash) "
                            + "VALUES ('BOOK', 'actor-1', 'operation-1', 'hash-duplicate')");
        }
    }

    private static void insertReferenceRows(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("INSERT INTO public.fd_product_ref "
                    + "(fpr_product_code, fpr_product_name) VALUES ('fixture-product', 'Fixture Product')");
            statement.executeUpdate("INSERT INTO public.fd_currency_ref "
                    + "(fcr_iso_code, fcr_currency_name) VALUES ('TST', 'Test Currency')");
            statement.executeUpdate("INSERT INTO public.fd_user_ref "
                    + "(fur_user_uuid, fur_customer_number, fur_full_name) "
                    + "VALUES ('fixture-user-uuid', 'fixture-customer', 'Fixture Customer')");
        }
    }

    private static void insertOperation(Connection connection, String scope, String actor, String key)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO public.fd_operation_idempotency "
                        + "(foi_operation_scope, foi_actor_key, foi_idempotency_key, foi_request_hash) "
                        + "VALUES (?, ?, ?, 'hash-1')")) {
            statement.setString(1, scope);
            statement.setString(2, actor);
            statement.setString(3, key);
            assertEquals(1, statement.executeUpdate());
        }
    }

    private static void assertRejected(Connection connection, String sql) {
        assertThrows(SQLException.class, () -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate(sql);
            }
        }, "Expected PostgreSQL to reject: " + sql);
    }

    private static Set<String> appliedVersions(Connection connection) throws SQLException {
        Set<String> versions = new HashSet<>();
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT version FROM public.flyway_schema_history WHERE success ORDER BY installed_rank")) {
            while (result.next()) {
                versions.add(result.getString(1));
            }
        }
        return versions;
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        return exists(connection,
                "SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = ?",
                tableName);
    }

    private static boolean indexExists(Connection connection, String indexName) throws SQLException {
        return exists(connection,
                "SELECT 1 FROM pg_indexes WHERE schemaname = 'public' AND indexname = ?",
                indexName);
    }

    private static boolean constraintExists(Connection connection, String tableName, String constraintName)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT 1 FROM pg_constraint c "
                        + "JOIN pg_class t ON t.oid = c.conrelid "
                        + "JOIN pg_namespace n ON n.oid = t.relnamespace "
                        + "WHERE n.nspname = 'public' AND t.relname = ? AND c.conname = ?")) {
            statement.setString(1, tableName);
            statement.setString(2, constraintName);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static boolean exists(Connection connection, String sql, String value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static String scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery(sql)) {
            assertTrue(result.next(), "Expected a scalar result for: " + sql);
            return result.getString(1);
        }
    }
}
