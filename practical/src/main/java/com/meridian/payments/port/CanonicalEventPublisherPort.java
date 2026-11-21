package com.meridian.payments.port;

import com.meridian.payments.domain.model.CanonicalPaymentEvent;

public interface CanonicalEventPublisherPort {
    void publish(CanonicalPaymentEvent event);
}
