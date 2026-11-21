package com.meridian.payments.adapter.persistence;

import com.meridian.payments.domain.model.CanonicalPaymentEvent;
import com.meridian.payments.domain.exception.IdempotencyConflictException;
import com.meridian.payments.domain.model.IntakeResult;
import com.meridian.payments.domain.model.PaymentState;
import com.meridian.payments.domain.repository.PaymentNotificationRepository;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Assessment stub only. Production requires a durable transactional inbox/outbox.
 */
@Component
public class InMemoryPaymentNotificationRepository implements PaymentNotificationRepository {
    private final ConcurrentMap<String, CanonicalPaymentEvent> accepted = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PaymentState> paymentStates = new ConcurrentHashMap<>();
    private final Set<String> published = ConcurrentHashMap.newKeySet();

    @Override
    public synchronized IntakeResult accept(CanonicalPaymentEvent event) {
        String key = key(event);
        CanonicalPaymentEvent existing = accepted.get(key);
        if (existing != null) {
            if (!existing.hasSameBusinessContent(event)) {
                throw new IdempotencyConflictException();
            }
            return IntakeResult.DUPLICATE;
        }

        PaymentState current = paymentStates.get(event.paymentKey());
        PaymentState.Transition transition = current == null
                ? new PaymentState.Transition(PaymentState.from(event), true)
                : current.apply(event);

        // The synchronized method makes inbox, payment state and outbox acceptance
        // one in-process critical section. Production must do this in one SQL transaction.
        accepted.put(key, event);
        paymentStates.put(event.paymentKey(), transition.state());
        return transition.applied() ? IntakeResult.ACCEPTED : IntakeResult.ACCEPTED_STALE;
    }

    public int size() {
        return accepted.size();
    }

    @Override
    public List<CanonicalPaymentEvent> pendingPublications(int limit) {
        return accepted.entrySet().stream()
                .filter(entry -> !published.contains(entry.getKey()))
                .map(java.util.Map.Entry::getValue)
                .sorted(Comparator.comparing(CanonicalPaymentEvent::eventId))
                .limit(limit)
                .toList();
    }

    @Override
    public void markPublished(CanonicalPaymentEvent event) {
        published.add(key(event));
    }

    public List<CanonicalPaymentEvent> acceptedEvents() {
        return accepted.values().stream()
                .sorted(Comparator.comparing(CanonicalPaymentEvent::eventId))
                .toList();
    }

    public int pendingCount() {
        return pendingPublications(Integer.MAX_VALUE).size();
    }

    @Override
    public Optional<PaymentState> findPaymentState(String paymentKey) {
        return Optional.ofNullable(paymentStates.get(paymentKey));
    }

    private String key(CanonicalPaymentEvent event) {
        return event.provider() + ":" + event.eventId();
    }
}
