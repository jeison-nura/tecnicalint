package com.meridian.payments.adapter.provider;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.payments.domain.model.CanonicalPaymentEvent;
import com.meridian.payments.domain.model.CanonicalPaymentEventBuilder;
import com.meridian.payments.domain.model.PaymentProvider;
import com.meridian.payments.domain.model.PaymentStatus;
import com.meridian.payments.error.InvalidWebhookException;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

@Component
public class ProviderPayloadMapper {
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public ProviderPayloadMapper(ObjectMapper objectMapper, Clock clock) {
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public CanonicalPaymentEvent fromQuetzal(byte[] rawBody) {
        JsonNode root = readJson(rawBody);
        String sourceEventId = boundedText(root, "id", 80);
        String type = text(root, "type");
        JsonNode charge = object(root, "data", "charge");
        String transactionId = boundedText(charge, "id", 80);
        long amount = nonNegativeLong(charge, "amount");
        String currency = text(charge, "currency");
        String policyNumber = text(charge, "reference");
        PaymentStatus status = switch (type) {
            case "charge.succeeded" -> PaymentStatus.CAPTURED;
            case "charge.failed" -> PaymentStatus.FAILED;
            case "charge.refunded" -> PaymentStatus.REFUNDED;
            default -> throw new InvalidWebhookException("Unsupported Quetzal event type");
        };
        Long refunded = optionalLong(charge, "amount_refunded");
        if (status == PaymentStatus.REFUNDED && refunded == null) {
            throw new InvalidWebhookException("Refund event is missing its refunded amount");
        }
        String stateTimestampField = status == PaymentStatus.REFUNDED ? "refunded_at" : "paid_at";
        Long stateTimestamp = optionalLong(charge, stateTimestampField);
        long eventSeconds = stateTimestamp != null ? stateTimestamp : nonNegativeLong(root, "created");
        String reason = status == PaymentStatus.FAILED ? optionalText(charge, "failure_code") : null;
        return new CanonicalPaymentEventBuilder()
                .provider(PaymentProvider.QUETZAL)
                .providerEventId(sourceEventId)
                .providerTransactionId(transactionId)
                .policyNumber(policyNumber)
                .status(status)
                .amountMinor(amount)
                .refundedAmountMinor(refunded)
                .currency(currency.toUpperCase(java.util.Locale.ROOT))
                .occurredAt(Instant.ofEpochSecond(eventSeconds))
                .receivedAt(clock.instant())
                .correlationId(UUID.randomUUID().toString())
                .reasonCode(reason)
                .build();
    }

    public CanonicalPaymentEvent fromAndina(byte[] rawBody) {
        JsonNode root = readJson(rawBody);
        boundedText(root, "notificacion_id", 80); // Attempt ID changes on provider retries.
        try {
            OffsetDateTime.parse(text(root, "fecha_notificacion"));
        } catch (RuntimeException exception) {
            throw new InvalidWebhookException("Invalid Andina notification timestamp");
        }
        JsonNode transaction = object(root, "transaccion");
        String transactionId = boundedText(transaction, "id", 80);
        String state = text(transaction, "estado");
        PaymentStatus status = switch (state) {
            case "PENDIENTE" -> PaymentStatus.PENDING;
            case "APROBADA" -> PaymentStatus.CAPTURED;
            case "RECHAZADA" -> PaymentStatus.FAILED;
            case "REVERSADA" -> PaymentStatus.REFUNDED;
            default -> throw new InvalidWebhookException("Unsupported Andina transaction state");
        };
        String amountText = text(transaction, "valor");
        if (!amountText.matches("[0-9]{1,14}\\.[0-9]{2}")) {
            throw new InvalidWebhookException("Amount exceeds supported Salesforce precision");
        }
        long amount;
        try {
            amount = new BigDecimal(amountText).movePointRight(2).longValueExact();
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new InvalidWebhookException("Invalid Andina amount");
        }
        Instant occurredAt;
        try {
            occurredAt = OffsetDateTime.parse(text(transaction, "fecha_transaccion")).toInstant();
        } catch (RuntimeException exception) {
            throw new InvalidWebhookException("Invalid Andina state timestamp");
        }
        String eventId = transactionId + ":" + state + ":" + occurredAt;
        String reason = status == PaymentStatus.REFUNDED ? optionalText(transaction, "motivo_reverso") : null;
        return new CanonicalPaymentEventBuilder()
                .provider(PaymentProvider.ANDINA)
                .providerEventId(eventId)
                .providerTransactionId(transactionId)
                .policyNumber(text(transaction, "referencia"))
                .status(status)
                .amountMinor(amount)
                .refundedAmountMinor(null)
                .currency(text(transaction, "moneda"))
                .occurredAt(occurredAt)
                .receivedAt(clock.instant())
                .correlationId(UUID.randomUUID().toString())
                .reasonCode(reason)
                .build();
    }

    private JsonNode readJson(byte[] rawBody) {
        try {
            return objectMapper.reader()
                    .with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .readTree(rawBody);
        } catch (Exception exception) {
            throw new InvalidWebhookException("Malformed provider JSON");
        }
    }

    private JsonNode object(JsonNode parent, String... path) {
        JsonNode current = parent;
        for (String field : path) {
            current = current == null ? null : current.get(field);
            if (current == null || !current.isObject()) {
                throw new InvalidWebhookException("Missing or invalid provider object");
            }
        }
        return current;
    }

    private String text(JsonNode parent, String field) {
        JsonNode value = parent == null ? null : parent.get(field);
        if (value == null || !value.isTextual() || value.asText().isBlank() || value.asText().length() > 256) {
            throw new InvalidWebhookException("Missing or invalid provider field");
        }
        return value.asText();
    }

    private String boundedText(JsonNode parent, String field, int maximum) {
        String value = text(parent, field);
        if (value.length() > maximum) {
            throw new InvalidWebhookException("Provider identifier exceeds maximum length");
        }
        return value;
    }

    private String optionalText(JsonNode parent, String field) {
        JsonNode value = parent == null ? null : parent.get(field);
        return value != null && value.isTextual() ? value.asText() : null;
    }

    private long nonNegativeLong(JsonNode parent, String field) {
        Long value = optionalLong(parent, field);
        if (value == null || value < 0) {
            throw new InvalidWebhookException("Missing or invalid provider integer");
        }
        return value;
    }

    private Long optionalLong(JsonNode parent, String field) {
        JsonNode value = parent == null ? null : parent.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToLong()) {
            throw new InvalidWebhookException("Invalid provider integer");
        }
        return value.longValue();
    }
}
