package com.bank.identitycustomer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.mock.web.MockMultipartFile;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {"logging.level.root=WARN", "logging.level.org.springframework.web=WARN", "logging.level.org.springframework.jdbc=WARN"})
@AutoConfigureMockMvc
class KycIntegrationTest {
    private static final String SECRET = Base64.getEncoder().encodeToString(new byte[64]);
    @Container static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("moneypal_customer").withUsername("kyc_test").withPassword(UUID.randomUUID().toString());
    @DynamicPropertySource static void properties(DynamicPropertyRegistry p) {
        p.add("spring.datasource.url", MYSQL::getJdbcUrl); p.add("spring.datasource.username", MYSQL::getUsername);
        p.add("spring.datasource.password", MYSQL::getPassword); p.add("app.jwt.secret", () -> SECRET); p.add("app.security.mfa-encryption-key", () -> SECRET);
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired AuthController authController;
    private static final String URL = "/api/v1/customers/me/kyc";
    record Customer(String token, String username, long id) {}
    private Customer customer(boolean phone) throws Exception {
        String username = "kyc_" + UUID.randomUUID().toString().substring(0, 8);
        @SuppressWarnings("unchecked") Map<String,java.time.Instant> verified=(Map<String,java.time.Instant>)org.springframework.test.util.ReflectionTestUtils.getField(authController,"verifiedEmails");
        verified.put(username+"@example.test",java.time.Instant.now().plusSeconds(600));
        Map<String,Object> body = new HashMap<>(Map.of("username", username, "password", "SkyRiver82!Mint", "email", username + "@example.test", "fullName", "KYC Test", "legalEntityType", "INDIVIDUAL", "address", "12 Sample Road", "city", "Sample City", "state", "Sample State", "postalCode", "560001"));
        if (phone) body.put("phone", "9000000000");
        JsonNode response = json.readTree(mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(json.writeValueAsBytes(body))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
        long id = jdbc.queryForObject("SELECT cus_customer_id FROM customers c JOIN user_credentials u ON c.cus_user_id=u.ucr_user_id WHERE u.ucr_username=?", Long.class, username);
        return new Customer(response.get("token").asText(), username, id);
    }
    private JsonNode getProfile(Customer c) throws Exception { return json.readTree(mvc.perform(get(URL).header("Authorization", "Bearer " + c.token())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()); }
    private JsonNode save(Customer c, Map<String,?> values) throws Exception { return json.readTree(mvc.perform(put(URL + "/draft").header("Authorization", "Bearer " + c.token()).contentType("application/json").content(json.writeValueAsBytes(values))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()); }
    private JsonNode submit(Customer c) throws Exception { return json.readTree(mvc.perform(post(URL + "/submit").header("Authorization", "Bearer " + c.token())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString()); }
    private Map<String,Object> complete() {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("dateOfBirth", "1990-01-01"); result.put("gender", "FEMALE"); result.put("relationship", "FATHER");
        result.put("fatherOrSpouseName", "Test Parent"); result.put("occupation", "SALARIED"); result.put("countryOfOrigin", "IN");
        result.put("proofOfIdType", "PAN"); result.put("proofOfIdValue", "ABCDE1234F"); result.put("idIssueDate", "2010-01-01"); return result;
    }
    @Test void lazyProfileAccountOwnershipMaskAndPartialErrors() throws Exception {
        Customer a = customer(false), b = customer(true);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM customer_kyc_profile WHERE customer_id=?", Integer.class, a.id()));
        JsonNode p = getProfile(a);
        assertEquals(1, p.get("cycleNumber").asInt()); assertEquals(0, p.get("completionPercent").asInt());
        assertTrue(p.at("/accountDetails/mobile").isNull()); assertEquals(a.username(), p.at("/accountDetails/username").asText());
        assertEquals("9000000000", getProfile(b).at("/accountDetails/mobile").asText());
        p = save(a, Map.of("gender", "BAD", "occupation", "STUDENT", "name", "Override", "email", "bad", "phone", "bad", "username", b.username(), "customerId", b.id()));
        assertTrue(p.get("fieldErrors").has("gender")); assertEquals(11, p.get("completionPercent").asInt());
        assertEquals("KYC Test", p.at("/accountDetails/fullName").asText()); assertEquals(a.username(), p.at("/accountDetails/username").asText());
        assertEquals(0, getProfile(b).get("completionPercent").asInt());
        p = save(a, Map.of("occupation", "")); assertEquals(0, p.get("completionPercent").asInt());
        p = save(a, Map.of("proofOfIdType", "PAN", "proofOfIdValue", "ABCDE1234F"));
        assertEquals("****234F", p.get("proofNumberMasked").asText()); assertFalse(p.toString().contains("ABCDE1234F")); assertFalse(p.get("values").has("proofOfIdValue"));
        p = save(a, Map.of("proofOfIdType", "AADHAAR")); assertTrue(p.get("proofNumberMasked").isNull()); assertFalse(p.at("/completedFields/proofOfIdValue").asBoolean());
    }
    @Test void draftStaysPendingUntilExplicitSubmitAndRejectsIncompleteOrRepeatedSubmit() throws Exception {
        Customer incomplete = customer(false);
        mvc.perform(post(URL + "/submit").header("Authorization", "Bearer " + incomplete.token()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Complete all 9 KYC fields before submitting."));

        Customer c = customer(false);
        jdbc.update("UPDATE customers SET cus_risk_category='HIGH' WHERE cus_customer_id=?", c.id());
        int snapshotsBefore = snapshotCount(c);
        JsonNode draft = save(c, complete());
        assertEquals(100, draft.get("completionPercent").asInt());
        assertEquals("PENDING", draft.get("kycStatus").asText());
        assertEquals("OPEN", draft.get("cycleStatus").asText());
        assertEquals(0, eventCount(c, "AUTO_VERIFIED"));
        assertEquals(snapshotsBefore, snapshotCount(c));

        JsonNode verified = submit(c);
        assertEquals("VERIFIED", verified.get("kycStatus").asText());
        assertEquals("COMPLETED", verified.get("cycleStatus").asText());
        assertEquals("HIGH", jdbc.queryForObject("SELECT cus_risk_category FROM customers WHERE cus_customer_id=?", String.class, c.id()));
        assertEquals("SALARIED", jdbc.queryForObject("SELECT cud_occupation FROM customer_details WHERE cud_customer_id=?", String.class, c.id()));
        assertEquals("1990-01-01", jdbc.queryForObject("SELECT CAST(cud_date_of_birth AS CHAR) FROM customer_details WHERE cud_customer_id=?", String.class, c.id()));
        assertEquals("Test Parent", jdbc.queryForObject("SELECT cud_father_or_spouse_name FROM customer_details WHERE cud_customer_id=?", String.class, c.id()));
        assertEquals(1, eventCount(c, "AUTO_VERIFIED"));
        assertEquals(snapshotsBefore + 1, snapshotCount(c));
        assertSnapshot(c, "VERIFIED", 2);
        mvc.perform(post(URL + "/submit").header("Authorization", "Bearer " + c.token())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Request re-KYC to update details."));
    }
    private int eventCount(Customer c, String type) { return jdbc.queryForObject("SELECT COUNT(*) FROM customer_kyc_events WHERE customer_id=? AND event_type=?", Integer.class, c.id(), type); }
    private int snapshotCount(Customer c) { return jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event o JOIN users u ON o.event_key=u.usr_user_uuid JOIN customers c ON c.cus_user_id=u.usr_user_id WHERE c.cus_customer_id=?", Integer.class, c.id()); }
    private void assertSnapshot(Customer c, String status, int version) throws Exception {
        String payload = jdbc.queryForObject("SELECT o.payload FROM outbox_event o JOIN users u ON o.event_key=u.usr_user_uuid JOIN customers c ON c.cus_user_id=u.usr_user_id WHERE c.cus_customer_id=? ORDER BY o.source_version DESC LIMIT 1", String.class, c.id());
        JsonNode event = json.readTree(payload); assertEquals(status, event.at("/user/kycStatus").asText()); assertEquals(version, event.get("sourceVersion").asInt());
    }
    @Test void rekycConfirmsSavedMaskedNumberAndStaffPendingReopens() throws Exception {
        Customer c = customer(false);
        mvc.perform(post(URL + "/rekyc").header("Authorization", "Bearer " + c.token()).contentType("application/json").content("{\"remarks\":\"Update my details\"}")).andExpect(status().isConflict());
        save(c, complete()); submit(c);
        mvc.perform(post("/api/v1/auth/mpin/setup").header("Authorization", "Bearer " + c.token()).contentType("application/json").content("{\"password\":\"SkyRiver82!Mint\",\"mpin\":\"864209\"}")).andExpect(status().isNoContent());
        for (String remarks : List.of("", "short", "x".repeat(501), "<update details>"))
            mvc.perform(post(URL + "/rekyc").header("Authorization", "Bearer " + c.token()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsBytes(Map.of("remarks", remarks,"mpin","864209")))).andExpect(status().isBadRequest());
        mvc.perform(post(URL + "/rekyc").header("Authorization", "Bearer " + c.token()).contentType("application/json").content("{\"remarks\":\" Update my details \",\"mpin\":\"864209\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.completionPercent").value(0)).andExpect(jsonPath("$.cycleNumber").value(2)).andExpect(jsonPath("$.values.occupation").value("SALARIED"));
        assertSnapshot(c, "PENDING", 3); assertEquals(1, eventCount(c, "REKYC_REQUESTED"));
        Map<String,Object> unchanged = complete(); unchanged.remove("proofOfIdValue"); unchanged.put("confirmSavedProof", true);
        assertEquals("PENDING", save(c, unchanged).get("kycStatus").asText());
        assertEquals("VERIFIED", submit(c).get("kycStatus").asText()); assertSnapshot(c, "VERIFIED", 4);
        jdbc.update("UPDATE customers SET cus_kyc_status='PENDING' WHERE cus_customer_id=?", c.id());
        JsonNode p = getProfile(c); assertEquals(3, p.get("cycleNumber").asInt()); assertEquals(0, p.get("completionPercent").asInt());
        jdbc.update("UPDATE customers SET cus_kyc_status='REJECTED' WHERE cus_customer_id=?", c.id());
        mvc.perform(put(URL + "/draft").header("Authorization", "Bearer " + c.token()).contentType("application/json").content(json.writeValueAsBytes(complete()))).andExpect(status().isConflict());
        mvc.perform(post(URL + "/rekyc").header("Authorization", "Bearer " + c.token()).contentType("application/json").content("{\"remarks\":\"Update my details\",\"mpin\":\"864209\"}")).andExpect(status().isConflict());
    }
    @Test void concurrentFinalDraftSavesStayPendingUntilSubmit() throws Exception {
        Customer c = customer(false); Map<String,Object> draft = complete(); draft.remove("idIssueDate"); save(c, draft);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> call = () -> { start.await(); return mvc.perform(put(URL + "/draft").header("Authorization", "Bearer " + c.token()).contentType("application/json").content("{\"idIssueDate\":\"2010-01-01\"}")).andReturn().getResponse().getStatus(); };
        try {
            Future<Integer> first = pool.submit(call), second = pool.submit(call); start.countDown();
            Set<Integer> statuses = new HashSet<>(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)));
            assertEquals(Set.of(200), statuses);
            assertEquals("PENDING", getProfile(c).get("kycStatus").asText());
            assertEquals("OPEN", getProfile(c).get("cycleStatus").asText());
            assertEquals(0, eventCount(c, "AUTO_VERIFIED"));
            assertEquals("VERIFIED", submit(c).get("kycStatus").asText());
            assertEquals(1, eventCount(c, "AUTO_VERIFIED")); assertSnapshot(c, "VERIFIED", 2);
        } finally { pool.shutdownNow(); }
    }
    @Test void documentsValidateReplaceRemoveAndLockWithoutAffectingCompletion() throws Exception {
        Customer c = customer(false);
        byte[][] valid = {new byte[]{37,80,68,70,45}, new byte[]{(byte)255,(byte)216,(byte)255}, new byte[]{(byte)137,80,78,71,13,10,26,10}};
        for (byte[] bytes : valid) mvc.perform(multipart(URL + "/document").file(new MockMultipartFile("file", "../proof.pdf", "application/octet-stream", bytes)).header("Authorization", "Bearer " + c.token()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.document.name").value("proof.pdf")).andExpect(jsonPath("$.completionPercent").value(0));
        for (byte[] bytes : new byte[][]{"fake".getBytes(), new byte[5 * 1024 * 1024 + 1], new byte[0]}) mvc.perform(multipart(URL + "/document").file(new MockMultipartFile("file", "proof.pdf", "application/pdf", bytes)).header("Authorization", "Bearer " + c.token())).andExpect(status().isBadRequest());
        mvc.perform(delete(URL + "/document").header("Authorization", "Bearer " + c.token())).andExpect(status().isOk()).andExpect(jsonPath("$.document").isEmpty());
        save(c, complete());
        mvc.perform(delete(URL + "/document").header("Authorization", "Bearer " + c.token())).andExpect(status().isOk());
    }
    @Test void authenticationAndStaffCannotAccessCustomerEndpoints() throws Exception {
        Customer c = customer(false);
        mvc.perform(get(URL)).andExpect(status().isForbidden());
        // Authorities are read from the database on each request, not trusted from token claims.
        jdbc.update("UPDATE user_roles ur JOIN customers c ON c.cus_user_id=ur.ur_user_id SET ur.ur_role_id=(SELECT rol_role_id FROM roles WHERE rol_role_code='ROLE_BANK_OFFICER') WHERE c.cus_customer_id=?", c.id());
        String staff = io.jsonwebtoken.Jwts.builder().setSubject(c.username()).claim("roles", "ROLE_BANK_OFFICER").claim("tokenVersion", 0)
                .setExpiration(new Date(System.currentTimeMillis() + 60000)).signWith(io.jsonwebtoken.security.Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET)), io.jsonwebtoken.SignatureAlgorithm.HS512).compact();
        mvc.perform(get(URL).header("Authorization", "Bearer " + staff)).andExpect(status().isForbidden());
        mvc.perform(put(URL + "/draft").header("Authorization", "Bearer " + staff).contentType("application/json").content("{}")).andExpect(status().isForbidden());
        mvc.perform(post(URL + "/submit").header("Authorization", "Bearer " + staff)).andExpect(status().isForbidden());
        mvc.perform(post(URL + "/rekyc").header("Authorization", "Bearer " + staff).contentType("application/json").content("{\"remarks\":\"Update my details\"}")).andExpect(status().isForbidden());
        mvc.perform(delete(URL + "/document").header("Authorization", "Bearer " + staff)).andExpect(status().isForbidden());
    }
    @Test void invalidFieldsNeverPersistAndClearingOrChangingDependenciesRemovesCompletion() throws Exception {
        Customer c = customer(false);
        Map<String, List<String>> invalid = Map.of(
                "dateOfBirth", List.of("1899-12-31", "2999-01-01", java.time.LocalDate.now().minusYears(17).toString()),
                "gender", List.of("UNKNOWN"), "relationship", List.of("PARENT"), "occupation", List.of("UNKNOWN"),
                "countryOfOrigin", List.of("ZZ", "India"), "fatherOrSpouseName", List.of("1", "123", "<name>", "name\n", "x".repeat(101)),
                "proofOfIdType", List.of("UNKNOWN"), "idIssueDate", List.of("2999-01-01", "1989-01-01", "1990-01-01"));
        save(c, Map.of("dateOfBirth", "1990-01-01"));
        for (var entry : invalid.entrySet()) for (String value : entry.getValue()) {
            JsonNode p = save(c, Map.of(entry.getKey(), value));
            assertTrue(p.get("fieldErrors").has(entry.getKey()));
            if (!entry.getKey().equals("dateOfBirth")) assertTrue(p.at("/values/" + entry.getKey()).isNull());
        }
        Map<String,String> formats = Map.of("AADHAAR", "234567890123", "PAN", "ABCDE1234F", "PASSPORT", "A1234567", "DRIVING_LICENSE", "KA-12345678", "VOTER_ID", "ABC1234567");
        for (var entry : formats.entrySet()) {
            JsonNode p = save(c, Map.of("proofOfIdType", entry.getKey(), "proofOfIdValue", "invalid"));
            assertTrue(p.get("fieldErrors").has("proofOfIdValue"));
            p = save(c, Map.of("proofOfIdValue", entry.getValue())); assertTrue(p.at("/completedFields/proofOfIdValue").asBoolean());
            p = save(c, Map.of("proofOfIdValue", "")); assertFalse(p.at("/completedFields/proofOfIdValue").asBoolean());
        }
        save(c, Map.of("idIssueDate", "2000-01-01"));
        JsonNode p = save(c, Map.of("dateOfBirth", "2001-01-01")); assertTrue(p.at("/values/idIssueDate").isNull());
        for (String field : KycValidation.FIELDS) {
            p = save(c, Map.of(field, "")); assertFalse(p.at("/completedFields/" + field).asBoolean());
        }
        assertEquals(0, p.get("completionPercent").asInt());
    }

    @Test void mpinSetupStoresNoPlaintextAndLocksAfterFiveFailures() throws Exception {
        Customer c=customer(false);
        mvc.perform(post("/api/v1/auth/mpin/setup").header("Authorization","Bearer "+c.token()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"SkyRiver82!Mint\",\"mpin\":\"864209\"}")).andExpect(status().isNoContent());
        String protectedValue=jdbc.queryForObject("SELECT cmp_hash FROM customer_mpin WHERE cmp_user_id=(SELECT ucr_user_id FROM user_credentials WHERE ucr_username=?)",String.class,c.username());
        assertNotEquals("864209",protectedValue); assertFalse(protectedValue.contains("864209"));
        String body=json.writeValueAsString(Map.of("mpin","111111","purpose","TRANSFER","fields",Map.of("fromAccountId",1,"toAccountNumber","SA-TEST-001","amount",new java.math.BigDecimal("10.00"))));
        for(int i=0;i<4;i++) mvc.perform(post("/api/v1/auth/mpin/verify").header("Authorization","Bearer "+c.token()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("MPIN_INVALID"));
        mvc.perform(post("/api/v1/auth/mpin/verify").header("Authorization","Bearer "+c.token()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isLocked()).andExpect(jsonPath("$.code").value("MPIN_LOCKED"));
        mvc.perform(get("/api/v1/auth/mpin/status").header("Authorization","Bearer "+c.token())).andExpect(status().isOk()).andExpect(jsonPath("$.locked").value(true));
    }
}
