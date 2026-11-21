package com.meridian.payments.port;

import com.meridian.payments.domain.model.PaymentProvider;

import java.util.List;

public interface WebhookSecretProviderPort {
    List<String> secretsFor(PaymentProvider provider);
}
