package com.meridian.payments.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

public final class CanonicalPaymentEventBuilder {
    private static final long MAX_AMOUNT_MINOR = 9_999_999_999_999_999L;

    private PaymentProvider provider;
    private String providerEventId;
    private String providerTransactionId;
    private String policyNumber;
    private PaymentStatus status;
    private long amountMinor = -1;
    private Long refundedAmountMinor;
    private String currency;
    private Instant occurredAt;
    private Instant receivedAt;
    private String correlationId;
    private String reasonCode;

    public CanonicalPaymentEventBuilder provider(PaymentProvider value) {
        provider = value;
        return this;
    }

    public CanonicalPaymentEventBuilder providerEventId(String value) {
        providerEventId = value;
        return this;
    }

    public CanonicalPaymentEventBuilder providerTransactionId(String value) {
        providerTransactionId = value;
        return this;
    }

    public CanonicalPaymentEventBuilder policyNumber(String value) {
        policyNumber = value;
        return this;
    }

    public CanonicalPaymentEventBuilder status(PaymentStatus value) {
        status = value;
        return this;
    }

    public CanonicalPaymentEventBuilder amountMinor(long value) {
        amountMinor = value;
        return this;
    }

    public CanonicalPaymentEventBuilder refundedAmountMinor(Long value) {
        refundedAmountMinor = value;
        return this;
    }

    public CanonicalPaymentEventBuilder currency(String value) {
        currency = value;
        return this;
    }

    public CanonicalPaymentEventBuilder occurredAt(Instant value) {
        occurredAt = value;
        return this;
    }

    public CanonicalPaymentEventBuilder receivedAt(Instant value) {
        receivedAt = value;
        return this;
    }

    public CanonicalPaymentEventBuilder correlationId(String value) {
        correlationId = value;
        return this;
    }

    public CanonicalPaymentEventBuilder reasonCode(String value) {
        reasonCode = value;
        return this;
    }

    public CanonicalPaymentEvent build() {
        require(provider != null, "provider");
        requireText(providerEventId, 160, "providerEventId");
        requireText(providerTransactionId, 80, "providerTransactionId");
        requireText(policyNumber, 20, "policyNumber");
        require(status != null, "status");
        require(amountMinor >= 0 && amountMinor <= MAX_AMOUNT_MINOR, "amountMinor");
        require(refundedAmountMinor == null
                || (refundedAmountMinor >= 0 && refundedAmountMinor <= amountMinor), "refundedAmountMinor");
        require(provider.currency().equals(currency), "currency");
        require(policyNumber.matches("^POL-" + provider.country() + "-[0-9]{9}$"), "policyNumber");
        require(occurredAt != null && receivedAt != null, "timestamps");
        requireText(correlationId, 128, "correlationId");

        String eventId = provider.providerName().toLowerCase(Locale.ROOT) + ":" + providerEventId;
        String paymentKey = provider.keyPrefix() + sha256(providerTransactionId);
        require(paymentKey.length() <= 80, "paymentKey");

        return new CanonicalPaymentEvent(
                1, eventId, paymentKey, provider.country(), provider.providerName(),
                providerEventId, providerTransactionId, policyNumber, status.value(),
                amountMinor, refundedAmountMinor, currency, occurredAt, receivedAt,
                correlationId, sanitizeReason(reasonCode));
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String sanitizeReason(String reason) {
        if (reason == null) {
            return null;
        }
        return switch (reason) {
            case "insufficient_funds" -> "INSUFFICIENT_FUNDS";
            case "SOLICITUD_BANCO" -> "BANK_REQUEST";
            default -> "PROVIDER_REASON_OTHER";
        };
    }

    private static void requireText(String value, int maximum, String field) {
        require(value != null && !value.isBlank() && value.length() <= maximum, field);
    }

    private static void require(boolean condition, String field) {
        if (!condition) {
            throw new IllegalArgumentException("Invalid canonical payment field: " + field);
        }
    }
}
