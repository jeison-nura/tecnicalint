package com.meridian.payments.domain.exception;

public class IdempotencyConflictException extends RuntimeException {
    public IdempotencyConflictException() {
        super("Provider event ID conflicts with previously accepted content");
    }
}
