package com.meridian.payments.adapter.http;

import com.meridian.payments.adapter.provider.security.WebhookSignatureException;
import com.meridian.payments.error.DependencyUnavailableException;
import com.meridian.payments.domain.exception.IdempotencyConflictException;
import com.meridian.payments.domain.exception.PaymentStateConflictException;
import com.meridian.payments.error.InvalidWebhookException;
import com.meridian.payments.adapter.provider.security.WebhookSecretUnavailableException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class WebhookExceptionHandler {
    @ExceptionHandler({
            InvalidWebhookException.class,
            IllegalArgumentException.class
    })
    public ResponseEntity<Void> invalidPayload() {
        return ResponseEntity.badRequest().build();
    }

    @ExceptionHandler({IdempotencyConflictException.class, PaymentStateConflictException.class})
    public ResponseEntity<Void> idempotencyConflict() {
        return ResponseEntity.status(HttpStatus.CONFLICT).build();
    }

    @ExceptionHandler(WebhookSignatureException.class)
    public ResponseEntity<Void> invalidSignature() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }

    @ExceptionHandler({
            DependencyUnavailableException.class,
            WebhookSecretUnavailableException.class
    })
    public ResponseEntity<Void> dependencyUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }
}
