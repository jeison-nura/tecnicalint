package com.meridian.payments.adapter.provider.security;

import com.meridian.payments.port.WebhookSecretProviderPort;
import com.meridian.payments.adapter.provider.security.WebhookSecretUnavailableException;
import com.meridian.payments.domain.model.PaymentProvider;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;

@Component
public class AndinaSignatureVerifier extends AbstractWebhookSignatureVerifier {
    private final Clock clock;

    public AndinaSignatureVerifier(WebhookSecretProviderPort secrets, Clock clock) {
        super(secrets);
        this.clock = clock;
    }

    public void verify(byte[] rawBody, String timestamp, String signature) {
        try {
            if (timestamp == null || timestamp.length() > 64
                    || signature == null || signature.length() != 44) {
                throw new WebhookSignatureException();
            }
            OffsetDateTime parsedTimestamp = OffsetDateTime.parse(timestamp);
            if (Duration.between(parsedTimestamp.toInstant(), clock.instant()).abs()
                    .compareTo(Duration.ofMinutes(10)) > 0) {
                throw new WebhookSignatureException();
            }
            byte[] signedContent = HmacSupport.utf8(timestamp + ".", rawBody);
            byte[] supplied = Base64.getDecoder().decode(signature);
            if (supplied.length != 32) {
                throw new WebhookSignatureException();
            }
            verifyHmac(PaymentProvider.ANDINA, signedContent, List.of(supplied));
        } catch (WebhookSignatureException | WebhookSecretUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new WebhookSignatureException();
        }
    }
}
