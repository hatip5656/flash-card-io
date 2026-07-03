package io.flashcard.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
public class TatoebaService {

    private static final Logger log = LoggerFactory.getLogger(TatoebaService.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public record TatoebaSentence(int id, String estonian, String english) {}

    public TatoebaService(ObjectMapper objectMapper, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public List<TatoebaSentence> searchSentences(String word, int limit) {
        try {
            String url = "https://tatoeba.org/en/api_v0/search?from=est&to=eng&query="
                + URLEncoder.encode(word, StandardCharsets.UTF_8) + "&per_page=" + limit;
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "flash-card-io/1.0")
                .timeout(Duration.ofSeconds(5))
                .GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("[tatoeba] API error: {}", response.statusCode());
                return List.of();
            }
            JsonNode data = objectMapper.readTree(response.body());
            List<TatoebaSentence> sentences = new ArrayList<>();
            for (JsonNode result : data.path("results")) {
                String estonian = result.path("text").asText();
                for (JsonNode transGroup : result.path("translations")) {
                    for (JsonNode t : transGroup) {
                        if ("eng".equals(t.path("lang").asText())) {
                            sentences.add(new TatoebaSentence(result.path("id").asInt(), estonian, t.path("text").asText()));
                            break;
                        }
                    }
                    if (!sentences.isEmpty() && sentences.get(sentences.size() - 1).estonian().equals(estonian)) break;
                }
            }
            return sentences;
        } catch (Exception e) {
            log.error("[tatoeba] Error searching for \"{}\": {}", word, e.getMessage());
            return List.of();
        }
    }
}
