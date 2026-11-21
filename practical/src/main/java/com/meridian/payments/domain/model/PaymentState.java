package com.meridian.payments.domain.model;

import com.meridian.payments.domain.exception.PaymentStateConflictException;

import java.time.Instant;

/**
 * Latest accepted business state for one payment identity.
 */
public record PaymentState(
        String paymentKey,
        PaymentStatus status,
        Instant occurredAt,
        String eventId,
        long version) {

    public static PaymentState from(CanonicalPaymentEvent event) {
        return new PaymentState(
                event.paymentKey(),
                PaymentStatus.fromValue(event.status()),
                event.occurredAt(),
                event.eventId(),
                1);
    }

    public Transition apply(CanonicalPaymentEvent event) {
        if (!paymentKey.equals(event.paymentKey())) {
            throw new IllegalArgumentException("Payment state key does not match event");
        }
        PaymentStatus incomingStatus = PaymentStatus.fromValue(event.status());

        if (event.occurredAt().isBefore(occurredAt)) {
            return new Transition(this, false);
        }
        if (event.occurredAt().equals(occurredAt)) {
            if (incomingStatus != status) {
                throw new PaymentStateConflictException(
                        "Different payment states share the same provider event time");
            }
            return new Transition(this, false);
        }
        if (status == PaymentStatus.REFUNDED && incomingStatus != PaymentStatus.REFUNDED) {
            return new Transition(this, false);
        }
        if (incomingStatus == PaymentStatus.PENDING && status != PaymentStatus.PENDING) {
            return new Transition(this, false);
        }

        return new Transition(new PaymentState(
                paymentKey, incomingStatus, event.occurredAt(), event.eventId(), version + 1), true);
    }

    public record Transition(PaymentState state, boolean applied) {}
}
