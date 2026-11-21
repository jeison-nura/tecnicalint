package com.meridian.payments.adapter.secrets;

import com.meridian.payments.port.WebhookSecretProviderPort;
import com.meridian.payments.adapter.provider.security.WebhookSecretUnavailableException;
import com.meridian.payments.domain.model.PaymentProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

@Component
public class EnvironmentWebhookSecretProvider implements WebhookSecretProviderPort {
    private final Function<String, String> environment;

    @Autowired
    public EnvironmentWebhookSecretProvider() {
        this(System::getenv);
    }

    EnvironmentWebhookSecretProvider(Function<String, String> environment) {
        this.environment = environment;
    }

    @Override
    public List<String> secretsFor(PaymentProvider provider) {
        String currentVariable = switch (provider) {
            case QUETZAL -> "QUETZAL_WEBHOOK_SECRET";
            case ANDINA -> "ANDINA_WEBHOOK_SECRET";
        };
        String current = environment.apply(currentVariable);
        if (current == null || current.isBlank()) {
            throw new WebhookSecretUnavailableException(new IllegalStateException("Secret is not configured"));
        }
        List<String> values = new ArrayList<>();
        values.add(current);
        if (provider == PaymentProvider.QUETZAL) {
            String previous = environment.apply("QUETZAL_PREVIOUS_WEBHOOK_SECRET");
            if (previous != null && !previous.isBlank()) {
                values.add(previous);
            }
        }
        return List.copyOf(values);
    }
}
