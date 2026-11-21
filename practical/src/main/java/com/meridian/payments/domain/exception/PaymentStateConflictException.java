package com.meridian.payments.domain.exception;

public class PaymentStateConflictException extends RuntimeException {
    public PaymentStateConflictException(String message) {
        super(message);
    }
}
