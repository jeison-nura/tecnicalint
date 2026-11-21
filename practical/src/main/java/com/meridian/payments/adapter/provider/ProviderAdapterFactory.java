package com.meridian.payments.adapter.provider;

import com.meridian.payments.port.WebhookRequest;
import com.meridian.payments.port.ProviderAdapterFactoryPort;
import com.meridian.payments.port.ProviderWebhookAdapterPort;
import com.meridian.payments.error.InvalidWebhookException;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProviderAdapterFactory implements ProviderAdapterFactoryPort {
    private final List<ProviderWebhookAdapterPort> adapters;

    public ProviderAdapterFactory(List<ProviderWebhookAdapterPort> adapters) {
        this.adapters = List.copyOf(adapters);
    }

    @Override
    public ProviderWebhookAdapterPort resolve(WebhookRequest request) {
        List<ProviderWebhookAdapterPort> matches = adapters.stream()
                .filter(adapter -> adapter.supports(request))
                .toList();
        if (matches.size() != 1) {
            throw new InvalidWebhookException("Missing or ambiguous provider signature-header profile");
        }
        return matches.getFirst();
    }
}
