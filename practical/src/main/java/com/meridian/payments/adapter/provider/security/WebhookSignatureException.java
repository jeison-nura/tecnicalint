package com.meridian.payments.adapter.provider.security;

public class WebhookSignatureException extends RuntimeException {
    public WebhookSignatureException() {
        super("Webhook signature is invalid or outside the allowed time window");
    }
}
