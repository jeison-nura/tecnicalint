package com.meridian.payments.domain.repository;

import com.meridian.payments.domain.model.CanonicalPaymentEvent;
import com.meridian.payments.domain.model.IntakeResult;
import com.meridian.payments.domain.model.PaymentState;

import java.util.List;
import java.util.Optional;

/**
 * Repository boundary for accepted payment notifications and their outbox state.
 * Implementations own atomic inbox/outbox persistence.
 */
public interface PaymentNotificationRepository {
    IntakeResult accept(CanonicalPaymentEvent event);

    List<CanonicalPaymentEvent> pendingPublications(int limit);

    void markPublished(CanonicalPaymentEvent event);

    Optional<PaymentState> findPaymentState(String paymentKey);
}
