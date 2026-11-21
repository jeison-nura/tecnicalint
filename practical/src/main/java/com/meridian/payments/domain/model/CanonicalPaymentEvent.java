package com.meridian.payments.domain.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;
import java.util.Objects;

public record CanonicalPaymentEvent(
        int schemaVersion,
        String eventId,
        String paymentKey,
        String country,
        String provider,
        String providerEventId,
        String providerTransactionId,
        String policyNumber,
        String status,
        long amountMinor,
        Long refundedAmountMinor,
        String currency,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant occurredAt,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant receivedAt,
        String correlationId,
        String reasonCode) {

    public boolean hasSameBusinessContent(CanonicalPaymentEvent other) {
        return other != null
                && eventId.equals(other.eventId)
                && paymentKey.equals(other.paymentKey)
                && country.equals(other.country)
                && provider.equals(other.provider)
                && providerTransactionId.equals(other.providerTransactionId)
                && policyNumber.equals(other.policyNumber)
                && status.equals(other.status)
                && amountMinor == other.amountMinor
                && Objects.equals(refundedAmountMinor, other.refundedAmountMinor)
                && currency.equals(other.currency)
                && occurredAt.equals(other.occurredAt)
                && Objects.equals(reasonCode, other.reasonCode);
    }
}
