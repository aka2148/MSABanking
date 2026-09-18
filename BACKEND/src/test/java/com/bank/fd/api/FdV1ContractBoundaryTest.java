package com.bank.fd.api;

import com.bank.fd.api.controller.FdV1ContractController;
import com.bank.fd.api.dto.ApiErrorResponse;
import com.bank.fd.api.dto.CreateAccountContractRequest;
import com.bank.fd.api.dto.UpdateAccountContractRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FdV1ContractBoundaryTest {

    @Test
    void createAccountUsesValidatedTypedContractFields() throws Exception {
        Method method = FdV1ContractController.class.getDeclaredMethod("createAccount", CreateAccountContractRequest.class);

        assertTrue(method.getParameters()[0].isAnnotationPresent(Valid.class));
        assertNotNull(field(CreateAccountContractRequest.class, "productId").getAnnotation(NotNull.class));
        assertNotNull(field(CreateAccountContractRequest.class, "currencyId").getAnnotation(NotNull.class));
        assertFalse(hasMemberNamed(CreateAccountContractRequest.class, "primaryHolderId"));
        assertEquals(BigDecimal.class, field(CreateAccountContractRequest.class, "principalAmount").getType());
        assertEquals("0.01", field(CreateAccountContractRequest.class, "principalAmount")
                .getAnnotation(DecimalMin.class).value());
        assertEquals(14, field(CreateAccountContractRequest.class, "principalAmount")
                .getAnnotation(Digits.class).integer());
        assertEquals(4, field(CreateAccountContractRequest.class, "principalAmount")
                .getAnnotation(Digits.class).fraction());
        assertEquals(Integer.class, field(CreateAccountContractRequest.class, "tenureMonths").getType());
        assertEquals(1, field(CreateAccountContractRequest.class, "tenureMonths")
                .getAnnotation(Min.class).value());
        assertEquals(BigDecimal.class, field(CreateAccountContractRequest.class, "interestRate").getType());
        assertEquals(5, field(CreateAccountContractRequest.class, "interestRate")
                .getAnnotation(Digits.class).integer());
        assertEquals(4, field(CreateAccountContractRequest.class, "interestRate")
                .getAnnotation(Digits.class).fraction());
    }

    @Test
    void updateAccountOnlyExposesAutoRenewalAndCannotPatchStatus() throws Exception {
        Method method = FdV1ContractController.class.getDeclaredMethod(
                "updateAccount", Long.class, UpdateAccountContractRequest.class);

        assertTrue(method.getParameters()[1].isAnnotationPresent(Valid.class));
        assertEquals(1, UpdateAccountContractRequest.class.getDeclaredFields().length);
        assertNotNull(field(UpdateAccountContractRequest.class, "autoRenewal"));
        assertFalse(hasMemberNamed(UpdateAccountContractRequest.class, "status"));

        String openApi = new String(
                getClass().getResourceAsStream("/static/openapi/fd-service-openapi.yaml").readAllBytes(),
                StandardCharsets.UTF_8);
        int updateStart = openApi.indexOf("UpdateAccountRequest:");
        int accountStart = openApi.indexOf("    Account:", updateStart);
        String updateSchema = openApi.substring(updateStart, accountStart);
        assertTrue(updateSchema.contains("auto_renewal"));
        assertFalse(updateSchema.contains("status"));

        int createStart = openApi.indexOf("CreateAccountRequest:");
        String createSchema = openApi.substring(createStart, updateStart);
        assertFalse(createSchema.contains("primary_holder_id"));
    }

    @Test
    void unsupportedAccountOperationsRemainExplicit501Errors() {
        FdV1ContractController controller = new FdV1ContractController();

        var response = controller.createAccount(new CreateAccountContractRequest());

        assertEquals(501, response.getStatusCode().value());
        assertEquals("createAccount", response.getHeaders().getFirst("X-Operation-Id"));
        assertEquals(new ApiErrorResponse(
                "NOT_IMPLEMENTED", "createAccount has no business implementation yet."), response.getBody());
    }

    @Test
    void fdV1ControllerRequiresAuthentication() {
        PreAuthorize authorization = FdV1ContractController.class.getAnnotation(PreAuthorize.class);

        assertNotNull(authorization);
        assertEquals("isAuthenticated()", authorization.value());
    }

    private static Field field(Class<?> type, String name) throws NoSuchFieldException {
        return type.getDeclaredField(name);
    }

    private static boolean hasMemberNamed(Class<?> type, String name) {
        for (Field field : type.getDeclaredFields()) {
            if (field.getName().equals(name)) {
                return true;
            }
        }
        for (Method method : type.getDeclaredMethods()) {
            if (method.getName().equalsIgnoreCase(name)
                    || method.getName().toLowerCase().contains(name.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
}
