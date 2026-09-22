package com.bank.fd.exception;

/** Thrown when an idempotency key is reused with a different request body. */
public class FdDuplicateOperationException extends RuntimeException {

    private final String operationScope;
    private final String existingReference;

    public FdDuplicateOperationException(String operationScope, String existingReference) {
        super("Duplicate " + operationScope + " operation; existing record: " + existingReference);
        this.operationScope = operationScope;
        this.existingReference = existingReference;
    }

    public String getOperationScope() {
        return operationScope;
    }

    public String getExistingReference() {
        return existingReference;
    }
}
