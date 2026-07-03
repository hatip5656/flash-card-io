package io.flashcard.service;

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
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class EkilexService {

    private static final Logger log = LoggerFactory.getLogger(EkilexService.class);
    private static final String API_BASE = "https://ekilex.ee/api";
    private static final int MAX_DETAIL_LOOKUPS = 10;
    private static final String LANG_EST = "est";
    private static final String LANG_ENG = "eng";
    private static final Set<String> VALID_LEVELS = Set.of("A1", "A2", "B1", "B2");
    private static final String[] SEARCH_PATTERNS = {
        "a*", "e*", "i*", "k*", "l*", "m*", "n*", "o*", "p*", "r*",
        "s*", "t*", "u*", "v*", "õ*", "ä*", "ö*", "ü*", "h*", "j*"
    };

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final TokenBucketRateLimiter rateLimiter = new TokenBucketRateLimiter(30, 60_000);

    public EkilexService(AppProperties appProperties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public record EkilexWord(int wordId, String wordValue, String cefrLevel, String english,
                             String pos, List<Usage> usages) {}
    public record Usage(String estonian, String english) {}

    private JsonNode apiRequest(String path, String apiKey) {
        if (!rateLimiter.tryConsume()) {
            log.warn("[ekilex] Rate limited, skipping {}", path);
            return null;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + path))
                .header("ekilex-api-key", apiKey)
                .timeout(Duration.ofSeconds(10))
                .GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.error("[ekilex] API error {} for {}", response.statusCode(), path);
                return null;
            }
            return objectMapper.readTree(response.body());
        } catch (Exception e) {
            log.error("[ekilex] Request failed for {}: {}", path, e.getMessage());
            return null;
        }
    }

    private String extractEnglish(JsonNode lexeme) {
        JsonNode groups = lexeme.path("synonymLangGroups");
        if (groups.isArray()) {
            for (JsonNode group : groups) {
                if (LANG_ENG.equals(group.path("lang").asText())) {
                    for (JsonNode syn : group.path("synonyms")) {
                        JsonNode words = syn.path("words");
                        if (words.isArray() && !words.isEmpty()) {
                            return words.get(0).path("wordValue").asText(null);
                        }
                    }
                }
            }
        }
        return null;
    }

    private List<Usage> extractUsages(JsonNode lexeme) {
        List<Usage> results = new ArrayList<>();
        JsonNode usages = lexeme.path("usages");
        if (!usages.isArray()) return results;
        for (JsonNode usage : usages) {
            String est = usage.has("value") ? usage.path("value").asText("") : usage.path("valuePrese").asText("");
            est = est.replaceAll("<[^>]*>", "");
            if (est.isBlank()) continue;
            String eng = "";
            for (JsonNode t : usage.path("translations")) {
                if (LANG_ENG.equals(t.path("lang").asText())) {
                    eng = t.path("value").asText("").replaceAll("<[^>]*>", "");
                    break;
                }
            }
            results.add(new Usage(est, eng));
        }
        return results;
    }

    private String extractPos(JsonNode lexeme) {
        JsonNode pos = lexeme.path("pos");
        return pos.isArray() && !pos.isEmpty() ? pos.get(0).path("value").asText(null) : null;
    }

    public List<EkilexWord> searchWord(String word, String apiKey) {
        JsonNode data = apiRequest("/word/search/" + URLEncoder.encode(word, StandardCharsets.UTF_8), apiKey);
        if (data == null || !data.has("words")) return List.of();

        List<EkilexWord> results = new ArrayList<>();
        for (JsonNode w : data.path("words")) {
            if (!LANG_EST.equals(w.path("lang").asText())) continue;
            int wordId = w.path("wordId").asInt();
            String wordValue = w.path("wordValue").asText();

            JsonNode details = apiRequest("/word/details/" + wordId, apiKey);
            if (details == null) continue;

            for (JsonNode lexeme : details.path("lexemes")) {
                String cefrLevel = lexeme.path("lexemeProficiencyLevelCode").asText(null);
                if (cefrLevel == null || !VALID_LEVELS.contains(cefrLevel)) continue;
                String english = extractEnglish(lexeme);
                if (english == null) continue;
                String pos = extractPos(lexeme);
                List<Usage> usages = extractUsages(lexeme);

                results.add(new EkilexWord(wordId, wordValue, cefrLevel, english.toLowerCase(), pos,
                    usages.size() > 3 ? usages.subList(0, 3) : usages));
            }
        }
        return results;
    }

    public EkilexWord getRandomWordForLevel(String level, Set<String> sentWordValues, String apiKey) {
        String pattern = SEARCH_PATTERNS[ThreadLocalRandom.current().nextInt(SEARCH_PATTERNS.length)];
        JsonNode data = apiRequest("/word/search/" + URLEncoder.encode(pattern, StandardCharsets.UTF_8), apiKey);
        if (data == null || !data.has("words")) return null;

        JsonNode wordsNode = data.path("words");
        List<JsonNode> shuffled = new ArrayList<>();
        wordsNode.forEach(shuffled::add);
        Collections.shuffle(shuffled);

        int lookups = 0;
        for (JsonNode w : shuffled) {
            if (!LANG_EST.equals(w.path("lang").asText())) continue;
            String wordValue = w.path("wordValue").asText();
            if (sentWordValues.contains(wordValue)) continue;
            if (++lookups > MAX_DETAIL_LOOKUPS) break;

            int wordId = w.path("wordId").asInt();
            JsonNode details = apiRequest("/word/details/" + wordId, apiKey);
            if (details == null) continue;

            for (JsonNode lexeme : details.path("lexemes")) {
                String cefrLevel = lexeme.path("lexemeProficiencyLevelCode").asText(null);
                if (!level.equals(cefrLevel)) continue;
                String english = extractEnglish(lexeme);
                if (english == null) continue;
                String pos = extractPos(lexeme);
                List<Usage> usages = extractUsages(lexeme);

                return new EkilexWord(wordId, wordValue, cefrLevel, english.toLowerCase(), pos,
                    usages.size() > 3 ? usages.subList(0, 3) : usages);
            }
        }
        return null;
    }
}
