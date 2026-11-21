package com.meridian.payments.adapter.secrets;

import com.meridian.payments.adapter.http.WebhookController;
import com.meridian.payments.adapter.http.WebhookExceptionHandler;
import com.meridian.payments.adapter.messaging.OutboxRelayScheduler;
import com.meridian.payments.adapter.persistence.InMemoryPaymentNotificationRepository;
import com.meridian.payments.adapter.provider.security.WebhookSecretUnavailableException;
import com.meridian.payments.application.usecase.OutboxRelay;
import com.meridian.payments.domain.model.CanonicalPaymentEvent;
import com.meridian.payments.domain.model.CanonicalPaymentEventBuilder;
import com.meridian.payments.domain.model.IntakeResult;
import com.meridian.payments.domain.model.PaymentProvider;
import com.meridian.payments.domain.model.PaymentStatus;
import com.meridian.payments.error.DependencyUnavailableException;
import com.meridian.payments.error.InvalidWebhookException;
import com.meridian.payments.port.ReceivePaymentWebhookUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class InfrastructureAdaptersTest {
    @Test
    void environmentSecretAdapterHandlesProviderVariablesAndQuetzalRotation() {
        Map<String, String> environment = Map.of(
                "QUETZAL_WEBHOOK_SECRET", "quetzal-current",
                "QUETZAL_PREVIOUS_WEBHOOK_SECRET", "quetzal-previous",
                "ANDINA_WEBHOOK_SECRET", "andina-current",
                "AUSTRAL_WEBHOOK_SECRET", "austral-current");
        EnvironmentWebhookSecretProvider provider =
                new EnvironmentWebhookSecretProvider(environment::get);

        assertEquals(List.of("quetzal-current", "quetzal-previous"),
                provider.secretsFor(PaymentProvider.QUETZAL));
        assertEquals(List.of("andina-current"), provider.secretsFor(PaymentProvider.ANDINA));
        assertEquals(List.of("austral-current"), provider.secretsFor(PaymentProvider.AUSTRAL));

        EnvironmentWebhookSecretProvider missing =
                new EnvironmentWebhookSecretProvider(key -> null);
        assertThrows(WebhookSecretUnavailableException.class,
                () -> missing.secretsFor(PaymentProvider.ANDINA));
    }

    @Test
    void httpControllerAcknowledgesUseCaseAndExceptionHandlerMapsStatuses() {
        byte[] body = "{\"id\":\"evt\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        ReceivePaymentWebhookUseCase accepted = request -> {
            assertArrayEquals(body, request.rawBody());
            return IntakeResult.ACCEPTED;
        };
        HttpHeaders headers = new HttpHeaders();
        headers.add("Quetzal-Signature", "signature");
        assertEquals(HttpStatus.OK,
                new WebhookController(accepted).receive(body, headers).getStatusCode());

        WebhookExceptionHandler handler = new WebhookExceptionHandler();
        assertEquals(HttpStatus.BAD_REQUEST, handler.invalidPayload().getStatusCode());
        assertEquals(HttpStatus.CONFLICT, handler.idempotencyConflict().getStatusCode());
        assertEquals(HttpStatus.UNAUTHORIZED, handler.invalidSignature().getStatusCode());
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, handler.dependencyUnavailable().getStatusCode());

        ReceivePaymentWebhookUseCase rejects = request -> {
            throw new InvalidWebhookException("invalid test request");
        };
        assertThrows(InvalidWebhookException.class,
                () -> new WebhookController(rejects).receive(body, headers));
    }

    @Test
    void relaySchedulerKeepsRunningWhenPublisherFails() {
        CanonicalPaymentEvent event = new CanonicalPaymentEventBuilder()
                .provider(PaymentProvider.QUETZAL)
                .providerEventId("evt-scheduler")
                .providerTransactionId("txn-scheduler")
                .policyNumber("POL-MX-000000001")
                .status(PaymentStatus.CAPTURED)
                .amountMinor(100)
                .currency("MXN")
                .occurredAt(Instant.parse("2026-11-28T19:00:00Z"))
                .receivedAt(Instant.parse("2026-11-28T19:00:01Z"))
                .correlationId("scheduler-test")
                .build();

        OutboxRelayScheduler emptyScheduler = new OutboxRelayScheduler(
                new OutboxRelay(new InMemoryPaymentNotificationRepository(), ignored -> {}));
        assertDoesNotThrow(emptyScheduler::publishPending);

        InMemoryPaymentNotificationRepository repository = new InMemoryPaymentNotificationRepository();
        repository.accept(event);
        OutboxRelayScheduler unavailableBroker = new OutboxRelayScheduler(
                new OutboxRelay(repository, ignored -> {
                    throw new IllegalStateException("broker unavailable");
                }));
        assertDoesNotThrow(unavailableBroker::publishPending);
        assertEquals(1, repository.pendingCount());
    }
}
