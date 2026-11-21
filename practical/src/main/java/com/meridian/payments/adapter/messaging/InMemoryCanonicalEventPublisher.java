package com.meridian.payments.adapter.messaging;

import com.meridian.payments.port.CanonicalEventPublisherPort;
import com.meridian.payments.domain.model.CanonicalPaymentEvent;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Assessment broker stub; records publications in-process only.
 */
@Component
public class InMemoryCanonicalEventPublisher implements CanonicalEventPublisherPort {
    private final List<CanonicalPaymentEvent> published = new CopyOnWriteArrayList<>();

    @Override
    public void publish(CanonicalPaymentEvent event) {
        published.add(event);
    }

    public List<CanonicalPaymentEvent> publishedEvents() {
        return List.copyOf(published);
    }
}
