package com.bank.common.exception;

import com.bank.fd.domain.FdLifecycleCommand;
import com.bank.fd.entity.FdStatus;
import com.bank.fd.exception.FdDuplicateOperationException;
import com.bank.fd.exception.InvalidFdTransitionException;
import com.bank.fd.exception.PrematureClosureNotSupportedException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Status-mapping proof for {@link GlobalExceptionHandler}: the handler is
 * invoked directly and each error maps to its D-11 status with an integer
 * {@code status} field in the body. No MockMvc, no Spring context.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void duplicateOperationMapsTo409WithExistingRecordReference() {
        ResponseEntity<Map<String, Object>> response = handler.handleFdDuplicateOperation(
                new FdDuplicateOperationException("FUNDING", "fd-42"));

        assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo(409);
        assertThat((String) response.getBody().get("message")).contains("existing record:");
    }

    @Test
    void invalidTransitionMapsTo409() {
        ResponseEntity<Map<String, Object>> response = handler.handleInvalidTransition(
                new InvalidFdTransitionException(FdStatus.ACTIVE, FdLifecycleCommand.FUND));

        assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo(409);
        assertThat((String) response.getBody().get("message")).contains("Invalid FD transition");
    }

    @Test
    void unsupportedPrematureClosureMapsTo422() {
        ResponseEntity<Map<String, Object>> response = handler.handlePrematureClosureNotSupported(
                new PrematureClosureNotSupportedException("Premature closure is not supported for this product"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(response.getStatusCode().value()).isEqualTo(422);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo(422);
    }

    @Test
    void missingResourceMapsTo404() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleResourceNotFound(new ResourceNotFoundException("FD account not found"));

        assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo(404);
        assertThat((String) response.getBody().get("message")).contains("FD account not found");
    }

    @Test
    void invalidInputMapsTo400() {
        ResponseEntity<Map<String, Object>> response =
                handler.handleInvalidInput(new InvalidInputException("Principal must be positive"));

        assertThat(response.getStatusCode().value()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("status")).isEqualTo(400);
    }
}
