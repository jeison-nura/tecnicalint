package com.meridian.payments.application.usecase;

import com.meridian.payments.error.DependencyUnavailableException;
import com.meridian.payments.port.CanonicalEventPublisherPort;
import com.meridian.payments.domain.model.CanonicalPaymentEvent;
import com.meridian.payments.domain.repository.PaymentNotificationRepository;

public final class OutboxRelay {
    private static final int BATCH_SIZE = 100;

    private final PaymentNotificationRepository repository;
    private final CanonicalEventPublisherPort publisher;

    public OutboxRelay(PaymentNotificationRepository repository, CanonicalEventPublisherPort publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    public int publishPendingBatch() {
        int published = 0;
        for (CanonicalPaymentEvent event : repository.pendingPublications(BATCH_SIZE)) {
            try {
                publisher.publish(event);
                repository.markPublished(event);
                published++;
            } catch (RuntimeException exception) {
                // Leave the outbox row pending. Retrying may publish a duplicate with the same eventId.
                throw new DependencyUnavailableException("Canonical event publisher", exception);
            }
        }
        return published;
    }
}
