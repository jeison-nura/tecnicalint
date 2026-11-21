package com.meridian.payments.adapter.provider.security;

import com.meridian.payments.port.WebhookSecretProviderPort;
import com.meridian.payments.adapter.provider.security.WebhookSecretUnavailableException;
import com.meridian.payments.domain.model.PaymentProvider;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

@Component
public class QuetzalSignatureVerifier extends AbstractWebhookSignatureVerifier {
    private final Clock clock;

    public QuetzalSignatureVerifier(WebhookSecretProviderPort secrets, Clock clock) {
        super(secrets);
        this.clock = clock;
    }

    public void verify(byte[] rawBody, String header) {
        try {
            ParsedHeader parsed = parse(header);
            Instant signedAt = Instant.ofEpochSecond(Long.parseLong(parsed.timestamp()));
            if (Duration.between(signedAt, clock.instant()).abs().compareTo(Duration.ofSeconds(300)) > 0) {
                throw new WebhookSignatureException();
            }
            byte[] signedContent = HmacSupport.utf8(parsed.timestamp() + ".", rawBody);
            List<byte[]> candidates = new ArrayList<>();
            for (String signature : parsed.signatures()) {
                if (!signature.matches("[0-9a-f]{64}")) {
                    throw new WebhookSignatureException();
                }
                candidates.add(HexFormat.of().parseHex(signature));
            }
            verifyHmac(PaymentProvider.QUETZAL, signedContent, candidates);
        } catch (WebhookSignatureException | WebhookSecretUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new WebhookSignatureException();
        }
    }

    private ParsedHeader parse(String header) {
        if (header == null || header.isBlank() || header.length() > 1024) {
            throw new IllegalArgumentException("Missing or oversized signature");
        }
        String timestamp = null;
        List<String> signatures = new ArrayList<>();
        for (String component : header.split(",")) {
            String[] pair = component.trim().split("=", 2);
            if (pair.length != 2) {
                throw new IllegalArgumentException("Malformed signature");
            }
            if (pair[0].equals("t")) {
                if (timestamp != null) {
                    throw new IllegalArgumentException("Duplicate timestamp");
                }
                timestamp = pair[1];
            } else if (pair[0].equals("v1")) {
                signatures.add(pair[1]);
            }
        }
        if (timestamp == null || signatures.isEmpty() || signatures.size() > 2
                || !timestamp.matches("[0-9]{1,12}")) {
            throw new IllegalArgumentException("Missing signature fields");
        }
        return new ParsedHeader(timestamp, List.copyOf(signatures));
    }

    private record ParsedHeader(String timestamp, List<String> signatures) {}
}
