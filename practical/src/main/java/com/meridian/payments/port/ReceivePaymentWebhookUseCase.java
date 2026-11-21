package com.meridian.payments.port;

import com.meridian.payments.domain.model.IntakeResult;

public interface ReceivePaymentWebhookUseCase {
    IntakeResult receive(WebhookRequest request);
}
