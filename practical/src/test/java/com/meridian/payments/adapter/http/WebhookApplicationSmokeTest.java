package com.meridian.payments.adapter.http;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import com.meridian.payments.port.WebhookSecretProviderPort;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(WebhookApplicationSmokeTest.TestSecretsConfiguration.class)
class WebhookApplicationSmokeTest {
    private static final String WEBHOOK_SECRET = "smoke-test-secret";

    @Autowired
    private TestRestTemplate http;

    @Test
    void startsApplicationAndProcessesValidWebhookAndDuplicateOverHttp() throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        String body = """
                {"id":"evt-http-smoke","type":"charge.succeeded","created":%d,
                 "data":{"charge":{"id":"txn-http-smoke","amount":12500,"currency":"MXN",
                 "reference":"POL-MX-000000001","paid_at":%d}}}
                """.formatted(timestamp, timestamp).strip();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Quetzal-Signature", "t=" + timestamp + ",v1=" + signature(timestamp, body));

        var first = post(body, headers);
        var retry = post(body, headers);

        assertEquals(HttpStatus.OK, first.getStatusCode());
        assertEquals(HttpStatus.OK, retry.getStatusCode());
    }

    private org.springframework.http.ResponseEntity<Void> post(String body, HttpHeaders headers) {
        return http.exchange(
                "/webhooks/v1/payments", HttpMethod.POST,
                new HttpEntity<>(body, headers), Void.class);
    }

    private String signature(long timestamp, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(WEBHOOK_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] signed = (timestamp + ".").getBytes(StandardCharsets.UTF_8);
        mac.update(signed);
        return HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    @TestConfiguration
    static class TestSecretsConfiguration {
        @Bean
        @Primary
        WebhookSecretProviderPort smokeTestSecrets() {
            return provider -> List.of(WEBHOOK_SECRET);
        }
    }
}
