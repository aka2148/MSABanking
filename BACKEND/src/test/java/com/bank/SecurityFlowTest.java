package com.bank;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end security flow test using the current YAML-spec routes (/api/v1/auth/*).
 * The legacy /api/auth/* routes were removed as part of Flaw-3 cleanup.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "spring.flyway.enabled=false")
public class SecurityFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testAuthenticationAndSecuredEndpointFlow() throws Exception {
        // 1. Register via the YAML-spec route — returns a JwtResponse with token
        Map<String, Object> registerRequest = new HashMap<>();
        registerRequest.put("username", "testcustomer_flow");
        registerRequest.put("password", "securepwd123");
        registerRequest.put("email", "customer_flow@bank.com");
        registerRequest.put("fullName", "John Doe");

        MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        // Extract JWT from registration response
        String registerBody = registerResult.getResponse().getContentAsString();
        Map<?, ?> registerMap = objectMapper.readValue(registerBody, Map.class);
        String token = (String) registerMap.get("token");

        // 2. /auth/me returns the current user's profile (200 OK with token)
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 3. CUSTOMER role must NOT be able to list all customers (403 Forbidden)
        mockMvc.perform(get("/api/v1/customers")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        // 4. Secured endpoint without any token should return 403
        mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isForbidden());
    }
}
