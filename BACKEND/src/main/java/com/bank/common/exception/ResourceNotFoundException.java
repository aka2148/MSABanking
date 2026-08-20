package com.bank.common.exception;

public class ResourceNotFoundException extends BankingException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
