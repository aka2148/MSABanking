package com.bank.fd;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import com.bank.fd.application.NormalizedProductTermsAdapter;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
            Set<String> expectedVersions = IntStream.rangeClosed(1, 29)
                    .mapToObj(String::valueOf)
                    .collect(Collectors.toSet());
            assertEquals(expectedVersions, appliedVersions(connection));
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
            assertTrue(scalar(connection,
                    "SELECT column_default FROM information_schema.columns "
                            + "WHERE table_schema = 'public' AND table_name = 'fd_accounts' "
                            + "AND column_name = 'fa_policy_version'").contains("academic-v1"));
            assertTrue(indexExists(connection, "uq_fd_user_ref_user_uuid"));
            assertEquals("DEPOSIT", scalar(connection,
                    "SELECT tt_txn_type_code FROM public.transaction_types "
                            + "WHERE tt_txn_type_code = 'DEPOSIT'"));
            assertTrue(tableExists(connection, "product_configuration_revision"));
            assertEquals("jsonb", scalar(connection,
                    "SELECT data_type FROM information_schema.columns "
                            + "WHERE table_schema = 'public' AND table_name = 'product_configuration_revision' "
                            + "AND column_name = 'pcr_snapshot_json'"));
            assertEquals("numeric", scalar(connection,
                    "SELECT data_type FROM information_schema.columns "
                            + "WHERE table_schema = 'public' AND table_name = 'product_currency_config' "
                            + "AND column_name = 'pcc_minimum_amount'"));
            assertEquals("numeric", scalar(connection,
                    "SELECT data_type FROM information_schema.columns "
                            + "WHERE table_schema = 'public' AND table_name = 'product_currency_config' "
                            + "AND column_name = 'pcc_maximum_amount'"));
            assertEquals("KWD", scalar(connection,
                    "SELECT cur_iso_code FROM public.currency WHERE cur_iso_code = 'KWD'"));
            assertEquals("3", scalar(connection,
                    "SELECT cur_decimal_places FROM public.currency WHERE cur_iso_code = 'KWD'"));
            assertTrue(constraintExists(connection, "product_currency_config",
                    "ck_product_currency_config_amount_limits"));

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
    void normalizedProductTermsQueryExecutesAgainstTheMigratedPostgresSchema() throws SQLException {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        String suffix = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        String productCode = "PG-FD-" + suffix;

        jdbc.update("INSERT INTO public.fd_product_ref (fpr_product_code, fpr_product_name, fpr_status) "
                + "VALUES (?, ?, 'ACTIVE')", productCode, "PostgreSQL Regression FD");
        jdbc.update("INSERT INTO public.product (pro_product_code, pro_product_name, pro_category_id, "
                        + "pro_base_currency_code, pro_status) "
                        + "VALUES (?, ?, (SELECT pc_category_id FROM public.product_category "
                        + "WHERE pc_category_code = 'FIXED_DEPOSIT'), 'USD', 'ACTIVE')",
                productCode, "PostgreSQL Regression FD");
        Long productId = jdbc.queryForObject("SELECT pro_product_id FROM public.product WHERE pro_product_code = ?",
                Long.class, productCode);
        Long currencyId = jdbc.queryForObject("SELECT cur_currency_id FROM public.currency WHERE cur_iso_code = 'USD'",
                Long.class);
        Long ruleTypeId = jdbc.queryForObject("SELECT rut_rule_type_id FROM public.rule_type "
                + "WHERE rut_rule_type_code = 'FD_TENURE_RATE'", Long.class);
        jdbc.update("INSERT INTO public.fd_currency_ref (fcr_iso_code, fcr_currency_name, fcr_decimal_places) "
                + "VALUES ('USD', 'US Dollar', 2) ON CONFLICT DO NOTHING");
        jdbc.update("INSERT INTO public.product_currency_config (pcc_product_id, pcc_currency_id, "
                        + "pcc_minimum_amount, pcc_maximum_amount) VALUES (?, ?, 100, 100000)",
                productId, currencyId);
        Long ruleId = jdbc.queryForObject("INSERT INTO public.product_rule (pr_product_id, pr_rule_type_id, "
                        + "pr_rule_name, pr_is_active) VALUES (?, ?, 'TENURE_RATE', TRUE) "
                        + "RETURNING pr_rule_id", Long.class, productId, ruleTypeId);
        jdbc.update("INSERT INTO public.rule_condition (rc_rule_id, rc_condition_attribute, "
                        + "rc_value_from, rc_value_to) VALUES (?, 'AMOUNT_RANGE', '100', '100000'), "
                        + "(?, 'TENURE_MONTHS', '3', '12')", ruleId, ruleId);
        Long outcomeId = jdbc.queryForObject("INSERT INTO public.rule_outcome (ro_rule_id, ro_outcome_type, "
                        + "ro_outcome_value, ro_compounding_basis, ro_payout_frequency, ro_currency_id) "
                        + "VALUES (?, 'INTEREST_RATE', '0.05', 'QUARTERLY', 'AT_MATURITY', ?) "
                        + "RETURNING ro_outcome_id", Long.class, ruleId, currencyId);
        jdbc.update("INSERT INTO public.fd_rule_outcome_ref (fror_outcome_type, fror_outcome_value, "
                        + "fror_source_outcome_id) VALUES ('INTEREST_RATE', '0.05', ?)", outcomeId);

        var terms = new NormalizedProductTermsAdapter(jdbc).listEligibleTerms(java.time.LocalDate.now()).stream()
                .filter(term -> productCode.equals(term.productCode())).toList();

        assertEquals(1, terms.size());
        assertEquals("USD", terms.get(0).currencyCode());
        assertEquals(3, terms.get(0).minimumTenureMonths());
        assertEquals(12, terms.get(0).maximumTenureMonths());
        assertEquals(new java.math.BigDecimal("0.05"), terms.get(0).annualRate());
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
