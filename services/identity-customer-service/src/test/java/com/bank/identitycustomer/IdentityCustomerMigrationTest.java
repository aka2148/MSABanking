package com.bank.identitycustomer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import com.sun.net.httpserver.HttpServer;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.Date;
import java.sql.Statement;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class IdentityCustomerMigrationTest {
    private static final String TEST_JWT_SECRET = "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWYwMTIzNDU2Nzg5YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZg==";
    private static final AtomicReference<String> TEST_EMAIL_OTP = new AtomicReference<>();
    private static final HttpServer MAIL_STUB = createMailStub();

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("moneypal_customer")
            .withUsername("test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20))
            .withPassword(UUID.randomUUID().toString());

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", MYSQL::getJdbcUrl);
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
        properties.add("app.jwt.secret", () -> TEST_JWT_SECRET);
        properties.add("app.security.mfa-encryption-key", () -> TEST_JWT_SECRET);
        properties.add("app.auth.password-reset-api-url", () -> "http://127.0.0.1:" + MAIL_STUB.getAddress().getPort() + "/send");
        properties.add("app.auth.password-reset-api-key", () -> "integration-test-key");
        properties.add("app.auth.password-reset-from", () -> "MoneyPal Tests <noreply@example.test>");
    }

    @AfterAll
    static void stopMailStub() {
        MAIL_STUB.stop(0);
    }

    private static HttpServer createMailStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/send", exchange -> {
                String payload = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                var matcher = Pattern.compile("verification code is (\\d{6})").matcher(payload);
                if (!matcher.find()) {
                    exchange.sendResponseHeaders(400, -1);
                } else {
                    TEST_EMAIL_OTP.set(matcher.group(1));
                    exchange.sendResponseHeaders(201, -1);
                }
                exchange.close();
            });
            server.start();
            return server;
        } catch (java.io.IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }

    @Autowired
    private JdbcTemplate jdbc;
    @Autowired private MockMvc mvc;

    @Test
    void startsAgainstItsOwnSchemaAndAppliesCustomerV1() {
        Integer appliedMigrations = jdbc.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '1' AND success = 1",
                Integer.class);

        assertEquals(1, appliedMigrations);
        assertEquals(13,jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1",Integer.class));
        assertTrue(tableExists("users"));
        assertTrue(tableExists("user_credentials"));
        assertTrue(tableExists("customers"));
        assertTrue(tableExists("outbox_event"));
        assertTrue(tableExists("customer_email_registry"));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM roles WHERE rol_role_code='ROLE_BANK_OFFICER'", Integer.class));
    }

    @Test
    void registrationReturnsCustomerTokenAndTokenProtectsMeEndpoint() throws Exception {
        String username = newUsername();
        String token = register(username);
        assertEquals("LOW",jdbc.queryForObject("SELECT c.cus_risk_category FROM customers c JOIN user_credentials u ON u.ucr_user_id=c.cus_user_id WHERE u.ucr_username=?",String.class,username));
        mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.fullName").value("Demo Customer"));
        mvc.perform(get("/api/v1/auth/session").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.kycStatus").value("PENDING"))
                .andExpect(jsonPath("$.roles[0]").value("ROLE_CUSTOMER"));
        assertEquals(1,jdbc.queryForObject("SELECT cem_is_verified FROM customer_emails e JOIN customers c ON c.cus_customer_id=e.cem_customer_id JOIN user_credentials u ON u.ucr_user_id=c.cus_user_id WHERE u.ucr_username=?",Integer.class,username));
        Long userId = jdbc.queryForObject("SELECT ucr_user_id FROM user_credentials WHERE ucr_username=?",Long.class,username);
        Integer version = jdbc.queryForObject("SELECT usr_event_version FROM users WHERE usr_user_id=?",Integer.class,userId);
        UserSnapshots.publishInitialUserSnapshot(jdbc,userId);
        assertEquals(version,jdbc.queryForObject("SELECT usr_event_version FROM users WHERE usr_user_id=?",Integer.class,userId));
        assertTrue(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE event_key=(SELECT usr_user_uuid FROM users WHERE usr_user_id=?) AND topic='user.snapshot'",Integer.class,userId) >= 2);
    }

    @Test
    void logoutRevokesCurrentSessionAndEmailRegistryIsUnique() throws Exception {
        String username = newUsername();
        String token = register(username);
        mvc.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/auth/session").header("Authorization", "Bearer " + token))
                .andExpect(status().is4xxClientError());

        String normalized = "claim-" + UUID.randomUUID() + "@example.test";
        jdbc.update("INSERT INTO customer_email_registry(cer_normalized_email) VALUES(?)", normalized);
        assertThrows(org.springframework.dao.DuplicateKeyException.class,
                () -> jdbc.update("INSERT INTO customer_email_registry(cer_normalized_email) VALUES(?)", normalized));
    }

    @Test
    void fiveFailedLoginsLockCredentialsForSubsequentAttempts() throws Exception {
        String username = newUsername(); register(username);
        for (int i=0;i<4;i++) mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"username\":\""+username+"\",\"password\":\"incorrect-password\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"username\":\""+username+"\",\"password\":\"incorrect-password\"}"))
                .andExpect(status().isLocked());
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"username\":\""+username+"\",\"password\":\"SkyRiver82!Mint\"}"))
                .andExpect(status().isLocked());
    }

    @Test
    void passwordChangeRequiresCurrentPasswordAndUpdatesLoginCredential() throws Exception {
        String username=newUsername(); String token=register(username);
        mvc.perform(post("/api/v1/auth/change-password").header("Authorization","Bearer "+token)
                .contentType("application/json").content("{\"currentPassword\":\"SkyRiver82!Mint\",\"newPassword\":\"OceanTrail49#Violet\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                .content("{\"username\":\""+username+"\",\"password\":\"OceanTrail49#Violet\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("ROLE_CUSTOMER"));
    }

    @Test
    void customerAdminApiRequiresOfficerOrAdminRole() throws Exception {
        String username=newUsername(); String customerToken=register(username);
        Long customerId=jdbc.queryForObject("SELECT cus_customer_id FROM customers c JOIN user_credentials u ON u.ucr_user_id=c.cus_user_id WHERE u.ucr_username=?",Long.class,username);
        mvc.perform(get("/api/v1/customers/"+customerId).header("Authorization","Bearer "+customerToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/customers/"+customerId).header("Authorization","Bearer "+bankOfficerToken()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value(username));
    }

    @Test
    void bankOfficerCanCreateSearchAndUpdateCustomerProfile() throws Exception {
        String username=newUsername();
        KeyHolder key=new GeneratedKeyHolder();
        jdbc.update(connection->{var statement=connection.prepareStatement("INSERT INTO users(usr_user_type,usr_status) VALUES('CUSTOMER','ACTIVE')",Statement.RETURN_GENERATED_KEYS);return statement;},key);
        long userId=key.getKey().longValue();
        jdbc.update("INSERT INTO user_credentials(ucr_user_id,ucr_username,ucr_password_hash) VALUES(?,?,?)",userId,username,"test-hash");
        String admin=bankOfficerToken();
        mvc.perform(post("/api/v1/customers").header("Authorization","Bearer "+admin)
                        .contentType("application/json").content("{\"userId\":"+userId+",\"fullName\":\"Admin Created Customer\",\"kycStatus\":\"VERIFIED\"}"))
                .andExpect(status().isBadRequest());
        String created=mvc.perform(post("/api/v1/customers").header("Authorization","Bearer "+admin)
                        .contentType("application/json").content("{\"userId\":"+userId+",\"fullName\":\"Admin Created Customer\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.riskCategory").value("LOW"))
                .andReturn().getResponse().getContentAsString();
        long customerId=com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(created).get("id").asLong();
        mvc.perform(get("/api/v1/customers/search").header("Authorization","Bearer "+admin).param("q","Admin Created Customer"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(customerId));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/customers/"+customerId)
                        .header("Authorization","Bearer "+admin).contentType("application/json")
                        .content("{\"fullName\":\"Updated Customer\",\"riskCategory\":\"LOW\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.fullName").value("Updated Customer"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/customers/"+customerId+"/status")
                        .header("Authorization","Bearer "+admin).contentType("application/json")
                        .content("{\"status\":\"VERIFIED\",\"riskCategory\":\"MEDIUM\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/customers/"+customerId)
                        .header("Authorization","Bearer "+admin).contentType("application/json")
                        .content("{\"kycStatus\":\"VERIFIED\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/customers/"+customerId+"/status")
                        .header("Authorization","Bearer "+admin).contentType("application/json").content("{\"riskCategory\":\"MEDIUM\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.kycStatus").value("PENDING"));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/customers/"+customerId+"/status")
                        .header("Authorization","Bearer "+admin).contentType("application/json").content("{\"riskCategory\":\"INVALID\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch("/api/v1/customers/"+customerId+"/status")
                        .header("Authorization","Bearer "+admin).contentType("application/json").content("{\"riskCategory\":\" \"}"))
                .andExpect(status().isBadRequest());
        var snapshot=com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(jdbc.queryForObject(
                "SELECT payload FROM outbox_event WHERE event_key=(SELECT usr_user_uuid FROM users WHERE usr_user_id=?) ORDER BY source_version DESC LIMIT 1",String.class,userId));
        assertEquals("PENDING",snapshot.path("user").path("kycStatus").asText());
        assertEquals("MEDIUM",snapshot.path("user").path("riskCategory").asText());
        assertEquals(3,snapshot.path("sourceVersion").asInt());
    }

    private String bankOfficerToken() throws Exception {
        String username=newUsername(); register(username);
        long userId=jdbc.queryForObject("SELECT ucr_user_id FROM user_credentials WHERE ucr_username=?",Long.class,username);
        long roleId=jdbc.queryForObject("SELECT rol_role_id FROM roles WHERE rol_role_code='ROLE_BANK_OFFICER'",Long.class);
        jdbc.update("INSERT INTO user_roles(ur_user_id,ur_role_id) VALUES(?,?)",userId,roleId);
        String response=mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content("{\"username\":\""+username+"\",\"password\":\"SkyRiver82!Mint\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles").isArray())
                .andReturn().getResponse().getContentAsString();
        return com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(response).get("token").asText();
    }

    private String newUsername() { return "demo_" + UUID.randomUUID().toString().substring(0, 8); }

    private String register(String username) throws Exception {
        String email = username + "@example.test";
        mvc.perform(post("/api/v1/auth/email/verification/send").contentType("application/json")
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().isOk());
        String code = TEST_EMAIL_OTP.getAndSet(null);
        assertTrue(code != null && code.matches("\\d{6}"));
        mvc.perform(post("/api/v1/auth/email/verification/verify").contentType("application/json")
                        .content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}"))
                .andExpect(status().isOk());
        String response=mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                        .content("{\"username\":\""+username+"\",\"password\":\"SkyRiver82!Mint\",\"email\":\""+email+"\",\"fullName\":\"Demo Customer\",\"address\":\"12 Sample Road\",\"city\":\"Sample City\",\"state\":\"Sample State\",\"postalCode\":\"560001\",\"role\":\"ADMIN\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.roles[0]").value("ROLE_CUSTOMER"))
                .andReturn().getResponse().getContentAsString();
        return com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(response).get("token").asText();
    }

    private boolean tableExists(String table) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE() AND table_name = ?",
                Integer.class,
                table);
        return count != null && count == 1;
    }
}
