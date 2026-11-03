package io.flashcard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flashcard.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class GoogleTokenVerifierTest {

    private HttpClient http;
    private GoogleTokenVerifier verifier;

    @BeforeEach
    void setUp() {
        http = mock(HttpClient.class);
        AppProperties props = new AppProperties();
        props.setGoogleClientIds(List.of("web-client.apps.googleusercontent.com", " "));
        verifier = new GoogleTokenVerifier(http, new ObjectMapper(), props);
    }

    @SuppressWarnings("unchecked")
    private void tokeninfo(int status, String body) throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        when(http.send(any(), any(HttpResponse.BodyHandler.class))).thenReturn((HttpResponse) response);
    }

    @Test
    void acceptsATokenForOurClient() throws Exception {
        tokeninfo(200, """
            {"iss":"https://accounts.google.com","aud":"web-client.apps.googleusercontent.com","sub":"123",
             "email":"a@b.c","email_verified":"true","name":"Ann Lee","given_name":"Ann"}""");

        var profile = verifier.verify("token");

        assertEquals("123", profile.subject());
        assertEquals("a@b.c", profile.email());
        assertEquals("Ann", profile.givenName());
    }

    @Test
    void rejectsATokenIssuedToAnotherApp() throws Exception {
        tokeninfo(200, """
            {"iss":"accounts.google.com","aud":"someone-else.apps.googleusercontent.com","sub":"123"}""");

        assertThrows(GoogleTokenVerifier.InvalidTokenException.class, () -> verifier.verify("token"));
    }

    @Test
    void dropsAnUnverifiedEmail() throws Exception {
        tokeninfo(200, """
            {"iss":"accounts.google.com","aud":"web-client.apps.googleusercontent.com","sub":"123",
             "email":"a@b.c","email_verified":"false"}""");

        assertNull(verifier.verify("token").email());
    }

    @Test
    void expiredOrForgedTokensAreRejected() throws Exception {
        tokeninfo(400, "{\"error\":\"invalid_token\"}");

        assertThrows(GoogleTokenVerifier.InvalidTokenException.class, () -> verifier.verify("token"));
    }

    @Test
    void blankClientIdsMeanNotConfigured() {
        AppProperties props = new AppProperties();
        props.setGoogleClientIds(List.of(""));
        assertFalse(new GoogleTokenVerifier(http, new ObjectMapper(), props).isConfigured());
        assertTrue(verifier.isConfigured());
    }
}
