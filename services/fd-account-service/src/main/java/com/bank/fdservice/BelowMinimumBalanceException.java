package com.bank.fdservice;

public final class BelowMinimumBalanceException extends RuntimeException {
    private final String amount;
    public BelowMinimumBalanceException(String amount) { this.amount=amount; }
    public String amount() { return amount; }
}
