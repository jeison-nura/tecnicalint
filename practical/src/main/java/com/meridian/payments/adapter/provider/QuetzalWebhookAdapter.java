package com.meridian.payments.adapter.provider;

import com.meridian.payments.adapter.provider.security.QuetzalSignatureVerifier;
import com.meridian.payments.port.WebhookRequest;
import com.meridian.payments.port.ProviderWebhookAdapterPort;
import com.meridian.payments.domain.model.CanonicalPaymentEvent;
import org.springframework.stereotype.Component;

@Component
public class QuetzalWebhookAdapter implements ProviderWebhookAdapterPort {
    private final QuetzalSignatureVerifier verifier;
    private final ProviderPayloadMapper mapper;

    public QuetzalWebhookAdapter(QuetzalSignatureVerifier verifier, ProviderPayloadMapper mapper) {
        this.verifier = verifier;
        this.mapper = mapper;
    }

    @Override
    public boolean supports(WebhookRequest request) {
        return request.header("Quetzal-Signature") != null;
    }

    @Override
    public CanonicalPaymentEvent verifyAndMap(WebhookRequest request) {
        verifier.verify(request.rawBody(), request.header("Quetzal-Signature"));
        return mapper.fromQuetzal(request.rawBody());
    }
}
