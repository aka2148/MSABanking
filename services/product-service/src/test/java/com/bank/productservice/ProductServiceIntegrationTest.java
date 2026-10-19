package com.bank.productservice;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class ProductServiceIntegrationTest {
    private static final String SECRET="MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWYwMTIzNDU2Nzg5YWJjZGVmMDEyMzQ1Njc4OWFiY2RlZg==";
    @Container private static final MySQLContainer<?> MYSQL=new MySQLContainer<>("mysql:8.4").withDatabaseName("moneypal_product").withUsername("product_test").withPassword("a-long-random-test-password");
    @DynamicPropertySource static void props(DynamicPropertyRegistry p){p.add("spring.datasource.url",MYSQL::getJdbcUrl);p.add("spring.datasource.username",MYSQL::getUsername);p.add("spring.datasource.password",MYSQL::getPassword);p.add("app.jwt.secret",()->SECRET);}
    @Autowired MockMvc mvc; @Autowired JdbcTemplate jdbc;

    @Test void migrationsCreateOwnedSchemaAndLocalization(){
        assertEquals(3,jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success=1",Integer.class));
        assertTrue(tableExists("product"));assertTrue(tableExists("outbox_event"));assertTrue(tableExists("product_translation"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM rule_type WHERE rut_rule_type_code='FD_TENURE_RATE'",Integer.class));
    }

    @Test void adminCanCreateAndActivateLocalizedMulticurrencyFdOfferAndWriteOutbox()throws Exception{
        String staff=token("ROLE_BANK_OFFICER");
        String category=mvc.perform(post("/api/v1/product-categories").header("Authorization","Bearer "+staff).contentType("application/json").content("{\"categoryCode\":\"FD\",\"categoryName\":\"Fixed Deposit\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long categoryId=com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(category).get("categoryId").asLong();
        mvc.perform(post("/api/v1/currencies").header("Authorization","Bearer "+staff).contentType("application/json").content("{\"isoCode\":\"USD\",\"currencyName\":\"US Dollar\",\"currencySymbol\":\"$\",\"decimalPlaces\":2}"))
                .andExpect(status().isCreated());
        String body="{\"productCode\":\"FD-DEMO\",\"productName\":\"Demo Fixed Deposit\",\"categoryId\":"+categoryId+",\"baseCurrencyCode\":\"USD\",\"currencies\":["
                +"{\"currencyCode\":\"USD\",\"minimumAmount\":100,\"maximumAmount\":100000,\"rateBands\":[{\"minimumTenureMonths\":1,\"maximumTenureMonths\":6,\"annualRate\":0.05},{\"minimumTenureMonths\":7,\"maximumTenureMonths\":12,\"annualRate\":0.06}]},"
                +"{\"currencyCode\":\"KWD\",\"minimumAmount\":25,\"maximumAmount\":25000,\"rateBands\":[{\"minimumTenureMonths\":1,\"maximumTenureMonths\":12,\"annualRate\":0.04}]}]}";
        String created=mvc.perform(post("/api/v1/fd-products").header("Authorization","Bearer "+staff).contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("DRAFT")).andReturn().getResponse().getContentAsString();
        long id=com.fasterxml.jackson.databind.json.JsonMapper.builder().build().readTree(created).get("catalog_product_id").asLong();
        mvc.perform(post("/api/v1/fd-products/"+id+"/activate").header("Authorization","Bearer "+staff))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        mvc.perform(post("/api/v1/fd-products/"+id+"/activate").header("Authorization","Bearer "+staff))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/product").param("lang","fr"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].productName").value("Demo Fixed Deposit"));
        assertEquals(1,jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE event_key=? AND topic='product.catalog' AND is_published=0",Integer.class,Long.toString(id)));
        assertTrue(jdbc.queryForObject("SELECT payload FROM outbox_event WHERE event_key=?",String.class,Long.toString(id)).contains("cur_decimal_places"));
        assertEquals("ACTIVE",jdbc.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(payload,'$.product.pro_status')) FROM outbox_event WHERE event_key=? AND source_version=1",String.class,Long.toString(id)));
        assertEquals(3,jdbc.queryForObject("SELECT cur_decimal_places FROM currency WHERE cur_iso_code='KWD'",Integer.class));
        mvc.perform(put("/api/v1/fd-products/"+id).header("Authorization","Bearer "+staff).contentType("application/json").content("{\"productName\":\"Updated Demo Deposit\",\"description\":\"Updated terms\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.product_name").value("Updated Demo Deposit"));
        assertEquals("Updated Demo Deposit",jdbc.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(payload,'$.product.pro_product_name')) FROM outbox_event WHERE event_key=? AND source_version=2",String.class,Long.toString(id)));
        mvc.perform(post("/api/v1/fd-products/"+id+"/deactivate").header("Authorization","Bearer "+staff))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
        assertEquals("INACTIVE",jdbc.queryForObject("SELECT JSON_UNQUOTE(JSON_EXTRACT(payload,'$.product.pro_status')) FROM outbox_event WHERE event_key=? AND source_version=3",String.class,Long.toString(id)));
    }

    @Test void productWritesRequireStaffRole()throws Exception{
        mvc.perform(post("/api/v1/products").contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/currencies")).andExpect(status().isOk());
    }

    @Test void fixedDepositCategoryAppearsInStaffListAndDuplicateExplainsConflict()throws Exception{
        String staff=token("ROLE_ADMIN");
        String suffix=java.util.UUID.randomUUID().toString().substring(0,8).toUpperCase();
        long categoryId=jdbc.queryForObject("SELECT pc_category_id FROM product_category WHERE pc_category_code='FIXED_DEPOSIT'",Long.class);
        mvc.perform(post("/api/v1/currencies").header("Authorization","Bearer "+staff).contentType("application/json")
                .content("{\"isoCode\":\"EUR\",\"currencyName\":\"Euro\",\"currencySymbol\":\"€\",\"decimalPlaces\":2}"))
                .andExpect(status().isCreated());
        String code="FD-"+suffix;
        String body="{\"productCode\":\""+code+"\",\"productName\":\"Staff Visible FD\",\"categoryId\":"+categoryId+
                ",\"currencies\":[{\"currencyCode\":\"EUR\",\"minimumAmount\":100,\"maximumAmount\":1000,\"rateBands\":[{\"minimumTenureMonths\":1,\"maximumTenureMonths\":6,\"annualRate\":0.05}]}]}";
        mvc.perform(post("/api/v1/fd-products").header("Authorization","Bearer "+staff).contentType("application/json").content(body))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/v1/fd-products").header("Authorization","Bearer "+staff))
                .andExpect(status().isOk()).andExpect(jsonPath("$[?(@.product_code == '"+code+"')]").exists());
        mvc.perform(post("/api/v1/fd-products").header("Authorization","Bearer "+staff).contentType("application/json").content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("Product code already exists: "+code));
    }

    private String token(String role){Date now=new Date();return Jwts.builder().setSubject("product-test-staff").claim("roles",role).setIssuedAt(now).setExpiration(new Date(now.getTime()+60000)).signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)),SignatureAlgorithm.HS512).compact();}
    private boolean tableExists(String name){return jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema=DATABASE() AND table_name=?",Integer.class,name)==1;}
}
