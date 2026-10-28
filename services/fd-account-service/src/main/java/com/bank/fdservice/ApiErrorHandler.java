package com.bank.fdservice;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.ErrorResponse;
import java.util.Map;

/** Safe fallback; controller-local and dedicated MPIN handlers retain precedence. */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
public class ApiErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of("message", ex.getReason() == null ? "The request could not be completed." : ex.getReason()));
    }

    @ExceptionHandler(BelowMinimumBalanceException.class)
    public ResponseEntity<Map<String,String>> belowMinimum(BelowMinimumBalanceException ex) {
        return ResponseEntity.status(409).body(Map.of("code","BELOW_MINIMUM_BALANCE",
                "message","This debit would take the account below its minimum balance of " + ex.amount() + "."));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ResponseEntity<Map<String, String>> validation(Exception ex) {
        // Binding/constraint messages can contain submitted credentials or identity values.
        return ResponseEntity.badRequest().body(Map.of("message", "Request validation failed. Check the required fields."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> denied() {
        return ResponseEntity.status(403).body(Map.of("message", "You do not have permission to perform this action."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> unexpected(Exception ex) {
        if (ex instanceof ErrorResponse response) {
            int status = response.getStatusCode().value();
            return ResponseEntity.status(status).body(Map.of("message", switch (status) {
                case 400 -> "The request body, parameters or headers are missing or invalid.";
                case 404 -> "The requested resource was not found.";
                case 405 -> "This request method is not supported.";
                case 413 -> "The uploaded file is too large.";
                case 415 -> "This request content type is not supported.";
                default -> "The request could not be completed.";
            }));
        }
        // Never log exception messages/causes: JDBC and binding exceptions may embed values.
        log.error("Unexpected API failure; diagnostic type={}", ex.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(Map.of("message", "The request could not be completed. Please try again later."));
    }
}
