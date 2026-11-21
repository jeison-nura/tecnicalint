package com.meridian.payments.port;

import com.meridian.payments.port.WebhookRequest;
import com.meridian.payments.domain.model.CanonicalPaymentEvent;

public interface ProviderWebhookAdapterPort {
    boolean supports(WebhookRequest request);

    CanonicalPaymentEvent verifyAndMap(WebhookRequest request);
}
