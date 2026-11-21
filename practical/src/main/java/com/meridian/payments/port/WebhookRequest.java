package com.meridian.payments.port;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public record WebhookRequest(byte[] rawBody, Map<String, String> headers) {
    public WebhookRequest {
        rawBody = rawBody == null ? null : rawBody.clone();
        Map<String, String> normalized = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        if (headers != null) {
            headers.forEach((key, value) -> normalized.put(key.toLowerCase(Locale.ROOT), value));
        }
        headers = Map.copyOf(normalized);
    }

    @Override
    public byte[] rawBody() {
        return rawBody == null ? null : rawBody.clone();
    }

    public String header(String name) {
        return headers.get(name.toLowerCase(Locale.ROOT));
    }
}
