package com.meridian.payments.adapter.provider.security;

import com.meridian.payments.adapter.provider.security.WebhookSecretUnavailableException;
import com.meridian.payments.domain.model.PaymentProvider;
import com.meridian.payments.port.WebhookSecretProviderPort;

import java.util.List;

/**
 * Shared security policy for provider webhook signatures.
 * Provider subclasses define only their wire format and signing convention.
 */
public abstract class AbstractWebhookSignatureVerifier {
    private final WebhookSecretProviderPort secretProvider;

    protected AbstractWebhookSignatureVerifier(WebhookSecretProviderPort secretProvider) {
        this.secretProvider = secretProvider;
    }

    protected final void verifyHmac(
            PaymentProvider provider,
            byte[] signedContent,
            List<byte[]> suppliedSignatures) {
        List<String> secrets;
        try {
            secrets = secretProvider.secretsFor(provider);
        } catch (RuntimeException exception) {
            throw new WebhookSecretUnavailableException(exception);
        }
        if (secrets == null || secrets.isEmpty()) {
            throw new WebhookSecretUnavailableException(
                    new IllegalStateException("No webhook secrets configured"));
        }

        boolean matched = false;
        for (String secret : secrets) {
            if (secret == null || secret.isBlank()) {
                continue;
            }
            byte[] expected = HmacSupport.sha256(secret, signedContent);
            for (byte[] supplied : suppliedSignatures) {
                if (supplied != null) {
                    matched |= HmacSupport.constantTimeEquals(expected, supplied);
                }
            }
        }
        if (!matched) {
            throw new WebhookSignatureException();
        }
    }
}
