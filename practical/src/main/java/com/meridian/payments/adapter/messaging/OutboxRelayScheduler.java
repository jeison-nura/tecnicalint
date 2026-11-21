package com.meridian.payments.adapter.messaging;

import com.meridian.payments.application.usecase.OutboxRelay;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OutboxRelayScheduler {
    private static final Logger LOGGER = LoggerFactory.getLogger(OutboxRelayScheduler.class);
    private final OutboxRelay relay;

    public OutboxRelayScheduler(OutboxRelay relay) {
        this.relay = relay;
    }

    @Scheduled(fixedDelayString = "${outbox.relay.fixed-delay-ms:1000}")
    public void publishPending() {
        try {
            relay.publishPendingBatch();
        } catch (RuntimeException exception) {
            // Do not log payloads or provider data. Pending outbox records are retried next cycle.
            LOGGER.warn("Outbox relay failed; pending payment events remain queued");
        }
    }
}
