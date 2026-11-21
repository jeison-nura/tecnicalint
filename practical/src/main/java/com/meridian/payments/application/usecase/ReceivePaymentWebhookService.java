package com.meridian.payments.application.usecase;

import com.meridian.payments.domain.model.CanonicalPaymentEvent;
import com.meridian.payments.domain.model.IntakeResult;
import com.meridian.payments.domain.exception.IdempotencyConflictException;
import com.meridian.payments.domain.exception.PaymentStateConflictException;
import com.meridian.payments.domain.repository.PaymentNotificationRepository;
import com.meridian.payments.error.DependencyUnavailableException;
import com.meridian.payments.error.InvalidWebhookException;
import com.meridian.payments.port.ProviderAdapterFactoryPort;
import com.meridian.payments.port.ProviderWebhookAdapterPort;
import com.meridian.payments.port.ReceivePaymentWebhookUseCase;
import com.meridian.payments.port.WebhookRequest;

public final class ReceivePaymentWebhookService implements ReceivePaymentWebhookUseCase {
    private static final int MAX_BODY_BYTES = 256 * 1024;

    private final ProviderAdapterFactoryPort adapterFactory;
    private final PaymentNotificationRepository repository;

    public ReceivePaymentWebhookService(
            ProviderAdapterFactoryPort adapterFactory,
            PaymentNotificationRepository repository) {
        this.adapterFactory = adapterFactory;
        this.repository = repository;
    }

    @Override
    public IntakeResult receive(WebhookRequest request) {
        byte[] rawBody = request.rawBody();
        if (rawBody == null || rawBody.length == 0 || rawBody.length > MAX_BODY_BYTES) {
            throw new InvalidWebhookException("Request body is empty or exceeds the size limit");
        }
        ProviderWebhookAdapterPort adapter = adapterFactory.resolve(request);
        CanonicalPaymentEvent event;
        try {
            event = adapter.verifyAndMap(request);
        } catch (IllegalArgumentException exception) {
            throw new InvalidWebhookException("Provider payload violates canonical payment rules");
        }

        IntakeResult result;
        try {
            result = repository.accept(event);
        } catch (IdempotencyConflictException | PaymentStateConflictException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new DependencyUnavailableException("Notification store", exception);
        }
        return result;
    }
}
