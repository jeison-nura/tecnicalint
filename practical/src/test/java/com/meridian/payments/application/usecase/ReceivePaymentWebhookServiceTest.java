package com.meridian.payments.application.usecase;

import com.meridian.payments.error.DependencyUnavailableException;
import com.meridian.payments.error.InvalidWebhookException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.meridian.payments.adapter.provider.AndinaWebhookAdapter;
import com.meridian.payments.adapter.provider.ProviderAdapterFactory;
import com.meridian.payments.adapter.provider.ProviderPayloadMapper;
import com.meridian.payments.adapter.provider.QuetzalWebhookAdapter;
import com.meridian.payments.adapter.provider.security.AndinaSignatureVerifier;
import com.meridian.payments.adapter.provider.security.QuetzalSignatureVerifier;
import com.meridian.payments.adapter.provider.security.WebhookSignatureException;
import com.meridian.payments.adapter.messaging.InMemoryCanonicalEventPublisher;
import com.meridian.payments.adapter.persistence.InMemoryPaymentNotificationRepository;
import com.meridian.payments.domain.model.IntakeResult;
import com.meridian.payments.port.ReceivePaymentWebhookUseCase;
import com.meridian.payments.port.WebhookRequest;
import com.meridian.payments.port.CanonicalEventPublisherPort;
import com.meridian.payments.domain.repository.PaymentNotificationRepository;
import com.meridian.payments.port.ProviderWebhookAdapterPort;
import com.meridian.payments.port.WebhookSecretProviderPort;
import com.meridian.payments.domain.model.CanonicalPaymentEvent;
import com.meridian.payments.domain.model.PaymentProvider;
import com.meridian.payments.domain.model.PaymentState;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.*;

class ReceivePaymentWebhookServiceTest {
    private static final String QUETZAL_SECRET = "qz_test_whsec_4f8a1c2e9b7d";
    private static final String ANDINA_SECRET = "andina_test_secret_7c3e19ab";

    @Test
    void providerFactoryRoutesAllQuetzalVectorsThroughOneUseCase() throws IOException {
        Vector[] vectors = {
                new Vector("01-charge-succeeded.json", "1795888803",
                        "2d07770582c02f15235ac4e027b7732beef86be103c574a1c3b988a4275f5031", "captured"),
                new Vector("02-charge-failed.json", "1795889403",
                        "d793d3da0a8d4d66e984ada3daa8b9f473a0eef153d99cdd83bb70f60f13ccc9", "failed"),
                new Vector("03-charge-refunded.json", "1795975203",
                        "a61f9d88d861cd4cc63359b5ca6e6d11d55369db7079387dee9a5024ccb2cae6", "refunded")
        };
        for (Vector vector : vectors) {
            InMemoryPaymentNotificationRepository store = new InMemoryPaymentNotificationRepository();
            ReceivePaymentWebhookUseCase useCase = useCase(
                    Instant.ofEpochSecond(Long.parseLong(vector.timestamp())), store, testSecrets());
            assertEquals(IntakeResult.ACCEPTED, useCase.receive(quetzalRequest(
                    sample("quetzal-mx", vector.file()), vector.timestamp(), vector.signature())));
            assertEquals(vector.status(), store.acceptedEvents().getFirst().status());
        }
    }

    @Test
    void providerFactoryRoutesAllAndinaVectorsAndExcludesPayerData() throws IOException {
        AndinaVector[] vectors = {
                new AndinaVector("01-transaccion-pendiente.json", "2026-11-28T14:02:59-05:00",
                        "JhaBZfk7eQsoMbbQbThmAx/bCyehBOzKYw8XTP2a40k=", "pending"),
                new AndinaVector("02-transaccion-aprobada.json", "2026-11-28T14:03:11-05:00",
                        "MFQjfviySi6CjqbWtQcy5spKtXxHIxAvmg5W3PTzPJk=", "captured"),
                new AndinaVector("03-transaccion-aprobada-reintento.json", "2026-11-28T14:08:11-05:00",
                        "CNu6m5X6B9bMKTsi/IzZfGquIh/KdxTyesBtL4ax424=", "captured"),
                new AndinaVector("04-transaccion-reversada.json", "2026-12-03T09:15:42-05:00",
                        "AfGO+MEffk0AWIEEE3cgyff4hWewoOaDhygCWxs/fNI=", "refunded")
        };
        for (AndinaVector vector : vectors) {
            InMemoryPaymentNotificationRepository store = new InMemoryPaymentNotificationRepository();
            ReceivePaymentWebhookUseCase useCase = useCase(
                    OffsetDateTime.parse(vector.timestamp()).toInstant(), store, testSecrets());
            assertEquals(IntakeResult.ACCEPTED, useCase.receive(andinaRequest(
                    sample("andina-co", vector.file()), vector.timestamp(), vector.signature())));
            assertEquals(vector.status(), store.acceptedEvents().getFirst().status());
        }

        CanonicalPaymentEvent event = mapper(Instant.parse("2026-11-28T19:03:11Z"))
                .fromAndina(sample("andina-co", "02-transaccion-aprobada.json"));
        assertEquals(18_500_000L, event.amountMinor());
        assertTrue(event.paymentKey().matches("CO:Andina:[0-9a-f]{64}"));
        String serialized = new ObjectMapper().findAndRegisterModules().writeValueAsString(event);
        assertFalse(serialized.contains("Laura GÃ³mez"));
        assertFalse(serialized.contains("laura.gomez@example.com"));
        assertFalse(serialized.contains("pagador"));
        assertTrue(serialized.contains("\"occurredAt\":\"2026-11-28T19:03:09Z\""));
    }

    @Test
    void rejectsChangedRawBodyAndStaleSignedTimestamp() throws IOException {
        byte[] body = sample("quetzal-mx", "01-charge-succeeded.json");
        byte[] changed = java.util.Arrays.copyOf(body, body.length + 1);
        changed[changed.length - 1] = ' ';
        String timestamp = "1795888803";
        String signature = "2d07770582c02f15235ac4e027b7732beef86be103c574a1c3b988a4275f5031";
        ReceivePaymentWebhookUseCase current = useCase(
                Instant.ofEpochSecond(1795888803L), new InMemoryPaymentNotificationRepository(), testSecrets());
        assertThrows(WebhookSignatureException.class, () -> current.receive(
                quetzalRequest(changed, timestamp, signature)));

        ReceivePaymentWebhookUseCase stale = useCase(
                Instant.ofEpochSecond(1795888803L + 301), new InMemoryPaymentNotificationRepository(), testSecrets());
        assertThrows(WebhookSignatureException.class, () -> stale.receive(
                quetzalRequest(body, timestamp, signature)));
    }

    @Test
    void factoryRejectsMissingOrAmbiguousProviderHeaderProfiles() {
        ReceivePaymentWebhookUseCase useCase = useCase(
                Instant.EPOCH, new InMemoryPaymentNotificationRepository(), testSecrets());
        byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
        assertThrows(InvalidWebhookException.class,
                () -> useCase.receive(new WebhookRequest(body, Map.of())));
        assertThrows(InvalidWebhookException.class, () -> useCase.receive(new WebhookRequest(body, Map.of(
                "Quetzal-Signature", "x",
                "X-Andina-Timestamp", "2026-11-28T14:03:11-05:00",
                "X-Andina-Signature", "x"))));
    }

    @Test
    void rejectsEmptyOrOversizedWebhookBodiesBeforeProviderDispatch() {
        ReceivePaymentWebhookUseCase useCase = useCase(
                Instant.EPOCH, new InMemoryPaymentNotificationRepository(), testSecrets());
        assertThrows(InvalidWebhookException.class,
                () -> useCase.receive(new WebhookRequest(new byte[0], Map.of())));
        assertThrows(InvalidWebhookException.class,
                () -> useCase.receive(new WebhookRequest(new byte[256 * 1024 + 1], Map.of())));
    }

    @Test
    void andinaRetryWithNewAttemptIdIsOneBusinessNotification() throws IOException {
        InMemoryPaymentNotificationRepository store = new InMemoryPaymentNotificationRepository();
        ReceivePaymentWebhookUseCase useCase = useCase(
                Instant.parse("2026-11-28T19:08:11Z"), store, testSecrets());
        assertEquals(IntakeResult.ACCEPTED, useCase.receive(andinaRequest(
                sample("andina-co", "02-transaccion-aprobada.json"),
                "2026-11-28T14:03:11-05:00", "MFQjfviySi6CjqbWtQcy5spKtXxHIxAvmg5W3PTzPJk=")));
        assertEquals(IntakeResult.DUPLICATE, useCase.receive(andinaRequest(
                sample("andina-co", "03-transaccion-aprobada-reintento.json"),
                "2026-11-28T14:08:11-05:00", "CNu6m5X6B9bMKTsi/IzZfGquIh/KdxTyesBtL4ax424=")));
        assertEquals(1, store.size());
        assertEquals(1, store.pendingCount());
    }

    @Test
    void concurrentRetriesAcceptOneInboxEventAndClassifyTheRestAsDuplicates() throws Exception {
        int workers = 32;
        InMemoryPaymentNotificationRepository repository = new InMemoryPaymentNotificationRepository();
        ReceivePaymentWebhookUseCase useCase = useCase(
                Instant.ofEpochSecond(1795888803L), repository, testSecrets());
        WebhookRequest request = quetzalRequest(
                sample("quetzal-mx", "01-charge-succeeded.json"),
                "1795888803",
                "2d07770582c02f15235ac4e027b7732beef86be103c574a1c3b988a4275f5031");
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);

        try {
            List<Future<IntakeResult>> results = new ArrayList<>();
            for (int index = 0; index < workers; index++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return useCase.receive(request);
                }));
            }
            ready.await();
            start.countDown();

            long accepted = 0;
            long duplicates = 0;
            for (Future<IntakeResult> result : results) {
                if (result.get() == IntakeResult.ACCEPTED) {
                    accepted++;
                } else {
                    duplicates++;
                }
            }
            assertEquals(1, accepted);
            assertEquals(workers - 1, duplicates);
            assertEquals(1, repository.size());
            assertEquals(1, repository.pendingCount());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void sameProviderEventIdWithDifferentBusinessContentIsAConflict() throws IOException {
        InMemoryPaymentNotificationRepository repository = new InMemoryPaymentNotificationRepository();
        CanonicalPaymentEvent accepted = mapper(Instant.ofEpochSecond(1795888803L))
                .fromQuetzal(sample("quetzal-mx", "01-charge-succeeded.json"));
        CanonicalPaymentEvent conflicting = new CanonicalPaymentEvent(
                accepted.schemaVersion(),
                accepted.eventId(),
                accepted.paymentKey(),
                accepted.country(),
                accepted.provider(),
                accepted.providerEventId(),
                accepted.providerTransactionId(),
                accepted.policyNumber(),
                accepted.status(),
                accepted.amountMinor() + 1,
                accepted.refundedAmountMinor(),
                accepted.currency(),
                accepted.occurredAt(),
                accepted.receivedAt(),
                accepted.correlationId(),
                accepted.reasonCode());

        assertEquals(IntakeResult.ACCEPTED, repository.accept(accepted));
        assertThrows(com.meridian.payments.domain.exception.IdempotencyConflictException.class,
                () -> repository.accept(conflicting));
        assertEquals(1, repository.size());
    }

    @Test
    void outOfOrderAndRegressiveEventsCannotOverwriteLatestPaymentState() throws IOException {
        InMemoryPaymentNotificationRepository repository = new InMemoryPaymentNotificationRepository();
        ProviderPayloadMapper mapper = mapper(Instant.parse("2026-12-03T14:16:00Z"));
        CanonicalPaymentEvent approved = mapper.fromAndina(
                sample("andina-co", "02-transaccion-aprobada.json"));
        CanonicalPaymentEvent pending = mapper.fromAndina(
                sample("andina-co", "01-transaccion-pendiente.json"));
        CanonicalPaymentEvent refunded = mapper.fromAndina(
                sample("andina-co", "04-transaccion-reversada.json"));

        assertEquals(IntakeResult.ACCEPTED, repository.accept(approved));
        assertEquals(IntakeResult.ACCEPTED_STALE, repository.accept(pending));
        PaymentState afterLatePending = repository.findPaymentState(approved.paymentKey()).orElseThrow();
        assertEquals("captured", afterLatePending.status().value());
        assertEquals(approved.eventId(), afterLatePending.eventId());
        assertEquals(1, afterLatePending.version());

        CanonicalPaymentEvent newerPending = copyWith(
                pending, "andina:newer-pending", "newer-pending", "pending",
                approved.occurredAt().plusSeconds(30));
        assertEquals(IntakeResult.ACCEPTED_STALE, repository.accept(newerPending));
        assertEquals("captured",
                repository.findPaymentState(approved.paymentKey()).orElseThrow().status().value());

        assertEquals(IntakeResult.ACCEPTED, repository.accept(refunded));
        PaymentState afterRefund = repository.findPaymentState(approved.paymentKey()).orElseThrow();
        assertEquals("refunded", afterRefund.status().value());
        assertEquals(refunded.eventId(), afterRefund.eventId());
        assertEquals(2, afterRefund.version());

        CanonicalPaymentEvent laterCapture = copyWith(
                approved, "andina:late-capture", "late-capture", "captured",
                refunded.occurredAt().plusSeconds(60));
        assertEquals(IntakeResult.ACCEPTED_STALE, repository.accept(laterCapture));
        assertEquals("refunded",
                repository.findPaymentState(approved.paymentKey()).orElseThrow().status().value());
    }

    @Test
    void concurrentOutOfOrderEventsKeepTheNewestStateRegardlessOfArrivalOrder() throws Exception {
        int workers = 24;
        InMemoryPaymentNotificationRepository repository = new InMemoryPaymentNotificationRepository();
        ProviderPayloadMapper mapper = mapper(Instant.parse("2026-11-28T19:08:11Z"));
        CanonicalPaymentEvent approved = mapper.fromAndina(
                sample("andina-co", "02-transaccion-aprobada.json"));
        CanonicalPaymentEvent pending = mapper.fromAndina(
                sample("andina-co", "01-transaccion-pendiente.json"));
        CountDownLatch ready = new CountDownLatch(workers);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);

        try {
            List<Future<IntakeResult>> results = new ArrayList<>();
            for (int index = 0; index < workers; index++) {
                CanonicalPaymentEvent event = index % 2 == 0 ? pending : approved;
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return repository.accept(event);
                }));
            }
            ready.await();
            start.countDown();
            for (Future<IntakeResult> result : results) {
                result.get();
            }

            PaymentState state = repository.findPaymentState(approved.paymentKey()).orElseThrow();
            assertEquals("captured", state.status().value());
            assertEquals(approved.occurredAt(), state.occurredAt());
            assertEquals(2, repository.size());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void conflictingStatesAtTheSameProviderTimeAreRejectedWithoutChangingStoredState() throws IOException {
        InMemoryPaymentNotificationRepository repository = new InMemoryPaymentNotificationRepository();
        CanonicalPaymentEvent captured = mapper(Instant.parse("2026-11-28T19:03:11Z"))
                .fromAndina(sample("andina-co", "02-transaccion-aprobada.json"));
        CanonicalPaymentEvent failedAtSameTime = copyWith(
                captured, "andina:conflicting-same-time", "conflicting-same-time", "failed",
                captured.occurredAt());
        assertEquals(IntakeResult.ACCEPTED, repository.accept(captured));
        assertThrows(com.meridian.payments.domain.exception.PaymentStateConflictException.class,
                () -> repository.accept(failedAtSameTime));

        assertEquals(1, repository.size());
        PaymentState stored = repository.findPaymentState(captured.paymentKey()).orElseThrow();
        assertEquals("captured", stored.status().value());
        assertEquals(captured.eventId(), stored.eventId());
    }

    @Test
    void rejectsAndinaAmountOutsideSalesforcePrecision() throws IOException {
        byte[] original = sample("andina-co", "02-transaccion-aprobada.json");
        String body = new String(original, StandardCharsets.UTF_8)
                .replace("185000.00", "2703077871307903304800185209570441998351128470441496499852999995786463181.15");
        assertThrows(InvalidWebhookException.class,
                () -> mapper(Instant.EPOCH).fromAndina(body.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void storeFailureReturnsRetryableDependencyError() throws IOException {
        PaymentNotificationRepository unavailable = new PaymentNotificationRepository() {
            @Override
            public IntakeResult accept(CanonicalPaymentEvent event) {
                throw new IllegalStateException("simulated store outage");
            }

            @Override
            public List<CanonicalPaymentEvent> pendingPublications(int limit) {
                return List.of();
            }

            @Override
            public void markPublished(CanonicalPaymentEvent event) {}

            @Override
            public Optional<PaymentState> findPaymentState(String paymentKey) {
                return Optional.empty();
            }
        };
        ReceivePaymentWebhookUseCase useCase = useCase(
                Instant.ofEpochSecond(1795888803L), unavailable, testSecrets());
        assertThrows(DependencyUnavailableException.class, () -> useCase.receive(
                quetzalRequest(sample("quetzal-mx", "01-charge-succeeded.json"), "1795888803",
                        "2d07770582c02f15235ac4e027b7732beef86be103c574a1c3b988a4275f5031")));
    }

    @Test
    void relayKeepsFailedPublicationPendingAndRetriesIt() throws IOException {
        InMemoryPaymentNotificationRepository store = new InMemoryPaymentNotificationRepository();
        ReceivePaymentWebhookUseCase useCase = useCase(
                Instant.ofEpochSecond(1795888803L), store, testSecrets());
        assertEquals(IntakeResult.ACCEPTED, useCase.receive(quetzalRequest(
                sample("quetzal-mx", "01-charge-succeeded.json"), "1795888803",
                "2d07770582c02f15235ac4e027b7732beef86be103c574a1c3b988a4275f5031")));

        OutboxRelay failingRelay = new OutboxRelay(store, event -> {
            throw new IllegalStateException("simulated broker outage");
        });
        assertThrows(DependencyUnavailableException.class, failingRelay::publishPendingBatch);
        assertEquals(1, store.pendingCount());

        InMemoryCanonicalEventPublisher publisher = new InMemoryCanonicalEventPublisher();
        OutboxRelay recoveryRelay = new OutboxRelay(store, publisher);
        assertEquals(1, recoveryRelay.publishPendingBatch());
        assertEquals(0, store.pendingCount());
        assertEquals(1, publisher.publishedEvents().size());
    }

    @Test
    void relayCanRepublishAfterBrokerAcceptedButAcknowledgementWasLost() throws IOException {
        InMemoryPaymentNotificationRepository repository = new InMemoryPaymentNotificationRepository();
        ReceivePaymentWebhookUseCase useCase = useCase(
                Instant.ofEpochSecond(1795888803L), repository, testSecrets());
        assertEquals(IntakeResult.ACCEPTED, useCase.receive(quetzalRequest(
                sample("quetzal-mx", "01-charge-succeeded.json"), "1795888803",
                "2d07770582c02f15235ac4e027b7732beef86be103c574a1c3b988a4275f5031")));

        List<CanonicalPaymentEvent> brokerAccepted = new ArrayList<>();
        java.util.concurrent.atomic.AtomicBoolean loseFirstAcknowledgement =
                new java.util.concurrent.atomic.AtomicBoolean(true);
        CanonicalEventPublisherPort publisher = event -> {
            brokerAccepted.add(event);
            if (loseFirstAcknowledgement.getAndSet(false)) {
                throw new IllegalStateException("broker accepted message but acknowledgement was lost");
            }
        };
        OutboxRelay relay = new OutboxRelay(repository, publisher);

        assertThrows(DependencyUnavailableException.class, relay::publishPendingBatch);
        assertEquals(1, repository.pendingCount());
        assertEquals(1, relay.publishPendingBatch());

        assertEquals(2, brokerAccepted.size());
        assertEquals(brokerAccepted.getFirst().eventId(), brokerAccepted.getLast().eventId());
        assertEquals(0, repository.pendingCount());
    }

    @Test
    void verifiesQuetzalRotationAgainstEitherConfiguredSecret() throws IOException {
        byte[] body = sample("quetzal-mx", "01-charge-succeeded.json");
        Clock clock = Clock.fixed(Instant.ofEpochSecond(1795888803L), ZoneOffset.UTC);
        WebhookSecretProviderPort rotated = provider -> switch (provider) {
            case QUETZAL -> List.of(QUETZAL_SECRET, "previous_test_secret");
            case ANDINA -> List.of(ANDINA_SECRET);
            case AUSTRAL -> List.of("unused");
        };
        String previousSignature = hmacHex("previous_test_secret",
                "1795888803.".getBytes(StandardCharsets.UTF_8), body);
        ReceivePaymentWebhookUseCase useCase = useCase(
                clock, new InMemoryPaymentNotificationRepository(), rotated);
        assertEquals(IntakeResult.ACCEPTED, useCase.receive(new WebhookRequest(body, Map.of(
                "Quetzal-Signature", "t=1795888803,v1=" + "00".repeat(32) + ",v1=" + previousSignature))));
    }

    private ReceivePaymentWebhookUseCase useCase(
            Instant now, PaymentNotificationRepository store, WebhookSecretProviderPort secrets) {
        return useCase(Clock.fixed(now, ZoneOffset.UTC), store, secrets);
    }

    private ReceivePaymentWebhookUseCase useCase(
            Clock clock, PaymentNotificationRepository store, WebhookSecretProviderPort secrets) {
        ProviderPayloadMapper mapper = new ProviderPayloadMapper(new ObjectMapper(), clock);
        List<ProviderWebhookAdapterPort> adapters = List.of(
                new QuetzalWebhookAdapter(new QuetzalSignatureVerifier(secrets, clock), mapper),
                new AndinaWebhookAdapter(new AndinaSignatureVerifier(secrets, clock), mapper));
        return new ReceivePaymentWebhookService(new ProviderAdapterFactory(adapters), store);
    }

    private WebhookSecretProviderPort testSecrets() {
        return provider -> switch (provider) {
            case QUETZAL -> List.of(QUETZAL_SECRET);
            case ANDINA -> List.of(ANDINA_SECRET);
            case AUSTRAL -> List.of("unused");
        };
    }

    private ProviderPayloadMapper mapper(Instant now) {
        return new ProviderPayloadMapper(new ObjectMapper(), Clock.fixed(now, ZoneOffset.UTC));
    }

    private WebhookRequest quetzalRequest(byte[] body, String timestamp, String signature) {
        return new WebhookRequest(body,
                Map.of("Quetzal-Signature", "t=" + timestamp + ",v1=" + signature));
    }

    private WebhookRequest andinaRequest(byte[] body, String timestamp, String signature) {
        return new WebhookRequest(body, Map.of(
                "X-Andina-Timestamp", timestamp,
                "X-Andina-Signature", signature));
    }

    private CanonicalPaymentEvent copyWith(
            CanonicalPaymentEvent source,
            String eventId,
            String providerEventId,
            String status,
            Instant occurredAt) {
        return new CanonicalPaymentEvent(
                source.schemaVersion(), eventId, source.paymentKey(), source.country(), source.provider(),
                providerEventId, source.providerTransactionId(), source.policyNumber(), status,
                source.amountMinor(), source.refundedAmountMinor(), source.currency(), occurredAt,
                source.receivedAt(), source.correlationId(), source.reasonCode());
    }

    private byte[] sample(String provider, String file) throws IOException {
        Path direct = Path.of("reference", "providers", provider, "samples", file);
        Path fromPractical = Path.of("..", "reference", "providers", provider, "samples", file);
        String body = Files.readString(Files.exists(direct) ? direct : fromPractical, StandardCharsets.UTF_8)
                .replace("\r\n", "\n");
        if (body.endsWith("\n")) {
            body = body.substring(0, body.length() - 1);
        }
        return body.getBytes(StandardCharsets.UTF_8);
    }

    private String hmacHex(String secret, byte[] prefix, byte[] rawBody) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(prefix);
            return HexFormat.of().formatHex(mac.doFinal(rawBody));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Vector(String file, String timestamp, String signature, String status) {}
    private record AndinaVector(String file, String timestamp, String signature, String status) {}
}
