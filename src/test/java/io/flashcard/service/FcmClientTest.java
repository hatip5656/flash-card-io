package io.flashcard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flashcard.config.AppProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FcmClientTest {

    private static final String TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String SEND_URL = "https://fcm.googleapis.com/v1/projects/demo-project/messages:send";

    private HttpClient http;
    private AppProperties props;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-06T08:00:00Z"), ZoneOffset.UTC);

    @BeforeEach
    void setUp() throws Exception {
        http = mock(HttpClient.class);
        props = new AppProperties();
        var gen = KeyPairGenerator.getInstance("RSA");
        gen.initialize(2048);
        String pem = "-----BEGIN PRIVATE KEY-----\n"
            + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(gen.generateKeyPair().getPrivate().getEncoded())
            + "\n-----END PRIVATE KEY-----\n";
        props.setFirebaseServiceAccount(mapper.writeValueAsString(Map.of(
            "type", "service_account", "project_id", "demo-project",
            "client_email", "push@demo-project.iam.gserviceaccount.com", "private_key", pem)));
    }

    @Test
    void notConfiguredWhenBlank() {
        props.setFirebaseServiceAccount("");
        var fcm = new FcmClient(http, mapper, props, clock);
        assertFalse(fcm.isConfigured());
        assertEquals(FcmClient.SendResult.FAILED, fcm.send("tok", "t", "b"));
        verifyNoInteractions(http);
    }

    @Test
    void sendsWithBearerAndCachesToken() throws Exception {
        respond(Map.of(TOKEN_URL, ok("{\"access_token\":\"ya29.abc\",\"expires_in\":3600}"),
            SEND_URL, ok("{\"name\":\"projects/demo-project/messages/1\"}")));
        var fcm = new FcmClient(http, mapper, props, clock);

        assertTrue(fcm.isConfigured());
        assertEquals(FcmClient.SendResult.SENT, fcm.send("device-1", "Wordagram", "Hello"));
        assertEquals(FcmClient.SendResult.SENT, fcm.send("device-2", "Wordagram", "Hello"));

        var requests = ArgumentCaptor.forClass(HttpRequest.class);
        verify(http, times(3)).send(requests.capture(), any());
        List<HttpRequest> all = requests.getAllValues();
        assertEquals(1, all.stream().filter(r -> r.uri().toString().equals(TOKEN_URL)).count(), "token fetched once");
        HttpRequest send = all.get(1);
        assertEquals(SEND_URL, send.uri().toString());
        assertEquals("Bearer ya29.abc", send.headers().firstValue("Authorization").orElseThrow());
    }

    @Test
    void unregisteredIsDeadToken() throws Exception {
        respond(Map.of(TOKEN_URL, ok("{\"access_token\":\"ya29.abc\",\"expires_in\":3600}"),
            SEND_URL, response(404, "{\"error\":{\"status\":\"NOT_FOUND\",\"details\":[{\"errorCode\":\"UNREGISTERED\"}]}}")));
        var fcm = new FcmClient(http, mapper, props, clock);

        assertEquals(FcmClient.SendResult.DEAD_TOKEN, fcm.send("gone", "t", "b"));
    }

    @Test
    void invalidTokenIsDeadToken() throws Exception {
        respond(Map.of(TOKEN_URL, ok("{\"access_token\":\"ya29.abc\",\"expires_in\":3600}"),
            SEND_URL, response(400, "{\"error\":{\"status\":\"INVALID_ARGUMENT\",\"details\":[{\"errorCode\":\"INVALID_ARGUMENT\"}]}}")));
        var fcm = new FcmClient(http, mapper, props, clock);

        assertEquals(FcmClient.SendResult.DEAD_TOKEN, fcm.send("bad", "t", "b"));
    }

    @Test
    void serverErrorIsFailed() throws Exception {
        respond(Map.of(TOKEN_URL, ok("{\"access_token\":\"ya29.abc\",\"expires_in\":3600}"),
            SEND_URL, response(503, "{\"error\":{\"status\":\"UNAVAILABLE\"}}")));
        var fcm = new FcmClient(http, mapper, props, clock);

        assertEquals(FcmClient.SendResult.FAILED, fcm.send("tok", "t", "b"));
    }

    // ── helpers ──

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void respond(Map<String, HttpResponse<String>> byUrl) throws Exception {
        when(http.send(any(), any(HttpResponse.BodyHandler.class))).thenAnswer(inv -> {
            HttpRequest r = inv.getArgument(0);
            return (HttpResponse) byUrl.get(r.uri().toString());
        });
    }

    private static HttpResponse<String> ok(String body) {
        return response(200, body);
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(int status, String body) {
        HttpResponse<String> r = mock(HttpResponse.class);
        when(r.statusCode()).thenReturn(status);
        when(r.body()).thenReturn(body);
        return r;
    }
}
