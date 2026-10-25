package com.bank.fdservice;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class LoanForexIntegrationTest {
    private static final String SECRET="MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWYwMTIzNDU2Nzg5YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZg==";
    @Container static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("moneypal_fd").withUsername("fd_test").withPassword(UUID.randomUUID().toString());
    private static final HttpServer PRODUCTS=productServer();
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username",MYSQL::getUsername);
        registry.add("spring.datasource.password",MYSQL::getPassword);
        registry.add("app.jwt.secret",()->SECRET);
        registry.add("app.kafka.enabled",()->false);
        registry.add("app.product.base-url",()->"http://127.0.0.1:"+PRODUCTS.getAddress().getPort());
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    private final ObjectMapper json=new ObjectMapper();

    @Test void loanReviewDisbursementAndForexRequestsRespectOwnershipAndRetries() throws Exception {
        long customer=91231,other=91232,admin=91233;
        jdbc.update("INSERT INTO fd_user_ref(fur_user_id,fur_user_uuid,fur_customer_number,fur_full_name,fur_user_status,fur_kyc_status) VALUES(?,?,?,?, 'ACTIVE','VERIFIED')",customer,UUID.randomUUID().toString(),"TEST-CUSTOMER","Test Customer");
        jdbc.update("INSERT INTO fd_user_ref(fur_user_id,fur_user_uuid,fur_customer_number,fur_full_name,fur_user_status,fur_kyc_status) VALUES(?,?,?,?, 'ACTIVE','VERIFIED')",other,UUID.randomUUID().toString(),"TEST-OTHER","Other Customer");
        jdbc.update("INSERT INTO fd_currency_ref(fcr_currency_id,fcr_iso_code,fcr_decimal_places) VALUES(1,'INR',2),(2,'USD',2)");
        long destination=openSavings(customer,"INR");
        long otherAccount=openSavings(other,"INR");
        long wrongCurrencyAccount=openSavings(customer,"USD");
        String applicant=token(customer,"ROLE_CUSTOMER"),stranger=token(other,"ROLE_CUSTOMER"),administrator=token(admin,"ROLE_ADMIN");
        String body="{\"productId\":11,\"amount\":1000,\"currencyCode\":\"INR\",\"tenureMonths\":12,\"purpose\":\"Education\",\"destinationSavingsAccountId\":"+destination+"}";
        String created=mvc.perform(post("/api/v1/loans").header("Authorization",applicant).header("X-MPIN-Proof",proof(customer,"LOAN_APPLICATION",MpinProofVerifier.canonical("LOAN_APPLICATION",11,1000,"INR",12,destination))).header("Idempotency-Key","loan-1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("PENDING_REVIEW"))
                .andExpect(jsonPath("$.disbursementTransactionId").isEmpty()).andReturn().getResponse().getContentAsString();
        long id=json.readTree(created).path("id").asLong();
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM savings_transactions WHERE st_account_id=?",Integer.class,destination));
        mvc.perform(post("/api/v1/loans").header("Authorization",applicant).header("Idempotency-Key","loan-1").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(id));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM loan_applications WHERE la_customer_id=?",Integer.class,customer));
        mvc.perform(get("/api/v1/loans/{id}",id).header("Authorization",stranger)).andExpect(status().isNotFound());
        String wrong=body.replace("\"destinationSavingsAccountId\":"+destination,"\"destinationSavingsAccountId\":"+otherAccount);
        mvc.perform(post("/api/v1/loans").header("Authorization",applicant).header("X-MPIN-Proof",proof(customer,"LOAN_APPLICATION",MpinProofVerifier.canonical("LOAN_APPLICATION",11,1000,"INR",12,otherAccount))).header("Idempotency-Key","loan-wrong").contentType(MediaType.APPLICATION_JSON).content(wrong)).andExpect(status().isBadRequest());
        String mismatch=body.replace("\"destinationSavingsAccountId\":"+destination,"\"destinationSavingsAccountId\":"+wrongCurrencyAccount);
        mvc.perform(post("/api/v1/loans").header("Authorization",applicant).header("X-MPIN-Proof",proof(customer,"LOAN_APPLICATION",MpinProofVerifier.canonical("LOAN_APPLICATION",11,1000,"INR",12,wrongCurrencyAccount))).header("Idempotency-Key","loan-mismatch").contentType(MediaType.APPLICATION_JSON).content(mismatch)).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/loans").header("Authorization",applicant).header("X-MPIN-Proof",proof(customer,"LOAN_APPLICATION",MpinProofVerifier.canonical("LOAN_APPLICATION",11,1000000,"INR",12,destination))).header("Idempotency-Key","loan-limits").contentType(MediaType.APPLICATION_JSON).content(body.replace("\"amount\":1000","\"amount\":1000000"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/loans").header("Authorization",applicant).header("Idempotency-Key","loan-1").contentType(MediaType.APPLICATION_JSON).content(body.replace("\"amount\":1000","\"amount\":2000"))).andExpect(status().isConflict());
        mvc.perform(post("/api/v1/admin/loans/{id}/approve",id).header("Authorization",applicant)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/admin/loans/{id}/approve",id).header("Authorization",administrator)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(post("/api/v1/admin/loans/{id}/disburse",id).header("Authorization",administrator)).andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Loan disbursement is unavailable until loan receivable accounting is implemented."));
        mvc.perform(get("/api/v1/admin/loans/{id}",id).header("Authorization",administrator)).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("APPROVED"));
        mvc.perform(post("/api/v1/admin/loans/{id}/disburse",id).header("Authorization",administrator)).andExpect(status().isConflict());
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM savings_transactions WHERE st_reference_id=?",Integer.class,"LOAN:"+id));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM savings_transactions WHERE st_account_id=?",Integer.class,otherAccount));
        assertEquals(0,jdbc.queryForObject("SELECT COUNT(*) FROM loan_applications WHERE la_id=? AND la_destination_savings_id<>?",Integer.class,id,destination));
        String rejected=mvc.perform(post("/api/v1/loans").header("Authorization",applicant).header("X-MPIN-Proof",proof(customer,"LOAN_APPLICATION",MpinProofVerifier.canonical("LOAN_APPLICATION",11,1000,"INR",12,destination))).header("Idempotency-Key","loan-2").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long rejectedId=json.readTree(rejected).path("id").asLong();
        mvc.perform(post("/api/v1/admin/loans/{id}/reject",rejectedId).header("Authorization",administrator).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\" \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/admin/loans/{id}/reject",rejectedId).header("Authorization",administrator).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Missing evidence\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("REJECTED"));
        mvc.perform(post("/api/v1/admin/loans/{id}/reject",rejectedId).header("Authorization",administrator).contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Missing evidence\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/v1/admin/loans/{id}/disburse",rejectedId).header("Authorization",administrator)).andExpect(status().isConflict());
        String forex="{\"productId\":12,\"amount\":2000,\"fundingCurrencyCode\":\"INR\",\"destinationCurrencyCode\":\"USD\"}";
        String request=mvc.perform(post("/api/v1/forex-requests").header("Authorization",applicant).header("X-MPIN-Proof",proof(customer,"FOREX_REQUEST",MpinProofVerifier.canonical("FOREX_REQUEST",12,2000,"INR","USD"))).header("Idempotency-Key","forex-1").contentType(MediaType.APPLICATION_JSON).content(forex))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("REQUESTED")).andExpect(jsonPath("$.markupRate").value(0.01)).andReturn().getResponse().getContentAsString();
        long forexId=json.readTree(request).path("id").asLong();
        mvc.perform(post("/api/v1/forex-requests").header("Authorization",applicant).header("X-MPIN-Proof",proof(customer,"FOREX_REQUEST",MpinProofVerifier.canonical("FOREX_REQUEST",12,2000,"INR","INR"))).header("Idempotency-Key","forex-invalid").contentType(MediaType.APPLICATION_JSON).content(forex.replace("\"destinationCurrencyCode\":\"USD\"","\"destinationCurrencyCode\":\"INR\""))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/forex-requests").header("Authorization",applicant).header("Idempotency-Key","forex-1").contentType(MediaType.APPLICATION_JSON).content(forex))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(forexId));
        mvc.perform(get("/api/v1/forex-requests/{id}",forexId).header("Authorization",stranger)).andExpect(status().isNotFound());
    }

    @Test void snapshotsGateAllCustomerWritesAndIgnoreOlderVersions() throws Exception {
        long customer=98231;
        String actor=token(customer,"ROLE_CUSTOMER");
        String[] paths={"/api/v1/fixed-deposits", "/api/v1/fixed-deposits/999/fund", "/api/v1/loans", "/api/v1/forex-requests"};
        String[] bodies={
            "{\"productId\":1,\"savingsAccountId\":1,\"principal\":1000,\"currencyCode\":\"INR\",\"tenureMonths\":12}",
            "{}",
            "{\"productId\":1,\"amount\":1000,\"currencyCode\":\"INR\",\"tenureMonths\":12,\"purpose\":\"Education\",\"destinationSavingsAccountId\":1}",
            "{\"productId\":1,\"amount\":1000,\"fundingCurrencyCode\":\"INR\",\"destinationCurrencyCode\":\"USD\"}"};
        var consumer=new ReferenceSnapshotConsumer(jdbc,json);
        var ack=org.mockito.Mockito.mock(org.springframework.kafka.support.Acknowledgment.class);
        var user=new java.util.HashMap<String,Object>();
        user.put("userId",customer); user.put("userUuid",UUID.randomUUID().toString()); user.put("status","ACTIVE");
        long version=0;
        for (String kyc : new String[]{null,"PENDING","REJECTED","VERIFIED"}) {
            if (kyc!=null) {
                user.put("kycStatus",kyc); user.put("riskCategory","VERIFIED".equals(kyc)?"HIGH":"LOW");
                consumer.userSnapshot(json.writeValueAsString(java.util.Map.of("sourceVersion",++version,"user",user)),ack);
            }
            for (int i=0;i<paths.length;i++) mvc.perform(post(paths[i]).header("Authorization",actor).header("Idempotency-Key","blocked-"+version+"-"+i).contentType(MediaType.APPLICATION_JSON).content(bodies[i]))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/api/v1/loans").header("Authorization",actor)).andExpect(status().isOk());
        }
        user.put("riskCategory","LOW");
        consumer.userSnapshot(json.writeValueAsString(java.util.Map.of("sourceVersion",++version,"user",user)),ack);
        user.put("kycStatus","REJECTED");
        consumer.userSnapshot(json.writeValueAsString(java.util.Map.of("sourceVersion",version-1,"user",user)),ack);
        assertEquals("VERIFIED",jdbc.queryForObject("SELECT fur_kyc_status FROM fd_user_ref WHERE fur_user_id=?",String.class,customer));
        CustomerProductAccess.requireVerified(jdbc,customer);
    }

    private long openSavings(long user,String currency) {
        long currencyId=jdbc.queryForObject("SELECT fcr_currency_id FROM fd_currency_ref WHERE fcr_iso_code=?",Long.class,currency);
        String number="SA-TEST-"+UUID.randomUUID().toString().substring(0,12);
        jdbc.update("INSERT INTO savings_accounts(sa_account_number,sa_holder_id,sa_currency_id,sa_display_name,sa_balance,sa_status,sa_created_date) VALUES(?,?,?,?,0,'ACTIVE',UTC_TIMESTAMP(6))",number,user,currencyId,"Test savings");
        return jdbc.queryForObject("SELECT sa_account_id FROM savings_accounts WHERE sa_account_number=?",Long.class,number);
    }
    private String token(long user,String role) {
        Date now=new Date();
        return "Bearer "+Jwts.builder().setSubject("test-"+user).claim("userId",user).claim("roles",role).setIssuedAt(now)
                .setExpiration(new Date(now.getTime()+60000)).signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)),SignatureAlgorithm.HS512).compact();
    }
    private String proof(long user,String purpose,String canonical) {
        try{javax.crypto.Mac mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(Decoders.BASE64.decode(SECRET),"HmacSHA256"));byte[] key=mac.doFinal("mpin-proof-v1".getBytes(StandardCharsets.UTF_8));java.time.Instant now=java.time.Instant.now();
            return Jwts.builder().setIssuer("identity").setSubject(Long.toString(user)).claim("typ","mpin-proof").claim("act","test-"+user).claim("purpose",purpose).claim("bind",MpinProofVerifier.hash(canonical)).claim("jti",UUID.randomUUID().toString()).setIssuedAt(Date.from(now)).setExpiration(Date.from(now.plusSeconds(90))).signWith(Keys.hmacShaKeyFor(key),SignatureAlgorithm.HS256).compact();
        }catch(Exception e){throw new IllegalStateException(e);}
    }
    private static HttpServer productServer() {
        try {
            HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
            server.createContext("/api/v1/customer-products",exchange->{
                String path=exchange.getRequestURI().getPath();
                String body=path.endsWith("/LOAN/11") ? "{\"productId\":11,\"productName\":\"Test loan\",\"baseCurrencyCode\":\"INR\",\"currencies\":[{\"code\":\"INR\",\"minimumAmount\":100,\"maximumAmount\":10000,\"decimalPlaces\":2}],\"pricingBands\":[{\"currencyCode\":\"INR\",\"valueFrom\":12,\"valueTo\":36,\"rate\":0.10}]}"
                    : path.endsWith("/FOREX/12") ? "{\"productId\":12,\"productName\":\"Test forex\",\"baseCurrencyCode\":\"INR\",\"currencies\":[{\"code\":\"INR\",\"minimumAmount\":100,\"maximumAmount\":10000,\"decimalPlaces\":2},{\"code\":\"USD\",\"minimumAmount\":1,\"maximumAmount\":100,\"decimalPlaces\":2}],\"pricingBands\":[{\"currencyCode\":\"USD\",\"valueFrom\":100,\"valueTo\":10000,\"rate\":0.01}]}" : "{}";
                int status=body.equals("{}")?404:200;byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,bytes.length);
                try(var output=exchange.getResponseBody()){output.write(bytes);}
            });
            server.start();return server;
        } catch(Exception ex){throw new ExceptionInInitializerError(ex);}
    }
}
