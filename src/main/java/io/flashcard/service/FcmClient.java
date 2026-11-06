package io.flashcard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.flashcard.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Set;

/**
 * Sends push notifications through Firebase Cloud Messaging (HTTP v1). Authenticates as the service
 * account in {@code FIREBASE_SERVICE_ACCOUNT}: a signed JWT is exchanged for an access token, which is
 * reused until shortly before it expires. Without the setting nothing is sent.
 */
@Service
public class FcmClient {

    public enum SendResult { SENT, DEAD_TOKEN, FAILED }

    private static final Logger log = LoggerFactory.getLogger(FcmClient.class);
    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String SCOPE = "https://www.googleapis.com/auth/firebase.messaging";
    // FCM's answer for a token that no longer belongs to an installed app
    private static final Set<String> DEAD_TOKEN_CODES = Set.of("UNREGISTERED", "INVALID_ARGUMENT");

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final String projectId;
    private final String clientEmail;
    private final PrivateKey key;

    private String accessToken;
    private Instant accessTokenExpires = Instant.EPOCH;

    public FcmClient(HttpClient http, ObjectMapper mapper, AppProperties props, Clock clock) {
        this.http = http;
        this.mapper = mapper;
        this.clock = clock;
        String json = props.getFirebaseServiceAccount();
        String project = null, email = null;
        PrivateKey pk = null;
        if (json != null && !json.isBlank()) {
            try {
                JsonNode sa = mapper.readTree(json);
                project = sa.path("project_id").asText(null);
                email = sa.path("client_email").asText(null);
                pk = parseKey(sa.path("private_key").asText(""));
            } catch (Exception e) {
                // Never log the value itself: it is a private key
                log.error("FIREBASE_SERVICE_ACCOUNT is not a valid service-account JSON; push notifications are off");
            }
        }
        this.projectId = project;
        this.clientEmail = email;
        this.key = project != null && email != null ? pk : null;
    }

    public boolean isConfigured() {
        return key != null;
    }

    public SendResult send(String token, String title, String body) {
        if (!isConfigured()) return SendResult.FAILED;
        try {
            String message = mapper.writeValueAsString(Map.of("message", Map.of(
                "token", token,
                "notification", Map.of("title", title, "body", body),
                "android", Map.of("priority", "high"))));
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create("https://fcm.googleapis.com/v1/projects/" + projectId + "/messages:send"))
                .timeout(Duration.ofSeconds(10))
                .header("Authorization", "Bearer " + accessToken())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(message))
                .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            int status = response.statusCode();
            if (status / 100 == 2) return SendResult.SENT;
            if (status == 404 || (status == 400 && hasDeadTokenCode(response.body()))) return SendResult.DEAD_TOKEN;
            log.warn("FCM send failed with {}", status);
            return SendResult.FAILED;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SendResult.FAILED;
        } catch (Exception e) {
            log.warn("FCM send failed: {}", e.getMessage());
            return SendResult.FAILED;
        }
    }

    private synchronized String accessToken() throws Exception {
        Instant now = clock.instant();
        if (accessToken != null && now.isBefore(accessTokenExpires.minus(Duration.ofMinutes(5)))) return accessToken;
        String form = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", StandardCharsets.UTF_8)
            + "&assertion=" + URLEncoder.encode(signedJwt(now), StandardCharsets.UTF_8);
        HttpResponse<String> response = http.send(HttpRequest.newBuilder(URI.create(TOKEN_URL))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("Google token endpoint returned " + response.statusCode());
        JsonNode body = mapper.readTree(response.body());
        accessToken = body.path("access_token").asText();
        accessTokenExpires = now.plusSeconds(body.path("expires_in").asLong(3600));
        return accessToken;
    }

    private String signedJwt(Instant now) throws Exception {
        Base64.Encoder b64 = Base64.getUrlEncoder().withoutPadding();
        String header = b64.encodeToString("{\"alg\":\"RS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String claims = b64.encodeToString(mapper.writeValueAsBytes(Map.of(
            "iss", clientEmail, "scope", SCOPE, "aud", TOKEN_URL,
            "iat", now.getEpochSecond(), "exp", now.getEpochSecond() + 3600)));
        Signature signer = Signature.getInstance("SHA256withRSA");
        signer.initSign(key);
        signer.update((header + "." + claims).getBytes(StandardCharsets.UTF_8));
        return header + "." + claims + "." + b64.encodeToString(signer.sign());
    }

    private boolean hasDeadTokenCode(String body) {
        try {
            for (JsonNode d : mapper.readTree(body).path("error").path("details")) {
                if (DEAD_TOKEN_CODES.contains(d.path("errorCode").asText())) return true;
            }
        } catch (Exception ignored) {
            // not JSON: treat as a plain failure
        }
        return false;
    }

    private static PrivateKey parseKey(String pem) throws Exception {
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "").replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
    }
}
