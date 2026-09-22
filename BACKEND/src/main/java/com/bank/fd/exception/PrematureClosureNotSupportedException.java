package com.bank.fd.exception;

import com.bank.common.exception.BankingException;

/**
 * Thrown when a product has no usable {@code CLOSURE_PENALTY} rule, so the FD
 * cannot be closed prematurely (D-02). The FD stays open and nothing is
 * written. Plan 03 maps this to HTTP 422; no handler is added here.
 */
public class PrematureClosureNotSupportedException extends BankingException {

    public PrematureClosureNotSupportedException(String message) {
        super(message);
    }
}
