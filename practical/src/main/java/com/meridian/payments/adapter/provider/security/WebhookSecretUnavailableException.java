package com.meridian.payments.adapter.provider.security;

public class WebhookSecretUnavailableException extends RuntimeException {
    public WebhookSecretUnavailableException(Throwable cause) {
        super("Webhook secret is unavailable", cause);
    }
}
