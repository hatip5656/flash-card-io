package io.flashcard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.flashcard.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
public class TranslationService {

    private static final Logger log = LoggerFactory.getLogger(TranslationService.class);

    private final AppProperties appProperties;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public TranslationService(AppProperties appProperties, HttpClient httpClient, ObjectMapper objectMapper) {
        this.appProperties = appProperties;
        this.httpClient = httpClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Translate a single text.
     * @return translated text, or null on failure
     */
    public String translate(String text, String source, String target) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                "text", text, "source", source, "target", target));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(appProperties.getTranslatorApiUrl() + "/v1/translate"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(15))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("[translator] Error {}: {}", response.statusCode(), response.body().substring(0, Math.min(200, response.body().length())));
                return null;
            }

            var result = objectMapper.readValue(response.body(), Map.class);
            return (String) result.get("translated_text");
        } catch (Exception e) {
            log.error("[translator] Failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Batch translate up to 32 texts at once.
     * @return list of translated texts, or null on failure
     */
    @SuppressWarnings("unchecked")
    public List<String> translateBatch(List<String> texts, String source, String target) {
        if (texts == null || texts.isEmpty()) return List.of();
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                "texts", texts, "source", source, "target", target));

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(appProperties.getTranslatorApiUrl() + "/v1/translate/batch"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .timeout(Duration.ofSeconds(30))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("[translator] Batch error {}: {}", response.statusCode(), response.body().substring(0, Math.min(200, response.body().length())));
                return null;
            }

            var result = objectMapper.readValue(response.body(), Map.class);
            return (List<String>) result.get("translations");
        } catch (Exception e) {
            log.error("[translator] Batch failed: {}", e.getMessage());
            return null;
        }
    }
}
