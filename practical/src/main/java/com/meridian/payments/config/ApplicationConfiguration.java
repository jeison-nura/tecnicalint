package com.meridian.payments.config;

import com.meridian.payments.port.ReceivePaymentWebhookUseCase;
import com.meridian.payments.port.CanonicalEventPublisherPort;
import com.meridian.payments.port.ProviderAdapterFactoryPort;
import com.meridian.payments.application.usecase.OutboxRelay;
import com.meridian.payments.application.usecase.ReceivePaymentWebhookService;
import com.meridian.payments.domain.repository.PaymentNotificationRepository;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
public class ApplicationConfiguration {
    @Bean
    Clock utcClock() {
        return Clock.systemUTC();
    }

    @Bean
    ReceivePaymentWebhookUseCase receivePaymentWebhookUseCase(
            ProviderAdapterFactoryPort adapters,
            PaymentNotificationRepository repository) {
        return new ReceivePaymentWebhookService(adapters, repository);
    }

    @Bean
    OutboxRelay outboxRelay(PaymentNotificationRepository repository, CanonicalEventPublisherPort publisher) {
        return new OutboxRelay(repository, publisher);
    }
}
