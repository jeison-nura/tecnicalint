package com.meridian.payments.port;

import com.meridian.payments.port.WebhookRequest;

public interface ProviderAdapterFactoryPort {
    ProviderWebhookAdapterPort resolve(WebhookRequest request);
}
