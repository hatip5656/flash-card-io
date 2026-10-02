package io.flashcard.service;

import io.flashcard.config.AdminProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

@Service
public class JwtService {

    private final AdminProperties adminProperties;

    public JwtService(AdminProperties adminProperties) {
        this.adminProperties = adminProperties;
    }

    public String generateToken(String subject) {
        long exp = Instant.now().plusSeconds(86400).getEpochSecond(); // 24h
        String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
        String payload = base64Url("{\"sub\":\"" + subject + "\",\"exp\":" + exp + ",\"role\":\"admin\"}");
        String signature = sign(header + "." + payload);
        return header + "." + payload + "." + signature;
    }

    public String validateAndGetSubject(String token) {
        if (token == null || token.isBlank()) return null;
        String[] parts = token.split("\\.");
        if (parts.length != 3) return null;

        String expectedSig = sign(parts[0] + "." + parts[1]);
        if (!expectedSig.equals(parts[2])) return null;

        try {
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            // Simple JSON parsing — extract exp and sub
            long exp = Long.parseLong(extractJsonValue(payloadJson, "exp"));
            if (Instant.now().getEpochSecond() > exp) return null;
            return extractJsonValue(payloadJson, "sub");
        } catch (Exception e) {
            return null;
        }
    }

    private String sign(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(adminProperties.getJwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException("JWT signing failed", e);
        }
    }

    private static String base64Url(String json) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }

    private static String extractJsonValue(String json, String key) {
        String search = "\"" + key + "\":";
        int start = json.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        // Skip whitespace and quotes
        while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == '"')) start++;
        int end = start;
        while (end < json.length() && json.charAt(end) != ',' && json.charAt(end) != '}' && json.charAt(end) != '"') end++;
        return json.substring(start, end);
    }
}
