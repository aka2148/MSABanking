package com.bank.migrations;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.DockerClientFactory;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MySqlServiceSchemaMigrationTest {

    @Test
    void migratesEachServiceSchemaOnCleanMySql84Databases() throws Exception {
        Assumptions.assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is required for the MySQL schema migration test");

        List<SchemaCase> schemas = List.of(
                new SchemaCase("moneypal_customer", "customer", List.of(
                        "users", "user_credentials", "customers", "outbox_event")),
                new SchemaCase("moneypal_product", "product", List.of(
                        "product", "currency", "product_rule", "outbox_event")),
                new SchemaCase("moneypal_fd", "fd", List.of(
                        "fd_accounts", "savings_accounts", "fd_account_events", "fd_operation_idempotency"))
        );

        for (SchemaCase schema : schemas) {
            String username = "test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
            String password = UUID.randomUUID().toString();
            try (MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4")
                    .withDatabaseName(schema.database())
                    .withUsername(username)
                    .withPassword(password)) {
                mysql.start();

                Flyway flyway = Flyway.configure()
                        .dataSource(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())
                        .locations("classpath:db/service-migration/" + schema.location())
                        .load();

                flyway.migrate();
                assertNotNull(flyway.info().current(), schema.database() + " current migration");
                assertEquals("1", flyway.info().current().getVersion().getVersion(), schema.database() + " version");

                try (Connection connection = DriverManager.getConnection(
                        mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword())) {
                    for (String table : schema.requiredTables()) {
                        assertTrue(tableExists(connection, schema.database(), table),
                                schema.database() + " should contain " + table);
                    }
                }
            }
        }
    }

    private static boolean tableExists(Connection connection, String database, String table) throws Exception {
        try (var statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = ?")) {
            statement.setString(1, database);
            statement.setString(2, table);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1) == 1;
            }
        }
    }

    private record SchemaCase(String database, String location, List<String> requiredTables) { }
}
