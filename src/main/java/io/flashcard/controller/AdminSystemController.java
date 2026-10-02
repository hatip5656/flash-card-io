package io.flashcard.controller;

import io.flashcard.config.AppProperties;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/system")
public class AdminSystemController {

    private final JdbcClient jdbc;
    private final RedisConnectionFactory redisFactory;
    private final HttpClient httpClient;
    private final AppProperties appProperties;

    public AdminSystemController(JdbcClient jdbc, RedisConnectionFactory redisFactory,
                                 HttpClient httpClient, AppProperties appProperties) {
        this.jdbc = jdbc;
        this.redisFactory = redisFactory;
        this.httpClient = httpClient;
        this.appProperties = appProperties;
    }

    @GetMapping("/health")
    public ResponseEntity<?> health() {
        Map<String, Object> result = new LinkedHashMap<>();

        // Database
        try {
            jdbc.sql("SELECT 1").query(Integer.class).single();
            result.put("database", Map.of("status", "up"));
        } catch (Exception e) {
            result.put("database", Map.of("status", "down", "error", e.getMessage()));
        }

        // Redis
        try {
            var conn = redisFactory.getConnection();
            conn.ping();
            conn.close();
            result.put("redis", Map.of("status", "up"));
        } catch (Exception e) {
            result.put("redis", Map.of("status", "down", "error", e.getMessage()));
        }

        // TTS Service
        try {
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(appProperties.getTtsApiUrl() + "/health"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                result.put("tts", Map.of("status", "up", "detail", resp.body()));
            } else {
                result.put("tts", Map.of("status", "down", "httpStatus", resp.statusCode()));
            }
        } catch (Exception e) {
            result.put("tts", Map.of("status", "down", "error", e.getMessage()));
        }

        return ResponseEntity.ok(result);
    }
}
