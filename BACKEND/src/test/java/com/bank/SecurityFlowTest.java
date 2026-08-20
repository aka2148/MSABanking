package com.bank;

import com.bank.customer.entity.CustomerCategory;
import com.bank.customer.entity.Role;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public class SecurityFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testAuthenticationAndSecuredEndpointFlow() throws Exception {
        // 1. Register a customer user
        Map<String, Object> registerRequest = new HashMap<>();
        registerRequest.put("username", "testcustomer");
        registerRequest.put("password", "securepwd123");
        registerRequest.put("email", "customer@bank.com");
        registerRequest.put("role", Role.ROLE_CUSTOMER);
        registerRequest.put("firstName", "John");
        registerRequest.put("lastName", "Doe");
        registerRequest.put("phone", "1234567890");
        registerRequest.put("address", "123 Banking St");
        registerRequest.put("category", CustomerCategory.STANDARD);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk());

        // 2. Login to get JWT Token
        Map<String, String> loginRequest = new HashMap<>();
        loginRequest.put("username", "testcustomer");
        loginRequest.put("password", "securepwd123");

        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        Map<?, ?> responseMap = objectMapper.readValue(responseBody, Map.class);
        String token = (String) responseMap.get("token");

        // 3. Request profile with JWT Token (Expect 200 OK)
        mockMvc.perform(get("/api/customer/profile")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 4. Request security-protected admin operational report with customer token (Expect 403 Forbidden)
        mockMvc.perform(get("/api/reports/operational")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());

        // 5. Request secured endpoint without token (Expect 403 Forbidden since no authentication token is present)
        mockMvc.perform(get("/api/reports/operational"))
                .andExpect(status().isForbidden());
    }
}
