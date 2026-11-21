package com.meridian.payments.adapter.provider;

import com.meridian.payments.adapter.provider.security.AndinaSignatureVerifier;
import com.meridian.payments.port.WebhookRequest;
import com.meridian.payments.port.ProviderWebhookAdapterPort;
import com.meridian.payments.domain.model.CanonicalPaymentEvent;
import org.springframework.stereotype.Component;

@Component
public class AndinaWebhookAdapter implements ProviderWebhookAdapterPort {
    private final AndinaSignatureVerifier verifier;
    private final ProviderPayloadMapper mapper;

    public AndinaWebhookAdapter(AndinaSignatureVerifier verifier, ProviderPayloadMapper mapper) {
        this.verifier = verifier;
        this.mapper = mapper;
    }

    @Override
    public boolean supports(WebhookRequest request) {
        return request.header("X-Andina-Timestamp") != null
                || request.header("X-Andina-Signature") != null;
    }

    @Override
    public CanonicalPaymentEvent verifyAndMap(WebhookRequest request) {
        verifier.verify(request.rawBody(), request.header("X-Andina-Timestamp"),
                request.header("X-Andina-Signature"));
        return mapper.fromAndina(request.rawBody());
    }
}
