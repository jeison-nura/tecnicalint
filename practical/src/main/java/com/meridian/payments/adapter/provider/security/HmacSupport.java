package com.meridian.payments.adapter.provider.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;

final class HmacSupport {
    private HmacSupport() {}

    static byte[] sha256(String secret, byte[] message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(message);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
        }
    }

    static boolean constantTimeEquals(byte[] expected, byte[] supplied) {
        return MessageDigest.isEqual(expected, supplied);
    }

    static byte[] utf8(String prefix, byte[] rawBody) {
        byte[] prefixBytes = prefix.getBytes(StandardCharsets.UTF_8);
        byte[] result = new byte[prefixBytes.length + rawBody.length];
        System.arraycopy(prefixBytes, 0, result, 0, prefixBytes.length);
        System.arraycopy(rawBody, 0, result, prefixBytes.length, rawBody.length);
        return result;
    }

}
