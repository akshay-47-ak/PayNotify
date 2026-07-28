/*
 * File: JwtService.java
 * Created: 2026-07-28
 * Author: Akshay Athavale
 * Use: Creates and validates HMAC-SHA256 JWT tokens for PayNotify API and WebSocket security.
 */
package com.acme.PayNotify.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class JwtService {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    @Value("${payment.security.jwt.secret:PayNotifyDevelopmentJwtSecretChangeThisBeforeProduction12345}")
    private String jwtSecret;

    @Value("${payment.security.jwt.expiry-minutes:480}")
    private long jwtExpiryMinutes;

    public TokenResponse createToken(String subject, String enterpriseCode, String role, String clientType) {
        long expiresAt = Instant.now().plusSeconds(jwtExpiryMinutes * 60).toEpochMilli();

        String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String payloadJson = "{"
                + "\"sub\":\"" + escape(subject) + "\","
                + "\"enterpriseCode\":\"" + escape(enterpriseCode) + "\","
                + "\"role\":\"" + escape(role) + "\","
                + "\"clientType\":\"" + escape(clientType) + "\","
                + "\"exp\":" + expiresAt
                + "}";

        String encodedHeader = base64Url(headerJson.getBytes(StandardCharsets.UTF_8));
        String encodedPayload = base64Url(payloadJson.getBytes(StandardCharsets.UTF_8));
        String signingInput = encodedHeader + "." + encodedPayload;
        String signature = base64Url(sign(signingInput));

        return new TokenResponse(signingInput + "." + signature, expiresAt);
    }

    public JwtClaims validateToken(String token) {
        if (token == null || token.trim().isEmpty()) {
            throw new RuntimeException("JWT token is required");
        }

        String[] parts = token.trim().split("\\.");
        if (parts.length != 3) {
            throw new RuntimeException("Invalid JWT token");
        }

        String signingInput = parts[0] + "." + parts[1];
        String expectedSignature = base64Url(sign(signingInput));
        if (!constantTimeEquals(expectedSignature, parts[2])) {
            throw new RuntimeException("Invalid JWT signature");
        }

        String payloadJson = new String(BASE64_URL_DECODER.decode(parts[1]), StandardCharsets.UTF_8);
        Map<String, String> payload = parseFlatJson(payloadJson);

        long expiresAt;
        try {
            expiresAt = Long.parseLong(payload.getOrDefault("exp", "0"));
        } catch (NumberFormatException e) {
            throw new RuntimeException("Invalid JWT expiry");
        }

        if (expiresAt <= Instant.now().toEpochMilli()) {
            throw new RuntimeException("JWT token is expired");
        }

        return new JwtClaims(
                payload.get("sub"),
                payload.get("enterpriseCode"),
                payload.get("role"),
                payload.get("clientType"),
                expiresAt
        );
    }

    private byte[] sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new RuntimeException("Unable to sign JWT token", e);
        }
    }

    private String base64Url(byte[] value) {
        return BASE64_URL_ENCODER.encodeToString(value);
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }

        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual.getBytes(StandardCharsets.UTF_8);
        if (expectedBytes.length != actualBytes.length) {
            return false;
        }

        int result = 0;
        for (int i = 0; i < expectedBytes.length; i++) {
            result |= expectedBytes[i] ^ actualBytes[i];
        }
        return result == 0;
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Map<String, String> parseFlatJson(String json) {
        Map<String, String> values = new LinkedHashMap<>();
        String body = json.trim();
        if (body.startsWith("{")) {
            body = body.substring(1);
        }
        if (body.endsWith("}")) {
            body = body.substring(0, body.length() - 1);
        }

        String[] pairs = body.split(",");
        for (String pair : pairs) {
            int separator = pair.indexOf(':');
            if (separator < 0) {
                continue;
            }
            String key = unquote(pair.substring(0, separator).trim());
            String value = unquote(pair.substring(separator + 1).trim());
            values.put(key, value);
        }
        return values;
    }

    private String unquote(String value) {
        String result = value;
        if (result.startsWith("\"") && result.endsWith("\"") && result.length() >= 2) {
            result = result.substring(1, result.length() - 1);
        }
        return result.replace("\\\"", "\"").replace("\\\\", "\\");
    }

    public record TokenResponse(String token, Long expiresAt) {
    }

    public record JwtClaims(
            String subject,
            String enterpriseCode,
            String role,
            String clientType,
            Long expiresAt
    ) {
    }
}
