package com.example.orderservice.exception;

public class UnknownSortRuleException extends RuntimeException {
    public UnknownSortRuleException(String key) {
        super("Unknown sort rule: " + key);
    }
}
