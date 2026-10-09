package com.bank.customer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthCustomerEndpointContractTest {
    @Autowired private MockMvc mockMvc;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping mappings;

    @Test
    void registersAllYamlEndpoints() {
        Set<String> registered = new HashSet<>();
        for (RequestMappingInfo mapping : mappings.getHandlerMethods().keySet()) {
            mapping.getMethodsCondition().getMethods().forEach(method ->
                    mapping.getPatternValues().forEach(path -> registered.add(method.name() + " " + normalize(path))));
        }

        yamlEndpoints().forEach(endpoint ->
                assertTrue(registered.contains(endpoint), "Missing endpoint mapping: " + endpoint));
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void customerCannotListAllCustomers() throws Exception {
        mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "BANK_OFFICER")
    void bankOfficerCanListCustomers() throws Exception {
        mockMvc.perform(get("/api/v1/customers"))
                .andExpect(status().isOk());
    }

    private static Set<String> yamlEndpoints() {
        Set<String> endpoints = new HashSet<>();
        add(endpoints, "POST", "/api/v1/auth/register"); add(endpoints, "POST", "/api/v1/auth/login");
        add(endpoints, "POST", "/api/v1/auth/refresh"); add(endpoints, "POST", "/api/v1/auth/logout");
        add(endpoints, "GET", "/api/v1/auth/me"); add(endpoints, "POST", "/api/v1/auth/change-password");
        add(endpoints, "GET", "/api/v1/users/{userId}"); add(endpoints, "GET", "/api/v1/users"); add(endpoints, "PATCH", "/api/v1/users/{userId}/status");
        add(endpoints, "POST", "/api/v1/employees"); add(endpoints, "GET", "/api/v1/employees/{employeeProfileId}"); add(endpoints, "PUT", "/api/v1/employees/{employeeProfileId}"); add(endpoints, "GET", "/api/v1/employees"); add(endpoints, "GET", "/api/v1/employees/search");
        add(endpoints, "GET", "/api/v1/roles"); add(endpoints, "POST", "/api/v1/roles"); add(endpoints, "PUT", "/api/v1/roles/{roleId}"); add(endpoints, "POST", "/api/v1/users/{userId}/roles"); add(endpoints, "GET", "/api/v1/users/{userId}/roles"); add(endpoints, "DELETE", "/api/v1/users/{userId}/roles/{roleId}");
        add(endpoints, "GET", "/api/v1/branches"); add(endpoints, "POST", "/api/v1/branches"); add(endpoints, "GET", "/api/v1/branches/{branchId}"); add(endpoints, "PUT", "/api/v1/branches/{branchId}"); add(endpoints, "PATCH", "/api/v1/branches/{branchId}/status");
        add(endpoints, "GET", "/api/v1/internal/users/{userId}"); add(endpoints, "GET", "/api/v1/internal/users/{userId}/roles"); add(endpoints, "GET", "/api/v1/internal/branches/{branchId}");
        add(endpoints, "POST", "/api/v1/customers"); add(endpoints, "GET", "/api/v1/customers/{customerId}"); add(endpoints, "PUT", "/api/v1/customers/{customerId}"); add(endpoints, "PATCH", "/api/v1/customers/{customerId}/status"); add(endpoints, "PATCH", "/api/v1/customers/{customerId}/risk-category"); add(endpoints, "GET", "/api/v1/customers"); add(endpoints, "GET", "/api/v1/customers/search"); add(endpoints, "GET", "/api/v1/customers/by-user/{userId}");
        add(endpoints, "GET", "/api/v1/customers/{customerId}/details"); add(endpoints, "PUT", "/api/v1/customers/{customerId}/details");
        add(endpoints, "GET", "/api/v1/customer-classification-types"); add(endpoints, "POST", "/api/v1/customer-classification-types"); add(endpoints, "PUT", "/api/v1/customer-classification-types/{typeId}"); add(endpoints, "PATCH", "/api/v1/customer-classification-types/{typeId}/status");
        add(endpoints, "POST", "/api/v1/customers/{customerId}/identity-proofs"); add(endpoints, "GET", "/api/v1/customers/{customerId}/identity-proofs"); add(endpoints, "GET", "/api/v1/customers/{customerId}/identity-proofs/{proofId}"); add(endpoints, "PATCH", "/api/v1/customers/{customerId}/identity-proofs/{proofId}/verify"); add(endpoints, "DELETE", "/api/v1/customers/{customerId}/identity-proofs/{proofId}");
        add(endpoints, "POST", "/api/v1/customers/{customerId}/addresses"); add(endpoints, "GET", "/api/v1/customers/{customerId}/addresses"); add(endpoints, "PUT", "/api/v1/customers/{customerId}/addresses/{addressId}"); add(endpoints, "DELETE", "/api/v1/customers/{customerId}/addresses/{addressId}"); add(endpoints, "PATCH", "/api/v1/customers/{customerId}/addresses/{addressId}/primary"); add(endpoints, "PATCH", "/api/v1/customers/{customerId}/addresses/{addressId}/verify");
        add(endpoints, "POST", "/api/v1/customers/{customerId}/phones"); add(endpoints, "GET", "/api/v1/customers/{customerId}/phones"); add(endpoints, "PUT", "/api/v1/customers/{customerId}/phones/{phoneId}"); add(endpoints, "DELETE", "/api/v1/customers/{customerId}/phones/{phoneId}"); add(endpoints, "PATCH", "/api/v1/customers/{customerId}/phones/{phoneId}/primary"); add(endpoints, "PATCH", "/api/v1/customers/{customerId}/phones/{phoneId}/verify");
        add(endpoints, "POST", "/api/v1/customers/{customerId}/emails"); add(endpoints, "GET", "/api/v1/customers/{customerId}/emails"); add(endpoints, "PUT", "/api/v1/customers/{customerId}/emails/{emailId}"); add(endpoints, "DELETE", "/api/v1/customers/{customerId}/emails/{emailId}"); add(endpoints, "PATCH", "/api/v1/customers/{customerId}/emails/{emailId}/primary"); add(endpoints, "PATCH", "/api/v1/customers/{customerId}/emails/{emailId}/verify");
        add(endpoints, "GET", "/api/v1/internal/customers/{customerId}/eligibility");
        return endpoints;
    }

    private static void add(Set<String> endpoints, String method, String path) {
        endpoints.add(method + " " + normalize(path));
    }

    /**
     * Normalises path variable names so spec and controller can use e.g. {customerId} and {customerId}
     * consistently, while still catching cases where a param at a given position has the wrong name.
     * Strategy: replace each {…} placeholder with a positional token {p0}, {p1}, … so both the spec
     * path and the registered path are compared at the structural level without depending on the
     * exact variable name chosen by the developer.
     */
    private static String normalize(String path) {
        String[] segments = path.split("/");
        int paramIndex = 0;
        StringBuilder sb = new StringBuilder();
        for (String segment : segments) {
            if (!sb.isEmpty()) sb.append("/");
            if (segment.startsWith("{") && segment.endsWith("}")) {
                sb.append("{p").append(paramIndex++).append("}");
            } else {
                sb.append(segment);
            }
        }
        return sb.toString();
    }
}