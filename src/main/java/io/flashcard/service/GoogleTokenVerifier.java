package io.flashcard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.flashcard.config.AppProperties;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Checks a Google ID token from the app's native sign-in. Google's tokeninfo endpoint validates the
 * signature and expiry; here we check the token was issued to one of our client ids. Sign-in is rare,
 * so one network call per sign-in is fine and saves keeping Google's signing keys.
 */
@Service
public class GoogleTokenVerifier {

    private static final String TOKENINFO = "https://oauth2.googleapis.com/tokeninfo?id_token=";
    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    public record GoogleProfile(String subject, String email, String name, String givenName) {}

    public static class InvalidTokenException extends Exception {
        public InvalidTokenException(String message) { super(message); }
    }

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final AppProperties props;

    public GoogleTokenVerifier(HttpClient http, ObjectMapper mapper, AppProperties props) {
        this.http = http;
        this.mapper = mapper;
        this.props = props;
    }

    public boolean isConfigured() {
        return !clientIds().isEmpty();
    }

    public GoogleProfile verify(String idToken) throws InvalidTokenException, IOException, InterruptedException {
        if (idToken == null || idToken.isBlank()) throw new InvalidTokenException("idToken is required");
        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKENINFO + URLEncoder.encode(idToken, StandardCharsets.UTF_8)))
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        // Google answers 400 for expired, malformed or forged tokens
        if (response.statusCode() == 400) throw new InvalidTokenException("Google did not accept the sign-in token");
        if (response.statusCode() != 200) throw new IOException("Google tokeninfo returned " + response.statusCode());

        JsonNode claims = mapper.readTree(response.body());
        if (!clientIds().contains(claims.path("aud").asText())) {
            throw new InvalidTokenException("The sign-in token was issued for another app");
        }
        if (!ISSUERS.contains(claims.path("iss").asText())) {
            throw new InvalidTokenException("The sign-in token was not issued by Google");
        }
        String subject = claims.path("sub").asText("");
        if (subject.isEmpty()) throw new InvalidTokenException("The sign-in token has no account id");

        // tokeninfo returns booleans as strings
        boolean verified = "true".equals(claims.path("email_verified").asText());
        String email = verified ? emptyToNull(claims.path("email").asText(null)) : null;
        return new GoogleProfile(subject, email,
            emptyToNull(claims.path("name").asText(null)), emptyToNull(claims.path("given_name").asText(null)));
    }

    private List<String> clientIds() {
        return props.getGoogleClientIds().stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String emptyToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
}
